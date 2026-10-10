package com.selfness.mapswork.update

import android.content.Context
import java.io.IOException

class PreferencesUpdateStore(context: Context) : AutomaticUpdateStore {
    private val preferences = context.getSharedPreferences("mapswork-settings", Context.MODE_PRIVATE)

    override fun read() = AutomaticUpdateState(preferences.getLong("update-last-checked", 0),
        preferences.getLong("update-last-notified", 0))

    override fun write(state: AutomaticUpdateState) {
        if (!preferences.edit().putLong("update-last-checked", state.lastCheckedAtMillis)
                .putLong("update-last-notified", state.lastNotifiedVersion).commit()) {
            throw IOException("Could not persist update state")
        }
    }
}
