package com.selfness.mapswork.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selfness.mapswork.core.DeviceState
import com.selfness.mapswork.location.*
import com.selfness.mapswork.update.UpdateStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    enabled: Boolean, device: DeviceState, status: RegistrationStatus,
    onBack: () -> Unit, onEnable: (Boolean) -> Unit, onLocation: () -> Unit,
    onBackground: () -> Unit, onNotifications: () -> Unit, onLocationSettings: () -> Unit,
    onAppSettings: () -> Unit, onRefresh: () -> Unit, snackbar: SnackbarHostState,
    update: UpdateStatus, onUpdateCheck: () -> Unit, onUpdateDownload: () -> Unit, onUpdateInstall: () -> Unit,
    automaticUpdates: Boolean, onAutomaticUpdates: (Boolean) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("설정") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "목록으로") }
    }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UpdateCard(update, onUpdateCheck, onUpdateDownload, onUpdateInstall, automaticUpdates, onAutomaticUpdates)
            Card {
                Row(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("근처 알림 사용", style = MaterialTheme.typography.titleMedium)
                        Text("저장한 상점 근처에서 알려 드려요", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(enabled, onCheckedChange = onEnable)
                }
            }
            Text((if (enabled) status else RegistrationStatus(RegistrationPhase.PAUSED)).description(), style = MaterialTheme.typography.bodyMedium)
            Text("필요한 설정", style = MaterialTheme.typography.titleMedium)
            SettingCard("정확한 위치", device.fineLocation,
                "저장한 상점 근처인지 판단하는 데 사용해요. 위치 권한에서 ‘정확한 위치’를 켜 주세요.", "위치 허용", onLocation)
            SettingCard("앱을 닫아도 알림 받기", device.backgroundLocation,
                "위치 권한을 ‘항상 허용’으로 설정해야 화면을 닫은 상태에서도 알림을 받을 수 있어요.",
                "항상 허용 설정", onBackground, canAct = device.fineLocation)
            SettingCard("휴대폰 알림", device.notifications,
                "상점 도착과 새 버전 알림을 표시해요. 앱 알림과 ‘저장한 상점 근처 알림’·‘앱 업데이트’를 켜 주세요.", "알림 허용", onNotifications)
            SettingCard("휴대폰 위치 사용", device.locationServices,
                "휴대폰의 위치 사용이 켜져 있어야 근처를 감지할 수 있어요.", "위치 설정", onLocationSettings)
            if (!device.playServices) {
                Text("이 휴대폰에서 Google Play 서비스를 사용할 수 없어요. 서비스를 켜거나 업데이트한 뒤 다시 확인해 주세요.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("알림 상태 다시 확인") }
            TextButton(onClick = onAppSettings, modifier = Modifier.fillMaxWidth()) { Text("휴대폰의 앱 설정 열기") }
            Text("알림은 위치 정확도와 절전 설정에 따라 수 분 늦을 수 있어요. 위치를 계속 조회하거나 이동 경로를 기록하지 않아요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("상점은 이 휴대폰에 저장돼요. 앱을 삭제하면 등록한 장소도 삭제돼요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingCard(title: String, ready: Boolean, description: String, action: String, onAction: () -> Unit, canAct: Boolean = true) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(if (ready) "설정됨" else "설정 필요", color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (!ready) TextButton(onClick = onAction, enabled = canAct) { Text(action) }
        }
    }
}
