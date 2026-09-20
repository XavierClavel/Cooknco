package com.xavierclavel.cooknco.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where the notification's buttons land, and what its tick boxes remember.
 *
 * Worth pinning down here rather than on a device, because this is the part that has to be
 * right *without* one: a session is moved by a process with no recipe, no network and nothing
 * drawn, over a recipe that may have been edited since it was written down.
 */
class CookSessionStateTest {

    private val session = CookSessionState(
        recipeId = 7L,
        recipeTitle = "Harcha",
        stepIndex = 0,
        steps = listOf(
            CookSessionStep(
                text = "Mix the semolina with the butter",
                ingredients = listOf("250 g Semolina", "100 g Butter"),
                durationSeconds = 300,
            ),
            CookSessionStep(text = "Shape into patties"),
            CookSessionStep(text = "Brown them in a dry pan"),
        ),
    )

    @Test
    fun `a step out of range is clamped rather than refused`() {
        // A session written down yesterday can name a step an edit has since removed, and the
        // buttons in the shade are drawn from a state that may be a redraw behind the taps.
        assertEquals(2, session.at(9).stepIndex)
        assertEquals(0, session.at(-3).stepIndex)
        assertEquals(0, session.copy(steps = emptyList()).at(4).stepIndex)
    }

    @Test
    fun `the ends of the recipe are where the buttons stop`() {
        assertTrue(session.at(0).isFirstStep)
        assertFalse(session.at(0).isLastStep)
        assertTrue(session.at(2).isLastStep)
        assertFalse(session.at(2).isFirstStep)
    }

    @Test
    fun `a tick goes on and comes off, and belongs to its own step`() {
        val step = session.steps[0]
        assertEquals(setOf(1), step.toggled(1).checked)
        assertEquals(emptySet(), step.toggled(1).toggled(1).checked)
        assertEquals(setOf(0, 1), step.toggled(1).toggled(0).checked)
    }

    @Test
    fun `reopening a recipe keeps the ticks on the lines that still read the same`() {
        val before = listOf(
            CookSessionStep(
                text = "Mix the semolina with the butter",
                ingredients = listOf("250 g Semolina", "100 g Butter"),
                checked = setOf(0, 1),
            ),
        )
        // The recipe re-rendered: the butter was edited, the semolina was not.
        val after = listOf(
            CookSessionStep(
                text = "Mix the semolina with the butter",
                ingredients = listOf("250 g Semolina", "120 g Butter"),
            ),
        )
        // A line whose words changed is not obviously the line that was ticked, and a wrongly
        // kept tick says an ingredient went in when it did not.
        assertEquals(setOf(0), after.carryTicksFrom(before).single().checked)
    }

    @Test
    fun `ticks are not carried onto a step that has since been removed`() {
        val before = listOf(
            CookSessionStep(text = "One", ingredients = listOf("A"), checked = setOf(0)),
            CookSessionStep(text = "Two", ingredients = listOf("B"), checked = setOf(0)),
        )
        val after = listOf(CookSessionStep(text = "Two", ingredients = listOf("B")))
        // Position is all there is to go on, so step two's tick is simply not step one's.
        assertEquals(emptySet(), after.carryTicksFrom(before).single().checked)
    }

    @Test
    fun `a step with more portions cooked keeps no tick it cannot vouch for`() {
        val before = listOf(CookSessionStep(ingredients = listOf("250 g Semolina"), checked = setOf(0)))
        val after = listOf(CookSessionStep(ingredients = listOf("500 g Semolina")))
        assertEquals(emptySet(), after.carryTicksFrom(before).single().checked)
    }
}
