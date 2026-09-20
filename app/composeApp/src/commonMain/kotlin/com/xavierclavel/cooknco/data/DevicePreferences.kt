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
    private val exactAlarmsAskedKey = booleanPreferencesKey("exact_alarms_asked")
    private val languageKey = stringPreferencesKey("app_language")
    private val unitSystemKey = stringPreferencesKey("unit_system")

    val pushEnabled: Flow<Boolean> = dataStore.data.map { it[pushEnabledKey] ?: true }

    suspend fun setPushEnabled(enabled: Boolean) {
        dataStore.edit { it[pushEnabledKey] = enabled }
    }

    /**
     * Whether the cook has already been asked to let the timer ring on time.
     *
     * Kept so they are asked once rather than every time a timer is started. It is a device
     * property like [pushEnabled] — the permission it is about belongs to this install, not
     * to the account, and a tablet that has never run a timer should get its own asking.
     */
    val exactAlarmsAsked: Flow<Boolean> = dataStore.data.map { it[exactAlarmsAskedKey] ?: false }

    suspend fun setExactAlarmsAsked() {
        dataStore.edit { it[exactAlarmsAskedKey] = true }
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

    /**
     * The ladder the last session resolved, or null on a first launch.
     *
     * Cached for the same reason as [language]: a relaunch should draw the units the
     * account reads in straight away rather than in metric until the settings request comes
     * back. It is the account's choice and not the handset's — see [AppUnits].
     */
    val unitSystem: Flow<String?> = dataStore.data.map { it[unitSystemKey] }

    suspend fun setUnitSystem(code: String) {
        dataStore.edit { it[unitSystemKey] = code }
    }

    /**
     * Forgets the two values above, which are the account's rather than the handset's.
     *
     * Called on sign-out ([AccountSettings.forget]). Push and the exact-alarm asking are
     * deliberately not cleared: they describe this phone, and they mean the same thing
     * whoever is holding it.
     */
    suspend fun clearAccountSettings() {
        dataStore.edit {
            it.remove(languageKey)
            it.remove(unitSystemKey)
        }
    }
}
