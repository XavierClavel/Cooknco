package com.xavierclavel.cooknco.platform

import platform.Foundation.NSBundle

/**
 * `CFBundleShortVersionString`, which is the version the App Store lists — not
 * `CFBundleVersion`, which is the build number underneath it and resets between releases.
 */
actual val appVersion: String
    get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
        .orEmpty()
