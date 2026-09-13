package com.xavierclavel.cooknco.platform

import com.xavierclavel.cooknco.data.CookTimerState

/**
 * Nothing yet, for the same reason [currentPushToken] returns nothing: this app does not ask
 * iOS for permission to show a notification, so there is none to post one through.
 *
 * The timer itself still works — it is a deadline, and `CookTimer` reads it from the clock —
 * so cook mode counts down correctly here and is correct again when the app comes back. What
 * is missing is only the part that happens with the app closed: the countdown in the
 * notification centre, and the ring. Both are `UNUserNotificationCenter` with a
 * `UNTimeIntervalNotificationTrigger`, and both belong with the rest of iOS notifications
 * rather than ahead of them.
 */
actual fun onCookTimerChanged(state: CookTimerState?) = Unit

actual fun onCookTimerFinished(state: CookTimerState) = Unit
