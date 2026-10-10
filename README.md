# 이곳 — Mapswork

Android 앱에 상점을 저장하고 근처에 도착하면 알림을 받는 개인용 앱입니다.

## 0.3.0

- 지도에서 상점 위치 선택, 이름 입력, 수정·삭제
- 알림 반경 100m / **200m 기본** / 500m / 1km
- 전체·상점별 알림 스위치, 활성 상점 최대 100곳
- 같은 방문의 반복 알림 억제와 상점별 최소 30분 간격
- 휴대폰 내부 저장, 재부팅·앱 업데이트 후 근접 감지 복구
- 큰 설정 버튼, 설정에서 APK 업데이트 확인·다운로드·설치
- Firebase Hosting 버전 파일로 하루 한 번 새 버전 자동 확인·알림, GitHub 확인 경로 포함
- Google 지도, 파란 내 위치 표시, 공식 Google 상점 이름 검색을 위한 SDK 연결

Android 8.0 이상, Google Play 서비스가 있는 ARM 휴대폰이 필요합니다. 위치 감지는 Google Play Geofencing, 지도·검색은 Google Maps/Places SDK를 사용합니다. **현재 APK에는 Google API 키가 없어 앱 내부 지도·검색은 연결 대기 상태입니다.** Google 지도 앱/웹 검색·위치 보기와 현재 위치를 상점 위치로 선택하기는 사용할 수 있습니다. 앱 내부 지도 연결은 [Google 설정 안내](docs/GOOGLE_MAPS_SETUP.ko.md)를 따릅니다. 검색 사용량에 따라 요금이 생길 수 있습니다. Google 지도 저장 목록 가져오기·Drive 백업은 없습니다.

**0.3.0 검증용 APK**입니다. Firebase 프로젝트 `inanimals-1977`의 기존 사이트 `https://inanimals-1977.web.app/mapswork/update.json`을 읽도록 구성했습니다. 기존 웹사이트 배포 소스와 인증을 확인하지 못해 Firebase 파일 배포는 아직 수행하지 않았습니다. 연결 실패 시 GitHub에서 확인합니다. [Firebase 파일 추가 안내](docs/FIREBASE_UPDATE_SETUP.ko.md)를 따릅니다.

클라우드 자동 시험과 빌드를 확인하며, 실제 휴대폰의 지도·검색·업데이트 설치·예약 알림·재부팅 복구는 별도 기기 시험이 필요합니다.

## 사용과 보관

[한국어 사용 안내서](docs/USER_GUIDE.ko.md)에서 설치, 권한, 상점 등록과 휴대폰 시험 순서를 확인하세요.

완성 파일은 [GitHub 다운로드 폴더](https://github.com/selfness-byte/mapswork/tree/codex/nearby-shop-alert/downloads)에 있습니다.

- [APK·소스·안내서 전체 ZIP](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/mapswork-complete.zip)
- [Android 설치용 APK](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/mapswork-0.3.0-test.apk)
- [Firebase 사이트에 추가할 파일](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/mapswork-firebase-hosting.zip)
- [Mac APK 저장 도구](https://github.com/selfness-byte/mapswork/raw/refs/heads/codex/nearby-shop-alert/downloads/save-apk-to-mac.command)

개발과 빌드는 클라우드에서 합니다. 전체 ZIP은 Mac의 **M → 공통작업 → mapswork-github**에 보관하세요. APK·소스·안내서·Firebase 배포 파일·검증 기록이 모두 들어 있습니다. 클라우드는 M을 직접 읽거나 자동으로 저장하지 않습니다. 저장 도구를 Mac에서 실행하면 실제 `mapswork-github` 폴더를 선택해 APK만 받을 수도 있습니다.

## 클라우드 빌드

```bash
cd /workspace/mapswork
python3 tools/bootstrap_android.py
tools/cloud-gradle.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

도구는 `/workspace/.mapswork-tools`, Gradle 캐시는 `/workspace/.mapswork-gradle`에 보관됩니다. 다운로드 체크섬과 TLS를 확인하며 시스템의 프록시·신뢰 저장소를 사용합니다. 반복 실행하면 설치된 도구를 재사용합니다.

변경을 커밋한 뒤 전달 파일을 만듭니다. 자동 검증, 서명·정렬 확인, 소스 ZIP 검사와 체크섬 생성을 함께 수행합니다.

```bash
python3 tools/package_delivery.py
```

소스 ZIP에는 Git 추적 소스와 문서만 들어갑니다. 캐시, 사용자 상점 데이터, 인증 정보와 서명 키는 포함하지 않습니다. `downloads/`는 Git 소스 아카이브에서 제외해 전달 파일이 소스 ZIP 안에 중첩되지 않게 합니다. 향후 기존 앱에 업데이트하려면 같은 서명을 유지해야 합니다.

## 문서

- [설계](docs/superpowers/specs/2026-10-10-nearby-shop-alert-design.md)
- [구현 계획](docs/superpowers/plans/2026-10-10-nearby-shop-alert.md)
- [0.2 지도·업데이트 설계](docs/superpowers/specs/2026-10-10-igot-google-updates-design.md)
- [Google 지도 설정](docs/GOOGLE_MAPS_SETUP.ko.md)
- [Firebase 자동 업데이트 설정](docs/FIREBASE_UPDATE_SETUP.ko.md)
- [검증 범위와 제한](docs/VALIDATION.ko.md)
- [클라우드 환경 재사용](docs/CLOUD_SETUP.ko.md)
- [외부 라이브러리·지도 출처](docs/THIRD_PARTY_NOTICES.md)
