# 이곳 — 공식 설치·업데이트 파일

**이곳** Android 앱의 공개 시험판 설치 안내입니다. 최신 버전은 **0.4.0 (versionCode 4)**이며 Android 8.0 이상에서 사용할 수 있습니다. APK 파일은 이미 검증된 GitHub 커밋에 두어 이 폴더에 중복 저장하지 않았습니다.

- [이곳 0.4.0 APK 다운로드](https://raw.githubusercontent.com/selfness-byte/mapswork/773727132552bf323fc1bdcb3248d8f530ed9e4a/downloads/mapswork-0.4.0-test.apk) — **18,653,298바이트**
- [APK SHA-256 체크섬 파일](https://raw.githubusercontent.com/selfness-byte/mapswork/773727132552bf323fc1bdcb3248d8f530ed9e4a/downloads/mapswork-0.4.0-test.apk.sha256)
- SHA-256: `16758cb07a575537ac7af8598a3192376e013283187c6a62cb6951a7fe03c850`
- [기계가 읽는 버전 정보](update.json): APK 주소·버전·크기·SHA-256을 기록합니다.

## 설치

1. Android 기기에서 위 APK를 내려받습니다. 받은 파일의 SHA-256을 체크섬 파일과 비교할 수 있습니다.
2. 기존 이곳 앱이 있으면 **삭제하지 말고** 그 위에 설치합니다. 기존 앱과 서명이 다르면 Android가 업데이트를 거부합니다. 데이터를 보존하려면 서명 오류를 앱 삭제로 해결하지 마세요.
3. Android의 설치 확인과 필요한 출처 허용은 사용자가 직접 승인합니다. 앱 내부 업데이트를 사용할 때는 앱이 APK의 해시·패키지명·버전·서명을 검사합니다.

**이 APK는 개인 기기 시험용 디버그 서명본이며 Google Play 정식 배포본이 아닙니다.** 0.3.0 시험판과 APK v2 서명 인증서 지문이 같지만, 실제 휴대폰의 설치·업데이트와 데이터 보존은 아직 검증하지 않았습니다. 현재 Google Maps API 키가 설정되지 않아 앱 내부 지도·상점 검색은 연결 대기 상태입니다. Google 지도 앱이나 웹 검색 결과는 이곳 앱에 자동 등록되지 않습니다.

앱이 기존에 조회하던 GitHub 대체 업데이트 주소는 [기존 downloads/update.json](https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/update.json)으로 유지합니다. 이 폴더는 기본 브랜치에서 바로 찾을 수 있는 공개 설치·업데이트 안내입니다.
