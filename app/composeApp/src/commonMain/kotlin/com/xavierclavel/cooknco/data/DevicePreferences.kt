package com.xavierclavel.cooknco.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The settings that belong to *this handset* rather than to the account.
 *
 * Push is the one the settings screen offers: whether a phone buzzes is a property of the
 * phone, not of the person — the same account on a tablet should be able to answer
 * differently. It lives here rather than in `UserSettingsDTO` for that reason, and it has
 * to persist, because [PushRepository.registerCurrentDevice] runs on every launch and would
 * otherwise re-register a device the user had switched off.
 *
 * Defaults to on: an account that has never opened this screen is in exactly the state the
 * app shipped with, and the platform's own notification permission is still the gate in
 * front of it.
 */
class DevicePreferences(private val dataStore: DataStore<Preferences>) {

    private val pushEnabledKey = booleanPreferencesKey("push_enabled")
    private val languageKey = stringPreferencesKey("app_language")

    val pushEnabled: Flow<Boolean> = dataStore.data.map { it[pushEnabledKey] ?: true }

    suspend fun setPushEnabled(enabled: Boolean) {
        dataStore.edit { it[pushEnabledKey] = enabled }
    }

    /**
     * The language the last session resolved, or null on a first launch.
     *
     * Cached so a relaunch opens in the right language rather than in the handset's until
     * the settings request comes back — see [AppLanguage].
     */
    val language: Flow<String?> = dataStore.data.map { it[languageKey] }

    suspend fun setLanguage(code: String) {
        dataStore.edit { it[languageKey] = code }
    }
}
