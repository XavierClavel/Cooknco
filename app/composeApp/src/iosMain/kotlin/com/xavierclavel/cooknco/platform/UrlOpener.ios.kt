package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberUrlOpener(): UrlOpener = remember {
    UrlOpener { url ->
        val nsUrl = NSURL.URLWithString(url) ?: return@UrlOpener
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
    }
}
