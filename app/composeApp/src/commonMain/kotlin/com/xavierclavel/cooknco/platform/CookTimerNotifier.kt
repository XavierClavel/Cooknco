package com.xavierclavel.cooknco.platform

import com.xavierclavel.cooknco.data.CookTimerState

/**
 * Mirrors the cook timer onto whatever the platform shows outside the app.
 *
 * The split is deliberate. [onCookTimerChanged] is about the timer *while it runs* — the
 * ongoing notification and the alarm that will end it — and is called on every transition,
 * `null` included, which means "there is no timer, take it all down". [onCookTimerFinished]
 * is the ring, and replaces the ongoing notification rather than updating it, because the
 * two say opposite things: one is silent and cannot be dismissed, the other is loud and is
 * meant to be.
 *
 * Neither is a request to *keep counting*. The timer is a deadline
 * ([CookTimerState.endsAtEpochMillis]), so what a platform is asked for is a countdown it
 * renders itself and an alarm that wakes something at the end — not a process kept alive to
 * tick.
 */
expect fun onCookTimerChanged(state: CookTimerState?)

/** The timer has run out. See [onCookTimerChanged]. */
expect fun onCookTimerFinished(state: CookTimerState)
