package com.xavierclavel.cooknco.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

class TokenDataStore(private val dataStore: DataStore<Preferences>) {

    private val tokenKey = stringPreferencesKey("auth_token")

    val tokenFlow: Flow<String?> = dataStore.data.map { it[tokenKey] }

    suspend fun saveToken(token: String) {
        dataStore.edit { it[tokenKey] = token }
    }

    suspend fun clearToken() {
        dataStore.edit { it.remove(tokenKey) }
    }
}

/** File name shared by both platforms; on Android it matches what `preferencesDataStore("auth")` used. */
internal const val AUTH_PREFERENCES_FILE = "auth.preferences_pb"

internal fun createPreferencesDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
