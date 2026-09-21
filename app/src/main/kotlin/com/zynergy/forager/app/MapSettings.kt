package com.zynergy.forager.app

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.offline.OfflineStyleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Map settings that survive a restart. A flat preference, so DataStore rather than Room.
 *
 * Created per instance with the factory rather than the preferencesDataStore delegate, which caches
 * one instance per process and makes separate tests share state.
 */
class MapSettings(context: Context) {

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile("map_settings")
    }

    /** The saved mode, or the default when nothing was saved or the saved value is not one this build knows. */
    val offlineStyleMode: Flow<OfflineStyleMode> = store.data.map { prefs ->
        val raw = prefs[MODE] ?: return@map DEFAULT_MODE
        runCatching { OfflineStyleMode.valueOf(raw) }.getOrElse {
            Log.w("ForagerSettings", "unknown offline style mode '$raw'; using $DEFAULT_MODE")
            DEFAULT_MODE
        }
    }

    suspend fun setOfflineStyleMode(mode: OfflineStyleMode): Outcome<Unit> = try {
        store.edit { it[MODE] = mode.name }
        Outcome.Ok(Unit)
    } catch (e: Exception) {
        Outcome.Failed("could not save the map setting", e)
    }

    companion object {
        private val MODE = stringPreferencesKey("map.offline_style_mode")
        val DEFAULT_MODE = OfflineStyleMode.WHEN_OFFLINE
    }
}
