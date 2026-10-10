package com.selfness.mapswork.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.selfness.mapswork.MapsworkApplication
import com.selfness.mapswork.core.*

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val container = (context.applicationContext as MapsworkApplication).container
        if (event.hasError()) {
            container.registrar.reportEventError()
            RestoreWorker.enqueue(context)
            return
        }
        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> Transition.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> Transition.EXIT
            else -> return
        }
        val ids = event.triggeringGeofences?.map { it.requestId } ?: return
        val pending = goAsync()
        container.events.submit(ids, transition, System.currentTimeMillis()) { pending.finish() }
    }
}
