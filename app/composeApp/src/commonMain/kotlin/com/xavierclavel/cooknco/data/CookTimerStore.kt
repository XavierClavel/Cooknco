package com.xavierclavel.cooknco.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Where a running timer is written down, so it survives the process it was started in.
 *
 * One row, because there is one timer. It is stored as JSON rather than as a handful of
 * preference keys so that a half-written timer is impossible: the deadline, the step and
 * the paused remainder only mean anything together.
 *
 * Anything that will not parse — a build that changed the shape, a truncated write — reads
 * as no timer at all. A cook whose timer is silently gone goes and looks at the pan; a cook
 * whose app crashes on launch has nothing to look at.
 */
class CookTimerStore(private val dataStore: DataStore<Preferences>) {

    private val key = stringPreferencesKey("cook_timer")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): CookTimerState? {
        val stored = dataStore.data.first()[key] ?: return null
        return runCatching { json.decodeFromString<CookTimerState>(stored) }.getOrNull()
    }

    suspend fun write(state: CookTimerState?) {
        dataStore.edit { preferences ->
            if (state == null) preferences.remove(key)
            else preferences[key] = json.encodeToString(state)
        }
    }
}
