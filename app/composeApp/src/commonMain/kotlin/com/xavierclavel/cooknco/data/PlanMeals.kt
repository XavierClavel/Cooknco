package com.xavierclavel.cooknco.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Which meals the meal plan offers to plan: lunch and dinner, and — when the cook asks for them
 * in the settings — breakfast and the afternoon snack too.
 *
 * Off by default: most days are planned around the two main meals, and two more slots on every
 * day are noise for anybody who does not plan the others. It only decides what is *offered*. A
 * breakfast already planned is shown whatever this says, because hiding a meal somebody put
 * down would read as losing it.
 *
 * Held here rather than read per screen, like [AppUnits]: the tab, its sheets and the recipe
 * page's "add to meal plan" all draw the same meal chips, and they must agree. It is this
 * handset's choice ([DevicePreferences]), restored once at launch.
 */
object PlanMeals {

    private val _showsBreakfastAndSnack = MutableStateFlow(false)
    val showsBreakfastAndSnack: StateFlow<Boolean> = _showsBreakfastAndSnack.asStateFlow()

    /** Writes the choice to disk on a scope that outlives the settings screen, as [AppUnits] does. */
    private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Called once, from the app's entry point. */
    suspend fun restore(preferences: DevicePreferences) {
        _showsBreakfastAndSnack.value = preferences.showsBreakfastAndSnack.first()
    }

    fun set(shows: Boolean, preferences: DevicePreferences) {
        _showsBreakfastAndSnack.value = shows
        persistence.launch { preferences.setShowsBreakfastAndSnack(shows) }
    }

    /** The meals offered to plan, in the order a day is eaten in. */
    fun slots(showsBreakfastAndSnack: Boolean): List<MealSlot> =
        if (showsBreakfastAndSnack) MealSlot.entries else listOf(MealSlot.LUNCH, MealSlot.DINNER)
}

/** [PlanMeals.slots] for the current setting, following it as it changes. */
@Composable
fun plannableMealSlots(): List<MealSlot> {
    val shows by PlanMeals.showsBreakfastAndSnack.collectAsState()
    return PlanMeals.slots(shows)
}
