# GitHub Actions APK 게시·버전 안내 자동 갱신

`codex/nearby-shop-alert`에 `downloads/mapswork-X.Y.Z-test.apk`와 같은 이름의
`.sha256` 파일을 게시하면 `sync-updates.yml`이 실행됩니다. 워크플로 설치·검증
스크립트 변경 때도 현재 최신 APK를 검증합니다. 앱 소스를 main에 합치지 않습니다.

## 검증과 게시

1. 기존 Ubuntu 러너의 `aapt`·`apksigner`로 실제 패키지명, 버전, minSdk, APK v2
   서명을 검사합니다. 서명 인증서는 기존 시험판 SHA-256
   `80dfd84789e5c205b319562bf99bd33500b64000695594f1b8dbf2b58638cf40`에 고정합니다.
2. 실제 크기(최대 80MiB)와 SHA-256을 계산하고 게시된 체크섬과 비교합니다.
   파일명은 APK의 versionName과 일치해야 합니다. 체크섬 형식은
   `<소문자 SHA-256>  mapswork-X.Y.Z-test.apk`와 마지막 줄바꿈입니다.
3. 두 브랜치의 기존 버전 및 아직 버전 정보에 반영되지 않은 게시 APK의 실제
   버전과 비교합니다. 버전 코드·이름 역행, 같은 버전의 다른 바이너리,
   손상된 버전 정보, 더 최신 APK가 이미 있는 경우 실패합니다.
4. APK·체크섬이 함께 있는 실제 게시 커밋으로 다운로드 주소를 고정합니다.
   안내 파일 수정 커밋으로 주소를 바꾸지 않습니다.
5. 앱 브랜치의 `downloads/update.json`, `downloads/README.md`, 루트 README와
   main의 `이곳/update.json`, `이곳/README.md`, 루트 README를 갱신합니다.
   버전 JSON은 두 브랜치에서 동일합니다. 다른 파일은 커밋에 들어가지 않습니다.
6. 두 브랜치를 `git push --atomic`으로 함께 게시합니다. 보호 규칙 또는 동시 변경으로
   실패하면 어느 브랜치도 갱신하지 않습니다. 두 브랜치 모두 변경이 없으면
   커밋하지 않습니다. 한쪽 안내만 고칠 때는 반대쪽에 내용이 같은 조정 커밋을
   만들어 두 ref가 실제 원자적 갱신에 참여하도록 합니다. 파일 변경은 없습니다.

기존 APK·이전 ZIP·기록은 보존합니다. 시험용 디버그 서명본이며 실제 휴대폰의
설치·데이터 보존은 별도 시험이 필요합니다. Firebase 기존 사이트는 배포하지 않습니다.

## APK를 다른 Actions 빌드 작업에서 게시할 때

**GITHUB_TOKEN으로 푸시한 변경은 다른 push 워크플로를 시작하지 않습니다.**
따라서 빌드 작업이 APK를 바로 Git에 푸시하는 대신, APK를 Actions artifact로
보관한 뒤 아래 재사용 가능한 **게시 워크플로**를 호출하세요. 같은 실행의 artifact를
가져와 APK를 먼저 검증·게시하고, `needs: publish` 후속 작업이 버전 갱신 워크플로를
직접 호출합니다. 이 연결은 `publish-apk.yml`에 구현되어 있습니다.

앱 브랜치에서 실행되는 빌드 워크플로의 job 연결 예:

```yaml
jobs:
  build:
    # 기존 빌드·검증 작업. 동일 서명의 mapswork-X.Y.Z-test.apk를
    # Actions artifact 'mapswork-apk'로 저장한다.
    # Actions upload-artifact 사용 시 공식 action을 커밋 SHA로 고정한다.
    # 원본 서명 키를 저장소·artifact·로그에 포함하지 않는다.
    ...
  publish:
    needs: build
    permissions:
      contents: write
      actions: read
    uses: ./.github/workflows/publish-apk.yml
    with:
      artifact_name: mapswork-apk
      release_notes: '이번 버전의 변경 안내'
```

현재 저장소에는 별도 APK 빌드·게시 Actions 작업이 없습니다. 위 재사용 워크플로는
향후 빌드 작업이 직접 호출하는 게시 경로입니다. 앱 소스를 자동 빌드하거나
새 서명 키를 만들지 않습니다. 재사용 워크플로는 이 저장소의 앱 브랜치 실행만 받습니다.
다른 게시 작업을 추가할 때 이 호출을 생략하고 GITHUB_TOKEN push만 하면 자동 갱신되지 않습니다.

## 검사·재실행

```bash
python3 -m unittest discover -s tools/tests -v
python3 tools/sync_github_updates.py sync --report /tmp/mapswork-preview.json
# 위 명령은 검증·커밋 준비까지만 수행하고 원격을 변경하지 않는다.
# 실제 게시는 같은 명령에 --push를 추가한다.
```

Android SDK build-tools가 이미 설치되어 있어야 합니다. 경로를 지정하려면
`--build-tools /설치된/sdk/build-tools/36.0.0`을 사용합니다. 새 도구·인증을 설치하지 않습니다.
검사는 실제 0.4.0 APK와 임시 로컬 Git 원격에서 수행됩니다.

GitHub 저장소의 **Actions → Verify APK and sync update information**에서 결과를
확인합니다. 성공한 작업의 Summary에는 검증한 APK 정보, 서명 지문, 양쪽 결과 커밋이
남습니다. 동시 변경 또는 보호 규칙 때문에 실패했다면 원인을 확인하고 **Re-run jobs**로
최신 원격 상태에서 다시 검사합니다. 브랜치 보호나 계정 권한을 바꾸지 않습니다.
워크플로가 앱 브랜치에 있으므로 기본 브랜치의 수동 Run workflow 메뉴에는 표시되지 않을 수 있습니다.

공식 동작 설명:
- [워크플로 문법](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [GITHUB_TOKEN과 워크플로 실행](https://docs.github.com/en/actions/concepts/security/github_token)
