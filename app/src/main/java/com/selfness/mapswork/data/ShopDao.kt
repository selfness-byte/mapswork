package com.selfness.mapswork.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops ORDER BY createdAtMillis DESC, id")
    fun observeAll(): Flow<List<ShopEntity>>

    @Query("SELECT * FROM shops ORDER BY createdAtMillis DESC, id")
    suspend fun all(): List<ShopEntity>

    @Query("SELECT * FROM shops WHERE id = :id")
    suspend fun find(id: String): ShopEntity?

    @Upsert
    suspend fun upsert(shop: ShopEntity)

    @Query("DELETE FROM shops WHERE id = :id")
    suspend fun delete(id: String)
}
