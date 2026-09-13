package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/**
 * Whether this platform will ring a timer *at* the second it is due rather than somewhere
 * near it.
 *
 * This is not a detail. An approximate alarm is batched with whatever else the system has
 * queued, and on a phone in a pocket that can be minutes — long enough that the only thing
 * that rings the timer is the cook opening the app to find out why it had not. A timer that
 * is late is not a late timer, it is a broken one.
 *
 * True where the question does not arise, so a platform that never asks never prompts.
 */
expect fun canRingTimersExactly(): Boolean

/** Takes the user to wherever the platform has them grant that. */
fun interface ExactTimerConsent {
    fun request()
}

@Composable
expect fun rememberExactTimerConsent(): ExactTimerConsent
