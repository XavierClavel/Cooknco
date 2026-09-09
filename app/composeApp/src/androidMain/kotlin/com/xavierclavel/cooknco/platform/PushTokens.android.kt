package com.xavierclavel.cooknco.platform

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Asks Firebase for this install's token.
 *
 * The token is created on first request and then cached by the SDK, so this is cheap to
 * call on every launch — which the app does, because a token can be rotated at any time and
 * the backend has to be told when it is (see `CookncoMessagingService.onNewToken`).
 */
actual suspend fun currentPushToken(): String? = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnCompleteListener { task ->
            // A failure here is a device without Play Services, or one offline at a bad
            // moment. Neither is worth an exception: the app simply has no token this run.
            continuation.resume(task.result?.takeIf { task.isSuccessful && it.isNotBlank() })
        }
}

actual val pushSupported: Boolean = true
