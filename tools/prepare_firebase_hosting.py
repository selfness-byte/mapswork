#!/usr/bin/env python3
"""Prepare version metadata for a dedicated Firebase Hosting site; never deploy."""
import argparse
import hashlib
import html
import json
from pathlib import Path
import re
import zipfile

PROJECT = "inanimals-1977"
SITE = "inanimals-1977-mapswork"
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
    if not re.fullmatch(label, project) or not re.fullmatch(label, site) or site == project:
        raise ValueError("Choose a dedicated Hosting site, distinct from the project's default site.")
    metadata = json.loads((source / "update.json").read_text())
    version = metadata["versionName"]
    if not isinstance(version, str) or not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Invalid release version")
    if not isinstance(metadata["versionCode"], int) or metadata["versionCode"] <= 0:
        raise ValueError("Invalid version code")
    if metadata["minSdk"] < 26 or len(metadata.get("notes", "")) > 4000:
        raise ValueError("Invalid release metadata")
    apk = source / f"mapswork-{version}-test.apk"
    expected_url = f"https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/{apk.name}"
    if metadata["apkUrl"] != expected_url or metadata["sha256"].lower() != digest(apk):
        raise ValueError("Manifest must reference the verified GitHub APK with its exact SHA-256.")
    data = (json.dumps(metadata, ensure_ascii=False, indent=2) + "\n").encode()
    if len(data) > 32 * 1024:
        raise ValueError("Manifest exceeds the app's size limit")

    files = {
        "firebase.json": json.dumps({"hosting": {"site": site, "public": "public",
            "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
            "headers": [{"source": "/updates/update.json", "headers": [
                {"key": "Cache-Control", "value": "no-store"},
                {"key": "Content-Type", "value": "application/json; charset=utf-8"},
                {"key": "X-Content-Type-Options", "value": "nosniff"}]}]}}, indent=2) + "\n",
        ".firebaserc": json.dumps({"projects": {"default": project}}, indent=2) + "\n",
        "public/updates/update.json": data.decode(),
        "public/index.html": f'<!doctype html><html lang="ko"><meta charset="utf-8">'
            f'<meta name="viewport" content="width=device-width,initial-scale=1"><title>이곳 업데이트</title>'
            f'<body><h1>이곳 {html.escape(version)}</h1><p>Android 8.0 이상 개인 기기 시험용 APK입니다.</p>'
            f'<p><a href="{html.escape(metadata["apkUrl"], quote=True)}">APK 다운로드</a></p>'
            f'<p>기존 앱을 삭제하지 않고 업데이트하면 저장한 상점이 유지됩니다.</p></body></html>\n',
        "DEPLOY.ko.md": f"""# 이곳 Firebase 배포
프로젝트: `{project}`
업데이트 전용 사이트: `{site}`

이 폴더에는 버전 정보와 다운로드 안내만 있습니다. APK는 GitHub에서 받습니다.
Firebase 배포는 아직 실행하지 않았습니다. 먼저 GitHub에 같은 APK와 update.json을 게시하세요.

Node.js 20 이상과 프로젝트의 Hosting 배포 권한이 필요합니다.
폴더 안에서 터미널을 열고 아래 명령을 실행하세요. 로그인은 자신의 브라우저에서 진행합니다.
인증 파일이나 토큰을 채팅·Git 저장소에 올리지 마세요.

```bash
npx --yes firebase-tools@{FIREBASE_CLI_VERSION} login
npx --yes firebase-tools@{FIREBASE_CLI_VERSION} hosting:sites:create {site} --project {project}
npx --yes firebase-tools@{FIREBASE_CLI_VERSION} deploy --only hosting --project {project}
```

이 사이트를 해당 프로젝트에 이미 만들었다면 생성 명령을 생략합니다.
생성 실패 시 같은 이름의 다른 사이트나 프로젝트 기본 사이트에 배포하지 마세요.
이 설정을 배포하면 선택한 사이트의 파일 전체가 바뀌므로 업데이트 전용 사이트를 사용합니다.

배포 후 https://{site}.web.app/updates/update.json 에서 버전 {version}과 APK 체크섬을 확인하세요.
앱은 다음 자동 확인 또는 설정의 ‘업데이트 확인’에서 이 정보를 읽습니다.
다음 버전은 GitHub APK 게시 후 이 폴더를 새 전달본으로 바꿔 다시 배포합니다.
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
    print(f"Firebase deploy-ready files: {result} (not deployed)")


if __name__ == "__main__":
    main()
