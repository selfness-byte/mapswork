#!/usr/bin/env python3
"""Build and verify a personal Android delivery from the committed checkout."""
from datetime import datetime, timezone
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from prepare_firebase_hosting import prepare as prepare_firebase

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
APP_GRADLE = (ROOT / "app/build.gradle.kts").read_text()
VERSION = re.search(r'versionName\s*=\s*"([^"]+)"', APP_GRADLE).group(1)
VERSION_CODE = int(re.search(r'versionCode\s*=\s*(\d+)', APP_GRADLE).group(1))


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def run(command, log):
    log.write("\n$ " + " ".join(map(str, command)) + "\n")
    log.flush()
    subprocess.run(list(map(str, command)), cwd=ROOT, check=True, stdout=log, stderr=subprocess.STDOUT)


def test_results(module, task):
    reports = sorted((ROOT / module / "build/test-results" / task).glob("TEST-*.xml"))
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, "0"))
    if not reports or totals["tests"] == 0 or any(totals[k] for k in ("failures", "errors", "skipped")):
        raise RuntimeError(f"Incomplete or failed {module} tests: {totals}")
    return totals


def native_alignment(apk, log):
    alignments = {}
    with zipfile.ZipFile(apk) as bundle, tempfile.TemporaryDirectory() as temporary:
        for name in bundle.namelist():
            if not name.startswith("lib/arm64-v8a/") or not name.endswith(".so"):
                continue
            library = Path(temporary) / Path(name).name
            library.write_bytes(bundle.read(name))
            output = subprocess.check_output(["readelf", "-lW", str(library)], text=True)
            values = [int(line.split()[-1], 16) for line in output.splitlines()
                      if line.strip().startswith("LOAD")]
            if not values or any(value < 16384 for value in values):
                raise RuntimeError(f"Native library is not 16KB aligned: {name}")
            alignments[name] = values
            log.write(f"ELF LOAD alignment {name}: {values}\n")
    return alignments


def main():
    if git("status", "--porcelain"):
        raise RuntimeError("Commit source and documentation before packaging; the source ZIP uses HEAD.")
    DIST.mkdir(exist_ok=True)
    apk = DIST / f"mapswork-{VERSION}-test.apk"
    source = DIST / "mapswork-source.zip"
    tools = Path(os.environ.get("MAPSWORK_TOOLS", "/workspace/.mapswork-tools"))
    sdk_build = tools / "android-sdk/build-tools/36.0.0"
    with (DIST / "VERIFY_LOG.txt").open("w") as log:
        run([ROOT / "tools/cloud-gradle.sh", ":core:test", ":app:testDebugUnitTest",
             ":app:assembleDebug", ":app:lintDebug"], log)
        tests = {"core": test_results("core", "test"),
                 "app": test_results("app", "testDebugUnitTest")}
        lint = ET.parse(ROOT / "app/build/reports/lint-results-debug.xml").getroot()
        issues = [{"id": issue.attrib["id"], "severity": issue.attrib["severity"]}
                  for issue in lint.findall("issue")]
        if any(i["severity"] in ("Error", "Fatal") for i in issues):
            raise RuntimeError("Android lint has errors")
        shutil.copyfile(ROOT / "app/build/outputs/apk/debug/app-debug.apk", apk)
        run([sdk_build / "apksigner", "verify", "--verbose", apk], log)
        run([sdk_build / "zipalign", "-c", "-P", "16", "4", apk], log)
        alignments = native_alignment(apk, log)
        run(["git", "archive", "--format=zip", "--prefix=mapswork-source/", "-o", source, "HEAD"], log)

    with zipfile.ZipFile(source) as bundle:
        if bundle.testzip():
            raise RuntimeError("Source ZIP integrity failure")
        forbidden = {".git", ".gradle", "build", "dist", "downloads", ".superpowers", "__pycache__", "local.properties"}
        for name in bundle.namelist():
            path = Path(name)
            if forbidden.intersection(path.parts) or path.suffix in (".jks", ".keystore", ".pyc"):
                raise RuntimeError(f"Excluded material in source ZIP: {name}")

    for name in ("USER_GUIDE.ko.md", "GOOGLE_MAPS_SETUP.ko.md", "FIREBASE_UPDATE_SETUP.ko.md", "THIRD_PARTY_NOTICES.md"):
        shutil.copyfile(ROOT / "docs" / name, DIST / name)
    shutil.copyfile(ROOT / "app/build/reports/lint-results-debug.html", DIST / "LINT_REPORT.html")
    report = {
        "app": "이곳 — Mapswork", "version": VERSION, "version_code": VERSION_CODE,
        "kind": "personal-device debug test APK",
        "built_at_utc": datetime.now(timezone.utc).isoformat(), "source_commit": git("rev-parse", "HEAD"),
        "tests": tests, "lint": issues, "apk_signature_verified": True,
        "apk_zip_16kb_alignment_verified": True, "arm64_elf_load_alignment": alignments,
        "apk_bytes": apk.stat().st_size, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "minimum_android": 26, "target_android": 36, "abis": ["arm64-v8a", "armeabi-v7a"],
        "physical_android_device_tested": False,
        "google_maps_api_key_configured": google_maps_key_configured(),
        "firebase_hosting_site": firebase_hosting_site(),
        "firebase_hosting_deployment_verified": False,
        "github_update_fallback_enabled": True,
        "automatic_update_check_interval_hours": 24,
        "untested": ["live Firebase Hosting deployment", "physical-device scheduled update checks and notifications",
                     "native Google map rendering and Places responses", "Android APK update installer",
                     "real Google Play geofence callbacks",
                     "locked/background notification delivery", "reboot/package replacement recovery"],
        "external_M_accessed": False,
    }
    (DIST / "BUILD_REPORT.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    notes = (ROOT / "docs/RELEASE_NOTES.ko.md").read_text().strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", VERSION) or len(notes) > 4000:
        raise RuntimeError("Update metadata requires a numeric version and release notes up to 4000 characters")
    update = dict(versionCode=VERSION_CODE, versionName=VERSION, minSdk=26,
                  apkUrl=f"https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/{apk.name}",
                  sha256=report["apk_sha256"], notes=notes)
    (DIST / "update.json").write_text(json.dumps(update, ensure_ascii=False, indent=2) + "\n")
    firebase = prepare_firebase(DIST, DIST / "firebase-hosting", site=firebase_hosting_site() or "inanimals-1977-mapswork")
    mac_saver = write_mac_saver(update)
    with (DIST / "VERIFY_LOG.txt").open("a") as log:
        run(["bash", "-n", mac_saver], log)
    delivered = [apk, source, firebase, mac_saver, *(DIST / name for name in
        ("USER_GUIDE.ko.md", "GOOGLE_MAPS_SETUP.ko.md", "FIREBASE_UPDATE_SETUP.ko.md", "THIRD_PARTY_NOTICES.md", "LINT_REPORT.html",
         "BUILD_REPORT.json", "VERIFY_LOG.txt", "update.json"))]
    checksums = DIST / "SHA256SUMS.txt"
    file_sums = "".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
                        for path in sorted(delivered))
    checksums.write_text(file_sums)
    completed = DIST / "mapswork-complete.zip"
    with zipfile.ZipFile(completed, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as bundle:
        for path in [*delivered, checksums]:
            bundle.write(path, arcname=path.name)
    with zipfile.ZipFile(completed) as bundle:
        if bundle.testzip():
            raise RuntimeError("Completed-files ZIP integrity failure")
    # The checksum inside the bundle covers its contents; the external list also
    # covers the bundle itself without creating a self-referential checksum.
    checksums.write_text(file_sums + f"{hashlib.sha256(completed.read_bytes()).hexdigest()}  {completed.name}\n")
    delivered += [completed, checksums]
    print(f"Verified {sum(t['tests'] for t in tests.values())} tests; delivery ready: {DIST}")
    for path in sorted(delivered):
        print(f"{path.name}: {path.stat().st_size:,} bytes")


def google_maps_key_configured():
    key = os.environ.get("MAPSWORK_GOOGLE_MAPS_API_KEY")
    if key is not None:
        return bool(key.strip())
    properties = ROOT / "local.properties"
    if properties.is_file():
        for line in properties.read_text().splitlines():
            if line.strip().startswith("mapswork.googleMapsApiKey="):
                return bool(line.split("=", 1)[1].strip())
    return False


def firebase_hosting_site():
    site = os.environ.get("MAPSWORK_FIREBASE_HOSTING_SITE")
    if site is not None:
        return site.strip()
    properties = ROOT / "local.properties"
    if properties.is_file():
        for line in properties.read_text().splitlines():
            if line.strip().startswith("mapswork.firebaseHostingSite="):
                return line.split("=", 1)[1].strip()
    return "inanimals-1977-mapswork"


def write_mac_saver(update):
    script = """#!/bin/bash
set -euo pipefail
if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "이 저장 도구는 Mac에서 실행하세요."
    exit 1
fi
task_folder="$(osascript -e 'POSIX path of (choose folder with prompt "mapswork-github 폴더를 선택하세요")')"
task_destination="$task_folder/mapswork-__VERSION__-test.apk"
task_expected="__SHA256__"
if [[ -e "$task_destination" ]]; then
    if [[ "$(shasum -a 256 "$task_destination" | awk '{print $1}')" == "$task_expected" ]]; then
        echo "이미 저장되어 있습니다: $task_destination"
        exit 0
    fi
    echo "같은 이름의 다른 파일이 있습니다. 기존 파일을 보존하고 중단합니다."
    exit 1
fi
task_download="$(mktemp /tmp/mapswork-apk.XXXXXX)"
trap 'rm -f "$task_download"' EXIT
curl --fail --silent --show-error --location "__APK_URL__" -o "$task_download"
if [[ "$(shasum -a 256 "$task_download" | awk '{print $1}')" != "$task_expected" ]]; then
    echo "APK 체크섬이 다릅니다. 저장하지 않았습니다."
    exit 1
fi
cp -n "$task_download" "$task_destination"
if [[ "$(shasum -a 256 "$task_destination" | awk '{print $1}')" != "$task_expected" ]]; then
    echo "저장한 파일을 확인하지 못했습니다."
    exit 1
fi
echo "APK 저장 완료: $task_destination"
"""
    script = script.replace("__VERSION__", update["versionName"]).replace("__SHA256__", update["sha256"])
    script = script.replace("__APK_URL__", update["apkUrl"])
    output = DIST / "save-apk-to-mac.command"
    output.write_text(script)
    output.chmod(0o755)
    return output


if __name__ == "__main__":
    main()
