package com.xavierclavel.cooknco.platform

import android.content.Context

/**
 * Read from the installed package rather than from `BuildConfig`.
 *
 * This is a library module: its own `BuildConfig` carries the library's version, not the
 * application's, so the number the store shows is only reachable through the package
 * manager. Captured once at startup — see `AppGraph.initFor` — because the version check
 * runs from a repository, which has no `Context` to ask.
 */
private var installed: String = ""

internal fun captureAppVersion(context: Context) {
    installed = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}

actual val appVersion: String get() = installed
