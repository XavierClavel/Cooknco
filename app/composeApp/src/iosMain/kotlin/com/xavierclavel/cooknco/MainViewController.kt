package com.xavierclavel.cooknco

import androidx.compose.ui.window.ComposeUIViewController
import com.xavierclavel.cooknco.data.createAuthDataStore
import com.xavierclavel.cooknco.di.AppGraph
import platform.UIKit.UIViewController

/** Entry point called from `iOSApp.swift`. */
fun MainViewController(): UIViewController {
    AppGraph.init { createAuthDataStore() }
    return ComposeUIViewController { App() }
}

/** Called from Swift's `onOpenURL` for the `cooknco://` scheme. */
fun handleDeepLink(url: String) = DeepLinks.onCallbackUrl(url)
