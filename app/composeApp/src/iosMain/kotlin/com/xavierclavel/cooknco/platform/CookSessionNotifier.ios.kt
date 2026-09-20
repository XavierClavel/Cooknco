package com.xavierclavel.cooknco.platform

import com.xavierclavel.cooknco.data.CookSessionState

/**
 * Nothing yet, for the same reason [onCookTimerChanged] does nothing here: this app does not
 * ask iOS for permission to show a notification, so there is none to post one through.
 *
 * Cook mode itself is unaffected — the session is state, and the screen reads it — so what is
 * missing is only following the recipe with the app closed. It would be a
 * `UNNotificationCategory` with its actions plus a Live Activity to be worth having, and both
 * belong with the rest of iOS notifications rather than ahead of them.
 */
actual fun onCookSessionChanged(state: CookSessionState?) = Unit
