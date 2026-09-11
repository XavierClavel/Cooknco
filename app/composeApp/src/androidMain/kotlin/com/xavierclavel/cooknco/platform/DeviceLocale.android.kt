package com.xavierclavel.cooknco.platform

import java.util.Locale

/**
 * The JVM default locale, which Android keeps in step with the system language — including
 * a per-app language override, since that is applied to the process before anything here
 * runs.
 */
actual val deviceLocale: String
    get() = localeFromLanguageTag(Locale.getDefault().language)
