package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Closes the keyboard when a tap lands on something that does not take one: the page around
 * a field, a heading, a picture.
 *
 * iOS has no key that hides its keyboard, and Compose does not hide it for a tap elsewhere,
 * so without this a field of several lines — a description, a step, a note — keeps it up
 * over whatever sits below it, Save and Next among them. Android has its back gesture for
 * the same job; there this is simply one more way.
 *
 * Applied once, at the root. A button, a field or a row takes its own tap before this one
 * sees it, so it only fires on a tap nothing else wanted, and a drag is never a tap:
 * scrolling a page leaves the keyboard where it is.
 */
fun Modifier.closeKeyboardOnTapOutside(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
