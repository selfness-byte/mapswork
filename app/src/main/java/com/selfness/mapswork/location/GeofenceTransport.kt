package com.selfness.mapswork.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.*
import com.selfness.mapswork.core.Shop
import kotlinx.coroutines.tasks.await

interface GeofenceTransport {
    suspend fun clear()
    suspend fun register(shops: List<Shop>)
}

class PlayServicesGeofenceTransport(context: Context) : GeofenceTransport {
    private val client = LocationServices.getGeofencingClient(context)
    private val pendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0,
    )

    override suspend fun clear() { client.removeGeofences(pendingIntent).await() }

    @SuppressLint("MissingPermission") // Registrar checks precise and background location before registration.
    override suspend fun register(shops: List<Shop>) {
        val geofences = shops.map { shop ->
            Geofence.Builder().setRequestId(shop.geofenceRequestId)
                .setCircularRegion(shop.point.latitude, shop.point.longitude, shop.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setNotificationResponsiveness(120_000)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences).build()
        client.addGeofences(request, pendingIntent).await()
    }
}
