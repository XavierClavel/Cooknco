package com.xavierclavel.cooknco.platform

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
actual fun KeepKeyboardFromPanning() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val previous = window.attributes.softInputMode
        // Resize rather than pan. The app draws edge to edge, so nothing actually resizes:
        // the keyboard is only reported as an inset, which the screen pads for itself.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(
            (previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        onDispose { window.setSoftInputMode(previous) }
    }
}
