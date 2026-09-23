package com.xavierclavel.cooknco.platform

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Application Support rather than Documents or Caches.
 *
 * Caches is purgeable, which is the one thing this must not be. Documents is the user's own
 * files, which these are not — they are a copy of what the server holds. Application Support
 * is the middle, and it is created here because, unlike Documents, it may not exist yet.
 *
 * Excluded from backup: Apple requires it of anything re-downloadable, and a cook's whole
 * recipe collection is exactly the kind of thing that would otherwise quietly fill an iCloud
 * allowance with a second copy of what the server already has.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun offlineRoot(): Path {
    val manager = NSFileManager.defaultManager
    val support: NSURL = requireNotNull(
        manager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    ) { "No application support directory available" }
    val offline = support.URLByAppendingPathComponent("offline", isDirectory = true)
        ?: error("Could not resolve the offline directory")
    manager.createDirectoryAtURL(offline, withIntermediateDirectories = true, attributes = null, error = null)
    offline.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(offline.path) { "The offline directory has no path" }.toPath()
}
