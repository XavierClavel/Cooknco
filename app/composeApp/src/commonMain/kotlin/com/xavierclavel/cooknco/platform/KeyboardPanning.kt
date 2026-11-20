package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/**
 * While in composition, stops the system from sliding the whole window up to keep a focused
 * field above the keyboard, for a screen that makes room for the keyboard itself.
 *
 * Both at once is a page shifted up and shrunk, with the gap between showing the screen's
 * background above the keys. Scoped to the screen that asks, because every other screen still
 * relies on the slide to keep its fields in view.
 */
@Composable
expect fun KeepKeyboardFromPanning()
