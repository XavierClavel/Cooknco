package com.xavierclavel.cooknco.platform

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberUrlOpener(): UrlOpener {
    val context = LocalContext.current
    return remember(context) {
        UrlOpener { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }
    }
}
