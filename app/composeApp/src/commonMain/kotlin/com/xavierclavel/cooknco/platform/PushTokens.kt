package com.xavierclavel.cooknco.platform

/**
 * The push registration token for this install, or null where there is none.
 *
 * Null is a normal answer, not an error: the user may have refused notifications, the
 * platform may have no push service configured, or — on iOS, where this is a stub — the
 * feature may simply not be built yet. Every caller therefore treats it as optional and the
 * app works without it.
 */
expect suspend fun currentPushToken(): String?

/** True where this build can actually receive pushes. Lets the UI hide what it cannot do. */
expect val pushSupported: Boolean

/**
 * What this client is, for the backend to record against the device.
 *
 * An expect rather than a default on the DTO: kotlinx.serialization omits default values
 * unless `encodeDefaults` is set, so a default here would be a field that never reaches the
 * server — which is exactly how the first version of this shipped a request the backend
 * rejected as missing `platform`.
 */
expect val devicePlatform: String
