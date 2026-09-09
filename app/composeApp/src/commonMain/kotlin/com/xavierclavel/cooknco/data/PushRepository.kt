package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.NotificationApi
import com.xavierclavel.cooknco.platform.currentPushToken
import kotlinx.coroutines.flow.first

/**
 * Keeps the backend's idea of this device in step with the platform's.
 *
 * Registration is driven from three places, all of which end up here: the app becoming
 * authenticated, Firebase rotating the token, and signing out. All three are safe to repeat
 * — the backend keys a device on its token — so nothing here has to track whether it has
 * run before.
 *
 * Every method answers with a [Result] and none of them is worth failing a screen over: a
 * device that could not be registered costs the user a buzz, not the app.
 */
class PushRepository(
    private val notificationApi: NotificationApi,
    private val tokenDataStore: TokenDataStore,
) {

    /**
     * Registers whatever token the platform currently has.
     *
     * Called on every launch once signed in, because a token can be rotated while the app is
     * not running, in which case `onNewToken` fired with nobody to send it.
     */
    suspend fun registerCurrentDevice(): Result<Unit> = runCatching {
        val pushToken = currentPushToken() ?: return@runCatching
        register(pushToken).getOrThrow()
    }

    /** Registers a specific token — what Firebase hands over when it rotates one. */
    suspend fun register(pushToken: String): Result<Unit> = runCatching {
        val authToken = tokenDataStore.tokenFlow.first() ?: return@runCatching
        notificationApi.registerDevice(authToken, pushToken)
    }

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
    }
}
