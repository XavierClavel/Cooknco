package com.xavierclavel.cooknco.platform

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Asks Firebase for this install's token.
 *
 * Null is a normal answer on a fresh install: the token does not exist until the Firebase
 * Installations service has registered with Google, which needs the network and is not
 * instant. `PushRepository.registerCurrentDevice` therefore retries rather than treating
 * the first null as final.
 *
 * Once created the SDK caches it, so this is cheap to call on every launch — which the app
 * does, because a token can be rotated while the app is not running.
 */
actual suspend fun currentPushToken(): String? = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnCompleteListener { task ->
            // The caller may have gone away — a LaunchedEffect cancelled by recomposition —
            // and resuming a dead continuation throws rather than being ignored
            if (!continuation.isActive) return@addOnCompleteListener

            if (task.isSuccessful) {
                continuation.resume(task.result?.takeIf { it.isNotBlank() })
            } else {
                // Usually SERVICE_NOT_AVAILABLE on a fresh install, or a device with no
                // Play Services. Said out loud, because the retry above is otherwise silent.
                println("CookncoPush: could not get a push token: ${task.exception?.message}")
                continuation.resume(null)
            }
        }
}

actual val pushSupported: Boolean = true

actual val devicePlatform: String = "ANDROID"
