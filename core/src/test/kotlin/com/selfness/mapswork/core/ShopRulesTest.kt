package com.selfness.mapswork.core

import org.junit.Test
import kotlin.test.*

class ShopRulesTest {
    private val point = GeoPoint(37.5665, 126.9780)
    private val draft = ShopDraft("  커피 집  ", point, 200, true)
    private fun shops(count: Int) = (1..count).map { Shop("$it", "상점 $it", point, 200, true) }

    @Test fun validNameWithInternalSpacesIsAccepted() {
        assertTrue(ShopRules.validate(draft, emptyList(), null).isEmpty())
    }
    @Test fun blankNameIsRejected() {
        assertContains(ShopRules.validate(draft.copy(name = "  "), emptyList(), null), ValidationError.NAME)
    }
    @Test fun eightyCharactersAreAccepted() {
        assertTrue(ShopRules.validate(draft.copy(name = "가".repeat(80)), emptyList(), null).isEmpty())
    }
    @Test fun eightyOneCharactersAreRejected() {
        assertContains(ShopRules.validate(draft.copy(name = "가".repeat(81)), emptyList(), null), ValidationError.NAME)
    }
    @Test fun missingPinIsRejected() {
        assertContains(ShopRules.validate(draft.copy(point = null), emptyList(), null), ValidationError.LOCATION)
    }
    @Test fun invalidCoordinatesAreRejected() {
        for (invalid in listOf(GeoPoint(91.0, 0.0), GeoPoint(0.0, -181.0), GeoPoint(Double.NaN, 0.0), GeoPoint(0.0, Double.POSITIVE_INFINITY))) {
            assertContains(ShopRules.validate(draft.copy(point = invalid), emptyList(), null), ValidationError.LOCATION)
        }
    }
    @Test fun unsupportedRadiusIsRejected() {
        assertContains(ShopRules.validate(draft.copy(radiusMeters = 10), emptyList(), null), ValidationError.RADIUS)
    }
    @Test fun oneHundredthEnabledShopIsAccepted() {
        assertTrue(ShopRules.validate(draft, shops(99), null).isEmpty())
    }
    @Test fun oneHundredAndFirstEnabledShopIsRejected() {
        assertContains(ShopRules.validate(draft, shops(100), null), ValidationError.ACTIVE_LIMIT)
    }
    @Test fun existingShopCanBeEditedAtLimit() {
        assertTrue(ShopRules.validate(draft, shops(100), "1").isEmpty())
    }
    @Test fun disabledShopCanBeSavedBeyondActiveLimit() {
        assertTrue(ShopRules.validate(draft.copy(enabled = false), shops(100), null).isEmpty())
    }
    @Test fun samePointHasZeroDistance() {
        assertEquals(0.0, point.distanceMetersTo(point), 0.001)
    }
    @Test fun dateLineDistanceUsesTheShortPath() {
        val distance = GeoPoint(0.0, 179.999).distanceMetersTo(GeoPoint(0.0, -179.999))
        assertTrue(distance in 220.0..224.0, "Expected about 222m, got $distance")
    }
}
