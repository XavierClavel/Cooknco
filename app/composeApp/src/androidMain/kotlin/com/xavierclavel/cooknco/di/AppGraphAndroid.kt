package com.xavierclavel.cooknco.di

import android.content.Context
import com.xavierclavel.cooknco.data.createAuthDataStore
import com.xavierclavel.cooknco.platform.captureAppVersion
import com.xavierclavel.cooknco.platform.captureCookModeContext
import com.xavierclavel.cooknco.platform.captureOfflineRoot

/**
 * Wires the object graph from an Android [Context]. Keeps DataStore off the
 * application module's classpath — it only needs to hand over a context.
 */
fun AppGraph.initFor(context: Context) {
    // The version check runs from a repository, which has no context of its own to read
    // the installed package with. See AppVersion.android.kt.
    captureAppVersion(context)
    // Same reason: the cook timer is driven from common code, and posting its notification
    // or setting its alarm needs a context that code has no way to get hold of.
    captureCookModeContext(context)
    // And the directory the offline recipes live in: `filesDir` is only reachable from a
    // context, and the store is reached from repositories that have none.
    captureOfflineRoot(context)
    init { createAuthDataStore(context) }
}
