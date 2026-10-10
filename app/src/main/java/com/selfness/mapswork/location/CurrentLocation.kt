package com.selfness.mapswork.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object CurrentLocation {
    @SuppressLint("MissingPermission")
    suspend fun get(context: Context): Location? {
        if (!DeviceStateReader.granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) return null
        val token = CancellationTokenSource()
        return try {
            withTimeoutOrNull(15_000) {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            token.cancel()
        }
    }
}
