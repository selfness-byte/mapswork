# 이곳 다운로드

## 이곳 0.3.0 검증용

- [Android APK 다운로드](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/mapswork-0.3.0-test.apk)
- [전체 파일 ZIP](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/mapswork-complete.zip) — APK·소스·안내서·Firebase 배포 파일·검증 기록 포함
- [Firebase 배포 파일 ZIP](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/mapswork-firebase-hosting.zip)
- [Mac APK 저장 도구](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/save-apk-to-mac.command)
- [사용 안내](../docs/USER_GUIDE.ko.md) · [Firebase 설정](../docs/FIREBASE_UPDATE_SETUP.ko.md) · [Google 지도 설정](../docs/GOOGLE_MAPS_SETUP.ko.md) · [체크섬](SHA256SUMS.txt)

Firebase Hosting의 버전 파일로 하루 한 번 새 버전을 자동 확인하고 알림을 표시합니다. 설정에서 자동 확인을 끌 수 있습니다. 알림을 누르면 설정이 열립니다. APK 다운로드와 Android 설치 확인은 사용자가 선택합니다.

프로젝트는 `inanimals-1977`, 업데이트 전용 사이트는 `inanimals-1977-mapswork`입니다. **Firebase 사이트 생성·배포는 아직 수행하지 않았습니다.** 연결 전·접속 실패 시 GitHub 버전 파일에서 확인합니다. Firebase ZIP을 전용 사이트에 배포하는 순서는 설정 안내에 있습니다. APK는 GitHub에서 전송하므로 Firebase에는 작은 버전 정보와 안내 페이지만 둡니다.

같은 앱 서명으로 업데이트하면 상점 데이터가 유지됩니다. 기존 앱을 삭제하지 마세요. 0.1.0에는 업데이트 메뉴가 없어 새 APK를 직접 설치해야 합니다. 0.2.0에서는 기존 설정의 업데이트 메뉴로 0.3.0을 설치할 수 있습니다. `update.json`은 APK 버전·URL·SHA-256·변경 내용을 담으며 앱은 해시·앱 식별자·버전·기존 서명을 검사합니다.

현재 API 키가 없어 앱 내부 Google 지도·검색은 연결 대기입니다. Google 지도 앱/웹 검색·위치 보기와 현재 위치를 상점 위치로 선택하기는 사용할 수 있습니다.

개인 기기 시험용 디버그 서명 APK입니다. Android 8.0 이상, Google Play 서비스가 있는 ARM 휴대폰용입니다. 자동 시험 113개와 빌드·정적 검사를 통과했습니다. 실제 Firebase 배포·휴대폰의 지도·검색·설치·백그라운드 알림은 아직 확인하지 않았습니다.

## Mac의 mapswork-github에 저장

Mac에서 아래 명령을 실행하면 폴더 선택 창이 열립니다. 실제 **mapswork-github** 폴더를 선택하면 APK를 받아 체크섬을 확인한 뒤 저장합니다. 별도 Android 빌드 도구는 설치하지 않습니다.

```bash
curl -fL 'https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/save-apk-to-mac.command' -o /tmp/mapswork-save.command && bash /tmp/mapswork-save.command
```

전체 ZIP은 원하는 `mapswork-github` 폴더에 보관하세요. 클라우드는 Mac이나 외장하드 M에 직접 접근하지 않습니다.

이 폴더는 Git 소스 아카이브에서 제외합니다. 인증키·서명 키·캐시·사용자 상점 데이터는 소스 ZIP에 포함하지 않습니다.
