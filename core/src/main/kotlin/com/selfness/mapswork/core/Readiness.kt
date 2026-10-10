package com.selfness.mapswork.core

data class DeviceState(
    val sdk: Int,
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    val locationServices: Boolean,
    val playServices: Boolean,
)

enum class Readiness {
    READY, UNSUPPORTED, PRECISE_LOCATION, BACKGROUND_LOCATION,
    NOTIFICATIONS, LOCATION_SERVICES, PLAY_SERVICES;

    companion object {
        fun evaluate(state: DeviceState): Readiness = when {
            state.sdk < 26 -> UNSUPPORTED
            !state.playServices -> PLAY_SERVICES
            !state.locationServices -> LOCATION_SERVICES
            !state.fineLocation -> PRECISE_LOCATION
            state.sdk >= 29 && !state.backgroundLocation -> BACKGROUND_LOCATION
            !state.notifications -> NOTIFICATIONS
            else -> READY
        }
    }
}
