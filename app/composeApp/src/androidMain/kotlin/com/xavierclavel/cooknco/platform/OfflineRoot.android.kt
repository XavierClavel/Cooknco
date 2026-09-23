package com.xavierclavel.cooknco.platform

import android.content.Context
import okio.Path
import okio.Path.Companion.toPath

/**
 * Captured once at startup — see `AppGraph.initFor` — for the reason [appVersion] is: the
 * offline store is reached from repositories, which have no `Context` to ask.
 */
private var root: Path? = null

internal fun captureOfflineRoot(context: Context) {
    if (root == null) {
        root = "${context.applicationContext.filesDir.absolutePath}/offline".toPath()
    }
}

actual fun offlineRoot(): Path = checkNotNull(root) {
    "AppGraph.initFor() must be called before the offline store is used"
}
