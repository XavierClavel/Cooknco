package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a week of dishes is laid out: which day, which meal, in what order — and what is left out
 * rather than failing the week.
 */
class MealPlanWeekTest {

    private val monday = LocalDate(2026, 10, 12)

    private fun dish(id: Long, date: String, slot: String, position: Int = 0) =
        MealPlanEntry(id = id, date = date, slot = slot, position = position, title = "dish $id")

    @Test
    fun a_week_starts_on_the_monday_of_whatever_day_it_is_asked_about() {
        assertEquals(monday, weekStartOf(monday))
        assertEquals(monday, weekStartOf(LocalDate(2026, 10, 15)))
        assertEquals(monday, weekStartOf(LocalDate(2026, 10, 18)), "Sunday ends the week, it does not start the next")
        assertEquals(LocalDate(2026, 9, 28), weekStartOf(LocalDate(2026, 10, 1)), "across a month")
    }

    @Test
    fun a_week_is_seven_days_with_its_meals_in_the_order_a_day_is_eaten_in() {
        val days = planWeek(
            monday,
            listOf(
                dish(1, "2026-10-13", "DINNER", position = 1),
                dish(2, "2026-10-13", "DINNER", position = 0),
                dish(3, "2026-10-13", "BREAKFAST"),
                dish(4, "2026-10-13", "SNACK"),
                dish(5, "2026-10-12", "LUNCH"),
            ),
        )

        assertEquals((12..18).map { LocalDate(2026, 10, it) }, days.map { it.date })
        assertEquals(listOf(MealSlot.LUNCH), days[0].meals.map { it.slot })
        val tuesday = days[1]
        assertEquals(listOf(MealSlot.BREAKFAST, MealSlot.SNACK, MealSlot.DINNER), tuesday.meals.map { it.slot })
        assertEquals(listOf(2L, 1L), tuesday.meals.last().dishes.map { it.id }, "by position, not by id")
        assertTrue(days.drop(2).all { it.meals.isEmpty() })
    }

    /**
     * A meal added after this build shipped, a day that will not parse, or one outside the week
     * — none of them may cost the cook the rest of the week.
     */
    @Test
    fun what_cannot_be_placed_is_left_out_rather_than_failing_the_week() {
        val days = planWeek(
            monday,
            listOf(
                dish(1, "2026-10-12", "BRUNCH"),
                dish(2, "12/10/2026", "LUNCH"),
                dish(3, "2026-10-19", "LUNCH"),
                dish(4, "2026-10-12", "DINNER"),
            ),
        )

        assertEquals(listOf(4L), days.flatMap { day -> day.meals.flatMap { it.dishes } }.map { it.id })
    }

    @Test
    fun a_dish_is_offered_for_the_first_main_meal_with_nothing_in_it() {
        fun nextOpen(vararg slots: String) =
            planWeek(monday, slots.mapIndexed { i, slot -> dish(i.toLong(), "2026-10-12", slot) }).first().nextOpenSlot

        assertEquals(MealSlot.LUNCH, nextOpen())
        assertEquals(MealSlot.LUNCH, nextOpen("BREAKFAST", "DINNER"))
        assertEquals(MealSlot.DINNER, nextOpen("LUNCH"))
        assertEquals(MealSlot.DINNER, nextOpen("LUNCH", "DINNER"), "dinner once both are planned")
    }
}
