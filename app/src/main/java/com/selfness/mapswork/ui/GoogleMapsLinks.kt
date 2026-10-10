package com.selfness.mapswork.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.selfness.mapswork.core.GeoPoint

object GoogleMapsLinks {
    fun search(name: String, bias: GeoPoint?): Pair<Intent, Intent> {
        require(name.trim().isNotEmpty())
        val center = bias?.takeIf { it.isValid } ?: GeoPoint(0.0, 0.0)
        return intents("${center.latitude},${center.longitude}", name.trim())
    }

    fun location(point: GeoPoint): Pair<Intent, Intent> {
        require(point.isValid)
        val coordinates = "${point.latitude},${point.longitude}"
        return intents(coordinates, coordinates)
    }

    private fun intents(center: String, query: String): Pair<Intent, Intent> {
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$center?q=${Uri.encode(query, ",")}"))
            .setPackage("com.google.android.apps.maps")
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/").buildUpon()
            .appendQueryParameter("api", "1").appendQueryParameter("query", query).build())
        return app to web
    }

    fun open(context: Context, intents: Pair<Intent, Intent>): Boolean {
        try { context.startActivity(intents.first); return true }
        catch (_: ActivityNotFoundException) { /* Try the Google Maps website. */ }
        try { context.startActivity(intents.second); return true }
        catch (_: ActivityNotFoundException) { return false }
    }
}
