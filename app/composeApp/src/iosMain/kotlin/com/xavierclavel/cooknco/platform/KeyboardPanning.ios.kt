package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

// The view controller's focus behaviour is fixed when it is created, so it cannot be changed
// for one screen from here.
@Composable
actual fun KeepKeyboardFromPanning() = Unit
