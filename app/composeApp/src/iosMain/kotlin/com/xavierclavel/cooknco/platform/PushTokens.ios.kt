package com.xavierclavel.cooknco.platform

/**
 * iOS has no push yet.
 *
 * Reaching one means an APNs key, the Firebase iOS SDK linked into a real Xcode project,
 * and a `UNUserNotificationCenter` delegate — none of which exists here, and none of which
 * the shared code can stand in for. Returning null keeps the common path compiling and
 * running on iOS with the notification list working and the buzz missing.
 */
actual suspend fun currentPushToken(): String? = null

actual val pushSupported: Boolean = false
