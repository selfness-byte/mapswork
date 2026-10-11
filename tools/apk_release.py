"""Verify APK release identity and render public update information."""
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import re
import subprocess

REPOSITORY = "selfness-byte/mapswork"
PACKAGE_NAME = "com.selfness.mapswork"
TRUSTED_CERT_SHA256 = "80dfd84789e5c205b319562bf99bd33500b64000695594f1b8dbf2b58638cf40"
MAX_APK_BYTES = 80 * 1024 * 1024
VERSION = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
APK_PATH = re.compile(r"downloads/mapswork-(" + VERSION + r")-test\.apk")
START = "<!-- mapswork-latest:start -->"
END = "<!-- mapswork-latest:end -->"


class ReleaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class VerifiedApk:
    version_code: int
    version_name: str
    min_sdk: int
    size_bytes: int
    sha256: str
    signer_sha256: str


def find_build_tools(explicit: str | None = None) -> Path:
    candidates = [Path(explicit)] if explicit else []
    if not explicit:
        for root in [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                     "/workspace/.mapswork-tools/android-sdk", "/usr/local/lib/android/sdk"]:
            if root:
                base = Path(root) / "build-tools"
                if base.is_dir():
                    candidates.extend(sorted(base.iterdir(), reverse=True))
    for candidate in candidates:
        if all((candidate / tool).is_file() for tool in ["aapt", "apksigner"]):
            return candidate.resolve()
    raise ReleaseError("Installed Android aapt/apksigner are required; no tools were installed")


def run_tool(args: list[str]) -> str:
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise ReleaseError(f"{Path(args[0]).name} verification failed: {result.stdout[-2000:]}")
    return result.stdout


def verify_apk(path: Path, build_tools: Path) -> VerifiedApk:
    size = path.stat().st_size
    if not 0 < size <= MAX_APK_BYTES:
        raise ReleaseError("APK size must be between 1 byte and 80MiB")
    badging = run_tool([str(build_tools / "aapt"), "dump", "badging", str(path)])
    identity = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    sdk = re.search(r"^sdkVersion:'([0-9]+)'", badging, re.M)
    if not identity or not sdk or identity[1] != PACKAGE_NAME:
        raise ReleaseError("APK package/minSdk does not match the release policy")
    code, name, minimum = int(identity[2]), identity[3], int(sdk[1])
    if not 0 < code <= 2147483647 or not re.fullmatch(VERSION, name) or minimum < 26:
        raise ReleaseError("Invalid APK version or minimum Android version")
    if path.name != f"mapswork-{name}-test.apk":
        raise ReleaseError("APK filename must match its actual versionName")
    signing = run_tool([str(build_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(path)])
    certificates = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", signing, re.M)
    if [c.lower() for c in certificates] != [TRUSTED_CERT_SHA256]:
        public_diagnostics = "\n".join(line for line in signing.splitlines()
                                       if "SHA-256" in line or line.startswith("Verified using"))
        raise ReleaseError(f"APK certificate differs from the existing trusted test signer; "
                           f"build-tools={build_tools.name}; parsed={certificates}\n{public_diagnostics}")
    if not re.search(r"Verified using v2 scheme .*: true", signing):
        raise ReleaseError("APK requires a valid v2 signature for Android 8 compatibility")
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return VerifiedApk(code, name, minimum, size, digest, certificates[0].lower())


def version_tuple(name: str) -> tuple[int, ...]:
    if not isinstance(name, str) or not re.fullmatch(VERSION, name):
        raise ReleaseError("Invalid versionName in update metadata")
    return tuple(map(int, name.split(".")))


def enforce_progress(release: VerifiedApk, manifests: list[dict]) -> None:
    for old in manifests:
        if (type(old.get("versionCode")) is not int or not 0 < old["versionCode"] <= 2147483647
                or not re.fullmatch(r"[0-9a-f]{64}", str(old.get("sha256", "")))
                or type(old.get("sizeBytes")) is not int or not 0 < old["sizeBytes"] <= MAX_APK_BYTES
                or type(old.get("minSdk")) is not int or old["minSdk"] < 26):
            raise ReleaseError("Malformed existing update metadata")
        old_version = version_tuple(old.get("versionName"))
        current_version = version_tuple(release.version_name)
        if release.version_code < old["versionCode"] or current_version < old_version:
            raise ReleaseError("Version rollback: no publication allowed")
        if release.version_code == old["versionCode"]:
            if (release.version_name != old["versionName"] or release.sha256 != old["sha256"]
                    or release.size_bytes != old["sizeBytes"] or release.min_sdk != old["minSdk"]):
                raise ReleaseError("Same version cannot be replaced with another APK")
        elif current_version <= old_version:
            raise ReleaseError("A new versionCode also requires a new versionName")


def make_manifest(release: VerifiedApk, commit: str, notes: str) -> dict:
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ReleaseError("APK URL requires a full immutable commit SHA")
    if not isinstance(notes, str) or not notes.strip() or len(notes) > 4000 or START in notes or END in notes:
        raise ReleaseError("Release notes must be nonempty text of at most 4000 characters")
    return dict(versionCode=release.version_code, versionName=release.version_name,
                minSdk=release.min_sdk,
                apkUrl=f"https://raw.githubusercontent.com/{REPOSITORY}/{commit}/downloads/mapswork-{release.version_name}-test.apk",
                sha256=release.sha256, notes=notes.strip(), sizeBytes=release.size_bytes)


def latest_block(manifest: dict, metadata_link: str) -> str:
    return f"""{START}
## 최신 시험판 — 이곳 {manifest['versionName']}

- 버전 코드: **{manifest['versionCode']}** · minSdk **{manifest['minSdk']}**
- [APK 다운로드]({manifest['apkUrl']}) — **{manifest['sizeBytes']:,}바이트**
- [SHA-256 체크섬 파일]({manifest['apkUrl']}.sha256)
- APK SHA-256: `{manifest['sha256']}`
- 서명 인증서 SHA-256: `{TRUSTED_CERT_SHA256}`
- [업데이트 정보]({metadata_link})

{manifest['notes']}
{END}"""


def replace_latest(text: str, block: str) -> str:
    if START in text or END in text:
        if text.count(START) != 1 or text.count(END) != 1 or text.index(START) > text.index(END):
            raise ReleaseError("Malformed generated README markers")
        return re.sub(re.escape(START) + r".*?" + re.escape(END), lambda _: block, text, flags=re.S).rstrip() + "\n"
    paragraphs = text.rstrip().split("\n\n", 1)
    return (paragraphs[0] + "\n\n" + block + "\n\n" + (paragraphs[1] if len(paragraphs) > 1 else "")).rstrip() + "\n"


def app_readme(text: str, manifest: dict) -> str:
    text = re.sub(r"(?m)^## " + VERSION + r"$", "## 앱 기능", text)
    text = text.replace("**0.3.0 검증용 APK**입니다.", "공개 시험판 APK입니다.")
    text = re.sub(r"(?m)^- \[Android 설치용 APK\]\([^\n]*\)$",
                  f"- [Android 설치용 APK]({manifest['apkUrl']})", text)
    for old, new in [("[APK·소스·안내서 전체 ZIP]", "[이전 0.3.0 APK·소스·안내서 ZIP]"),
                     ("[Firebase 사이트에 추가할 파일]", "[이전 0.3.0 Firebase 배포 파일]"),
                     ("[Mac APK 저장 도구]", "[이전 0.3.0 Mac APK 저장 도구]")]:
        text = text.replace(old, new)
    return replace_latest(text, latest_block(manifest, "downloads/update.json"))


def install_guide(manifest: dict, main: bool = False) -> str:
    fallback = (f"https://raw.githubusercontent.com/{REPOSITORY}/codex/nearby-shop-alert/downloads/update.json")
    return "# 이곳 — 공식 설치·업데이트 파일\n\n" + latest_block(manifest, "update.json") + f"""

## 설치

1. Android 기기에서 위 APK를 내려받습니다. 받은 파일의 SHA-256을 체크섬 파일과 비교할 수 있습니다.
2. 기존 이곳 앱을 **삭제하지 말고** 그 위에 설치합니다. 서명이 다르면 Android가 설치를 거부합니다. 데이터를 보존하려면 서명 오류를 앱 삭제로 해결하지 마세요.
3. 설치 확인과 필요한 출처 허용은 사용자가 Android 화면에서 직접 승인합니다. 앱 내부 업데이트는 APK의 해시·패키지명·더 높은 버전 코드·동일 서명을 검사합니다.

시험용 디버그 서명 APK이며 Google Play 정식 배포본이 아닙니다. 실제 휴대폰 설치·업데이트·데이터 보존 시험은 별도로 필요합니다.

[앱의 GitHub 대체 업데이트 정보]({fallback})와 main의 `이곳/update.json`은 같은 검증 결과로 갱신됩니다. Firebase의 기존 사이트는 이 작업에서 배포하지 않습니다.

이전 APK·ZIP·문서·Mac 저장 도구는 보존합니다. 최신 APK는 위의 고정 커밋 주소로 내려받으세요. GitHub Actions는 APK의 실제 버전·크기·SHA-256·기존 인증서 서명을 검증한 뒤 두 브랜치를 함께 갱신합니다.
"""
