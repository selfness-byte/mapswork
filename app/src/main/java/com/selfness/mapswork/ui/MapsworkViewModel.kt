package com.selfness.mapswork.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selfness.mapswork.MapsworkApplication
import com.selfness.mapswork.BuildConfig
import com.selfness.mapswork.core.*
import com.selfness.mapswork.location.*
import com.selfness.mapswork.update.*
import java.io.File
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MapsworkViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MapsworkApplication).container
    val shops = container.repository.shops.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val alertsEnabled = container.settings.alertsEnabled
    val registration = container.registrar.status
    private val mutableDevice = MutableStateFlow(DeviceStateReader.read(application))
    val device = mutableDevice.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = mutableMessages.asSharedFlow()
    private val updates = UpdateManager(File(application.cacheDir, "updates"), GitHubUpdateSource(),
        BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT, ApkVerifier(application)::verify)
    val update = updates.status
    fun checkForUpdate() { viewModelScope.launch { updates.check() } }
    fun downloadUpdate() { viewModelScope.launch { updates.download() } }
    suspend fun installationFile(): File? = updates.installationFile()

    private fun synchronize(force: Boolean = false) {
        container.registrar.invalidate()
        container.scope.launch { container.registrar.synchronize(force) }
    }

    fun refreshDevice() {
        mutableDevice.value = DeviceStateReader.read(getApplication())
        synchronize(force = true)
    }

    fun onResume() {
        refreshDevice()
        val state = device.value
        if (state.fineLocation && state.locationServices && state.playServices) {
            viewModelScope.launch {
                val location = CurrentLocation.get(getApplication()) ?: return@launch
                val ageMillis = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                if (location.hasAccuracy() && location.accuracy <= 50 && ageMillis in 0..30_000) {
                    container.repository.reconcilePresence(GeoPoint(location.latitude, location.longitude))
                }
            }
        }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        container.settings.setAlertsEnabled(enabled)
        synchronize()
    }

    fun setShopEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val errors = container.repository.setEnabled(id, enabled)
                if (errors.isEmpty()) {
                    if (!enabled) container.notifier.cancel(id)
                    synchronize()
                } else mutableMessages.emit(errors.first().description())
            } catch (_: Exception) { mutableMessages.emit("변경하지 못했어요. 다시 시도해 주세요.") }
        }
    }

    suspend fun findShop(id: String): Shop? = container.repository.find(id)

    suspend fun save(draft: ShopDraft, id: String?): List<ValidationError> {
        val errors = container.repository.save(draft, id)
        if (errors.isEmpty()) synchronize()
        return errors
    }

    suspend fun delete(id: String) {
        container.repository.delete(id)
        container.notifier.cancel(id)
        synchronize()
    }

    suspend fun currentPoint(): GeoPoint? {
        val location = CurrentLocation.get(getApplication()) ?: return null
        return GeoPoint(location.latitude, location.longitude).takeIf { it.isValid }
    }
}
