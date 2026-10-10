package com.selfness.mapswork

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.selfness.mapswork.data.*
import kotlinx.coroutines.*

class MapsworkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = AppSettings(context)
    val database = Room.databaseBuilder(context, MapsworkDatabase::class.java, "mapswork.db").build()
    val repository = ShopRepository(database, settings)
}
