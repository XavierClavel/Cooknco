package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/** Nothing to ask for while iOS push is unbuilt. See [currentPushToken]. */
@Composable
actual fun EnsureNotificationPermission(request: Boolean) = Unit
