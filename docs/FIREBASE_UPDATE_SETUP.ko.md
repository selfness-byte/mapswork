# Firebase 자동 업데이트 설정 — 이곳 0.3.0

## 구성

- Firebase 프로젝트: **inanimals-1977**
- 업데이트 전용 Hosting 사이트: **inanimals-1977-mapswork**
- 버전 정보 주소: `https://inanimals-1977-mapswork.web.app/updates/update.json`
- APK: GitHub `selfness-byte/mapswork`의 `codex/nearby-shop-alert/downloads`

앱은 Firebase의 작은 공개 버전 파일을 확인하고 새 버전 알림을 표시합니다. APK 다운로드는 사용자가 선택합니다. 설치 단계는 Android의 확인 화면을 거칩니다. 일반 Android 앱은 사용자 확인을 생략한 APK 설치를 할 수 없습니다.

현재 전달본에는 사이트 주소가 들어 있습니다. **이 클라우드에서 Firebase에 로그인하거나 사이트를 만들고 배포하지는 않았습니다.** 배포 전·접속 실패 시 공개 GitHub 버전 파일에서 다시 확인합니다. Firebase SDK, Analytics, Auth, Firestore, Storage와 `google-services.json`은 사용하지 않습니다. 상점이나 위치를 Firebase에 전송하지 않습니다.

## 자동 확인

설정의 **새 버전 자동 확인**은 기본으로 켜져 있습니다. 처음 예약한 작업은 15분 이후부터 실행할 수 있으며, 이후 약 하루에 한 번 인터넷이 연결되고 배터리가 부족하지 않을 때 확인합니다. Android 절전 정책에 따라 더 늦어질 수 있습니다.

같은 버전은 한 번만 알립니다. 알림 권한·채널이 꺼져 있으면 알린 것으로 기록하지 않습니다. 권한을 켠 뒤 다음 확인 때 다시 알릴 수 있습니다. 네트워크 오류는 제한적으로 재시도합니다. 스위치를 끄면 자동 작업과 업데이트 알림을 취소합니다. 상점 근처 알림 스위치와 별개입니다.

업데이트 알림을 누르면 설정이 열리고 버전을 확인합니다. **업데이트 다운로드 → 업데이트 설치**를 선택하세요. 해시, 앱 식별자, 버전과 기존 앱 서명을 확인합니다. 같은 서명을 유지한 앱을 삭제하지 않고 업데이트하면 상점이 유지됩니다.

## Firebase에 처음 배포하기

1. 전달 ZIP에서 `mapswork-firebase-hosting.zip`을 꺼내 압축을 풉니다.
2. Firebase 콘솔에서 `inanimals-1977`에 대한 Hosting 배포 권한을 확인합니다.
3. Node.js 20 이상이 있는 컴퓨터에서 압축을 푼 `mapswork-firebase-hosting` 폴더로 이동합니다.
4. 아래 명령을 실행합니다. 로그인은 자신의 브라우저에서 합니다. 비밀번호·인증 파일·토큰은 채팅이나 Git에 올리지 마세요.

```bash
npx --yes firebase-tools@15.33.0 login
npx --yes firebase-tools@15.33.0 hosting:sites:create inanimals-1977-mapswork --project inanimals-1977
npx --yes firebase-tools@15.33.0 deploy --only hosting --project inanimals-1977
```

이 전용 사이트를 해당 프로젝트에 이미 만들었다면 생성 명령을 생략합니다. 생성 실패 시 다른 사이트나 프로젝트 기본 사이트에 대신 배포하지 마세요. Firebase Hosting 배포는 선택한 사이트의 파일 전체를 바꾸므로 이곳 업데이트 전용 사이트를 사용합니다. 제공한 `firebase.json`은 전용 사이트를 명시합니다.

배포 후 버전 정보 주소를 열어 전달본의 버전·APK URL·SHA-256과 같은지 확인합니다. 휴대폰의 **설정 → 업데이트 확인**으로 연결을 시험하세요. 현재 설치한 버전과 같으면 새 버전 알림이 나오지 않습니다.

## 다음 업데이트 게시

1. 같은 앱 ID `com.selfness.mapswork`와 서명 키를 유지하고 `versionCode`·`versionName`을 올립니다.
2. 클라우드에서 시험·빌드를 마친 소스를 커밋하고 `python3 tools/package_delivery.py`를 실행합니다.
3. 새 APK·`update.json`·ZIP·체크섬을 GitHub에 올립니다.
4. 새 Firebase ZIP을 풀고 전용 사이트에 다시 배포합니다.

앱의 자동 확인과 배포 자동화는 별개입니다. 이 전달본은 앱의 자동 확인을 구현하며 Firebase 배포는 위 명령으로 진행합니다.

다른 전용 사이트를 쓰려면 빌드 환경의 `MAPSWORK_FIREBASE_HOSTING_SITE` 또는 무시되는 `local.properties`의 `mapswork.firebaseHostingSite`에 실제 사이트 ID를 넣고 버전을 올려 다시 빌드합니다. 사이트 ID는 공개 주소이며 인증키가 아닙니다.

## 비용과 저장

Firebase에는 버전 JSON과 안내 페이지만 배포합니다. Hosting의 무료 할당량 안에서 사용하도록 구성했으며 APK는 GitHub에서 전송합니다. Firebase Storage·유료 데이터베이스는 사용하지 않습니다. 프로젝트의 기존 요금제와 다른 서비스 사용 요금은 Firebase 콘솔에서 확인합니다.

Firebase 연결은 Google Maps API 키 연결과 별개입니다. 현재 APK의 앱 내부 Google 지도·검색은 기존처럼 키 연결 대기입니다.

## 확인한 범위

클라우드의 버전 규칙·자동 확인 간격·중복 알림·권한 거부·중간 해제·동시 확인·알림의 설정 진입 시험, APK 빌드와 정적 검사를 통과했습니다. 실제 Firebase 사이트 배포와 휴대폰의 예약 실행·알림·APK 설치는 여기서 확인하지 않았습니다.

Mac에서는 APK와 완료 파일만 보관하면 됩니다. 전달본의 `save-apk-to-mac.command`를 Mac에서 실행하면 Finder 폴더 선택 창이 열립니다. **mapswork-github** 폴더를 선택하면 APK를 받아 SHA-256을 확인한 뒤 그 폴더에 저장합니다. 클라우드는 Mac 폴더나 외장하드 M을 직접 읽거나 저장할 수 없습니다.
