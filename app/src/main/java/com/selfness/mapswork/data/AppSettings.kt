package com.selfness.mapswork.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("mapswork-settings", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("alerts-enabled", false))
    val alertsEnabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun setAlertsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("alerts-enabled", enabled).apply()
        mutableEnabled.value = enabled
    }
}
