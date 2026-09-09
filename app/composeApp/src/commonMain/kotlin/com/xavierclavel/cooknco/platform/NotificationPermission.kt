package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/**
 * Asks for permission to show notifications, once, when [request] first becomes true.
 *
 * Driven by a flag rather than exposed as a function to call, so the ask can be tied to
 * being signed in: a permission prompt on the login screen is one the user has no reason to
 * grant yet, and Android only ever shows it once.
 *
 * A no-op where the platform needs no such permission — which is every Android before 13,
 * and iOS, where push is not built yet.
 */
@Composable
expect fun EnsureNotificationPermission(request: Boolean)
