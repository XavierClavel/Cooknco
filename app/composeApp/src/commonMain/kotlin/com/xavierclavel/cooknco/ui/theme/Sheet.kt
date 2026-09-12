package com.xavierclavel.cooknco.ui.theme

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Makes a sheet's dark backdrop dismiss it.
 *
 * Every sheet in the app is a `Dialog` with `usePlatformDefaultWidth = false`, which means
 * its window covers the screen — so there is no "outside" for the platform to notice, and
 * `onDismissRequest` fires for the back gesture and nothing else. Tapping away from the
 * panel has to be wired up, and this is it.
 *
 * `detectTapGestures` rather than `clickable`: a clickable backdrop is one enormous ripple,
 * and it would claim a semantics role and a focus stop that belong to the panel in front
 * of it.
 */
fun Modifier.sheetScrim(onDismissRequest: () -> Unit): Modifier =
    pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }

/**
 * Stops a tap on the sheet's own panel from reaching the [sheetScrim] behind it.
 *
 * Without it, the gaps between a panel's controls — its padding, the space beside a
 * heading — are backdrop, and tapping one closes the sheet. `detectTapGestures` consumes
 * the press, and the scrim's own detector only accepts an unconsumed one, so a control
 * that handles its own tap still keeps it.
 */
fun Modifier.swallowTaps(): Modifier =
    pointerInput(Unit) { detectTapGestures { } }
