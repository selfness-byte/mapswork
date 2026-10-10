package com.selfness.mapswork.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.selfness.mapswork.core.*
import com.selfness.mapswork.BuildConfig
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity
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
    var searching by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(emptyList<ValidationError>()) }
    var cameraTarget by remember { mutableStateOf<GeoPoint?>(null) }
    var cameraRevision by remember { mutableIntStateOf(0) }
    var hasCurrent by rememberSaveable(shopId) { mutableStateOf(false) }
    var currentLatitude by rememberSaveable(shopId) { mutableDoubleStateOf(0.0) }
    var currentLongitude by rememberSaveable(shopId) { mutableDoubleStateOf(0.0) }
    val currentPoint = if (hasCurrent) GeoPoint(currentLatitude, currentLongitude) else null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val point = if (hasPoint) GeoPoint(latitude, longitude) else null
    val searchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            scope.launch {
                searching = true
                try {
                    val selected = GooglePlaceSearch.selection(context, data)
                    if (selected != null) {
                        name = selected.name; latitude = selected.point.latitude; longitude = selected.point.longitude
                        hasPoint = true; cameraTarget = selected.point; cameraRevision++
                    } else onMessage("이 상점의 위치를 확인하지 못했어요. 다른 검색 결과를 선택해 주세요.")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { onMessage("상점 정보를 가져오지 못했어요. 인터넷 연결과 검색 설정을 확인해 주세요.") }
                finally { searching = false }
            }
        } else if (result.resultCode == PlaceAutocompleteActivity.RESULT_ERROR) {
            onMessage("상점을 검색하지 못했어요. 인터넷 연결과 검색 설정을 확인해 주세요.")
        }
    }

    fun locate() {
        if (locating) return
        scope.launch {
            locating = true
            try {
                val current = viewModel.currentPoint()
                if (current != null) {
                    currentLatitude = current.latitude; currentLongitude = current.longitude; hasCurrent = true
                    cameraTarget = current; cameraRevision++
                }
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
                supportingText = { Text("${name.trim().length}/80자") }, enabled = !saving && !searching)
            OutlinedButton(onClick = {
                if (BuildConfig.HAS_GOOGLE_MAPS_KEY) {
                    try { searchLauncher.launch(GooglePlaceSearch.intent(context, name, currentPoint ?: point)) }
                    catch (_: Exception) { onMessage("검색 화면을 열지 못했어요. 검색 설정을 확인해 주세요.") }
                } else {
                    if (!GoogleMapsLinks.open(context, GoogleMapsLinks.search(name, currentPoint ?: point))) onMessage("Google 지도 앱이나 웹 브라우저를 설치해 주세요.")
                }
            }, enabled = name.isNotBlank() && !saving && !searching, modifier = Modifier.fillMaxWidth()) {
                Text(if (searching) "상점 확인 중" else "상점 이름 검색")
            }
            if (!BuildConfig.HAS_GOOGLE_MAPS_KEY) Text("지금은 Google 지도 앱에서 검색해요. 앱 안에서 결과 선택은 지도 연결 후 사용할 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("상점 위치", style = MaterialTheme.typography.titleSmall)
                    Text(if (BuildConfig.HAS_GOOGLE_MAPS_KEY) "지도를 누르면 위치가 선택돼요" else "내 위치를 확인하고 상점 위치로 선택해 주세요",
                        style = MaterialTheme.typography.bodySmall)
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
                if (!saving && !searching) { latitude = it.latitude; longitude = it.longitude; hasPoint = true }
            }, currentPoint, cameraTarget, cameraRevision, Modifier.fillMaxWidth().height(320.dp))
            if (currentPoint != null) TextButton(onClick = {
                latitude = currentPoint.latitude; longitude = currentPoint.longitude; hasPoint = true
                cameraTarget = currentPoint; cameraRevision++
            }, enabled = !saving && !searching, modifier = Modifier.fillMaxWidth()) { Text("내 위치를 상점 위치로 선택") }
            Text(if (point == null) "상점 위치를 골라 주세요" else "상점 위치가 선택됐어요. 실제 상점 위치인지 확인해 주세요.",
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
            }, enabled = !saving && !searching, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "저장 중" else "저장") }
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
