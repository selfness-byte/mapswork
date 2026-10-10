package com.selfness.mapswork.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.selfness.mapswork.core.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.concurrent.atomic.AtomicBoolean

private const val OSM_STYLE = """{
  "version": 8,
  "sources": {"osm": {"type": "raster", "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
    "tileSize": 256, "maxzoom": 19, "attribution": "© OpenStreetMap contributors"}},
  "layers": [{"id": "background", "type": "background", "paint": {"background-color": "#EAF1F5"}},
    {"id": "osm", "type": "raster", "source": "osm"}]
}"""

@Composable
fun ShopMap(point: GeoPoint?, onPointSelected: (GeoPoint) -> Unit, cameraTarget: GeoPoint?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val latestPoint by rememberUpdatedState(point)
    val latestSelect by rememberUpdatedState(onPointSelected)
    val lifetime = remember { AtomicBoolean(true) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapTileClient.configure(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    var loadingFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val failures by MapTileClient.failures.collectAsState()
    var failureBaseline by remember { mutableLongStateOf(failures) }

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
        val failureListener = MapView.OnDidFailLoadingMapListener { if (alive) loadingFailed = true }
        mapView.addOnDidFailLoadingMapListener(failureListener)
        owner.lifecycle.addObserver(observer)
        mapView.getMapAsync { ready ->
            if (alive) {
                ready.uiSettings.isLogoEnabled = false
                ready.uiSettings.isAttributionEnabled = false // Always-visible attribution below the map.
                ready.setPrefetchZoomDelta(0)
                val center = latestPoint ?: GeoPoint(37.5665, 126.9780)
                ready.cameraPosition = CameraPosition.Builder().target(center.latLng()).zoom(if (latestPoint == null) 13.0 else 16.0).build()
                ready.addOnMapClickListener { location ->
                    if (alive) latestSelect(GeoPoint(location.latitude, location.longitude))
                    true
                }
                map = ready
            }
        }
        onDispose {
            alive = false
            lifetime.set(false)
            owner.lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(failureListener)
            stop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(map, retry) {
        val ready = map ?: return@LaunchedEffect
        styleLoaded = false
        ready.setStyle(Style.Builder().fromJson(OSM_STYLE)) { style ->
            if (!lifetime.get()) return@setStyle
            style.addSource(GeoJsonSource("selected-location", FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(CircleLayer("selected-location-marker", "selected-location").withProperties(
                circleRadius(9f), circleColor("#155E75"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
            ))
            latestPoint?.let { updatePoint(style, it) }
            styleLoaded = true
        }
    }
    LaunchedEffect(point, styleLoaded) {
        if (styleLoaded) map?.style?.let { updatePoint(it, point) }
    }
    LaunchedEffect(cameraTarget, map) {
        cameraTarget?.let { map?.cameraPosition = CameraPosition.Builder().target(it.latLng()).zoom(16.0).build() }
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            if (loadingFailed || failures > failureBaseline) {
                Card(Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("지도를 불러오지 못했어요. 인터넷 연결을 확인해 주세요.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { loadingFailed = false; failureBaseline = failures; retry++ }) { Text("재시도") }
                    }
                }
            }
        }
        Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }.padding(8.dp))
    }
}

private fun GeoPoint.latLng() = LatLng(latitude.coerceIn(-85.0511, 85.0511), longitude)

private fun updatePoint(style: Style, point: GeoPoint?) {
    val features = point?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))) } ?: emptyList()
    style.getSourceAs<GeoJsonSource>("selected-location")?.setGeoJson(FeatureCollection.fromFeatures(features))
}
