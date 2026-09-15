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

/**
 * Called from Swift for every URL the app is opened with: the `cooknco://` OAuth
 * callback through `onOpenURL`, and a Universal Link through `onContinueUserActivity`.
 */
fun handleDeepLink(url: String) = DeepLinks.onIncomingUrl(url)
