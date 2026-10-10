package com.selfness.mapswork.core

import org.junit.Test
import kotlin.test.*

class ReadinessTest {
    private val ready = DeviceState(36, true, true, true, true, true)

    @Test fun supportedDeviceWithPermissionsIsReady() {
        assertEquals(Readiness.READY, Readiness.evaluate(ready))
    }
    @Test fun missingPreciseLocationBlocksReadiness() {
        assertEquals(Readiness.PRECISE_LOCATION, Readiness.evaluate(ready.copy(fineLocation = false)))
    }
    @Test fun androidEightDoesNotRequireBackgroundPermission() {
        assertEquals(Readiness.READY, Readiness.evaluate(ready.copy(sdk = 26, backgroundLocation = false)))
    }
    @Test fun androidTenRequiresBackgroundPermission() {
        assertEquals(Readiness.BACKGROUND_LOCATION, Readiness.evaluate(ready.copy(sdk = 29, backgroundLocation = false)))
    }
    @Test fun androidThirteenWithNotificationsDeniedIsNotReady() {
        assertEquals(Readiness.NOTIFICATIONS, Readiness.evaluate(ready.copy(sdk = 33, notifications = false)))
    }
    @Test fun disabledNotificationChannelIsNotReadyEvenOnOldAndroid() {
        assertEquals(Readiness.NOTIFICATIONS, Readiness.evaluate(ready.copy(sdk = 26, notifications = false)))
    }
    @Test fun locationServicesDisabledAreNotReady() {
        assertEquals(Readiness.LOCATION_SERVICES, Readiness.evaluate(ready.copy(locationServices = false)))
    }
    @Test fun missingGooglePlayServicesAreNotReady() {
        assertEquals(Readiness.PLAY_SERVICES, Readiness.evaluate(ready.copy(playServices = false)))
    }
}
