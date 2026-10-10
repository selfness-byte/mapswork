package com.selfness.mapswork.ui

import android.content.Intent
import android.net.Uri
import com.selfness.mapswork.core.GeoPoint
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleMapsLinksTest {
    @Test fun searchNameIsEncodedWithoutIntroducingExtraQueryParameters() {
        val name = "이곳 카페 & 빵#1"
        val (app, web) = GoogleMapsLinks.search(name, GeoPoint(37.5, 127.0))
        assertEquals(Intent.ACTION_VIEW, app.action)
        assertEquals("com.google.android.apps.maps", app.`package`)
        assertEquals(name, Uri.decode(app.dataString!!.substringAfter("?q=")))
        assertEquals(name, web.data!!.getQueryParameter("query"))
        assertEquals(setOf("api", "query"), web.data!!.queryParameterNames)
        assertEquals("www.google.com", web.data!!.host)
    }
    @Test fun currentPositionHasExactCoordinatesInBothAppAndBrowser() {
        val (app, web) = GoogleMapsLinks.location(GeoPoint(37.123, 127.456))
        assertEquals("geo:37.123,127.456?q=37.123,127.456", app.dataString)
        assertEquals("37.123,127.456", web.data!!.getQueryParameter("query"))
    }
    @Test fun emptyQueryDoesNotOpenAnUnrelatedSearch() { assertFailsWith<IllegalArgumentException> { GoogleMapsLinks.search("  ", null) } }
}
