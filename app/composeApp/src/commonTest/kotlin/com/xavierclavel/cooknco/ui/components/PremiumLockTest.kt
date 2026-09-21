package com.xavierclavel.cooknco.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a sheet row for a paid feature comes out as, on either side of the grant.
 *
 * No composition and no server: [premiumSheetAction] is the whole of the decision, which is
 * why it is a function rather than three lines repeated on each screen that sells something.
 */
class PremiumLockTest {

    private var used = 0
    private var explained = 0

    private fun action(isPremium: Boolean) = premiumSheetAction(
        label = "Export as PDF",
        icon = Icons.Outlined.FileDownload,
        isPremium = isPremium,
        onUse = { used++ },
        onLocked = { explained++ },
    )

    /**
     * The row is there either way. Hiding it is what this replaced: a feature only
     * subscribers can see is one only subscribers hear about, and nothing else in the app
     * says what premium buys.
     */
    @Test
    fun the_feature_is_advertised_whether_or_not_it_is_paid_for() {
        assertEquals("Export as PDF", action(isPremium = true).label)
        assertEquals("Export as PDF", action(isPremium = false).label)
        // The same icon on both, so the locked row reads as the action it will become
        // rather than as a padlock that happens to mention an export.
        assertSame(action(isPremium = true).icon, action(isPremium = false).icon)
    }

    @Test
    fun a_grant_unlocks_the_row_and_running_it_runs_the_feature() {
        val action = action(isPremium = true)

        assertFalse(action.locked)
        action.onClick()
        assertEquals(1, used)
        assertEquals(0, explained)
    }

    /**
     * The one that matters. The route behind the export answers a non-subscriber `403`, so
     * a locked row wired to the feature would spend a print's worth of waiting to arrive at
     * "the PDF could not be prepared" — a failure's wording for something working as
     * intended. Tapping it must explain the lock instead, and must not call the feature.
     */
    @Test
    fun without_one_the_row_is_locked_and_explains_itself_rather_than_being_refused() {
        val action = action(isPremium = false)

        assertTrue(action.locked)
        action.onClick()
        assertEquals(0, used)
        assertEquals(1, explained)
    }
}
