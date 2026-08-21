package com.xavierclavel.cooknco.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

/**
 * Same on-disk location `preferencesDataStore(name = "auth")` used before the
 * multiplatform migration, so existing sessions survive the upgrade.
 */
fun createAuthDataStore(context: Context): DataStore<Preferences> =
    createPreferencesDataStore(
        File(context.applicationContext.filesDir, "datastore/$AUTH_PREFERENCES_FILE").absolutePath
    )
