package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Which meal of the day a dish is for — the backend's `shared.enums.MealSlot`, in the order a
 * day is eaten in, which is the order the planner draws them.
 */
enum class MealSlot {
    BREAKFAST,
    LUNCH,
    SNACK,
    DINNER;

    companion object {
        /** Null for a meal this build does not know, which is then left out of the week rather than failing it. */
        fun of(name: String): MealSlot? = entries.firstOrNull { it.name == name }
    }
}

/** The Monday of the week [date] falls in. A plan is read a week at a time, Monday first. */
fun weekStartOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/** The [count] days from [start], in order. */
fun daysFrom(start: LocalDate, count: Int): List<LocalDate> = (0 until count).map { start.plus(it, DateTimeUnit.DAY) }

/** The dishes of one meal, in the order they were planned. */
data class PlannedMeal(val slot: MealSlot, val dishes: List<MealPlanEntry>)

/** One day of the week, with only the meals that have something in them. */
data class PlannedDay(val date: LocalDate, val meals: List<PlannedMeal>) {

    /**
     * The meal a dish added to this day is offered for: the first of lunch and dinner that has
     * nothing in it yet, and dinner when both do. Breakfast and the afternoon snack are a tap
     * away in the sheet; the two main meals are what most days are planned around, so filling
     * the gap is the guess that is right most often.
     */
    val nextOpenSlot: MealSlot
        get() = listOf(MealSlot.LUNCH, MealSlot.DINNER).firstOrNull { slot -> meals.none { it.slot == slot } }
            ?: MealSlot.DINNER
}

/**
 * The seven days from [weekStart], each with its meals in the order a day is eaten in, and
 * each meal's dishes in the order they were planned.
 *
 * A dish dated outside the week, on a day that will not parse, or for a meal this build does
 * not know is left out: the week shows what it can place, rather than nothing.
 */
fun planWeek(weekStart: LocalDate, entries: List<MealPlanEntry>): List<PlannedDay> {
    val placed = entries.mapNotNull { entry ->
        val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@mapNotNull null
        val slot = MealSlot.of(entry.slot) ?: return@mapNotNull null
        Triple(date, slot, entry)
    }
    return daysFrom(weekStart, 7).map { date ->
        val ofTheDay = placed.filter { it.first == date }
        PlannedDay(
            date = date,
            meals = MealSlot.entries.mapNotNull { slot ->
                ofTheDay.filter { it.second == slot }
                    .map { it.third }
                    .sortedWith(compareBy({ it.position }, { it.id }))
                    .takeIf { it.isNotEmpty() }
                    ?.let { PlannedMeal(slot, it) }
            },
        )
    }
}
