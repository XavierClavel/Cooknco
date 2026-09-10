package com.xavierclavel.cooknco.di

import android.content.Context
import com.xavierclavel.cooknco.data.createAuthDataStore
import com.xavierclavel.cooknco.platform.captureAppVersion

/**
 * Wires the object graph from an Android [Context]. Keeps DataStore off the
 * application module's classpath — it only needs to hand over a context.
 */
fun AppGraph.initFor(context: Context) {
    // The version check runs from a repository, which has no context of its own to read
    // the installed package with. See AppVersion.android.kt.
    captureAppVersion(context)
    init { createAuthDataStore(context) }
}
