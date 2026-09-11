package com.xavierclavel.cooknco.platform

/**
 * The language this install is running in, as one of the backend's `Locale` names.
 *
 * "FR" or "EN", never anything else: the API takes an enum with exactly those two values,
 * and a phone set to any other language is answered with EN — the same fallback the rest of
 * the product uses for a language it does not speak.
 *
 * This is what the account adopts when it has no language of its own, so it is read from the
 * platform rather than declared: the whole point is that the backend learns what the person
 * actually reads in, instead of the constant every build used to send.
 */
expect val deviceLocale: String

/**
 * Maps a platform language tag onto the two the API knows.
 *
 * Shared by both actuals, which differ only in where the tag comes from. The region is
 * dropped — `fr-CA` and `fr-FR` are one language here — and anything unrecognised, the empty
 * string included, answers EN.
 */
internal fun localeFromLanguageTag(tag: String): String =
    if (tag.substringBefore('-').substringBefore('_').equals("fr", ignoreCase = true)) "FR" else "EN"
