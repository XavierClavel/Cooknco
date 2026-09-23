package com.xavierclavel.cooknco.ui.components

import com.xavierclavel.cooknco.ui.i18n.EnStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The sentence the offline banner prints.
 *
 * It is the age that is being tested, not the wording: content of unstated age is worse than
 * an error, because nothing else on the screen tells a cook that the recipe in front of them
 * predates the correction they made last night.
 */
class OfflineBannerTest {

    private val now = 1_000_000L

    @Test
    fun a_store_that_has_never_been_written_says_so() {
        assertEquals(
            EnStrings.offlineShowingSavedUnknown,
            offlineBannerText(EnStrings, syncedAt = 0, now = now),
            "rather than claiming an age of nearly sixty years",
        )
    }

    @Test
    fun a_recent_sync_reads_as_just_now() {
        assertEquals(EnStrings.offlineShowingSaved("just now"), offlineBannerText(EnStrings, now - 60, now))
    }

    @Test
    fun hours_and_days_are_counted_coarsely() {
        assertEquals(EnStrings.offlineShowingSaved("an hour ago"), offlineBannerText(EnStrings, now - 3_600, now))
        assertEquals(EnStrings.offlineShowingSaved("5 hours ago"), offlineBannerText(EnStrings, now - 18_000, now))
        assertEquals(EnStrings.offlineShowingSaved("yesterday"), offlineBannerText(EnStrings, now - 90_000, now))
        assertEquals(EnStrings.offlineShowingSaved("3 days ago"), offlineBannerText(EnStrings, now - 260_000, now))
    }

    /** A clock that has gone backwards must not print a negative age. */
    @Test
    fun a_sync_in_the_future_reads_as_just_now() {
        assertEquals(EnStrings.offlineShowingSaved("just now"), offlineBannerText(EnStrings, now + 5_000, now))
    }
}
