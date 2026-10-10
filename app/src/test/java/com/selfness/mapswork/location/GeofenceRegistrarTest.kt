package com.selfness.mapswork.location

import android.app.Application
import androidx.room.Room
import com.selfness.mapswork.core.*
import com.selfness.mapswork.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GeofenceRegistrarTest {
    private lateinit var db: MapsworkDatabase
    private lateinit var settings: AppSettings
    private lateinit var repository: ShopRepository
    private lateinit var transport: RecordingTransport
    private lateinit var registrar: GeofenceRegistrar
    private var device = DeviceState(35, true, true, true, true, true)

    private class RecordingTransport : GeofenceTransport {
        var clears = 0
        val batches = mutableListOf<List<Shop>>()
        var fail = false
        var delayMillis = 0L
        var duringRegister: (suspend () -> Unit)? = null
        override suspend fun clear() { clears++ }
        override suspend fun register(shops: List<Shop>) {
            if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis)
            if (fail) error("transport unavailable")
            batches.add(shops)
            duringRegister?.invoke()
        }
    }

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mapswork-settings", 0).edit().clear().commit()
        settings = AppSettings(context).also { it.setAlertsEnabled(true) }
        db = Room.inMemoryDatabaseBuilder(context, MapsworkDatabase::class.java).build()
        repository = ShopRepository(db, settings)
        transport = RecordingTransport()
        registrar = GeofenceRegistrar(repository, settings, transport) { device }
    }
    @After fun close() { db.close() }

    @Test fun readyStateIncludesOnlyEnabledShopsAfterTransportSucceeds() = runBlocking {
        repository.save(ShopDraft("켜짐", GeoPoint(37.5, 127.0)), null)
        repository.save(ShopDraft("꺼짐", GeoPoint(37.6, 127.0), enabled = false), null)
        registrar.synchronize()
        assertEquals(listOf("켜짐"), transport.batches.single().map { it.name })
        assertEquals(RegistrationPhase.READY, registrar.status.value.phase)
        assertEquals(1, registrar.status.value.registeredCount)
    }
    @Test fun globalPauseClearsRegistrationsAndDoesNotAddAny() = runBlocking {
        repository.save(ShopDraft("카페", GeoPoint(37.5, 127.0)), null)
        settings.setAlertsEnabled(false)
        registrar.synchronize()
        assertEquals(1, transport.clears)
        assertTrue(transport.batches.isEmpty())
        assertEquals(RegistrationPhase.PAUSED, registrar.status.value.phase)
    }
    @Test fun missingBackgroundPermissionDoesNotRegister() = runBlocking {
        repository.save(ShopDraft("카페", GeoPoint(37.5, 127.0)), null)
        device = device.copy(backgroundLocation = false)
        registrar.synchronize()
        assertTrue(transport.batches.isEmpty())
        assertEquals(Readiness.BACKGROUND_LOCATION, registrar.status.value.reason)
        assertEquals(0, registrar.status.value.registeredCount)
    }
    @Test fun transportFailureDoesNotReportReady() = runBlocking {
        repository.save(ShopDraft("카페", GeoPoint(37.5, 127.0)), null)
        transport.fail = true
        registrar.synchronize()
        assertEquals(RegistrationPhase.ERROR, registrar.status.value.phase)
        assertEquals(0, registrar.status.value.registeredCount)
    }
    @Test fun emptyListClearsAndReportsEmpty() = runBlocking {
        registrar.synchronize()
        assertEquals(1, transport.clears)
        assertEquals(RegistrationPhase.EMPTY, registrar.status.value.phase)
    }
    @Test fun subsequentSyncReadsLatestDeletedShopState() = runBlocking {
        repository.save(ShopDraft("카페", GeoPoint(37.5, 127.0)), null)
        registrar.synchronize()
        repository.delete(repository.all().single().id)
        registrar.synchronize()
        assertEquals(2, transport.clears)
        assertEquals(1, transport.batches.size)
        assertEquals(RegistrationPhase.EMPTY, registrar.status.value.phase)
    }

    @Test fun nonrespondingTransportReportsErrorInsteadOfCheckingForever() = runBlocking {
        repository.save(ShopDraft("카페", GeoPoint(37.5, 127.0)), null)
        transport.delayMillis = 200
        val bounded = GeofenceRegistrar(repository, settings, transport, requestTimeoutMillis = 20) { device }
        bounded.synchronize()
        assertEquals(RegistrationPhase.ERROR, bounded.status.value.phase)
        assertEquals(0, bounded.status.value.registeredCount)
    }

    @Test fun changedSettingsInvalidatePreviousReadyCount() = runBlocking {
        repository.save(ShopDraft("카페", GeoPoint(37.5, 127.0)), null)
        registrar.synchronize()
        registrar.invalidate()
        assertEquals(RegistrationPhase.CHECKING, registrar.status.value.phase)
        assertEquals(0, registrar.status.value.registeredCount)
    }

    @Test fun renamingShopKeepsItsExistingGeofenceRegistration() = runBlocking {
        val draft = ShopDraft("카페", GeoPoint(37.5, 127.0))
        repository.save(draft, null)
        registrar.synchronize()
        repository.save(draft.copy(name = "새 이름"), repository.all().single().id)
        registrar.synchronize()
        assertEquals(1, transport.batches.size)
        assertEquals(RegistrationPhase.READY, registrar.status.value.phase)
    }

    @Test fun pausedDuringRegistrationIsNotReportedReadyAfterwards() = runBlocking {
        repository.save(ShopDraft("카페", GeoPoint(37.5, 127.0)), null)
        transport.duringRegister = { settings.setAlertsEnabled(false) }
        registrar.synchronize()
        assertEquals(RegistrationPhase.PAUSED, registrar.status.value.phase)
        assertEquals(0, registrar.status.value.registeredCount)
    }
}
