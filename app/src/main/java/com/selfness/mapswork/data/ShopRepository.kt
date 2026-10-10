package com.selfness.mapswork.data

import androidx.room.withTransaction
import com.selfness.mapswork.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ShopRepository(private val database: MapsworkDatabase, private val settings: AppSettings) {
    private val dao = database.shops()
    val shops: Flow<List<Shop>> = dao.observeAll().map { entries -> entries.map { it.toShop() } }

    suspend fun all(): List<Shop> = dao.all().map { it.toShop() }
    suspend fun find(id: String): Shop? = dao.find(id)?.toShop()

    suspend fun save(draft: ShopDraft, id: String?): List<ValidationError> = database.withTransaction {
        saveInsideTransaction(draft, id)
    }

    private suspend fun saveInsideTransaction(draft: ShopDraft, id: String?): List<ValidationError> {
        val old = id?.let { dao.find(it) }
        if (id != null && old == null) return listOf(ValidationError.NOT_FOUND)
        val errors = ShopRules.validate(draft, all(), id)
        if (errors.isNotEmpty()) return errors
        val point = requireNotNull(draft.point)
        val now = System.currentTimeMillis()
        val registrationChanged = old != null && (old.latitude != point.latitude ||
            old.longitude != point.longitude || old.radiusMeters != draft.radiusMeters || old.enabled != draft.enabled)
        dao.upsert(ShopEntity(
            id = old?.id ?: UUID.randomUUID().toString(), name = draft.name.trim(),
            latitude = point.latitude, longitude = point.longitude,
            radiusMeters = draft.radiusMeters, enabled = draft.enabled,
            visitState = if (registrationChanged) VisitState.UNKNOWN.name else old?.visitState ?: VisitState.UNKNOWN.name,
            lastNotifiedAtMillis = old?.lastNotifiedAtMillis,
            registrationVersion = (old?.registrationVersion ?: 1) + if (registrationChanged) 1 else 0,
            createdAtMillis = old?.createdAtMillis ?: now, updatedAtMillis = now,
        ))
        return emptyList()
    }

    suspend fun setEnabled(id: String, enabled: Boolean): List<ValidationError> = database.withTransaction {
        val old = dao.find(id) ?: return@withTransaction listOf(ValidationError.NOT_FOUND)
        saveInsideTransaction(ShopDraft(old.name, GeoPoint(old.latitude, old.longitude), old.radiusMeters, enabled), id)
    }

    suspend fun delete(id: String) { dao.delete(id) }

    suspend fun consumeTransitions(ids: List<String>, transition: Transition, nowMillis: Long,
        deliver: (Shop) -> Boolean): List<Shop> =
        database.withTransaction {
            if (!settings.alertsEnabled.value) return@withTransaction emptyList()
            buildList {
                for (requestId in ids.distinct()) {
                    val separator = requestId.lastIndexOf(':')
                    if (separator <= 0) continue
                    val version = requestId.substring(separator + 1).toLongOrNull() ?: continue
                    val entity = dao.find(requestId.substring(0, separator)) ?: continue
                    if (!entity.enabled || version != entity.registrationVersion) continue
                    val result = VisitRules.transition(entity.toShop(), transition, nowMillis)
                    // Keep delivery and state under the same DB transaction. A rejected post must
                    // leave the visit and previous successful notification time available to retry.
                    if (result.shouldNotify && !deliver(result.shop)) continue
                    dao.upsert(entity.copy(visitState = result.shop.visitState.name,
                        lastNotifiedAtMillis = result.shop.lastNotifiedAtMillis))
                    if (result.shouldNotify) add(result.shop)
                }
            }
        }

    suspend fun reconcilePresence(point: GeoPoint) = database.withTransaction {
        if (!point.isValid) return@withTransaction
        for (entity in dao.all()) {
            val shop = entity.toShop()
            if (shop.enabled && point.distanceMetersTo(shop.point) > shop.radiusMeters) {
                dao.upsert(entity.copy(visitState = VisitState.OUTSIDE.name))
            }
        }
    }
}
