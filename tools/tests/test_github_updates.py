"""Release gates exercised with a real signed APK and real two-branch Git remotes."""
import dataclasses
import hashlib
import json
import os
from pathlib import Path
import shutil
import shlex
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import apk_release as release
from sync_github_updates import APP_BRANCH, Repository
import sync_github_updates

APK = ROOT / "downloads/mapswork-0.4.0-test.apk"
EXPECTED_SHA = "16758cb07a575537ac7af8598a3192376e013283187c6a62cb6951a7fe03c850"


def git(cwd, *args):
    return subprocess.check_output(["git", "-C", str(cwd), *args], text=True, stderr=subprocess.PIPE).strip()


class ApkGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tools = release.find_build_tools(os.environ.get("MAPSWORK_TEST_BUILD_TOOLS"))
        cls.verified = release.verify_apk(APK, cls.tools)

    def test_real_published_apk_identity_hash_size_and_signature(self):
        v = self.verified
        self.assertEqual((v.version_code, v.version_name, v.min_sdk), (4, "0.4.0", 26))
        self.assertEqual((v.sha256, v.size_bytes), (EXPECTED_SHA, 18653298))
        self.assertEqual(v.signer_sha256, release.TRUSTED_CERT_SHA256)
        manifest = release.make_manifest(v, "a" * 40, "시험판")
        self.assertIn("/" + "a" * 40 + "/downloads/", manifest["apkUrl"])

    def test_tampered_apk_is_rejected_by_android_signature_verifier(self):
        with tempfile.TemporaryDirectory() as directory:
            altered = Path(directory) / APK.name
            shutil.copyfile(APK, altered)
            with zipfile.ZipFile(altered, "a") as archive:
                archive.writestr("assets/untrusted-change", b"changed without signing")
            with self.assertRaises(release.ReleaseError):
                release.verify_apk(altered, self.tools)

    def test_valid_signature_from_untrusted_certificate_is_rejected(self):
        with patch.object(release, "TRUSTED_CERT_SHA256", "0" * 64):
            with self.assertRaisesRegex(release.ReleaseError, "certificate"):
                release.verify_apk(APK, self.tools)

    def test_filename_must_match_actual_apk_version(self):
        with tempfile.TemporaryDirectory() as directory:
            wrong = Path(directory) / "mapswork-9.9.9-test.apk"
            shutil.copyfile(APK, wrong)
            with self.assertRaisesRegex(release.ReleaseError, "filename"):
                release.verify_apk(wrong, self.tools)

    def test_rollbacks_and_same_version_replacement_are_rejected(self):
        manifest = release.make_manifest(self.verified, "a" * 40, "notes")
        for candidate in [dataclasses.replace(self.verified, version_code=3, version_name="0.3.0"),
                          dataclasses.replace(self.verified, version_code=5, version_name="0.3.0"),
                          dataclasses.replace(self.verified, sha256="b" * 64)]:
            with self.subTest(candidate=candidate), self.assertRaises(release.ReleaseError):
                release.enforce_progress(candidate, [manifest])

    def test_malformed_existing_metadata_blocks_publication(self):
        manifest = release.make_manifest(self.verified, "a" * 40, "notes")
        for key, value in [("versionCode", True), ("versionName", "latest"), ("sha256", "wrong")]:
            invalid = dict(manifest, **{key: value})
            with self.subTest(key=key), self.assertRaises(release.ReleaseError):
                release.enforce_progress(self.verified, [invalid])


class GitPublicationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tools = release.find_build_tools(os.environ.get("MAPSWORK_TEST_BUILD_TOOLS"))
        cls.verified = release.verify_apk(APK, cls.tools)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / "remote.git"
        self.work = self.root / "work"
        subprocess.run(["git", "init", "--quiet", "--bare", str(self.remote)], check=True)
        subprocess.run(["git", "init", "--quiet", str(self.work)], check=True)
        git(self.work, "config", "user.name", "Release Tests")
        git(self.work, "config", "user.email", "release-tests@example.invalid")
        git(self.work, "remote", "add", "origin", str(self.remote))
        git(self.work, "checkout", "--quiet", "-b", APP_BRANCH)
        self.write("downloads/" + APK.name, APK.read_bytes())
        self.write("downloads/" + APK.name + ".sha256", EXPECTED_SHA + "  " + APK.name + "\n")
        self.write("app/preserve.txt", "private app source must not appear on main\n")
        self.write("README.md", "# 이곳 — Mapswork\n\n## 0.3.0\n\n기존 설명 유지\n")
        self.write("downloads/README.md", "# old guide\n")
        self.artifact_commit = self.commit("publish signed APK")
        self.manifest = release.make_manifest(self.verified, self.artifact_commit, "기존 검증 안내")
        self.write("downloads/update.json", json.dumps(self.manifest))
        self.commit("existing metadata")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/" + APP_BRANCH)
        git(self.work, "checkout", "--quiet", "--orphan", "main")
        git(self.work, "rm", "--quiet", "-rf", ".")
        self.write("README.md", "# mapswork\n지도\n")
        self.write("이곳/README.md", "# old public guide\n")
        self.write("이곳/update.json", json.dumps(self.manifest))
        self.write("preserve.txt", "main-only content\n")
        self.commit("public landing page")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/main")
        git(self.work, "checkout", "--quiet", APP_BRANCH)
        self.repo = Repository(self.work, self.tools)

    def write(self, path, content):
        target = self.work / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content if isinstance(content, bytes) else content.encode())

    def commit(self, message):
        git(self.work, "add", ".")
        git(self.work, "commit", "--quiet", "-m", message)
        return git(self.work, "rev-parse", "HEAD")

    def refs(self):
        return {branch: git(self.remote, "rev-parse", "refs/heads/" + branch)
                for branch in [APP_BRANCH, "main"]}

    def test_both_branches_share_manifest_and_keep_unrelated_files(self):
        result = self.repo.sync(push=True)
        self.assertTrue(result["changed"])
        app = git(self.remote, "show", APP_BRANCH + ":downloads/update.json")
        main = git(self.remote, "show", "main:이곳/update.json")
        self.assertEqual(app, main)
        self.assertEqual(json.loads(app)["apkUrl"], self.manifest["apkUrl"])
        self.assertEqual(git(self.remote, "show", APP_BRANCH + ":app/preserve.txt"),
                         "private app source must not appear on main")
        self.assertEqual(git(self.remote, "show", "main:preserve.txt"), "main-only content")
        self.assertNotIn("app/", git(self.remote, "ls-tree", "-r", "--name-only", "main"))
        self.assertIn("0.4.0", git(self.remote, "show", APP_BRANCH + ":README.md"))
        self.assertNotIn("## 0.3.0", git(self.remote, "show", APP_BRANCH + ":README.md"))

    def test_second_run_is_noop_and_keeps_original_apk_commit(self):
        self.repo.sync(push=True)
        before = self.refs()
        result = self.repo.sync(push=True)
        self.assertFalse(result["changed"])
        self.assertEqual(before, self.refs())
        self.assertEqual(result["postedCommit"], self.artifact_commit)

    def test_bad_checksum_leaves_both_remote_refs_unchanged(self):
        self.write("downloads/" + APK.name + ".sha256", "0" * 64 + "  " + APK.name + "\n")
        self.commit("bad checksum")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/" + APP_BRANCH)
        before = self.refs()
        with self.assertRaises(release.ReleaseError):
            self.repo.sync(push=True)
        self.assertEqual(before, self.refs())

    def test_version_rollback_in_either_branch_blocks_all_updates(self):
        git(self.work, "checkout", "--quiet", "main")
        self.write("이곳/update.json", json.dumps(dict(self.manifest, versionCode=5, versionName="0.5.0")))
        self.commit("newer public metadata")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/main")
        before = self.refs()
        with self.assertRaises(release.ReleaseError):
            self.repo.sync(push=True)
        self.assertEqual(before, self.refs())

    def test_atomic_push_rejects_race_without_updating_other_branch(self):
        prepared = self.repo.sync(push=False)
        git(self.work, "checkout", "--quiet", "main")
        self.write("preserve.txt", "concurrent main change\n")
        self.commit("concurrent change")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/main")
        before = self.refs()
        with self.assertRaises(release.ReleaseError):
            self.repo.push(prepared)
        self.assertEqual(before, self.refs())

    def test_atomic_push_protects_branch_even_when_its_metadata_is_unchanged(self):
        self.repo.sync(push=True)
        git(self.work, "fetch", "--quiet", "origin")
        git(self.work, "checkout", "--quiet", "-B", APP_BRANCH, "origin/" + APP_BRANCH)
        self.write("downloads/README.md", "guide needs repair\n")
        self.commit("change app guide only")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/" + APP_BRANCH)
        prepared = self.repo.sync(push=False)
        self.assertNotEqual(prepared["mainBefore"], prepared["mainCommit"])
        self.assertEqual(git(self.work, "rev-parse", prepared["mainBefore"] + "^{tree}"),
                         git(self.work, "rev-parse", prepared["mainCommit"] + "^{tree}"))
        git(self.work, "checkout", "--quiet", "-B", "main", "origin/main")
        self.write("preserve.txt", "concurrent main change\n")
        self.commit("concurrent change on otherwise unchanged branch")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/main")
        before = self.refs()
        with self.assertRaises(release.ReleaseError):
            self.repo.push(prepared)
        self.assertEqual(before, self.refs())

    def test_queued_old_event_cannot_publish_after_apk_is_replaced(self):
        self.write("downloads/" + APK.name, APK.read_bytes() + b"replacement")
        self.commit("replace the APK while an old event is queued")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/" + APP_BRANCH)
        before = self.refs()
        with self.assertRaises(release.ReleaseError):
            self.repo.sync(commit=self.artifact_commit, push=True)
        self.assertEqual(before, self.refs())

    def test_late_race_after_ref_advertisement_rejects_entire_transaction(self):
        self.repo.sync(push=True)
        git(self.work, "fetch", "--quiet", "origin")
        git(self.work, "checkout", "--quiet", "-B", APP_BRANCH, "origin/" + APP_BRANCH)
        self.write("downloads/README.md", "repair only the app guide\n")
        self.commit("one-sided guide change")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/" + APP_BRANCH)
        prepared = self.repo.sync(push=False)
        race = self.repo.create_commit(prepared["mainBefore"], {"preserve.txt": b"late concurrent change\n"}, "late race")
        git(self.work, "push", "--quiet", "origin", race + ":refs/heads/test-race-object")
        hook = self.work / ".git/hooks/pre-push"
        hook.write_text("#!/bin/sh\ngit --git-dir=" + shlex.quote(str(self.remote)) +
                        " update-ref refs/heads/main " + race + "\n")
        hook.chmod(0o755)
        app_before = self.refs()[APP_BRANCH]
        with self.assertRaises(release.ReleaseError):
            self.repo.push(prepared)
        self.assertEqual(self.refs()[APP_BRANCH], app_before)
        self.assertEqual(self.refs()["main"], race)

    def pending_version_fixtures(self, include_candidate):
        # Cryptographic identity is covered by real APK tests. Here verified identities
        # simulate a refresh failure: version 0.5/code6 is posted while metadata stays code4.
        pending = dataclasses.replace(self.verified, version_code=6, version_name="0.5.0")
        candidate = dataclasses.replace(self.verified, version_code=5, version_name="0.6.0")
        names = ["0.5.0", "0.6.0"] if include_candidate else ["0.5.0"]
        for name in names:
            filename = f"mapswork-{name}-test.apk"
            self.write("downloads/" + filename, APK.read_bytes())
            self.write("downloads/" + filename + ".sha256", EXPECTED_SHA + "  " + filename + "\n")
        self.commit("pending posted releases before metadata refresh")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/" + APP_BRANCH)
        def verified(commit, path):
            return pending if "0.5.0" in path else candidate if "0.6.0" in path else self.verified
        return candidate, verified

    def test_pending_posted_versioncode_blocks_older_new_apk_publication(self):
        candidate, verified = self.pending_version_fixtures(False)
        apk = self.root / "mapswork-0.6.0-test.apk"
        apk.write_bytes(APK.read_bytes())
        before = self.refs()
        with patch.object(sync_github_updates, "verify_apk", return_value=candidate), \
                patch.object(self.repo, "verified_at", side_effect=verified):
            with self.assertRaises(release.ReleaseError):
                self.repo.publish(apk, push=True)
        self.assertEqual(before, self.refs())

    def test_pending_posted_versioncode_blocks_older_metadata_publication(self):
        candidate, verified = self.pending_version_fixtures(True)
        before = self.refs()
        with patch.object(self.repo, "verified_at", side_effect=verified):
            with self.assertRaises(release.ReleaseError):
                self.repo.sync(push=True)
        self.assertEqual(before, self.refs())

    def test_dirty_worktree_and_index_remain_unchanged(self):
        self.write("app/preserve.txt", "uncommitted app work\n")
        git(self.work, "add", "app/preserve.txt")
        self.write("private-local.txt", "do not publish\n")
        before = git(self.work, "status", "--porcelain")
        self.repo.sync(push=True)
        self.assertEqual(before, git(self.work, "status", "--porcelain"))
        self.assertEqual((self.work / "app/preserve.txt").read_text(), "uncommitted app work\n")
        self.assertNotIn("private-local.txt", git(self.remote, "ls-tree", "-r", "--name-only", APP_BRANCH))

    def test_existing_apk_publisher_calls_sync_without_reposting_binary(self):
        before = self.refs()
        result = self.repo.publish(APK, push=True)
        self.assertFalse(result["changed"])
        self.assertEqual(result["postedCommit"], self.artifact_commit)
        self.assertEqual(before, self.refs())

    def test_new_apk_publication_then_explicit_sync_preserves_previous_apk(self):
        # Model the real repository before 0.4 was published, retaining its 0.3 artifact.
        old_apk = ROOT / "downloads/mapswork-0.3.0-test.apk"
        old = release.verify_apk(old_apk, self.tools)
        old_manifest = release.make_manifest(old, self.artifact_commit, "previous release")
        git(self.work, "rm", "--quiet", "downloads/" + APK.name, "downloads/" + APK.name + ".sha256")
        self.write("downloads/" + old_apk.name, old_apk.read_bytes())
        self.write("downloads/" + old_apk.name + ".sha256", old.sha256 + "  " + old_apk.name + "\n")
        self.write("downloads/update.json", json.dumps(old_manifest))
        self.commit("pre-0.4 app branch")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/" + APP_BRANCH)
        git(self.work, "checkout", "--quiet", "main")
        self.write("이곳/update.json", json.dumps(old_manifest))
        self.commit("pre-0.4 public branch")
        git(self.work, "push", "--quiet", "origin", "HEAD:refs/heads/main")
        publication = self.repo.publish(APK, push=True)
        self.assertTrue(publication["changed"])
        synced = self.repo.sync(commit=publication["postedCommit"], push=True)
        self.assertEqual(synced["manifest"]["versionCode"], 4)
        self.assertIn(publication["postedCommit"], synced["manifest"]["apkUrl"])
        self.assertEqual(git(self.remote, "show", APP_BRANCH + ":downloads/update.json"),
                         git(self.remote, "show", "main:이곳/update.json"))
        self.assertEqual(hashlib.sha256(subprocess.check_output(
            ["git", "-C", str(self.remote), "show", APP_BRANCH + ":downloads/" + old_apk.name])).hexdigest(), old.sha256)


class WorkflowContractTests(unittest.TestCase):
    def test_actions_publisher_directly_calls_reusable_metadata_sync(self):
        publisher = (ROOT / ".github/workflows/publish-apk.yml").read_text()
        self.assertIn("needs: publish", publisher)
        self.assertIn("uses: ./.github/workflows/sync-updates.yml", publisher)
        self.assertIn("needs.publish.outputs.commit", publisher)
        updater = (ROOT / ".github/workflows/sync-updates.yml").read_text()
        self.assertIn("workflow_call:", updater)
        self.assertIn("downloads/*.apk", updater)
        self.assertNotIn("pull_request_target:", updater + publisher)


if __name__ == "__main__":
    unittest.main()
