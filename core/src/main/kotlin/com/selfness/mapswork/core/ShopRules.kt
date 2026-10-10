package com.selfness.mapswork.core

enum class ValidationError { NAME, LOCATION, RADIUS, ACTIVE_LIMIT }

object ShopRules {
    const val MAX_ENABLED = 100
    val RADII = listOf(100, 200, 500, 1000)

    fun validate(draft: ShopDraft, existing: List<Shop>, editingId: String?): List<ValidationError> = buildList {
        if (draft.name.trim().length !in 1..80) add(ValidationError.NAME)
        if (draft.point?.isValid != true) add(ValidationError.LOCATION)
        if (draft.radiusMeters !in RADII) add(ValidationError.RADIUS)
        if (draft.enabled && existing.count { it.enabled && it.id != editingId } >= MAX_ENABLED) {
            add(ValidationError.ACTIVE_LIMIT)
        }
    }
}
