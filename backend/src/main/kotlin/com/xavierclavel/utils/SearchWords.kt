package com.xavierclavel.utils

import java.text.Normalizer

/** Lowercase, accents and ligatures off, so "œuf" finds "Oeuf". */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

/** The words of [text] as a search compares them: normalised, split on anything but letters and digits. */
fun searchWordsOf(text: String): List<String> =
    normalizeForSearch(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
