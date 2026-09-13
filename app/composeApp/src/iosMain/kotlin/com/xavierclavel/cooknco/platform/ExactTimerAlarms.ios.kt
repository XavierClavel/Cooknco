package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS has no equivalent of Android's exact-alarm permission: a scheduled notification fires
 * when it is due. So the question does not arise, nothing is ever asked, and the dialog this
 * gates never appears here. See [canRingTimersExactly].
 */
actual fun canRingTimersExactly(): Boolean = true

@Composable
actual fun rememberExactTimerConsent(): ExactTimerConsent = remember { ExactTimerConsent {} }
