package com.xavierclavel.cooknco

import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridge for the `cooknco://login?token=...` OAuth callback. Platform entry points
 * push URLs in; [App] collects them.
 *
 * Replays the last token so a callback that arrives during a cold start is not
 * lost before the UI has composed.
 */
object DeepLinks {

    private val _oauthTokens = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val oauthTokens: SharedFlow<String> = _oauthTokens.asSharedFlow()

    fun onCallbackUrl(url: String) {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return
        if (parsed.protocol.name != "cooknco" || parsed.host != "login") return
        val token = parsed.parameters["token"] ?: return
        _oauthTokens.tryEmit(token)
    }
}
