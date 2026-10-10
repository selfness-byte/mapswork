# 클라우드 환경 재사용

## 이곳 0.3 Firebase 자동 확인

Firebase 프로젝트는 `inanimals-1977`, 업데이트 전용 Hosting 사이트는 `inanimals-1977-mapswork`입니다. 기본 빌드 주소를 지정하고 24시간 간격 자동 확인·알림과 GitHub 대체 확인 경로를 구현했습니다. `MAPSWORK_FIREBASE_HOSTING_SITE`는 사이트 ID를 바꾸는 공개 빌드 설정입니다. 별도 Firebase SDK나 관리 인증을 APK에 넣지 않습니다.

Firebase CLI·배포 인증과 GCP 실행 신원은 이 클라우드에 구성되어 있지 않습니다. 사이트 생성·배포는 아직 하지 않았습니다. 완료 ZIP의 Firebase 배포 파일과 [안내](FIREBASE_UPDATE_SETUP.ko.md)를 사용합니다. 공개 사이트 주소의 클라우드 확인을 위한 네트워크·시작 지침과 사이트 환경 설정은 초안 revision 9에 저장하고 다시 읽어 확인했습니다. 환경 설정에서 검토·저장한 뒤 게시해야 적용되며, 초안 저장만으로 현재 네트워크에 적용되지는 않습니다.

## 이곳 0.2 지도 연결

Google Maps/Places SDK는 빌드·단위 시험을 통과했습니다. 사용자에게 Google API 키가 아직 없으므로 현재 APK의 앱 내부 지도·검색은 연결 대기입니다. 별도 Google 지도 앱/웹으로 검색·위치 보기를 제공합니다.

환경 초안에 `MAPSWORK_GOOGLE_MAPS_API_KEY` 환경 변수 요구사항과 Google 연결·서명 유지·GitHub 업데이트 게시 절차를 포함한 `start_skill`을 저장했습니다. 키 값은 저장하지 않았습니다. 환경 설정에 실제 Android API 키를 입력하고 설정을 적용한 뒤 새 버전으로 빌드해야 합니다. 초안 저장은 실행 환경 적용이나 게시를 뜻하지 않습니다. [설정 안내](GOOGLE_MAPS_SETUP.ko.md)를 따릅니다.

현재 작업 경로는 `/workspace/mapswork`입니다. Android 개발·빌드 캐시는 클라우드 안에 있으며 Mac 외장하드 M과 연결되어 있지 않습니다.

## 설치 스크립트

환경 설정의 `install_script`에는 `tools/setup_cloud.sh`의 전체 내용을 저장합니다. 다음 순서를 실제로 실행해 확인합니다.

1. `/workspace/mapswork`에 앱 소스가 있는지 확인
2. 체크섬을 검증한 JDK 21·Gradle 8.11.1·Android SDK 36 설치 또는 재사용
3. 핵심·Android 단위 시험, APK 빌드와 Android 정적 검사

프록시 설정과 TLS 검증을 유지하고, 도구와 캐시는 쓰기 가능한 `/workspace` 아래에 둡니다. 현재 작업에는 Google Drive·GCS·AWS 인증이 필요하지 않습니다. 기존 Drive 관련 환경 요구사항과 네트워크 목록은 유지합니다.

## 시작 지침

- 기존 체크아웃과 로컬 변경을 보존하고 `git status`·문서를 확인합니다.
- Android 명령은 `tools/cloud-gradle.sh`를 사용합니다.
- 수정 후 필요한 시험을 실행하고 APK 전달 시 `tools/package_delivery.py`를 실행합니다.
- 별도 로컬 에뮬레이터·지도 서버는 필요하지 않습니다.
- 완성 파일의 M 보관은 사용자가 다운로드한 뒤 Finder에서 옮기는 방식입니다.

## 초안과 게시

설치·시작 지침은 환경 초안 revision 5에 저장했고 다시 읽어 저장을 확인했습니다. 기존 저장소 목록, Drive 요구사항과 네트워크 규칙은 유지했습니다. 초안 저장만으로 스크립트가 실행되거나 설정이 게시되지는 않습니다. 다른 작업에서 재사용하려면 환경 설정에서 내용을 검토·저장하고 게시해야 합니다. 현재 실행 환경은 이 세션에서 직접 설치·검증했습니다.

앱 소스는 GitHub `selfness-byte/mapswork`의 `codex/nearby-shop-alert` 브랜치에 업로드했습니다. 새 환경에서 앱을 개발하려면 이 브랜치를 선택하거나 제공한 소스 ZIP·환경 스냅샷을 사용하세요. `main`에는 아직 앱 코드를 병합하지 않았습니다. 새 작업의 스냅샷 복원은 여기서 검증하지 않았습니다.

GitHub API에 대한 프록시 CONNECT 요청이 403으로 거절되어 PR·Release 자동 생성은 현재 막혀 있습니다. 설치 파일은 Git 업로드로 `downloads/`에 전달합니다. API 작업에 필요한 `api.github.com`·`uploads.github.com`을 기존 네트워크 규칙과 함께 환경 초안에 추가했습니다. 초안 저장은 현재 네트워크에 적용하거나 게시한 것이 아닙니다.
