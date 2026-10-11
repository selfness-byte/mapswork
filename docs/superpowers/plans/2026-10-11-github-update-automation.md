# GitHub APK Update Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify published APKs and automatically publish identical update metadata plus current download guides on the app branch and main.

**Architecture:** Python standard-library scripts verify APKs with installed Android build tools and construct both metadata commits using temporary Git indexes. GitHub Actions handles direct APK pushes and explicitly chains a reusable APK publisher to the reusable metadata updater. A single atomic, fast-forward push publishes metadata on both branches.

**Tech Stack:** Python 3, unittest, Git, aapt, apksigner, GitHub Actions, existing Ubuntu Android SDK.

**Spec:** `docs/superpowers/specs/2026-10-11-github-update-automation.md`

## Global Constraints

- Repository `selfness-byte/mapswork`; app branch `codex/nearby-shop-alert`; public branch `main`.
- Package `com.selfness.mapswork`; minSdk >= 26; APK <= 80MiB.
- Signer SHA-256 `80dfd84789e5c205b319562bf99bd33500b64000695594f1b8dbf2b58638cf40`.
- Update files `downloads/update.json` and `이곳/update.json`; immutable 40-hex APK commit URL.
- Preserve all old APKs, local unpublished source and verification records. No force push, app merge into main, Firebase deployment, permission changes, new credentials or tool installations.
- User has approved implementation, workflow/script/check/guide commits and pushes; execute inline without an additional approval gate.

## Review Focus

- Invalid or differently signed APK: no metadata or APK publication (Task 1 signature/tampering regression).
- Older version or same version with changed bytes: fail before any ref update (Task 1 progress regression).
- Concurrent branch changes: atomic push rejects all updates (Task 2 two-ref race regression).
- Repeated execution or guide-only commit: original APK commit stays pinned and no new commit (Task 2 idempotence regression).
- APK pushed with GITHUB_TOKEN: publisher directly calls reusable sync, with no dependence on a push trigger (Task 3 workflow wiring check).

### Task 1: APK verification and release rendering

**Files:** `tools/apk_release.py`, `tools/tests/test_github_updates.py`

**Interfaces:** `verify_apk(path: Path, build_tools: Path) -> VerifiedApk`, `make_manifest(release: VerifiedApk, commit: str, notes: str) -> dict`, `enforce_progress(release: VerifiedApk, manifests: list[dict]) -> None`.

- [x] Write tests for the real 0.4 APK, invalid signature, wrong filename, wrong checksum, and rollback/replacement rejection.
- [x] Run unittest; observe missing module failures before implementation.
- [x] Implement Android tool verification, strict identities, release metadata and generated README sections.
- [x] Rerun tests; expect all APK and policy checks to pass.

### Task 2: Git publication and repeatability

**Files:** `tools/sync_github_updates.py`, `tools/tests/test_github_updates.py`

**Interfaces:** `Repository.sync(commit: str | None, apk_path: str | None, notes: str | None, push: bool) -> dict`; `Repository.publish(apk: Path, push: bool) -> dict`.

- [x] Add real bare-Git fixtures with separate app/main branches and the verified APK.
- [x] Observe sync tests fail before implementation.
- [x] Implement fetch, posted-commit discovery, checksum verification, temporary-index commits, allowed paths and atomic normal push.
- [x] Assert identical JSON, pinned URL, retained app/main unrelated content, no changes on second run, rollback and concurrent-push rejection.

### Task 3: Actions and operational documentation

**Files:** `.github/workflows/sync-updates.yml`, `.github/workflows/publish-apk.yml`, `docs/GITHUB_UPDATES.ko.md`, generated README files on both branches.

- [x] Add workflow contract checks; observe missing workflow failure.
- [x] Add app-branch APK push trigger and reusable updater; trigger an initial verification on installing the workflow.
- [x] Add reusable artifact publisher with direct `needs: publish` call to updater and least required repository permissions.
- [x] Use pinned official actions, existing SDK, and fail on missing tools or protected-branch rejection.
- [x] Run all checks and obtain one independent final review; address material findings with regression tests.

### Task 4: Authorized publication and server verification

- [x] Verify current public 0.4 APK against its stored checksum/signature; generate current guide corrections.
- [x] Inspect exact app/main commit path lists before atomic fast-forward push.
- [ ] Push approved workflows/scripts/checks/guides; verify remote commit SHAs and original local-file snapshot.
- [ ] Read GitHub Actions result when accessible; report actual server execution separately from successful local checks and push.

## Execution ledger

- Task 1/2: missing implementation and missing workflow observed failing; initial suite 14/14 green.
- Task 2: queued stale APK and pre-advertisement race tests RED→GREEN; suite 17/17 green.
- Final review: independent Astra reviewer found two Important issues; late ref-advertisement race and pending posted versionCode regression tests all observed RED.
- Final: fixed both Important findings — late-race and pending-published-versionCode tests RED→GREEN; final suite 20/20 passed. YAML, shell syntax and Python compilation passed.
- Ruling: when there is a one-sided repair, create a tree-identical coordination child on the other branch — Git otherwise omits its up-to-date ref from the atomic receive transaction — cost: one empty coordination commit; complete no-op still creates no commits.
- Ruling: use current remote source for automation and preserve unpublished 0.4 application files in the original checkout — user explicitly deferred missing commit recovery — cost: no automatic application compilation until a separate build pipeline is connected.
