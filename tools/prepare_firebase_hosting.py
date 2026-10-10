#!/usr/bin/env python3
"""Prepare files to merge into the existing Firebase website; never deploy."""
import argparse
import hashlib
import html
import json
from pathlib import Path
import re
import zipfile

PROJECT = "inanimals-1977"
SITE = "inanimals-1977"
FIREBASE_CLI_VERSION = "15.33.0"


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def prepare(source, output, project=PROJECT, site=SITE):
    source, output = Path(source), Path(output)
    label = r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
    if not re.fullmatch(label, project) or not re.fullmatch(label, site):
        raise ValueError("Invalid Firebase project or Hosting site ID")
    metadata = json.loads((source / "update.json").read_text())
    version = metadata["versionName"]
    if not isinstance(version, str) or not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Invalid release version")
    if type(metadata["versionCode"]) is not int or metadata["versionCode"] <= 0:
        raise ValueError("Invalid version code")
    if type(metadata["minSdk"]) is not int or metadata["minSdk"] < 26:
        raise ValueError("Invalid minimum SDK")
    notes = metadata.get("notes", "")
    if not isinstance(notes, str) or len(notes) > 4000:
        raise ValueError("Invalid release notes")
    apk = source / f"mapswork-{version}-test.apk"
    expected_url = f"https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/{apk.name}"
    if metadata["apkUrl"] != expected_url or metadata["sha256"].lower() != digest(apk):
        raise ValueError("Manifest must reference the verified GitHub APK with its exact SHA-256.")
    data = (json.dumps(metadata, ensure_ascii=False, indent=2) + "\n").encode()
    if len(data) > 32 * 1024:
        raise ValueError("Manifest exceeds the app's size limit")

    files = {
        "mapswork/update.json": data.decode(),
        "mapswork/index.html": f'<!doctype html><html lang="ko"><meta charset="utf-8">'
            f'<meta name="viewport" content="width=device-width,initial-scale=1"><title>이곳 업데이트</title>'
            f'<body><h1>이곳 {html.escape(version)}</h1><p>Android 8.0 이상 개인 기기 시험용 APK입니다.</p>'
            f'<p><a href="{html.escape(metadata["apkUrl"], quote=True)}">APK 다운로드</a></p>'
            f'<p>기존 앱을 삭제하지 않고 업데이트하면 저장한 상점이 유지됩니다.</p></body></html>\n',
        "DEPLOY.ko.md": f"""# 이곳 업데이트 파일 추가
프로젝트: {project}
기존 Hosting 사이트: https://{site}.web.app/
버전 파일: https://{site}.web.app/mapswork/update.json

이 ZIP은 기존 사이트에 추가할 파일입니다. 배포는 아직 실행하지 않았습니다.
APK는 GitHub에서 받습니다. 먼저 같은 APK와 update.json을 GitHub에 게시하세요.

1. 기존 사이트를 배포하던 프로젝트 폴더와 firebase.json을 찾습니다.
2. 해당 사이트의 hosting.public 경로를 확인합니다. 빌드가 필요하면 기존 사이트를 먼저 빌드합니다.
3. 이 ZIP의 mapswork 폴더를 최종 공개 폴더 안에 추가합니다. /mapswork 경로가 이미 다른 용도로 쓰이면 덮어쓰지 말고 경로를 먼저 조정합니다.
4. 기존 firebase.json의 해당 Hosting 설정에 /mapswork/update.json의 Cache-Control: no-store 헤더를 추가합니다. 다른 설정과 사이트 파일은 보존합니다.
5. 기존 프로젝트의 정상 배포 절차로 사이트 전체를 함께 배포합니다. 여러 사이트가 있으면 {site}에 연결된 대상만 선택합니다.

Firebase Hosting 배포는 사이트의 전체 파일 목록을 교체합니다.
기존 사이트 파일을 포함하지 않은 이 ZIP만으로 기존 사이트에 배포하면 사이트 내용이 사라질 수 있습니다.
그래서 이 ZIP에는 독립 firebase.json이나 .firebaserc를 넣지 않았습니다.
DEPLOY.ko.md는 공개 폴더에 복사할 필요가 없습니다.

수동 Firebase CLI를 쓴다면 Node.js 20 이상과 firebase-tools@{FIREBASE_CLI_VERSION}를 사용합니다.
로그인은 자신의 브라우저에서 진행합니다. 인증 파일이나 토큰은 채팅·Git 저장소에 올리지 마세요.
원래 배포 소스와 인증을 확인한 후에 배포 명령을 실행합니다.

배포 후 버전 파일이 HTML 대신 JSON을 반환하고 버전 {version}과 APK 체크섬이 전달본과 같은지 확인하세요.
앱은 다음 자동 확인 또는 설정의 ‘업데이트 확인’에서 이 정보를 읽습니다.
다음 버전도 GitHub APK 게시 후 mapswork/update.json을 교체하고 기존 사이트 전체와 함께 배포합니다.
""",
    }
    if output.exists():
        unknown = [p.relative_to(output) for p in output.rglob("*")
                   if p.is_file() and str(p.relative_to(output)) not in files]
        if unknown:
            raise ValueError("Output contains other files; choose a new output directory.")
    for name, content in files.items():
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    archive = source / "mapswork-firebase-hosting.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for name in files:
            bundle.write(output / name, arcname=f"mapswork-firebase-hosting/{name}")
    with zipfile.ZipFile(archive) as bundle:
        if bundle.testzip():
            raise RuntimeError("Firebase delivery archive is corrupt")
    return archive


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[1] / "dist")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--project", default=PROJECT)
    parser.add_argument("--site", default=SITE)
    args = parser.parse_args()
    result = prepare(args.source, args.output or args.source / "firebase-hosting", args.project, args.site)
    print(f"Firebase website merge files: {result} (not deployed)")


if __name__ == "__main__":
    main()
