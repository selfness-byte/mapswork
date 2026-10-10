# 이곳 다운로드

## 이곳 0.2.0 검증용

- [전체 파일 ZIP 다운로드](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/mapswork-complete.zip) — APK·소스 ZIP·안내서·Google 설정 안내·검증 기록 포함
- [Android APK 다운로드](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/mapswork-0.2.0-test.apk)
- [한국어 사용 안내](../docs/USER_GUIDE.ko.md)
- [Google 지도·검색 연결 안내](../docs/GOOGLE_MAPS_SETUP.ko.md)
- [체크섬](SHA256SUMS.txt)

이름 ‘이곳’, 큰 설정 버튼, 설정의 GitHub 업데이트 메뉴와 상점 이름 검색 버튼을 추가했습니다. 현재 API 키가 없어 앱 내부 Google 지도·검색은 연결 대기입니다. Google 지도 앱/웹으로 검색·위치 보기와 내 위치를 상점 위치로 선택하기는 사용할 수 있습니다.

0.1.0에는 업데이트 메뉴가 없으므로 0.2.0 APK를 직접 설치하세요. 기존 앱을 삭제하지 않고 업데이트해야 상점 데이터가 유지됩니다. 이후 더 높은 버전부터 앱 설정의 업데이트 메뉴를 사용할 수 있습니다. `update.json`은 APK 버전·URL·SHA-256·변경 내용을 담습니다. 앱은 해시·앱 식별자·버전·기존 서명을 검증한 뒤 Android 설치 확인 화면을 엽니다.

개인 기기 시험용 디버그 서명 APK입니다. Android 8.0 이상, Google Play 서비스가 있는 ARM 휴대폰용입니다. 자동 테스트 93개와 빌드·정적 검사를 통과했으며 실제 휴대폰의 지도·검색·설치·백그라운드 알림은 아직 확인하지 않았습니다.

완료 ZIP을 내려받아 Mac의 **M → 공통작업 → mapswork**에 보관하세요. 클라우드는 M에 직접 접근하지 않습니다.

GitHub API 접속이 클라우드 프록시에서 차단되어 저장소 폴더로 전달합니다. 이 폴더는 Git 소스 아카이브에서 제외합니다. Google API 키·서명 키·캐시·사용자 상점 데이터는 소스 ZIP에 포함하지 않습니다.
