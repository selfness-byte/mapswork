package com.selfness.mapswork.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selfness.mapswork.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopEditorScreen(
    viewModel: MapsworkViewModel, shopId: String?, device: DeviceState,
    onBack: () -> Unit, onSaved: () -> Unit, onMissing: () -> Unit,
    onRequestLocation: () -> Unit, onMessage: (String) -> Unit, snackbar: SnackbarHostState,
) {
    var loaded by rememberSaveable(shopId) { mutableStateOf(shopId == null) }
    var name by rememberSaveable(shopId) { mutableStateOf("") }
    var hasPoint by rememberSaveable(shopId) { mutableStateOf(false) }
    var latitude by rememberSaveable(shopId) { mutableDoubleStateOf(0.0) }
    var longitude by rememberSaveable(shopId) { mutableDoubleStateOf(0.0) }
    var radius by rememberSaveable(shopId) { mutableIntStateOf(200) }
    var enabled by rememberSaveable(shopId) { mutableStateOf(true) }
    var waitingForLocationPermission by rememberSaveable(shopId) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(emptyList<ValidationError>()) }
    var cameraTarget by remember { mutableStateOf<GeoPoint?>(null) }
    val scope = rememberCoroutineScope()
    val point = if (hasPoint) GeoPoint(latitude, longitude) else null

    fun locate() {
        if (locating) return
        scope.launch {
            locating = true
            try {
                val current = viewModel.currentPoint()
                if (current != null) cameraTarget = current
                else onMessage("현재 위치를 확인하지 못했어요. 휴대폰 위치 사용을 켜고 다시 시도해 주세요.")
            } finally { locating = false }
        }
    }
    LaunchedEffect(shopId) {
        if (!loaded && shopId != null) {
            val shop = viewModel.findShop(shopId)
            if (shop == null) onMissing()
            else {
                name = shop.name; latitude = shop.point.latitude; longitude = shop.point.longitude
                hasPoint = true; radius = shop.radiusMeters; enabled = shop.enabled; loaded = true
            }
        }
    }
    LaunchedEffect(device.fineLocation) {
        if (device.fineLocation && waitingForLocationPermission) {
            waitingForLocationPermission = false
            locate()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (shopId == null) "상점 추가" else "상점 수정") },
        navigationIcon = { IconButton(onClick = onBack, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "목록으로") } }) },
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (!loaded) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name, onValueChange = { name = it }, label = { Text("상점 이름") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), isError = ValidationError.NAME in errors,
                supportingText = { Text("${name.trim().length}/80자") }, enabled = !saving)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("상점 위치", style = MaterialTheme.typography.titleSmall)
                    Text("지도를 누르면 위치가 선택돼요", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = {
                    if (device.fineLocation) locate()
                    else { waitingForLocationPermission = true; onRequestLocation() }
                }, enabled = !locating && !saving) {
                    Icon(Icons.Default.Place, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (locating) "확인 중" else "내 위치")
                }
            }
            ShopMap(point, onPointSelected = {
                if (!saving) { latitude = it.latitude; longitude = it.longitude; hasPoint = true }
            }, cameraTarget, Modifier.fillMaxWidth().height(320.dp))
            Text(if (point == null) "지도에서 상점 위치를 골라 주세요" else "위치가 선택됐어요. 표시한 점이 상점 위치인지 확인해 주세요.",
                color = if (ValidationError.LOCATION in errors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Text("알림 반경", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopRules.RADII.forEach { meters ->
                    FilterChip(selected = radius == meters, onClick = { radius = meters }, label = { Text(radiusLabel(meters)) }, enabled = !saving)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("이 상점의 알림", style = MaterialTheme.typography.titleSmall)
                    Text("알림 없이 저장해 둘 수도 있어요", style = MaterialTheme.typography.bodySmall)
                }
                Switch(enabled, onCheckedChange = { enabled = it }, enabled = !saving)
            }
            if (errors.isNotEmpty()) {
                Text(errors.joinToString("\n") { it.description() }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = {
                scope.launch {
                    saving = true
                    try {
                        errors = viewModel.save(ShopDraft(name, point, radius, enabled), shopId)
                        if (errors.isEmpty()) onSaved()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { onMessage("저장하지 못했어요. 다시 시도해 주세요.") }
                    finally { saving = false }
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "저장 중" else "저장") }
            if (shopId != null) TextButton(onClick = { confirmDelete = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text("상점 삭제", color = MaterialTheme.colorScheme.error)
            }
            Text("같은 방문에서는 한 번 알려 드려요. 같은 상점의 알림 사이에는 최소 30분 간격이 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("상점을 삭제할까요?") },
        text = { Text("$name 상점과 근처 알림 설정이 삭제돼요.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            scope.launch {
                saving = true
                try { viewModel.delete(requireNotNull(shopId)); onBack() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { onMessage("삭제하지 못했어요. 다시 시도해 주세요.") }
                finally { saving = false }
            }
        }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } })
}
