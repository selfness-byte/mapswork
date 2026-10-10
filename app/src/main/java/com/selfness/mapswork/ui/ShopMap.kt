package com.selfness.mapswork.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.selfness.mapswork.BuildConfig
import com.selfness.mapswork.core.GeoPoint
import kotlinx.coroutines.delay

private val DEFAULT_CENTER = GeoPoint(37.5665, 126.9780)

@Composable
fun ShopMap(
    point: GeoPoint?, onPointSelected: (GeoPoint) -> Unit, currentPoint: GeoPoint?,
    cameraTarget: GeoPoint?, cameraRevision: Int, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val servicesAvailable = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    if (!BuildConfig.HAS_GOOGLE_MAPS_KEY || !servicesAvailable) {
        OutlinedCard(modifier) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Text("Google 지도 연결 대기", style = MaterialTheme.typography.titleMedium)
                Text("지금은 Google 지도 앱에서 위치를 볼 수 있어요. 지도 연결이 포함된 업데이트 후에는 이 화면에서 위치를 고를 수 있어요.",
                    style = MaterialTheme.typography.bodySmall)
                if (currentPoint != null) Text("내 위치를 확인했어요", color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { GoogleMapsLinks.open(context, GoogleMapsLinks.location(cameraTarget ?: point ?: currentPoint ?: DEFAULT_CENTER)) }) {
                    Text("Google 지도에서 보기")
                }
                Spacer(Modifier.weight(1f))
            }
        }
        return
    }
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    key(attempt) {
        GoogleShopMap(point, onPointSelected, currentPoint, cameraTarget, cameraRevision, modifier) { attempt++ }
    }
}

@Composable
private fun GoogleShopMap(
    point: GeoPoint?, onPointSelected: (GeoPoint) -> Unit, currentPoint: GeoPoint?,
    cameraTarget: GeoPoint?, cameraRevision: Int, modifier: Modifier, onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestSelect by rememberUpdatedState(onPointSelected)
    val initialCenter = remember { cameraTarget ?: point ?: currentPoint ?: DEFAULT_CENTER }
    val mapView = remember {
        MapsInitializer.initialize(context.applicationContext, MapsInitializer.Renderer.LATEST, null)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var shopMarker by remember { mutableStateOf<Marker?>(null) }
    var locationMarker by remember { mutableStateOf<Marker?>(null) }

    DisposableEffect(mapView, owner) {
        var alive = true
        var started = false
        var resumed = false
        fun start() { if (!started) { mapView.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { mapView.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        else if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        mapView.getMapAsync { ready ->
            if (alive) {
                ready.uiSettings.isZoomControlsEnabled = true
                ready.uiSettings.isMyLocationButtonEnabled = false
                ready.setOnMapClickListener { if (alive) latestSelect(GeoPoint(it.latitude, it.longitude)) }
                ready.setOnPoiClickListener { if (alive) latestSelect(GeoPoint(it.latLng.latitude, it.latLng.longitude)) }
                ready.setOnMapLoadedCallback { if (alive) { loaded = true; failed = false } }
                ready.moveCamera(CameraUpdateFactory.newLatLngZoom(initialCenter.latLng(), if (point != null || currentPoint != null) 16f else 11f))
                map = ready
            }
        }
        onDispose {
            alive = false
            owner.lifecycle.removeObserver(observer)
            map?.apply { setOnMapClickListener(null); setOnPoiClickListener(null); setOnMapLoadedCallback(null) }
            stop(); mapView.onDestroy()
        }
    }
    LaunchedEffect(loaded) {
        if (!loaded) { delay(20_000); if (!loaded) failed = true }
    }
    LaunchedEffect(map, point, currentPoint) {
        map?.let { ready ->
            shopMarker?.remove(); locationMarker?.remove()
            locationMarker = currentPoint?.let {
                ready.addMarker(MarkerOptions().position(it.latLng()).title("내 위치")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)).zIndex(1f))
            }
            shopMarker = point?.let {
                ready.addMarker(MarkerOptions().position(it.latLng()).title("상점 위치").zIndex(2f))
            }
        }
    }
    LaunchedEffect(map, cameraTarget, cameraRevision) {
        cameraTarget?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it.latLng(), 16f)) }
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = MaterialTheme.shapes.small) {
            Text("파랑: 내 위치 · 빨강: 상점", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
        }
        if (!loaded && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (failed) Surface(Modifier.align(Alignment.Center).padding(16.dp), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("지도를 표시하지 못했어요. 인터넷 연결과 지도 설정을 확인해 주세요.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetry) { Text("지도 다시 열기") }
            }
        }
    }
}

private fun GeoPoint.latLng() = LatLng(latitude, longitude)
