package com.xavierclavel.cooknco

import com.xavierclavel.cooknco.navigation.WebRoutes
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridge for every URL the system hands the app. Platform entry points push them in; [App]
 * and [com.xavierclavel.cooknco.navigation.AppNavigation] collect what they produce.
 *
 * Two kinds arrive, on the same entry point because both platforms deliver them the same
 * way — an intent's data on Android, `onOpenURL` on iOS:
 *
 * - `cooknco://login?token=…`, the Google OAuth callback. A private scheme, because it is
 *   a credential coming back to *this* app and nothing else may claim it.
 * - `https://cooknco.eu/recipe/view?id=12` and friends — a shared link the app claims, so
 *   that tapping one opens the recipe rather than the website. Those are the paths listed
 *   in the manifest's App Links filter and in the Universal Links file; a URL that reaches
 *   here without matching one is left alone, and the browser keeps it.
 *
 * Both flows replay their last value so a URL that arrives during a cold start is not lost
 * before the UI has composed — which is the usual case for both: a link is tapped on a
 * device where the app is not running, and the OAuth callback comes back from a browser.
 */
object DeepLinks {

    private val _oauthTokens = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val oauthTokens: SharedFlow<String> = _oauthTokens.asSharedFlow()

    private val _routes = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

    /** App routes to navigate to, already translated from the link's web path. */
    val routes: SharedFlow<String> = _routes.asSharedFlow()

    /** Every URL the app is opened with, whatever its scheme. */
    fun onIncomingUrl(url: String) {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return
        if (parsed.protocol.name == "cooknco" && parsed.host == "login") {
            parsed.parameters["token"]?.let { _oauthTokens.tryEmit(it) }
            return
        }
        WebRoutes.routeForUrl(url)?.let { _routes.tryEmit(it) }
    }
}
