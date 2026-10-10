package com.selfness.mapswork.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.selfness.mapswork.core.*

@Entity(tableName = "shops")
data class ShopEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val enabled: Boolean,
    val visitState: String,
    val lastNotifiedAtMillis: Long?,
    val registrationVersion: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toShop() = Shop(
        id, name, GeoPoint(latitude, longitude), radiusMeters, enabled,
        VisitState.entries.firstOrNull { it.name == visitState } ?: VisitState.UNKNOWN,
        lastNotifiedAtMillis, registrationVersion,
    )
}
