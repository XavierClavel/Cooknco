package com.xavierclavel.services

import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * The `index.html` of the built SPA — the skeleton [LinkPreviewService] renders a shared
 * link's document into.
 *
 * An interface because the shell lives in the *frontend* image, not this one: the only way
 * the backend can get at it is over the network, and tests have no nginx to ask.
 */
interface AppShellSource {
    /** @return the shell, or null when it cannot be obtained right now. */
    suspend fun fetch(): String?
}

/**
 * Reads the shell from the nginx that serves it, over the cluster network.
 *
 * Cached, because otherwise every visit to a public entity page would cost a round trip;
 * but only briefly, because the shell names asset files by content hash and a copy kept
 * across a frontend deploy would point the browser at bundles nginx no longer has.
 */
class HttpAppShellSource(
    private val configuration: Configuration,
    private val client: HttpClient = HttpClient(CIO),
    private val ttlMillis: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis,
) : AppShellSource {

    private class Cached(val html: String, val fetchedAt: Long)

    private val cached = AtomicReference<Cached?>(null)

    /** Holds the refresh so a burst of misses costs one fetch, not one per request. */
    private val refreshing = Mutex()

    override suspend fun fetch(): String? {
        fresh()?.let { return it }
        return refreshing.withLock {
            // Whoever held the lock may have just filled the cache.
            fresh() ?: load()?.also { cached.set(Cached(it, now())) }
                // A stale shell beats none: its asset names are almost certainly still
                // valid, and the alternative is the visitor seeing nginx's error page.
                ?: cached.get()?.html
        }
    }

    private fun fresh(): String? = cached.get()?.takeIf { now() - it.fetchedAt < ttlMillis }?.html

    private suspend fun load(): String? =
        try {
            val url = "${configuration.frontend.internalUrl.trimEnd('/')}/index.html"
            val response = client.get(url)
            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                logger.warn { "Could not read the app shell from $url: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Could not read the app shell from ${configuration.frontend.internalUrl}" }
            null
        }
}
