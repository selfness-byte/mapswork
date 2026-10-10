package com.selfness.mapswork.ui

import android.content.Context
import android.content.Intent
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.selfness.mapswork.R
import com.selfness.mapswork.core.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object GooglePlaceSearch {
    private fun initialize(context: Context) {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext,
            context.getString(R.string.google_maps_api_key))
    }

    fun intent(context: Context, query: String, bias: GeoPoint?): Intent {
        initialize(context)
        return PlaceAutocomplete.IntentBuilder().setInitialQuery(query.trim()).setTypesFilter(listOf("establishment"))
            .apply { bias?.takeIf { it.isValid }?.let {
                setLocationBias(CircularBounds.newInstance(LatLng(it.latitude, it.longitude), 50_000.0))
            } }.build(context)
    }

    suspend fun selection(context: Context, result: Intent): SelectedPlace? {
        val prediction = PlaceAutocomplete.getPredictionFromIntent(result) ?: return null
        initialize(context)
        val cancellation = CancellationTokenSource()
        val request = FetchPlaceRequest.builder(prediction.placeId, listOf(Place.Field.DISPLAY_NAME, Place.Field.LOCATION))
            .setCancellationToken(cancellation.token).apply {
                PlaceAutocomplete.getSessionTokenFromIntent(result)?.let { setSessionToken(it) }
            }.build()
        try {
            return withTimeoutOrNull(15_000) {
                val place = Places.createClient(context.applicationContext).fetchPlace(request).await().place
                SelectedPlace.from(place.displayName, place.location?.let { GeoPoint(it.latitude, it.longitude) })
            }
        } finally { cancellation.cancel() }
    }
}
