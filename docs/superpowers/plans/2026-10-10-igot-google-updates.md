# 이곳 Google 지도·업데이트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

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

- [ ] 写 UpdateRulesTest：版本、SDK、HTTPS/本仓库 URL、SHA 和 APK 身份拒绝/允许。
- [ ] 跑 `tools/cloud-gradle.sh :core:test`，观察规则缺失失败；实现规则再运行通过。
- [ ] 写 UpdateManagerTest：完整文件、错误 SHA、失败文件删除、签名拒绝、重试和安装前再检查；先运行看到失败。
- [ ] 实现受限网络源、下载管理、Android APK 验证、FileProvider 和用户确认安装。
- [ ] 更新名称、大设置按钮、设置更新卡。调整现有真实 Compose 测试并先观察屏幕测试失败。
- [ ] 跑 `:core:test :app:testDebugUnitTest` 并提交。

### Task 2: Google 지도와 상점 검색

**Files:** app/build.gradle.kts, AndroidManifest.xml, ui/{ShopMap,ShopEditorScreen,GooglePlaceSearch,GoogleMapsLinks}.kt, docs/GOOGLE_MAPS_SETUP.ko.md.

**Interfaces:** `ShopMap(point, onPointSelected, currentPoint, cameraTarget, cameraRevision, modifier)`；`GooglePlaceSearch.intent(context, query, bias)` / `selection(context, resultIntent)`，返回 `SelectedPlace(name, point)`。

- [ ] 写 Google 地图链接测试：店名转义、GPS 点、浏览器回退；写 PlaceSelection 测试：无坐标拒绝/有效坐标选中。
- [ ] 先运行看到失败，实现最小辅助代码。
- [ ] 配置安全的构建 key 源，替换 MapLibre 为 Google SDK，实现生命周期和蓝色当前位置标记。
- [ ] 搜索按钮打开官方 Places UI，选择后填入名称/位置；取消和错误保持草稿。
- [ ] 无 key 显示等待并打开 Google 地图应用/网页。当前位置仍可明确选为店铺位置。
- [ ] 跑所有测试、assembleDebug、lintDebug，写 key/计费/签名操作说明，提交。

### Task 3: 审查和 GitHub 交付

**Files:** tools/package_delivery.py, README.md, docs/{USER_GUIDE.ko,VALIDATION.ko,THIRD_PARTY_NOTICES}.md, downloads/{README.md,update.json,APK,ZIP,SHA256SUMS.txt}。

- [ ] 一位全新上下文审查代理检查本次变更，处理 Important/Critical 的问题并测试。
- [ ] 更新交付脚本版本与没有原生库的合法 APK 检查；运行完整验证、提交代码。
- [ ] 从已提交源码打包 0.2.0 APK/ZIP；确认旧 APK 与新 APK 的签名相同。
- [ ] 从 APK 生成 SHA-256 更新元数据，检查包内容/摘要后提交交付文件并 push 已授权的分支。
- [ ] 回读 GitHub 更新文件，验证版本/摘要与交付 APK 一致；报告 API key 和实机尚需验证。
