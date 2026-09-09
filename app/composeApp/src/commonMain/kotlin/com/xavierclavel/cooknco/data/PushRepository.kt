package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.NotificationApi
import com.xavierclavel.cooknco.platform.currentPushToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Keeps the backend's idea of this device in step with the platform's.
 *
 * Registration is driven from three places, all of which end up here: the app becoming
 * authenticated, Firebase rotating the token, and signing out. All three are safe to repeat
 * — the backend keys a device on its token — so nothing here has to track whether it has
 * run before.
 *
 * Nothing here is worth failing a screen over: a device that could not be registered costs
 * the user a buzz, not the app. It is worth *saying* so, though — see [log]. A registration
 * that fails silently is indistinguishable from one that worked, which is exactly how a
 * first version of this shipped a device that never registered.
 */
class PushRepository(
    private val notificationApi: NotificationApi,
    private val tokenDataStore: TokenDataStore,
) {

    private companion object {
        /**
         * How long to keep asking Firebase for a token before giving up on this launch.
         *
         * A fresh install has no token until the Firebase Installations service has
         * registered with Google, which needs the network and is not instant — on a real
         * device it has taken twenty minutes. Signing in during that window used to find
         * `currentPushToken()` null and drop the registration on the floor for good, since
         * nothing re-ran once the auth state had settled. These retries close that window;
         * `onNewToken` covers a token that arrives after the app is gone.
         */
        private val TOKEN_BACKOFF_MILLIS = longArrayOf(0, 1_000, 3_000, 8_000, 20_000, 45_000)
    }

    /**
     * Registers whatever token the platform can give us, waiting for one if need be.
     *
     * Called on every launch once signed in, because a token can be rotated while the app is
     * not running, in which case `onNewToken` fired with nobody to send it to.
     */
    suspend fun registerCurrentDevice(): Result<Unit> = runCatching {
        TOKEN_BACKOFF_MILLIS.forEachIndexed { attempt, wait ->
            if (wait > 0) delay(wait)
            val pushToken = currentPushToken()
            if (pushToken != null) {
                register(pushToken).getOrThrow()
                return@runCatching
            }
            log("no push token yet (attempt ${attempt + 1}/${TOKEN_BACKOFF_MILLIS.size})")
        }
        // Not an error: the user may have no Play Services, or be offline. onNewToken will
        // fire if a token ever appears, and the notification list works without one.
        log("gave up waiting for a push token on this launch")
    }.onFailure { log("device registration failed: ${it.message}") }

    /** Registers a specific token — what Firebase hands over when it rotates one. */
    suspend fun register(pushToken: String): Result<Unit> = runCatching {
        val authToken = tokenDataStore.tokenFlow.first()
        if (authToken == null) {
            // The common case on a fresh install: the token is generated before anyone has
            // signed in. Nothing to do now — the next sign-in registers the cached token.
            log("push token available but nobody is signed in; leaving it to the next sign-in")
            return@runCatching
        }
        notificationApi.registerDevice(authToken, pushToken)
        log("device registered")
    }.onFailure { log("device registration failed: ${it.message}") }

    /**
     * Detaches this device from the account being signed out of.
     *
     * Has to run *before* the auth token is cleared, since it is an authenticated call — see
     * [AuthRepository.logout], which is the only caller for that reason. Skipping it would
     * leave the account receiving notifications on a handset it no longer owns.
     */
    suspend fun unregisterCurrentDevice(): Result<Unit> = runCatching {
        val pushToken = currentPushToken() ?: return@runCatching
        val authToken = tokenDataStore.tokenFlow.first() ?: return@runCatching
        notificationApi.unregisterDevice(authToken, pushToken)
        log("device unregistered")
    }.onFailure { log("device unregistration failed: ${it.message}") }

    /**
     * Says what happened, on a tag that is greppable in logcat.
     *
     * `println` rather than a logging framework because this module has none, and because
     * everything here is a rare one-line lifecycle event rather than a stream.
     */
    private fun log(message: String) = println("$LOG_TAG $message")
}

/** Grep this in logcat to see what registration did: `adb logcat | grep CookncoPush`. */
private const val LOG_TAG = "CookncoPush:"
