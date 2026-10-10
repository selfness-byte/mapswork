package com.selfness.mapswork.core

import org.junit.Test
import kotlin.test.*

class VisitRulesTest {
    private val shop = Shop("cafe", "카페", GeoPoint(37.5, 127.0), 200, true)
    private val now = 2_000_000L

    @Test fun firstEntryNotifiesAndRecordsVisit() {
        val result = VisitRules.transition(shop, Transition.ENTER, now)
        assertTrue(result.shouldNotify)
        assertEquals(VisitState.INSIDE, result.shop.visitState)
        assertEquals(now, result.shop.lastNotifiedAtMillis)
    }

    @Test fun repeatedEntryDoesNotNotifyAgain() {
        val first = VisitRules.transition(shop, Transition.ENTER, now)
        val repeated = VisitRules.transition(first.shop, Transition.ENTER, now + 4_000_000)
        assertFalse(repeated.shouldNotify)
        assertEquals(now, repeated.shop.lastNotifiedAtMillis)
    }

    @Test fun exitAllowsANewVisitAfterCooldown() {
        val first = VisitRules.transition(shop, Transition.ENTER, now)
        val exit = VisitRules.transition(first.shop, Transition.EXIT, now + 100)
        assertFalse(exit.shouldNotify)
        assertEquals(VisitState.OUTSIDE, exit.shop.visitState)
        assertTrue(VisitRules.transition(exit.shop, Transition.ENTER, now + 1_800_000).shouldNotify)
    }

    @Test fun entryJustBeforeCooldownDoesNotNotify() {
        val previous = shop.copy(visitState = VisitState.OUTSIDE, lastNotifiedAtMillis = now)
        val result = VisitRules.transition(previous, Transition.ENTER, now + 1_799_999)
        assertFalse(result.shouldNotify)
        assertEquals(VisitState.INSIDE, result.shop.visitState)
        assertEquals(now, result.shop.lastNotifiedAtMillis)
    }

    @Test fun suppressedVisitDoesNotNotifyWhenCooldownExpiresWhileInside() {
        val previous = shop.copy(visitState = VisitState.OUTSIDE, lastNotifiedAtMillis = now)
        val suppressed = VisitRules.transition(previous, Transition.ENTER, now + 100)
        assertFalse(VisitRules.transition(suppressed.shop, Transition.ENTER, now + 1_800_000).shouldNotify)
    }

    @Test fun disabledShopDoesNotNotify() {
        assertFalse(VisitRules.transition(shop.copy(enabled = false), Transition.ENTER, now).shouldNotify)
    }

    @Test fun clockMovingBackwardsDoesNotCauseAnotherAlert() {
        val previous = shop.copy(lastNotifiedAtMillis = now, visitState = VisitState.OUTSIDE)
        assertFalse(VisitRules.transition(previous, Transition.ENTER, now - 10_000).shouldNotify)
    }
}
