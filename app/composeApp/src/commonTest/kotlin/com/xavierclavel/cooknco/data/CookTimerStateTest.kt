package com.xavierclavel.cooknco.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The arithmetic the whole feature rests on: a timer is a deadline, and everything that
 * shows one — the step, the notification, the alarm — reads it from the clock.
 *
 * Which is exactly what cannot be checked by running it, so it is checked by handing it the
 * times a phone would: a moment later, ten minutes later with the process frozen in between,
 * and long past the end.
 */
class CookTimerStateTest {

    private fun running(endsAt: Long) = CookTimerState(
        recipeId = 1L,
        recipeTitle = "Harcha",
        stepIndex = 2,
        totalSeconds = 1800,
        endsAtEpochMillis = endsAt,
        pausedRemainingSeconds = 1800,
        running = true,
    )

    @Test
    fun `a running timer is read from the clock, not counted down`() {
        val timer = running(endsAt = 100_000L + 1_800_000L)
        assertEquals(1800, timer.remainingSecondsAt(100_000L))
        // Ten minutes with nothing running to decrement anything: still correct.
        assertEquals(1200, timer.remainingSecondsAt(700_000L))
    }

    @Test
    fun `part of a second still reads as that second`() {
        val timer = running(endsAt = 4_200L)
        // 4.2s left shows 5, so the last second is shown for a second rather than a blink.
        assertEquals(5, timer.remainingSecondsAt(0L))
        assertEquals(1, timer.remainingSecondsAt(4_000L))
    }

    @Test
    fun `past the deadline never reads negative`() {
        val timer = running(endsAt = 1_000L)
        assertEquals(0, timer.remainingSecondsAt(9_999_999L))
    }

    @Test
    fun `a paused timer ignores the clock entirely`() {
        val paused = running(endsAt = 0L).copy(running = false, pausedRemainingSeconds = 42)
        assertEquals(42, paused.remainingSecondsAt(9_999_999L))
        assertFalse(paused.finished)
    }

    @Test
    fun `finished is stopped with nothing left`() {
        assertFalse(running(endsAt = 1_000L).finished)
        assertTrue(running(endsAt = 0L).copy(running = false, pausedRemainingSeconds = 0).finished)
    }

    @Test
    fun `the hour only appears once there is one`() {
        assertEquals("00:09", formatCookTimer(9))
        assertEquals("30:00", formatCookTimer(1800))
        assertEquals("59:59", formatCookTimer(3599))
        assertEquals("1:00:00", formatCookTimer(3600))
        assertEquals("1:30:05", formatCookTimer(5405))
        assertEquals("00:00", formatCookTimer(-5))
    }
}
