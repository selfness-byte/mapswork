package com.selfness.mapswork

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.selfness.mapswork.data.*
import com.selfness.mapswork.core.Readiness
import com.selfness.mapswork.location.*
import com.selfness.mapswork.notifications.ShopNotifier
import com.selfness.mapswork.update.*
import android.os.Build
import androidx.work.Configuration
import kotlinx.coroutines.*

class MapsworkApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build()
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (container.updateStore.read().lastNotifiedVersion <= BuildConfig.VERSION_CODE) container.updateNotifier.cancel()
        AutomaticUpdateWorker.schedule(this, container.settings.automaticUpdates.value)
    }
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = AppSettings(context)
    val database = Room.databaseBuilder(context, MapsworkDatabase::class.java, "mapswork.db").build()
    val repository = ShopRepository(database, settings)
    val notifier = ShopNotifier(context)
    val updateSource: UpdateSource = HttpUpdateSource()
    val updateStore = PreferencesUpdateStore(context)
    val updateNotifier = UpdateNotifier(context)
    val automaticUpdates = AutomaticUpdateChecker(updateSource, updateStore,
        BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT, { settings.automaticUpdates.value },
        updateNotifier::notify)
    val registrar = GeofenceRegistrar(repository, settings, PlayServicesGeofenceTransport(context)) {
        DeviceStateReader.read(context)
    }
    val events = GeofenceEventQueue(scope, process = { ids, transition, receivedAtMillis ->
        if (Readiness.evaluate(DeviceStateReader.read(context)) == Readiness.READY) {
            repository.consumeTransitions(ids, transition, receivedAtMillis) { shop ->
                settings.alertsEnabled.value &&
                    Readiness.evaluate(DeviceStateReader.read(context)) == Readiness.READY && notifier.notify(shop)
            }
        }
    }, onFailure = {
        registrar.reportEventError()
        RestoreWorker.enqueue(context)
    })
}
