package com.selfness.mapswork.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ShopEntity::class], version = 1, exportSchema = true)
abstract class MapsworkDatabase : RoomDatabase() {
    abstract fun shops(): ShopDao
}
