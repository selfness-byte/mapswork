# 이곳의 Google 지도·상점 검색 연결

현재 전달 APK 0.3.0에는 Google API 키가 없습니다. 그래서 상점 추가 화면에 **Google 지도 연결 대기**가 표시됩니다. Google 지도 앱/웹으로 검색·위치 보기는 가능하며, 앱 안에서 지도와 검색 결과를 선택하는 기능은 키를 넣어 다시 빌드해야 합니다.

Firebase 업데이트 파일 배포와 앱 내부 Google 지도 연결은 각각 설정합니다. Firebase Hosting에 파일을 배포해도 이미 설치된 APK에 Google 지도 키가 추가되지는 않습니다.

## 1. Google Cloud 설정

1. [Google Cloud Console](https://console.cloud.google.com/apis/dashboard?project=inanimals-1977)에서 기존 Firebase 프로젝트와 같은 **inanimals-1977** 프로젝트를 선택합니다.
2. Google Maps Platform 사용에 필요한 결제 계정을 연결합니다. 검색은 사용량과 요청 필드에 따라 요금이 발생할 수 있습니다. [공식 요금 안내](https://developers.google.com/maps/billing-and-pricing/pricing)를 확인하세요. 앱이 대신 결제 계정을 만들거나 API를 활성화하지 않습니다.
3. **Maps SDK for Android**와 **Places API (New)**를 활성화합니다.
4. API 키를 발급합니다. 애플리케이션 제한을 **Android 앱**으로 설정하고 다음 정보를 등록합니다.
   - 패키지명: `com.selfness.mapswork`
   - 현재 시험 APK 서명 SHA-1: `C8:5F:14:4C:54:AB:14:17:72:85:CC:3B:FA:77:89:4B:CE:7E:B5:7F`
5. API 제한에서 Maps SDK for Android와 Places API (New)만 허용합니다. 검색 서비스의 지원되는 일일/분당 할당량과 예산 알림을 설정합니다. **예산 알림만으로 과금이 중단되지는 않습니다.**

패키지명·SHA-1은 반드시 실제 배포 APK와 일치해야 합니다. 위 서명은 개인 시험 APK용입니다. 판매용 정식 서명이나 Google Play App Signing으로 바꾸면 해당 정식 서명을 등록해야 합니다. 테스트 키와 판매용 키/프로젝트를 분리하는 편이 관리하기 쉽습니다.

## 2. 키 입력과 빌드

키 값은 채팅·GitHub·문서에 보내지 마세요. 클라우드 환경 설정의 **`MAPSWORK_GOOGLE_MAPS_API_KEY` 환경 변수**에 실제 Android API 키를 입력합니다. APK에 삽입하는 클라이언트 키이므로 HTTP 프록시용 비밀 변수의 대체 문자열은 사용할 수 없습니다.

다른 환경에서 개발할 때는 Git에서 제외되는 저장소 루트 `local.properties`에 `mapswork.googleMapsApiKey` 속성을 입력할 수도 있습니다. 환경 변수가 있으면 우선합니다. 파일과 키를 커밋하지 마세요.

```bash
cd /workspace/mapswork
tools/cloud-gradle.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

키를 클라우드 환경 설정에 입력하고 설정을 저장·적용한 뒤 새 APK를 빌드해야 합니다. 키가 포함된 업데이트를 앱에서 제안하려면 `app/build.gradle.kts`의 버전 코드와 버전 이름도 올려야 합니다. 현재 0.3.0의 versionCode는 `3`이므로 다음 빌드는 예를 들어 versionCode `4`, versionName `0.3.1`로 합니다. 기존 서명 키를 유지하고 소스를 커밋한 뒤 `tools/package_delivery.py`로 전달 파일을 만들고 GitHub에 게시합니다. 업데이트 메뉴는 더 큰 버전 코드만 제안합니다.

## 3. 휴대폰 확인

- 상점 추가의 Google 지도가 실제로 표시되는지 확인합니다.
- **내 위치**를 누르면 파란 마커가 표시되는지, 지도를 옮긴 뒤 다시 눌러도 중심이 돌아오는지 확인합니다.
- 상점 이름을 입력하고 **상점 이름 검색**을 누릅니다. 공식 Google 검색 화면에서 선택하면 이름과 빨간 상점 마커가 바뀌어야 합니다.
- 검색 취소·실패 시 편집하던 이름과 위치가 유지되는지 확인합니다.
- Google Cloud에서 지도·검색 사용량과 오류를 확인합니다. 앱의 요청 필드는 장소 이름과 위치로 제한하고 검색 결과 전체를 저장하지 않습니다.

Android APK의 API 키는 확인할 수 있습니다. Android 앱/서명과 API 제한으로 다른 앱의 사용을 막아야 합니다. 판매 시 구매자의 별도 키 발급은 필요하지 않으며, 사용 요금은 판매자의 프로젝트에 청구됩니다.
