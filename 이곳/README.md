# 이곳 — 공식 설치·업데이트 파일

<!-- mapswork-latest:start -->
## 최신 시험판 — 이곳 0.4.0

- 버전 코드: **4** · minSdk **26**
- [APK 다운로드](https://raw.githubusercontent.com/selfness-byte/mapswork/773727132552bf323fc1bdcb3248d8f530ed9e4a/downloads/mapswork-0.4.0-test.apk) — **18,653,298바이트**
- [SHA-256 체크섬 파일](https://raw.githubusercontent.com/selfness-byte/mapswork/773727132552bf323fc1bdcb3248d8f530ed9e4a/downloads/mapswork-0.4.0-test.apk.sha256)
- APK SHA-256: `16758cb07a575537ac7af8598a3192376e013283187c6a62cb6951a7fe03c850`
- 서명 인증서 SHA-256: `80dfd84789e5c205b319562bf99bd33500b64000695594f1b8dbf2b58638cf40`
- [업데이트 정보](update.json)
- [설치 안내](#설치)

앱을 열 때 새 버전이 있으면 업데이트 또는 나중에를 선택할 수 있습니다.
설정에서 APK 다운로드 진행을 확인하고 파일 검증 후 Android 설치 확인 화면을 엽니다.
앱 내부 지도·상점 검색은 Google Maps API 키가 설정되지 않아 연결 대기 상태입니다. Google 지도 앱이나 웹 검색 결과가 이 앱에 자동 등록되지는 않습니다.
시험용 APK이며 실제 휴대폰 설치·업데이트 동작은 아직 확인되지 않았습니다.
<!-- mapswork-latest:end -->

## 설치

1. Android 기기에서 위 APK를 내려받습니다. 받은 파일의 SHA-256을 체크섬 파일과 비교할 수 있습니다.
2. 기존 이곳 앱을 **삭제하지 말고** 그 위에 설치합니다. 서명이 다르면 Android가 설치를 거부합니다. 데이터를 보존하려면 서명 오류를 앱 삭제로 해결하지 마세요.
3. 설치 확인과 필요한 출처 허용은 사용자가 Android 화면에서 직접 승인합니다. 앱 내부 업데이트는 APK의 해시·패키지명·더 높은 버전 코드·동일 서명을 검사합니다.

시험용 디버그 서명 APK이며 Google Play 정식 배포본이 아닙니다. 실제 휴대폰 설치·업데이트·데이터 보존 시험은 별도로 필요합니다.

[앱의 GitHub 대체 업데이트 정보](https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/update.json)와 main의 `이곳/update.json`은 같은 검증 결과로 갱신됩니다. Firebase의 기존 사이트는 이 작업에서 배포하지 않습니다.

이전 APK·ZIP·문서·Mac 저장 도구는 보존합니다. 최신 APK는 위의 고정 커밋 주소로 내려받으세요. GitHub Actions는 APK의 실제 버전·크기·SHA-256·기존 인증서 서명을 검증한 뒤 두 브랜치를 함께 갱신합니다.
