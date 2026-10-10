package com.selfness.mapswork.core

data class SelectedPlace(val name: String, val point: GeoPoint) {
    companion object {
        fun from(name: String?, point: GeoPoint?): SelectedPlace? {
            val trimmed = name?.trim().orEmpty()
            return if (trimmed.isNotEmpty() && point?.isValid == true) SelectedPlace(trimmed, point) else null
        }
    }
}
