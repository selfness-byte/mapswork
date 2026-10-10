package com.selfness.mapswork.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfness.mapswork.location.DeviceStateReader
import kotlinx.coroutines.launch

@Composable
fun MapsworkApp(viewModel: MapsworkViewModel, incomingShopId: String?, onIncomingHandled: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val enabled by viewModel.alertsEnabled.collectAsStateWithLifecycle()
    val registration by viewModel.registration.collectAsStateWithLifecycle()
    val device by viewModel.device.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("list") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var backgroundExplanation by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { message("설정 화면을 열지 못했어요. 휴대폰 설정에서 이 앱을 찾아 주세요.") }
    }
    fun appSettings() = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    fun installUpdate() {
        scope.launch {
            val file = viewModel.installationFile() ?: return@launch
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_: ActivityNotFoundException) { message("설치 화면을 열지 못했어요. 휴대폰의 앱 설치 설정을 확인해 주세요.") }
            catch (_: SecurityException) { message("업데이트 설치가 허용되지 않았어요. 휴대폰의 앱 설치 설정을 확인해 주세요.") }
        }
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) installUpdate()
        else message("업데이트를 설치하려면 ‘이 출처 허용’을 켜 주세요.")
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refreshDevice() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshDevice() }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshDevice() }
    val requestLocation: () -> Unit = {
        if (device.fineLocation) appSettings()
        else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(incomingShopId) {
        incomingShopId?.let { id ->
            if (viewModel.findShop(id) != null) { editingId = id; screen = "edit" }
            else { screen = "list"; message("이미 삭제된 상점이에요.") }
            onIncomingHandled()
        }
    }
    BackHandler(enabled = screen != "list") { screen = "list" }

    when (screen) {
        "edit" -> key(editingId) {
            ShopEditorScreen(viewModel, editingId, device,
                onBack = { screen = "list" }, onSaved = { screen = "list"; message("저장했어요") },
                onMissing = { screen = "list"; message("이미 삭제된 상점이에요.") },
                onRequestLocation = requestLocation, onMessage = message, snackbar = snackbar)
        }
        "settings" -> SettingsScreen(enabled, device, registration,
            onBack = { screen = "list" }, onEnable = viewModel::setAlertsEnabled,
            onLocation = requestLocation,
            onBackground = {
                if (Build.VERSION.SDK_INT == 29) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                else backgroundExplanation = true
            },
            onNotifications = {
                if (Build.VERSION.SDK_INT >= 33 && !DeviceStateReader.granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            onLocationSettings = { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            onAppSettings = { appSettings() }, onRefresh = viewModel::refreshDevice, snackbar = snackbar,
            update = update, onUpdateCheck = viewModel::checkForUpdate, onUpdateDownload = viewModel::downloadUpdate,
            onUpdateInstall = {
                if (context.packageManager.canRequestPackageInstalls()) installUpdate()
                else try {
                    installPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                } catch (_: ActivityNotFoundException) { message("휴대폰 설정에서 이 앱의 ‘알 수 없는 앱 설치’를 허용해 주세요.") }
            })
        else -> ShopListScreen(shops, enabled, registration,
            onAdd = { editingId = null; screen = "edit" }, onEdit = { editingId = it; screen = "edit" },
            onSettings = { screen = "settings" }, onToggle = viewModel::setShopEnabled, snackbar = snackbar)
    }
    if (backgroundExplanation) AlertDialog(onDismissRequest = { backgroundExplanation = false },
        title = { Text("앱을 닫아도 알림 받기") },
        text = { Text("휴대폰의 앱 설정에서 ‘권한 → 위치 → 항상 허용’을 선택해 주세요. ‘정확한 위치’도 켜 주세요.") },
        confirmButton = { TextButton(onClick = { backgroundExplanation = false; appSettings() }) { Text("앱 설정 열기") } },
        dismissButton = { TextButton(onClick = { backgroundExplanation = false }) { Text("나중에") } })
}
