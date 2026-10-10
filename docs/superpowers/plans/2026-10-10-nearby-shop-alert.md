# Nearby Shop Alert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android 앱에 저장한 상점 근처에 도착하면 중복을 줄여 알림을 보내고, APK·소스 ZIP·사용 안내서를 전달한다.

**Architecture:** 순수 Kotlin `core` 모듈에 입력 검증·방문 판단·준비 상태 규칙을 둔다. Android `app` 모듈은 Room, Google Play Geofencing, 알림, MapLibre 지도와 Compose 화면을 연결한다. 개발과 검증은 기존 클라우드 체크아웃에서 직접 실행한다.

**Tech Stack:** JDK 21, Gradle 8.11.1, AGP 8.10.1, Kotlin 2.1.20, Compose BOM 2025.05.01, Room 2.7.1 / KSP 2.1.20-2.0.1, Play Services Location 21.3.0, WorkManager 2.10.1, MapLibre Android 11.11.0.

**Spec:** [../specs/2026-10-10-nearby-shop-alert-design.md](../specs/2026-10-10-nearby-shop-alert-design.md)

**Execution:** 사용자가 설계를 승인하고 “응 구현해봐”라고 구현을 지시했다. 이 세션에서 직접 구현하며, 별도 작업 트리를 만들지 않는다.

## Global Constraints

- Android 8.0 이상(minSdk 26), Google Play 서비스가 있는 휴대폰; compileSdk/targetSdk 36.
- 개인용, 휴대폰 내부 저장, 자동 클라우드 백업 비활성화.
- 기본 반경 200m, 선택 가능한 반경 100m / 200m / 500m / 1km.
- 같은 방문 중 중복 억제, 같은 상점 알림 사이 최소 30분(1,800,000ms).
- 알림 선택이 켜진 장소는 최대 100개; 알림을 끈 장소는 추가 저장 가능.
- 지도에서 위치 선택과 이름 입력. 지도 검색·Google 지도 목록 가져오기·Drive 백업은 첫 버전에서 제외.
- 앱을 닫거나 잠근 상태의 근접 알림, 부팅·업데이트 후 등록 복구.
- 서버와 상시 GPS 조회 없이 시스템 근접 감지 사용.
- M에는 완성 파일만 전달. 클라우드에서 로컬 외장하드를 직접 쓸 수 있다고 가정하지 않는다.

## Review Focus

- 권한을 거부하거나 철회하면 준비 상태가 바뀌고 상점 저장은 유지되어야 한다 → Task 1 준비 상태 테스트, Task 3 실제 상태 재확인.
- 상점 삭제·알림 끄기와 지연된 위치 이벤트가 겹치면 알림을 보내지 않아야 한다 → Task 1 꺼진 상점 테스트, Task 2·3 최신 DB 조회.
- 빠른 연속 진입·나감 이벤트, 쿨다운 경계, 시계가 뒤로 바뀐 경우 중복을 억제해야 한다 → Task 1 방문 규칙 테스트.
- 100개 한도에서 기존 상점 수정은 허용하고 101번째 활성화만 거부해야 한다 → Task 1 입력 검증 테스트.
- 지도 로딩 실패·앱 재시작·오래된 알림 진입에서 데이터를 잃거나 종료되지 않아야 한다 → Task 4 오류·복원 흐름 구현, 사용 안내서 기기 시험 목록.

## 파일 구성

- `core/src/main/kotlin/com/selfness/mapswork/core/Shop.kt`: 상점·좌표·방문 데이터.
- `core/src/main/kotlin/com/selfness/mapswork/core/ShopRules.kt`: 입력·활성화 한도 검증.
- `core/src/main/kotlin/com/selfness/mapswork/core/VisitRules.kt`: 진입·나감과 쿨다운 판단.
- `core/src/main/kotlin/com/selfness/mapswork/core/Readiness.kt`: 권한·기기 상태 판단.
- `app/src/main/java/com/selfness/mapswork/data/`: Room 엔티티·DAO·저장소·설정.
- `app/src/main/java/com/selfness/mapswork/location/`: 준비 상태 확인·근접 감지 등록·이벤트·복구.
- `app/src/main/java/com/selfness/mapswork/notifications/ShopNotifier.kt`: 알림 채널과 상세 진입.
- `app/src/main/java/com/selfness/mapswork/ui/`: 목록·등록·설정·지도 화면과 ViewModel.
- `app/src/main/java/com/selfness/mapswork/MapsworkApplication.kt`: 앱 의존성과 작업 스코프.
- `app/src/main/java/com/selfness/mapswork/MainActivity.kt`: 앱 시작·권한 결과·알림 진입.
- `tools/bootstrap_android.py`, `tools/cloud-gradle.sh`: 검증한 클라우드 도구 설치와 빌드 환경.
- `tools/package_delivery.py`, `docs/USER_GUIDE.ko.md`: 전달 파일과 사용·검증 안내.

## Task 1: 검증 가능한 핵심 규칙과 빌드 기반

**Files:** 루트 Gradle 구성·wrapper, `core/build.gradle.kts`, 위 `core` Kotlin 파일과 `core/src/test/kotlin/com/selfness/mapswork/core/*Test.kt`, 도구 설치·실행 스크립트.

**Interfaces:**
- `GeoPoint(latitude: Double, longitude: Double)`와 `distanceMetersTo(other: GeoPoint): Double`.
- `Shop(id: String, name: String, point: GeoPoint, radiusMeters: Int, enabled: Boolean, visitState: VisitState, lastNotifiedAtMillis: Long?)`.
- `ShopDraft(name: String, point: GeoPoint?, radiusMeters: Int, enabled: Boolean)`.
- `ShopRules.validate(draft: ShopDraft, existing: List<Shop>, editingId: String?): List<ValidationError>`.
- `VisitRules.transition(shop: Shop, transition: Transition, nowMillis: Long): VisitDecision`는 갱신된 상점과 알림 여부를 반환한다.
- `Readiness.evaluate(state: DeviceState): Readiness`는 준비 또는 구체적인 부족 조건을 반환한다.

- [x] **Step 1:** 공식 체크섬을 검증해 JDK·Gradle·Android 도구를 설치하고 SDK 36·build-tools 36.0.0을 확보한다. TLS 검증을 유지한다.
- [x] **Step 2:** 위 인터페이스를 사용하는 실패 테스트를 먼저 작성한다. 테스트는 첫 진입의 알림, 반복 진입 억제, 나감 뒤 재진입, 1,799,999ms 억제와 1,800,000ms 허용, 꺼진 상점, 시계 역행을 확인한다.
- [x] **Step 3:** 이름 앞뒤 공백, 빈 이름·81자·좌표 미선택·잘못된 위도/경도·NaN·잘못된 반경, 100개에서 기존 수정과 101번째 활성화의 입력 테스트를 작성한다. 날짜 변경선 근처 거리와 동일 좌표의 거리도 확인한다.
- [x] **Step 4:** Android 26·29·33·36의 권한·위치 서비스·Google Play 서비스·알림 비활성화 준비 상태 테스트를 작성한다.
- [x] **Step 5:** `tools/cloud-gradle.sh :core:test`를 실행해 구현 부재로 실패함을 확인하고 규칙을 구현한다. 같은 명령으로 모든 실제 테스트가 통과하는지 확인한다.
- [x] **Step 6:** 핵심 규칙과 도구 파일을 커밋한다.

## Task 2: 휴대폰의 영속 데이터와 안전한 이벤트 처리

**Files:** `app/build.gradle.kts`, `AndroidManifest.xml`, `data/ShopEntity.kt`, `data/ShopDao.kt`, `data/MapsworkDatabase.kt`, `data/ShopRepository.kt`, `data/AppSettings.kt`, `MapsworkApplication.kt`.

**Interfaces:**
- `ShopRepository.shops: Flow<List<Shop>>`, `suspend fun all(): List<Shop>`, `suspend fun find(id: String): Shop?`.
- `suspend fun save(draft: ShopDraft, id: String?): List<ValidationError>`, `suspend fun setEnabled(id: String, enabled: Boolean): List<ValidationError>`, `suspend fun delete(id: String)`.
- `suspend fun consumeTransitions(ids: List<String>, transition: Transition, nowMillis: Long, deliver: (Shop) -> Boolean): List<Shop>`는 Room 트랜잭션에서 최신 설정과 상태를 확인하고 전송 성공 후 방문 상태를 저장한다.
- `AppSettings.alertsEnabled: StateFlow<Boolean>`, `fun setAlertsEnabled(enabled: Boolean)`.
- `MapsworkApplication.container: AppContainer`는 저장소·설정·등록 관리자·알림 처리를 공유한다.

- [x] **Step 1:** Room 스키마와 변환을 구현한다. 앱에서 저장·삭제·알림 선택을 갱신할 때 Task 1 규칙을 사용하고 기존 방문 시각을 보존한다.
- [x] **Step 2:** 위치·반경 변경과 알림 재활성화는 방문 상태를 미확인으로 갱신한다. 이름 수정은 방문 상태를 유지한다. 알림 후보 생성은 하나의 DB 트랜잭션으로 처리한다.
- [x] **Step 3:** 앱 설정을 내부 SharedPreferences에 보관하고 백업·기기 전송에서 상점·설정 데이터를 제외한다.
- [x] **Step 4:** `tools/cloud-gradle.sh :app:compileDebugKotlin :core:test`로 Room 생성 코드와 모듈 연결을 확인한다. 데이터 재시작·삭제 경쟁 시험은 실제 기기 목록에 남긴다.
- [x] **Step 5:** 데이터와 앱 기반을 커밋한다.

## Task 3: 근접 감지·권한 상태·알림·재부팅 복구

**Files:** `location/DeviceStateReader.kt`, `location/GeofenceRegistrar.kt`, `location/GeofenceReceiver.kt`, `location/RestoreReceiver.kt`, `location/RestoreWorker.kt`, `notifications/ShopNotifier.kt`, manifest.

**Interfaces:**
- `DeviceStateReader.read(context: Context): DeviceState`는 운영체제의 현재 권한·위치·서비스·알림 상태를 읽는다.
- `GeofenceRegistrar.status: StateFlow<RegistrationStatus>`, `suspend fun synchronize(): Unit`는 Mutex 안에서 최신 DB·설정을 조회해 등록한다.
- `ShopNotifier.notify(shop: Shop)`는 상점 ID를 포함한 명시적 Activity PendingIntent로 알림을 만든다.
- `RestoreWorker.enqueue(context: Context)`는 단일 복구 작업을 예약한다.

- [x] **Step 1:** Task 1의 준비 규칙을 실제 Android 상태에 연결한다. 권한이 부족하면 등록을 시도하거나 준비 완료라고 표시하지 않는다.
- [x] **Step 2:** ENTER·EXIT 감지와 INITIAL_TRIGGER_ENTER를 등록한다. API 31 이상의 mutable PendingIntent는 명시적 비공개 수신기로 제한한다. 등록 성공 시에만 등록 수를 반영한다.
- [x] **Step 3:** 수신기는 `goAsync`와 도착 순서의 비동기 큐를 사용한다. 최신 전체·상점 설정을 확인하고 전송 성공 후 같은 DB 트랜잭션에서 방문을 갱신한다. 전송 거부는 방문을 소비하지 않으며 실패 이벤트·미등록 ID는 안전하게 무시한다.
- [x] **Step 4:** 부팅·앱 교체 수신기와 WorkManager 복구를 구현한다. 앱 재진입에서도 상태를 확인하고 권한 부족·서비스 오류를 구체적으로 표시한다.
- [x] **Step 5:** 알림 채널, 안정된 상점별 알림 ID, 상점 상세 진입을 구현한다.
- [x] **Step 6:** `tools/cloud-gradle.sh :app:compileDebugKotlin :core:test`로 검증하고 커밋한다. 실제 위치 이벤트와 부팅 복구는 휴대폰 검증 대상으로 분리한다.

## Task 4: 상점 목록·지도 등록·설정과 복원 흐름

**Files:** `MainActivity.kt`, `ui/MapsworkViewModel.kt`, `ui/MapsworkApp.kt`, `ui/ShopListScreen.kt`, `ui/ShopEditorScreen.kt`, `ui/SettingsScreen.kt`, `ui/ShopMap.kt`, `ui/Theme.kt`, 아이콘·문자열 리소스.

**Interfaces:**
- `MapsworkViewModel`은 Room 목록·설정·등록 상태를 StateFlow로 제공하고 Task 2 저장소와 Task 3 등록 관리자를 호출한다.
- `ShopMap(point: GeoPoint?, onPointSelected: (GeoPoint) -> Unit, cameraTarget: GeoPoint?)`은 MapLibre 라이프사이클·핀·오류·지도 출처를 관리한다.
- 화면 이동은 목록·상점 ID 기반 편집·설정 세 가지로 제한한다. 알림에서 전달받은 상점이 없으면 목록을 표시한다.

- [x] **Step 1:** 목록과 실제 준비 상태·상점 스위치·추가 버튼을 만든다. 100개 한도와 저장 오류를 한국어로 표시한다.
- [x] **Step 2:** 위치 선택·이름·반경·저장·삭제를 만든다. 입력 상태는 화면 재생성 시 복원하고 기존 상점은 저장 전까지 DB를 변경하지 않는다.
- [x] **Step 3:** OSM 지도 출처, 식별 가능한 User-Agent, 기본 캐시를 적용한다. 지도 실패의 재시도와 입력 보존을 구현한다. 현재 위치는 사용자 요청 시 한 번 조회한다.
- [x] **Step 4:** 권한 요청을 단계적으로 구현한다. Android 30 이상의 백그라운드 위치는 설정 안내로 연결하고, 복귀 시 준비 상태를 재계산한다.
- [x] **Step 5:** 앱 활성화 시 현재 위치를 한 번 확인해 방문 상태를 보정한다. 자동 알림 후보를 만들지는 않는다. 오래된 알림·삭제된 상점·위치 조회 실패를 처리한다.
- [x] **Step 6:** `tools/cloud-gradle.sh :app:assembleDebug :app:lintDebug :core:test`로 확인하고 화면 구현을 커밋한다.

## Task 5: 전체 검증·검토·전달 파일과 재사용 환경

**Files:** `README.md`, `docs/USER_GUIDE.ko.md`, `tools/package_delivery.py`, `.gitignore`, `dist/`(추적 제외), 환경의 `install_script`·`start_skill`.

- [x] **Step 1:** 전체 변경을 설계·권한 보안·삭제된 상점 이벤트·100개 한도·중복 억제·지도 정책·데이터 복원 관점에서 검토한다. 발견한 구체적인 문제만 수정하고 해당 검증을 다시 실행한다.
- [x] **Step 2:** `tools/cloud-gradle.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`를 실행해 실제 테스트 수와 빌드·검사 결과를 기록한다. SDK `apksigner verify --verbose`로 APK 서명을 확인한다.
- [ ] **Step 3:** 개인 기기 설치용 검증 APK, Git 추적 소스만 담은 ZIP, 한국어 사용·검증 안내서와 SHA-256 목록을 만든다. APK의 기기 검증 미실시를 명확히 표시하고 키·캐시·로컬 설정은 소스 ZIP에서 제외한다.
- [x] **Step 4:** 실제로 실행한 도구 설치·시작·검증 절차를 환경 초안에 저장한다. 사용자 Drive 설정과 기존 네트워크 목록을 보존한다. 저장과 게시·새 환경 검증은 구분한다.
- [ ] **Step 5:** 전달 파일의 내용·체크섬을 확인하고 최종 변경을 커밋한다. APK와 소스·안내서의 클라우드 파일 링크를 제공하고, 다운로드 후 M → 공통작업에 보관하는 방법을 안내한다.
