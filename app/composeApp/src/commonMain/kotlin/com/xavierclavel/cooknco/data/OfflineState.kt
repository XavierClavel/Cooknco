package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.isOffline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app currently believes it cannot reach the server.
 *
 * Inferred from requests rather than asked of the platform, which is deliberate. A
 * `ConnectivityManager` and an `NWPathMonitor` are two platform shims that answer a question
 * nobody asked: what matters is not whether the radio is on but whether *this app's requests
 * are arriving*, and a captive portal, a dead DNS server or a backend that is down all leave
 * the platform reporting a perfectly good connection.
 *
 * So every repository call reports what happened to it — see
 * [com.xavierclavel.cooknco.network.isOffline] — and the flag is whatever the last one found.
 * That means it can be a request behind, which is exactly right for what it drives: a banner
 * saying where the content on screen came from, not a decision about whether to try.
 */
object OfflineState {

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    /** A request came back, whatever it said. There is a server. */
    fun reachedServer() { _isOffline.value = false }

    /** A request did not come back. Only ever called for a failure that [isOffline] passed. */
    fun couldNotReachServer() { _isOffline.value = true }

    /**
     * Records what a call found and hands the result straight back, so a repository can wrap
     * a `runCatching` in it without an extra statement.
     */
    fun <T> observe(result: Result<T>): Result<T> = result.also {
        val error = it.exceptionOrNull()
        if (error != null && error.isOffline) couldNotReachServer() else reachedServer()
    }
}
