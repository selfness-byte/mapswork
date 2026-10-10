# 이곳 Google 지도·업데이트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 이름·설정 진입을 개선하고 GitHub APK 업데이트 및 Google 지도·장소 검색을 제공한다.

**Architecture:** 순수 Kotlin 업데이트 규칙, Android 다운로드/서명 검증, Compose 업데이트 카드로 나눈다. 지도는 Google MapView, 검색은 공식 Places 검색 화면과 장소 상세 조회를 사용한다. 키 없는 빌드에서는 Google 지도 앱/웹 연결을 제공한다.

**Tech Stack:** Kotlin/Compose/Room, Google Maps 19.2.0, Places 4.3.1, OkHttp, Android FileProvider.

**Spec:** docs/superpowers/specs/2026-10-10-igot-google-updates-design.md

## Global Constraints

- Android 8 이상, `com.selfness.mapswork`, Room v1 유지. versionCode `2`, versionName `0.2.0`, 기존 테스트 서명 유지.
- 매니페스트 최대 32KiB, APK 최대 80MiB; 다운로드 SHA-256·패키지·버전·서명 검증. 자동 설치 없음.
- Google 키를 Git이나 로그에 넣지 않음. 기본 전달 APK는 키가 없음. 실제 휴대폰 검증은 미실행으로 표시.
- 기존 클라우드 체크아웃을 사용. M 직접 접근 없음. 기존 근처 알림 규칙 유지.

## Review Focus

1. 같은 버전·다운그레이드·지원하지 않는 Android는 설치를 제안하지 않는다.
2. 손상되거나 다른 앱/서명인 APK와 비허용 URL은 설치에 전달하지 않는다.
3. 다운로드 취소·네트워크 실패 후 임시 파일이 남거나 버튼이 계속 잠기지 않는다.
4. 검색 취소·좌표 없는 결과는 편집하던 장소를 덮어쓰지 않는다.
5. 지도 화면을 닫은 뒤 SDK 콜백이 파괴한 MapView를 갱신하지 않는다. 내 위치 재요청은 같은 좌표여도 중심을 옮긴다.

### Task 1: GitHub 업데이트와 화면 이름

**Files:** core/.../UpdateRules.kt, app/.../update/{UpdateSource,UpdateManager,ApkVerifier}.kt, ui/{UpdateCard,SettingsScreen,MapsworkApp,MapsworkViewModel,ShopListScreen}.kt, AndroidManifest.xml, res/xml/update_paths.xml.

**Interfaces:** `UpdateRelease(versionCode, versionName, minSdk, apkUrl, sha256, notes)`; `UpdateRules.availability(release, installedCode, sdk)`; `UpdateSource.fetchRelease()` / `download(release, file, onProgress)`; `UpdateManager.status`, `check()`, `download()`, `installationFile()`.

- [x] UpdateRulesTest 작성: 버전, SDK, 이 저장소의 HTTPS URL, SHA, APK 식별 검증.
- [x] `tools/cloud-gradle.sh :core:test`로 규칙 누락 실패 확인, 구현 후 통과 확인.
- [x] UpdateManagerTest 작성: 완전한 파일, 잘못된 SHA, 실패 파일 삭제, 서명 거부, 재시도, 설치 전 재검증. 먼저 실패 확인.
- [x] 네트워크 제한, 다운로드 관리, Android APK 검증, FileProvider, 사용자 설치 확인 구현.
- [x] 이름·큰 설정 버튼·업데이트 카드 변경. 기존 Compose 테스트를 바꾸고 먼저 실패 확인.
- [x] `:core:test :app:testDebugUnitTest` 통과 후 커밋.

### Task 2: Google 지도와 상점 검색

**Files:** app/build.gradle.kts, AndroidManifest.xml, ui/{ShopMap,ShopEditorScreen,GooglePlaceSearch,GoogleMapsLinks}.kt, docs/GOOGLE_MAPS_SETUP.ko.md.

**Interfaces:** `ShopMap(point, onPointSelected, currentPoint, cameraTarget, cameraRevision, modifier)`; `GooglePlaceSearch.intent(context, query, bias)` / `selection(context, resultIntent)`, 결과 `SelectedPlace(name, point)`.

- [x] Google 지도 링크 테스트: 이름 인코딩, GPS 위치, 웹 대체 경로. 장소 선택 테스트: 좌표 없는 결과 거부, 유효한 장소 선택.
- [x] 먼저 테스트 실패를 확인하고 보조 코드 구현.
- [x] 빌드 키 입력 경로 설정, MapLibre를 Google SDK로 교체, 지도 수명과 파란 현재 위치 마커 구현.
- [x] 검색 버튼으로 공식 Places 화면 열기, 선택 후 이름·위치 채우기. 취소·오류는 초안 유지.
- [x] 키가 없으면 연결 대기와 Google 지도 앱/웹 열기 제공. 현재 위치를 명시적으로 상점 위치로 선택 가능.
- [x] 전체 테스트·assembleDebug·lintDebug 실행. 키·비용·서명 안내서 작성 후 커밋.

### Task 3: 검토와 GitHub 전달

**Files:** tools/package_delivery.py, README.md, docs/{USER_GUIDE.ko,VALIDATION.ko,THIRD_PARTY_NOTICES}.md, downloads/{README.md,update.json,APK,ZIP,SHA256SUMS.txt}.

- [ ] 새 맥락의 최종 검토 담당 한 명이 변경을 확인. Important/Critical 수정과 테스트.
- [ ] 패키징 버전 갱신, 네이티브 라이브러리가 없는 정상 APK 허용. 전체 검증 후 소스 커밋.
- [ ] 커밋 소스에서 0.2.0 APK/ZIP 생성. 기존 APK와 서명이 같은지 확인.
- [ ] APK SHA-256으로 업데이트 메타데이터 생성. 파일·체크섬 확인 후 전달 파일 커밋, 승인된 브랜치 push.
- [ ] GitHub 업데이트 파일을 다시 읽어 버전·해시 일치 확인. API 키와 실기기 확인 필요 사항 보고.
