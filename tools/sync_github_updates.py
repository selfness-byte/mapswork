#!/usr/bin/env python3
"""Verify APKs, prepare isolated commits and fast-forward both public branches."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from apk_release import (APK_PATH, MAX_APK_BYTES, REPOSITORY, ReleaseError, app_readme,
                         enforce_progress, find_build_tools, install_guide, latest_block,
                         make_manifest, replace_latest, verify_apk, version_tuple)

APP_BRANCH = "codex/nearby-shop-alert"
MAIN_BRANCH = "main"
APP_REF = "refs/remotes/origin/" + APP_BRANCH
MAIN_REF = "refs/remotes/origin/main"


class Repository:
    def __init__(self, path: Path, build_tools: Path):
        self.path, self.build_tools = path, build_tools

    def git(self, *args: str, data: bytes | None = None, env: dict | None = None) -> bytes:
        result = subprocess.run(["git", "-C", str(self.path), *args], input=data,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
        if result.returncode:
            raise ReleaseError(f"Git {args[0]} failed: {result.stderr.decode(errors='replace')[-2000:]}")
        return result.stdout

    def text(self, *args: str) -> str:
        return self.git(*args).decode().strip()

    def fetch(self):
        self.git("fetch", "--no-tags", "origin",
                 f"refs/heads/{APP_BRANCH}:{APP_REF}", f"refs/heads/main:{MAIN_REF}")
        return self.text("rev-parse", APP_REF), self.text("rev-parse", MAIN_REF)

    def read(self, commit: str, path: str) -> bytes:
        return self.git("show", f"{commit}:{path}")

    def manifests(self, app: str, main: str) -> list[dict]:
        try:
            return [json.loads(self.read(app, "downloads/update.json")),
                    json.loads(self.read(main, "이곳/update.json"))]
        except (ValueError, UnicodeError) as error:
            raise ReleaseError("Existing update JSON is invalid") from error

    def apk_paths(self, commit: str) -> list[str]:
        return [p for p in self.text("ls-tree", "-r", "--name-only", commit, "downloads").splitlines()
                if APK_PATH.fullmatch(p)]

    def create_commit(self, parent: str, changes: dict[str, bytes], message: str,
                      allow_empty: bool = False) -> str:
        # A private temporary index keeps the caller's worktree, staged work and HEAD intact.
        with tempfile.TemporaryDirectory(prefix="mapswork-git-index-") as directory:
            env = dict(os.environ, GIT_INDEX_FILE=str(Path(directory) / "index"))
            self.git("read-tree", parent, env=env)
            for path, contents in changes.items():
                digest = self.git("hash-object", "-w", "--stdin", data=contents).decode().strip()
                self.git("update-index", "--add", "--cacheinfo", "100644", digest, path, env=env)
            tree = self.git("write-tree", env=env).decode().strip()
        if tree == self.text("rev-parse", parent + "^{tree}") and not allow_empty:
            return parent
        actual = {p.decode() for p in self.git("diff", "--name-only", "-z", parent, tree).split(b"\0") if p}
        if not actual <= set(changes):
            raise ReleaseError("Commit contains files outside the publication allowlist")
        return self.git("commit-tree", tree, "-p", parent, data=(message + "\n").encode()).decode().strip()

    def push(self, result: dict):
        if result["appCommit"] != result["appBefore"] or result["mainCommit"] != result["mainBefore"]:
            # sync() ensures both refs have new children so Git sends two real CAS updates.
            self.git("push", "--atomic", "--porcelain", "origin",
                     f"{result['appCommit']}:refs/heads/{APP_BRANCH}",
                     f"{result['mainCommit']}:refs/heads/{MAIN_BRANCH}")

    def verified_at(self, source: str, path: str):
        size = int(self.text("cat-file", "-s", f"{source}:{path}"))
        if not 0 < size <= MAX_APK_BYTES:
            raise ReleaseError("Published APK exceeds the permitted size")
        with tempfile.TemporaryDirectory(prefix="mapswork-apk-verify-") as directory:
            apk = Path(directory) / Path(path).name
            apk.write_bytes(self.read(source, path))
            release = verify_apk(apk, self.build_tools)
        expected = f"{release.sha256}  {Path(path).name}\n".encode()
        if self.read(source, path + ".sha256") != expected:
            raise ReleaseError("Published checksum does not match actual APK bytes")
        return release

    def enforce_posted_progress(self, release, app: str, previous: list[dict], path: str):
        # Metadata can lag a successful APK push. Compare actual posted identities too.
        floor = min(version_tuple(m["versionName"]) for m in previous)
        for other in self.apk_paths(app):
            if other != path and version_tuple(APK_PATH.fullmatch(other)[1]) >= floor:
                baseline = self.verified_at(app, other)
                enforce_progress(release, [make_manifest(baseline, app, "Previously posted APK")])

    def sync(self, commit: str | None = None, apk_path: str | None = None,
             notes: str | None = None, push: bool = False) -> dict:
        app, main = self.fetch()
        if commit and not re.fullmatch(r"[0-9a-f]{40}", commit):
            raise ReleaseError("Source commit must be a full commit SHA")
        source = commit or app
        self.git("merge-base", "--is-ancestor", source, app)
        paths = self.apk_paths(source)
        if not paths:
            raise ReleaseError("No versioned APK found on the approved app branch")
        newest = max(paths, key=lambda p: version_tuple(APK_PATH.fullmatch(p)[1]))
        path = apk_path or newest
        if not APK_PATH.fullmatch(path) or path not in paths:
            raise ReleaseError("APK path must be a versioned downloads APK on the app branch")
        current_paths = self.apk_paths(app)
        if any(version_tuple(APK_PATH.fullmatch(p)[1]) > version_tuple(APK_PATH.fullmatch(path)[1])
               for p in current_paths):
            raise ReleaseError("A newer APK is already present on the app branch; refusing stale publication")
        for item in [path, path + ".sha256"]:
            if self.text("rev-parse", f"{source}:{item}") != self.text("rev-parse", f"{app}:{item}"):
                raise ReleaseError("APK or checksum changed since this event; refusing stale publication")
        release = self.verified_at(source, path)
        posted = self.text("log", "-1", "--format=%H", source, "--", path, path + ".sha256")
        if self.read(posted, path) != self.read(source, path) or self.read(posted, path + ".sha256") != self.read(source, path + ".sha256"):
            raise ReleaseError("APK and checksum are not available together at the pinned commit")
        previous = self.manifests(app, main)
        enforce_progress(release, previous)
        self.enforce_posted_progress(release, app, previous, path)
        if notes is None:
            matching = [m for m in previous if m["versionCode"] == release.version_code and m["sha256"] == release.sha256]
            notes = (matching[0]["notes"] if matching else
                     f"이곳 {release.version_name} 시험판입니다. APK의 버전·해시·서명을 검증했습니다. 실제 휴대폰 시험은 별도로 필요합니다.")
        manifest = make_manifest(release, posted, notes)
        metadata = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
        app_changes = {"downloads/update.json": metadata,
                       "downloads/README.md": install_guide(manifest).encode(),
                       "README.md": app_readme(self.read(app, "README.md").decode(), manifest).encode()}
        main_changes = {"이곳/update.json": metadata,
                        "이곳/README.md": install_guide(manifest, main=True).encode(),
                        "README.md": replace_latest(self.read(main, "README.md").decode(),
                                                    latest_block(manifest, "이곳/update.json")).encode()}
        message = f"docs: sync verified Mapswork {release.version_name} update information"
        result = dict(appBefore=app, mainBefore=main,
                      appCommit=self.create_commit(app, app_changes, message),
                      mainCommit=self.create_commit(main, main_changes, message),
                      postedCommit=posted, manifest=manifest, signerSha256=release.signer_sha256)
        result["changed"] = result["appCommit"] != app or result["mainCommit"] != main
        result["coordinationCommit"] = None
        if result["changed"]:
            # An unchanged ref is omitted by Git after advertisement. A tree-identical
            # child makes that ref participate in the same atomic receive transaction.
            for branch, key, parent in [(APP_BRANCH, "appCommit", app), (MAIN_BRANCH, "mainCommit", main)]:
                if result[key] == parent:
                    result[key] = self.create_commit(parent, {}, "chore: coordinate verified APK metadata transaction", allow_empty=True)
                    result["coordinationCommit"] = branch
        if push:
            self.push(result)
        return result

    def publish(self, apk: Path, push: bool = False) -> dict:
        app, main = self.fetch()
        release = verify_apk(apk, self.build_tools)
        previous = self.manifests(app, main)
        enforce_progress(release, previous)
        path = "downloads/" + apk.name
        self.enforce_posted_progress(release, app, previous, path)
        paths = self.apk_paths(app)
        if any(version_tuple(APK_PATH.fullmatch(p)[1]) > version_tuple(release.version_name) for p in paths):
            raise ReleaseError("A newer APK is already present on the app branch")
        if path in paths:
            existing = self.verified_at(app, path)
            if existing != release:
                raise ReleaseError("An already published APK cannot be replaced")
            posted = self.text("log", "-1", "--format=%H", app, "--", path, path + ".sha256")
            return dict(changed=False, postedCommit=posted, versionName=release.version_name)
        checksum = f"{release.sha256}  {apk.name}\n".encode()
        commit = self.create_commit(app, {path: apk.read_bytes(), path + ".sha256": checksum},
                                    f"build: publish verified Mapswork {release.version_name} APK")
        if push:
            self.git("push", "--porcelain", "origin", f"{commit}:refs/heads/{APP_BRANCH}")
        return dict(changed=True, postedCommit=commit, versionName=release.version_name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["sync", "publish"])
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--build-tools")
    parser.add_argument("--commit")
    parser.add_argument("--apk-path")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--notes-file", type=Path)
    parser.add_argument("--push", action="store_true", help="Publish approved fast-forward commits")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    repo = Repository(args.repo.resolve(), find_build_tools(args.build_tools))
    origin = repo.text("remote", "get-url", "origin")
    if origin.removesuffix(".git") != f"https://github.com/{REPOSITORY}":
        parser.error("CLI publication is restricted to the approved GitHub repository")
    try:
        if args.command == "publish":
            if not args.apk:
                parser.error("publish requires --apk")
            result = repo.publish(args.apk.resolve(), args.push)
        else:
            notes = args.notes_file.read_text() if args.notes_file else None
            result = repo.sync(args.commit, args.apk_path, notes, args.push)
        output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(output)
        print(output, end="")
    except (ReleaseError, OSError, KeyError, TypeError) as error:
        parser.exit(1, f"Publication stopped: {error}\n")


if __name__ == "__main__":
    main()
