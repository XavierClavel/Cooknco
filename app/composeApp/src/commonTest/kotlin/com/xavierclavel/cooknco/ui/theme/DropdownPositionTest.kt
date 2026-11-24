package com.xavierclavel.cooknco.ui.theme

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a dropdown opens, against a window the keyboard covers the bottom of.
 *
 * The window is not resized for the keyboard, so a menu measured only against the window
 * opens under a field sitting just above the keys — behind them, where nothing can tap it.
 */
class DropdownPositionTest {

    private val window = IntSize(1000, 2000)
    private val menu = IntSize(400, 300)
    private val gap = 16

    private fun place(anchor: IntRect, keyboardHeight: Int, alignEnd: Boolean = false) =
        DropdownPosition(gap = gap, alignEnd = alignEnd, keyboardHeight = keyboardHeight)
            .calculatePosition(anchor, window, LayoutDirection.Ltr, menu)

    @Test
    fun a_menu_hangs_under_its_anchor_when_it_fits_there() {
        val anchor = IntRect(left = 100, top = 400, right = 600, bottom = 500)

        assertEquals(IntOffset(anchor.left, anchor.bottom + gap), place(anchor, keyboardHeight = 800))
    }

    @Test
    fun a_menu_the_keyboard_would_cover_opens_above_its_anchor() {
        val keyboardHeight = 800
        // Just above the keys: the menu would need 300px under it and the keyboard leaves 50.
        val anchor = IntRect(left = 100, top = 1050, right = 600, bottom = window.height - keyboardHeight - 50)

        assertEquals(IntOffset(anchor.left, anchor.top - gap - menu.height), place(anchor, keyboardHeight))
    }

    @Test
    fun the_same_anchor_with_the_keyboard_down_hangs_under_it() {
        val anchor = IntRect(left = 100, top = 1050, right = 600, bottom = 1150)

        assertEquals(IntOffset(anchor.left, anchor.bottom + gap), place(anchor, keyboardHeight = 0))
    }

    @Test
    fun an_end_aligned_menu_shares_its_anchor_s_trailing_edge() {
        val anchor = IntRect(left = 700, top = 400, right = 900, bottom = 460)

        assertEquals(anchor.right - menu.width, place(anchor, keyboardHeight = 0, alignEnd = true).x)
    }
}
