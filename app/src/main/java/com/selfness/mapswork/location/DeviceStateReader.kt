package com.selfness.mapswork.location

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.selfness.mapswork.core.DeviceState
import com.selfness.mapswork.notifications.ShopNotifier

object DeviceStateReader {
    fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context): DeviceState {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ShopNotifier.CHANNEL_ID)
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)) &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
        return DeviceState(
            sdk = Build.VERSION.SDK_INT,
            fineLocation = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
            backgroundLocation = Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            notifications = notifications,
            locationServices = LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java)),
            playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS,
        )
    }
}
