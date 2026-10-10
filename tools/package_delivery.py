#!/usr/bin/env python3
"""Build and verify a personal Android delivery from the committed checkout."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
VERSION = "0.1.0"


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
    if not alignments:
        raise RuntimeError("APK is missing ARM64 native libraries")
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

    for name in ("USER_GUIDE.ko.md", "THIRD_PARTY_NOTICES.md"):
        shutil.copyfile(ROOT / "docs" / name, DIST / name)
    shutil.copyfile(ROOT / "app/build/reports/lint-results-debug.html", DIST / "LINT_REPORT.html")
    report = {
        "app": "Mapswork — 가까운 상점", "version": VERSION, "kind": "personal-device debug test APK",
        "built_at_utc": datetime.now(timezone.utc).isoformat(), "source_commit": git("rev-parse", "HEAD"),
        "tests": tests, "lint": issues, "apk_signature_verified": True,
        "apk_zip_16kb_alignment_verified": True, "arm64_elf_load_alignment": alignments,
        "apk_bytes": apk.stat().st_size, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "minimum_android": 26, "target_android": 36, "abis": ["arm64-v8a", "armeabi-v7a"],
        "physical_android_device_tested": False,
        "untested": ["native map rendering", "real Google Play geofence callbacks",
                     "locked/background notification delivery", "reboot/package replacement recovery"],
        "external_M_accessed": False,
    }
    (DIST / "BUILD_REPORT.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    delivered = [apk, source, *(DIST / name for name in
        ("USER_GUIDE.ko.md", "THIRD_PARTY_NOTICES.md", "LINT_REPORT.html", "BUILD_REPORT.json", "VERIFY_LOG.txt"))]
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


if __name__ == "__main__":
    main()
