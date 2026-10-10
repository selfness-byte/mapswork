package com.selfness.mapswork.core

import org.junit.Test
import kotlin.test.*

class SelectedPlaceTest {
    @Test fun selectionIncludesNameAndPointTogether() {
        assertEquals(SelectedPlace("이곳 카페", GeoPoint(37.5, 127.0)), SelectedPlace.from(" 이곳 카페 ", GeoPoint(37.5, 127.0)))
    }
    @Test fun resultWithoutCoordinatesDoesNotReplaceDraft() { assertNull(SelectedPlace.from("카페", null)) }
    @Test fun invalidCoordinatesAreRejected() { assertNull(SelectedPlace.from("카페", GeoPoint(Double.NaN, 127.0))) }
    @Test fun resultWithoutNameIsRejected() { assertNull(SelectedPlace.from("  ", GeoPoint(37.5, 127.0))) }
}
