package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/** Opens an external URL in the platform's browser. */
fun interface UrlOpener {
    fun open(url: String)
}

@Composable
expect fun rememberUrlOpener(): UrlOpener
