# Firebase 자동 업데이트 설정 — 이곳 0.3.0

## 구성

- Firebase 프로젝트·기존 Hosting 사이트 ID: **inanimals-1977**
- 버전 정보 주소: `https://inanimals-1977.web.app/mapswork/update.json`
- APK: GitHub `selfness-byte/mapswork`의 `codex/nearby-shop-alert/downloads`

앱은 Firebase의 작은 공개 버전 파일을 확인하고 새 버전 알림을 표시합니다. APK 다운로드는 사용자가 선택합니다. 설치 단계는 Android의 확인 화면을 거칩니다. 일반 Android 앱은 사용자 확인을 생략한 APK 설치를 할 수 없습니다.

현재 전달본에는 사이트 주소가 들어 있습니다. **Firebase에 파일을 배포하지는 않았습니다.** 이 클라우드에는 Firebase 배포 인증이 없으며, 원격 mapswork의 main·앱 브랜치에서 기존 웹사이트의 배포 설정과 소스도 찾지 못했습니다. 배포 전·접속 실패 시 공개 GitHub 버전 파일에서 다시 확인합니다. Firebase SDK, Analytics, Auth, Firestore, Storage와 google-services.json은 사용하지 않습니다. 상점이나 위치를 Firebase에 전송하지 않습니다.

## 자동 확인

설정의 **새 버전 자동 확인**은 기본으로 켜져 있습니다. 처음 예약한 작업은 15분 이후부터 실행할 수 있으며, 이후 약 하루에 한 번 인터넷이 연결되고 배터리가 부족하지 않을 때 확인합니다. Android 절전 정책에 따라 더 늦어질 수 있습니다.

같은 버전은 한 번만 알립니다. 알림 권한·채널이 꺼져 있으면 알린 것으로 기록하지 않습니다. 권한을 켠 뒤 다음 확인 때 다시 알릴 수 있습니다. 네트워크 오류는 제한적으로 재시도합니다. 스위치를 끄면 자동 작업과 업데이트 알림을 취소합니다. 상점 근처 알림 스위치와 별개입니다.

업데이트 알림을 누르면 설정이 열리고 버전을 확인합니다. **업데이트 다운로드 → 업데이트 설치**를 선택하세요. 해시, 앱 식별자, 버전과 기존 앱 서명을 확인합니다. 같은 서명을 유지한 앱을 삭제하지 않고 업데이트하면 상점이 유지됩니다.

## 기존 Firebase 사이트에 파일 추가

Firebase Hosting 배포는 해당 사이트의 전체 파일 목록을 교체합니다. 따라서 기존 웹사이트를 배포하던 소스와 공개 폴더를 확인한 뒤 사이트 전체와 함께 배포해야 합니다.

1. 전달 ZIP에서 mapswork-firebase-hosting.zip을 꺼내 압축을 풉니다.
2. 기존 웹사이트 프로젝트의 firebase.json에서 inanimals-1977 사이트에 연결된 hosting.public 경로를 확인합니다. 빌드 과정이 있다면 기존 웹사이트를 먼저 빌드합니다.
3. ZIP 안의 **mapswork 폴더**를 최종 공개 폴더 안에 추가합니다. 버전 파일은 최종 공개 폴더/mapswork/update.json에 있어야 합니다. 기존 /mapswork 경로가 다른 용도로 쓰이면 덮어쓰지 말고 앱 경로부터 조정합니다.
4. 기존 firebase.json의 해당 Hosting 설정에 다음 헤더를 추가합니다. 기존 헤더·리라이트·배포 대상과 사이트 파일은 보존합니다.

```json
{
  "source": "/mapswork/update.json",
  "headers": [
    {"key": "Cache-Control", "value": "no-store"},
    {"key": "Content-Type", "value": "application/json; charset=utf-8"},
    {"key": "X-Content-Type-Options", "value": "nosniff"}
  ]
}
```

5. 기존 프로젝트의 정상 배포 절차로 사이트 전체와 함께 배포합니다. 여러 Hosting 사이트가 있으면 inanimals-1977에 연결된 대상만 선택합니다.
6. 버전 정보 주소가 HTML 대신 JSON을 반환하고 전달본의 버전·APK URL·SHA-256과 같은지 확인합니다. 휴대폰의 **설정 → 업데이트 확인**으로 시험하세요.

제공한 ZIP은 **기존 사이트에 추가할 자료**이며 독립 배포용 firebase.json이나 .firebaserc가 없습니다. ZIP만으로 기존 사이트를 새로 배포하면 기존 페이지가 사라질 수 있습니다. DEPLOY.ko.md는 공개 폴더에 복사할 필요가 없습니다.

수동 CLI를 쓰는 기존 프로젝트에서는 Node.js 20 이상과 firebase-tools@15.33.0을 사용합니다. 로그인은 자신의 브라우저에서 합니다. 비밀번호·인증 파일·토큰은 채팅이나 Git에 올리지 마세요. 원래 웹사이트 소스와 배포 인증을 확인한 후 배포 명령을 실행합니다.

## 다음 업데이트 게시

1. 같은 앱 ID com.selfness.mapswork와 서명 키를 유지하고 versionCode·versionName을 올립니다.
2. 클라우드에서 시험·빌드를 마친 소스를 커밋하고 python3 tools/package_delivery.py를 실행합니다.
3. 새 APK·update.json·ZIP·체크섬을 GitHub에 올립니다.
4. 새 Firebase ZIP의 mapswork/update.json을 기존 웹사이트의 공개 폴더에 반영하고 사이트 전체와 함께 배포합니다.

앱의 자동 확인과 배포 자동화는 별개입니다. 이 전달본은 앱의 자동 확인을 구현하며 Firebase 배포는 기존 웹사이트 프로젝트에서 진행합니다.

다른 Hosting 사이트를 쓰려면 빌드 환경의 MAPSWORK_FIREBASE_HOSTING_SITE 또는 무시되는 local.properties의 mapswork.firebaseHostingSite에 실제 사이트 ID를 넣고 버전을 올려 다시 빌드합니다. 사이트 ID는 공개 주소이며 인증키가 아닙니다. 기본 파일 경로는 /mapswork/update.json입니다.

## 비용과 저장

Firebase에는 버전 JSON과 안내 페이지만 추가합니다. APK는 GitHub에서 전송합니다. Firebase Storage·유료 데이터베이스는 사용하지 않습니다. Hosting 비용은 프로젝트 요금제와 전체 사이트 사용량에 따라 달라지므로 Firebase 콘솔의 무료 할당량·사용량을 확인합니다.

Firebase 연결은 Google Maps API 키 연결과 별개입니다. 현재 APK의 앱 내부 Google 지도·검색은 기존처럼 키 연결 대기입니다.

## 확인한 범위

클라우드의 버전 규칙·자동 확인 간격·중복 알림·권한 거부·중간 해제·동시 확인·알림의 설정 진입 시험을 포함한 자동 시험 114개, APK 빌드와 정적 검사를 통과했습니다. 자동 확인을 끄는 동안 대기 중이던 알림도 표시하지 않는 것을 검증했습니다. 실제 Firebase 파일 배포와 휴대폰의 예약 실행·알림·APK 설치는 여기서 확인하지 않았습니다.

Mac에서는 APK와 완료 파일만 보관하면 됩니다. 전달본의 save-apk-to-mac.command를 Mac에서 실행하면 Finder 폴더 선택 창이 열립니다. **mapswork-github** 폴더를 선택하면 APK를 받아 SHA-256을 확인한 뒤 그 폴더에 저장합니다. 클라우드는 Mac 폴더나 외장하드 M을 직접 읽거나 저장할 수 없습니다.
