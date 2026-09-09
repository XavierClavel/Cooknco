package com.xavierclavel.services

import com.xavierclavel.utils.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Pushes through Firebase Cloud Messaging's HTTP v1 API.
 *
 * One request per device, run a bounded number at a time. That is not a shortcut around a
 * batch endpoint: FCM's `/batch` was retired in 2024, and the official SDKs now do exactly
 * this — a request per token, concurrently — behind a method that looks like a batch.
 *
 * Every message carries both halves of the FCM payload on purpose. The `notification` half
 * is what lets Android display it while the app is backgrounded or stopped, which is the
 * case that matters most and the one a data-only message does not cover. The `data` half
 * carries the kind and the path to open, which the app reads whether the system drew the
 * notification or it did. The two never draw twice: with a `notification` block present,
 * Android hands the message to the app *instead of* displaying it whenever the app is in
 * the foreground, and displays it itself otherwise.
 */
class FcmPushSender(
    private val credentials: FcmCredentials,
    /** Overrides the project on the credentials. Only useful when the two disagree. */
    projectId: String = "",
    /** Pushes in flight at once. See `Configuration.Push.maxConcurrentSends`. */
    concurrency: Int = 8,
    /** The Android notification channel the app creates. Must match, or nothing is shown. */
    private val androidChannelId: String = DEFAULT_CHANNEL_ID,
    private val client: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
    },
) : PushSender {

    companion object {
        /**
         * The channel id the app registers, repeated here because FCM needs to name it and
         * a message naming a channel that does not exist is silently dropped on Android 8+.
         */
        const val DEFAULT_CHANNEL_ID = "cooknco_default"

        private const val ENDPOINT = "https://fcm.googleapis.com/v1/projects/%s/messages:send"

        /**
         * What FCM calls a token that no longer belongs to an install.
         *
         * Only these mean the device is gone. A 401, a 429 or a 503 is this backend's
         * problem or Google's, and pruning on one of those would unsubscribe live users
         * during an outage — so the token is kept and the push simply counted as failed.
         */
        private val STALE = setOf("UNREGISTERED", "INVALID_ARGUMENT", "SENDER_ID_MISMATCH")
    }

    private val projectId = projectId.ifBlank { credentials.projectId }

    private val slots = Semaphore(concurrency)

    override suspend fun send(messages: List<PushMessage>): PushResult = coroutineScope {
        if (messages.isEmpty()) return@coroutineScope PushResult()

        val token = try {
            credentials.get()
        } catch (e: Exception) {
            // Nothing here is per-device: without a token not one of these can go out
            logger.error(e) { "Could not authenticate against FCM: ${messages.size} push(es) dropped" }
            return@coroutineScope PushResult(failed = messages.size)
        }

        val outcomes = messages
            .map { message -> async { slots.withPermit { push(token, message) } } }
            .awaitAll()

        PushResult(
            delivered = outcomes.count { it == Outcome.DELIVERED },
            failed = outcomes.count { it != Outcome.DELIVERED },
            staleTokens = messages.filterIndexed { i, _ -> outcomes[i] == Outcome.STALE }.map { it.token },
        )
    }

    private enum class Outcome { DELIVERED, FAILED, STALE }

    private suspend fun push(accessToken: String, message: PushMessage, retried: Boolean = false): Outcome {
        val response = try {
            client.post(ENDPOINT.format(projectId)) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                // Serialised here rather than handed over as a JsonObject for a plugin to
                // convert: this client installs no ContentNegotiation, and passing an
                // object it cannot convert fails at send time rather than at compile time.
                // JsonElement.toString() is the JSON text, so what goes out is exactly what
                // payload() built.
                setBody(payload(message).toString())
            }
        } catch (e: Exception) {
            logger.error(e) { "Could not reach FCM" }
            return Outcome.FAILED
        }

        if (response.status.isSuccess()) return Outcome.DELIVERED

        val body = response.bodyAsText()

        // A token rejected mid-flight: mint a fresh one and give this push one more go
        if (response.status == HttpStatusCode.Unauthorized && !retried) {
            credentials.invalidate()
            val refreshed = runCatching { credentials.get() }.getOrElse {
                logger.error(it) { "FCM rejected the access token and it could not be renewed" }
                return Outcome.FAILED
            }
            return push(refreshed, message, retried = true)
        }

        return if (STALE.any { body.contains(it) }) {
            logger.info { "FCM reports a dead device token; dropping it" }
            Outcome.STALE
        } else {
            logger.error { "FCM refused a push: ${response.status} $body" }
            Outcome.FAILED
        }
    }

    /**
     * The `message` envelope FCM expects.
     *
     * Built with the JSON DSL rather than by interpolating strings: a title is whatever a
     * user typed into a recipe, and hand-built JSON is how that becomes a malformed request
     * — or worse, a payload with fields the caller never wrote.
     */
    private fun payload(message: PushMessage) = buildJsonObject {
        putJsonObject("message") {
            put("token", message.token)
            putJsonObject("notification") {
                put("title", message.title)
                put("body", message.body)
            }
            if (message.data.isNotEmpty()) {
                putJsonObject("data") {
                    message.data.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
                }
            }
            putJsonObject("android") {
                // Wakes a dozing device: these are user-visible and time-relevant
                put("priority", "HIGH")
                putJsonObject("notification") {
                    put("channel_id", androidChannelId)
                    // Lets the app collapse a burst from one sender into one row
                    put("tag", message.data["kind"] ?: "announcement")
                }
            }
        }
    }
}
