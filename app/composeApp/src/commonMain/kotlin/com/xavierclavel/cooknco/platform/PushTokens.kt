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
