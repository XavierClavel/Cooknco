package com.xavierclavel.cooknco.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Where the recipe being cooked is written down, so the notification driving it survives the
 * process that posted it.
 *
 * One row, because there is one session, and JSON for the same reason [CookTimerStore] uses
 * it: the step, the place in it and the words shown on it only mean anything together.
 *
 * It holds rather more than the timer does — every step of the recipe, written out — which
 * is the price of a notification that can be moved forward by a process with no network. A
 * long recipe is a few kilobytes of text.
 *
 * Anything that will not parse reads as no session at all: a cook who finds the notification
 * gone opens the app, which is where the recipe was all along.
 */
class CookSessionStore(private val dataStore: DataStore<Preferences>) {

    private val key = stringPreferencesKey("cook_session")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): CookSessionState? {
        val stored = dataStore.data.first()[key] ?: return null
        return runCatching { json.decodeFromString<CookSessionState>(stored) }.getOrNull()
    }

    suspend fun write(state: CookSessionState?) {
        dataStore.edit { preferences ->
            if (state == null) preferences.remove(key)
            else preferences[key] = json.encodeToString(state)
        }
    }
}
