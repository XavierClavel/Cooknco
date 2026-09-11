package com.xavierclavel.cooknco.platform

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The first of the user's preferred languages, not `NSLocale.currentLocale`.
 *
 * `preferredLanguages` is what the app is actually displayed in — it is the ordered list iOS
 * resolves the bundle against. `currentLocale` follows the region format settings, so a
 * phone in English set to a French region reports French there and would have this app write
 * to its owner in a language they did not ask for.
 */
actual val deviceLocale: String
    get() = localeFromLanguageTag(NSLocale.preferredLanguages.firstOrNull() as? String ?: "")
