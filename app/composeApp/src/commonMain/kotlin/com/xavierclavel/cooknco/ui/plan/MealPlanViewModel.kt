package com.xavierclavel.cooknco.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.DEFAULT_MEAL_SERVINGS
import com.xavierclavel.cooknco.data.MealPlanRepository
import com.xavierclavel.cooknco.data.MealSlot
import com.xavierclavel.cooknco.data.PlannedDay
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.monthGrid
import com.xavierclavel.cooknco.data.monthOf
import com.xavierclavel.cooknco.data.planWeek
import com.xavierclavel.cooknco.data.weekStartOf
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * What a dish's sheet is editing: when, which meal, for how many, and — for a dish that is not
 * a recipe — what it is called. The sheet holds one while it is open and hands it back whole.
 */
data class MealEntryDraft(
    val date: LocalDate,
    val slot: MealSlot,
    val servings: Int?,
    val title: String,
)

/**
 * The "add a dish" sheet, while it is open.
 *
 * Opened from a day's "+", it plans on that day and no other. Opened from the header's "plan a
 * meal" ([picksDays]), the cook chooses the days in the sheet itself — any number of them, as
 * many weeks apart as they like — and one tap plans the dish on every one.
 */
data class AddDishState(
    val dates: Set<LocalDate>,
    val slot: MealSlot,
    val picksDays: Boolean = false,
    val query: String = "",
    /** The cook's own recipes, liked ones and cookbooks' that match [query]. */
    val recipes: List<RecipeOverview> = emptyList(),
    /** What they planned before as words, matching [query]. */
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** The meals already planned, by day, in the months the calendar has looked at. */
    val taken: Map<LocalDate, Set<MealSlot>> = emptyMap(),
    val loadedMonths: Set<LocalDate> = emptySet(),
)

/** What a tap in the "add a dish" sheet plans: one of the cook's recipes, or a few words. */
private sealed interface NewDish {
    data class Recipe(val recipe: RecipeOverview) : NewDish
    data class Words(val title: String) : NewDish
    data object NotCooked : NewDish
}

/** A planned dish's sheet, while it is open. */
data class EditDishState(
    /** The dish the sheet was opened on, which names the dish and the meal it is at. */
    val entry: MealPlanEntry,
    val slot: MealSlot,
    val title: String,
    /** Servings the cook set, by day. A day not in it keeps what it was planned for. */
    val servings: Map<LocalDate, Int?> = emptyMap(),
    /** What a newly ticked day is for: the count last set, or the dish's own. */
    val newDayServings: Int? = entry.servings,
    /**
     * Every day this dish is planned at its meal, in the months the calendar has looked at —
     * the plan is read a stretch at a time, so the rest arrive as the cook pages to them.
     */
    val planned: Map<LocalDate, MealPlanEntry> = emptyMap(),
    val loadedMonths: Set<LocalDate> = emptySet(),
    val isLoadingDays: Boolean = false,
    /** Days ticked that the dish is not planned on yet, and planned days unticked. */
    val added: Set<LocalDate> = emptySet(),
    val removed: Set<LocalDate> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    /** The days the dish will be on once saved. */
    val days: Set<LocalDate> get() = (planned.keys - removed) + added

    /** How many [date]'s serving is for, once saved: each day its own count. */
    fun servingsOn(date: LocalDate): Int? = when {
        date in servings -> servings[date]
        date in planned -> planned.getValue(date).servings
        else -> newDayServings
    }
}

data class MealPlanUiState(
    val today: LocalDate,
    /** The Monday of the week on screen. */
    val weekStart: LocalDate,
    val entries: List<MealPlanEntry> = emptyList(),
    val isLoading: Boolean = false,
    /** The server has answered for [weekStart], so an empty day says nothing is planned rather than nothing is known. */
    val hasLoaded: Boolean = false,
    val loadFailed: Boolean = false,
    /**
     * The weeks either side of [weekStart], asked for ahead of time so that a swipe shows the
     * week coming in rather than a spinner. Absent until they have answered.
     */
    val neighbours: Map<LocalDate, List<MealPlanEntry>> = emptyMap(),
    val adding: AddDishState? = null,
    val editing: EditDishState? = null,
) {
    val days: List<PlannedDay> get() = planWeek(weekStart, entries)

    /** What is known of the week starting on [week], or null while nothing is. */
    fun entriesOf(week: LocalDate): List<MealPlanEntry>? =
        if (week == weekStart) entries.takeIf { hasLoaded } else neighbours[week]
    val isCurrentWeek: Boolean get() = weekStart == weekStartOf(today)
}

/**
 * The meal plan tab: one week at a time, and the two sheets that change it.
 *
 * Every change is sent and then drawn from what the server answered, rather than drawn first
 * and sent after. A plan is small and the round trip is short, and the answer carries what
 * only the server knows — a planned recipe's servings when none were given, and where in its
 * meal a dish landed.
 *
 * Nothing here loads on its own: the tab asks each time it comes on screen (see
 * [MealPlanScreen]), which is also what shows a dish planned from a recipe's page a moment ago.
 */
class MealPlanViewModel(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
    private val userId: Long,
    today: LocalDate = currentDay(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealPlanUiState(today = today, weekStart = weekStartOf(today)))
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val prefetchJobs = mutableMapOf<LocalDate, Job>()

    /**
     * Asks for the week on screen again. The spinner is for a week's first answer only; after
     * that the days stay where they are while the new answer is fetched.
     */
    fun load() {
        loadJob?.cancel()
        val week = _uiState.value.weekStart
        prefetchNeighbours()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !it.hasLoaded, loadFailed = false) }
            val result = mealPlanRepository.week(week)
            _uiState.update { state ->
                // An answer for a week the cook has already swiped away from is not this week's
                if (state.weekStart != week) return@update state
                result.fold(
                    onSuccess = { state.copy(isLoading = false, hasLoaded = true, entries = it) },
                    onFailure = { state.copy(isLoading = false, loadFailed = true) },
                )
            }
        }
    }

    fun previousWeek() = showWeek(_uiState.value.weekStart.minus(7, DateTimeUnit.DAY))
    fun nextWeek() = showWeek(_uiState.value.weekStart.plus(7, DateTimeUnit.DAY))
    fun thisWeek() = showWeek(weekStartOf(_uiState.value.today))

    /** The week [date] falls in, however far from this one: what the calendar jumps to. */
    fun showWeekOf(date: LocalDate) = showWeek(weekStartOf(date))

    /**
     * Shows the week starting on [start] — straight away when it was asked for ahead, and asks
     * again either way, since what was fetched ahead may be a few moments old. The week left
     * behind is kept as a neighbour if it is one, so swiping back shows it too.
     */
    private fun showWeek(start: LocalDate) {
        if (start == _uiState.value.weekStart) return
        _uiState.update { state ->
            val known = state.neighbours + (if (state.hasLoaded) mapOf(state.weekStart to state.entries) else emptyMap())
            val entries = known[start]
            state.copy(
                weekStart = start,
                entries = entries.orEmpty(),
                hasLoaded = entries != null,
                loadFailed = false,
                neighbours = known.filterKeys { it != start && it.isNeighbourOf(start) },
            )
        }
        load()
    }

    /** Asks for the weeks either side of the one on screen, those not already known or on their way. */
    private fun prefetchNeighbours() {
        val start = _uiState.value.weekStart
        prefetchJobs.values.removeAll { !it.isActive }
        for (week in listOf(start.minus(7, DateTimeUnit.DAY), start.plus(7, DateTimeUnit.DAY))) {
            if (week in _uiState.value.neighbours || week in prefetchJobs) continue
            prefetchJobs[week] = viewModelScope.launch {
                mealPlanRepository.week(week).onSuccess { entries ->
                    _uiState.update { state ->
                        // The cook may have moved on; a week no longer beside theirs is not kept
                        if (!week.isNeighbourOf(state.weekStart)) state
                        else state.copy(neighbours = state.neighbours + (week to entries))
                    }
                }
            }
        }
    }

    /**
     * After a change, the weeks either side are asked for again: a dish planned on several days
     * or moved to next week lands in them, and showing them as they were would hide it.
     */
    private fun refreshNeighbours() {
        prefetchJobs.values.forEach { it.cancel() }
        prefetchJobs.clear()
        _uiState.update { it.copy(neighbours = emptyMap()) }
        prefetchNeighbours()
    }

    // ── Adding ────────────────────────────────────────────────────────────────

    /** The add sheet for [day], on [slot] — the empty meal the cook tapped, or the likeliest one. */
    fun openAdd(day: PlannedDay, slot: MealSlot = day.nextOpenSlot) {
        _uiState.update { it.copy(adding = AddDishState(dates = setOf(day.date), slot = slot)) }
        setQuery("")
    }

    /**
     * The header's "plan a meal": the same sheet, with the days left to the cook. It starts on
     * no day at all — any day picked for them would be a guess they have to undo.
     */
    fun openAddMeal() {
        _uiState.update { state ->
            state.copy(adding = AddDishState(dates = emptySet(), slot = MealSlot.DINNER, picksDays = true))
        }
        setQuery("")
    }

    /** Takes every picked day back out. */
    fun clearAddDates() = _uiState.update { state ->
        val adding = state.adding?.takeIf { it.picksDays && !it.isSaving } ?: return@update state
        state.copy(adding = adding.copy(dates = emptySet(), saveFailed = false))
    }

    /** Adds [date] to the days the sheet plans on, or takes it out if it was there. */
    fun toggleAddDate(date: LocalDate) = _uiState.update { state ->
        val adding = state.adding?.takeIf { it.picksDays && !it.isSaving } ?: return@update state
        val dates = if (date in adding.dates) adding.dates - date else adding.dates + date
        state.copy(adding = adding.copy(dates = dates, saveFailed = false))
    }

    /**
     * Reads which meals are already planned in the month [month] falls in, so the calendar can
     * mark them. A month already read is not asked for again while the sheet is open.
     */
    fun loadAddMonth(month: LocalDate) {
        val adding = _uiState.value.adding?.takeIf { it.picksDays } ?: return
        val first = monthOf(month)
        if (first in adding.loadedMonths) return
        val grid = monthGrid(first)
        _uiState.update { state -> state.copy(adding = state.adding?.let { it.copy(loadedMonths = it.loadedMonths + first) }) }
        viewModelScope.launch {
            val found = mealPlanRepository.between(grid.first(), grid.last())
            _uiState.update { state ->
                val current = state.adding?.takeIf { it.picksDays } ?: return@update state
                found.fold(
                    onSuccess = { entries ->
                        val taken = entries
                            .mapNotNull { e ->
                                val date = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@mapNotNull null
                                val slot = MealSlot.of(e.slot) ?: return@mapNotNull null
                                date to slot
                            }
                            .groupBy({ it.first }, { it.second })
                            .mapValues { (_, slots) -> slots.toSet() }
                        state.copy(adding = current.copy(taken = current.taken + taken))
                    },
                    // Asked again the next time the cook pages to it
                    onFailure = { state.copy(adding = current.copy(loadedMonths = current.loadedMonths - first)) },
                )
            }
        }
    }

    fun closeAdd() {
        searchJob?.cancel()
        _uiState.update { it.copy(adding = null) }
    }

    fun setAddSlot(slot: MealSlot) = _uiState.update { state -> state.copy(adding = state.adding?.copy(slot = slot)) }

    /**
     * Looks for [query] among the cook's recipes and among what they typed before, once they
     * stop typing for a moment. An empty query asks straight away: it is what the sheet opens on.
     */
    fun setQuery(query: String) {
        _uiState.update { state -> state.copy(adding = state.adding?.copy(query = query)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(SEARCH_DEBOUNCE_MILLIS)
            _uiState.update { state -> state.copy(adding = state.adding?.copy(isSearching = true)) }
            val recipes = async { recipeRepository.searchOwnCollections(userId, query.trim()) }
            val suggestions = async { mealPlanRepository.suggestions(query.trim()) }
            val foundRecipes = recipes.await().getOrDefault(emptyList())
            val foundSuggestions = suggestions.await().getOrDefault(emptyList())
            _uiState.update { state ->
                val adding = state.adding?.takeIf { it.query == query } ?: return@update state
                state.copy(adding = adding.copy(recipes = foundRecipes, suggestions = foundSuggestions, isSearching = false))
            }
        }
    }

    /**
     * For [DEFAULT_MEAL_SERVINGS], not for the recipe's yield, which is what the backend would
     * put down for a recipe sent with none.
     */
    fun addRecipe(recipe: RecipeOverview) = add(NewDish.Recipe(recipe))

    /** What was typed, as it is. */
    fun addTypedDish() {
        val title = _uiState.value.adding?.query?.trim().orEmpty()
        if (title.isEmpty()) return
        add(NewDish.Words(title))
    }

    fun addSuggestion(title: String) = add(NewDish.Words(title))

    /** Notes the meal as not cooked on the sheet's days: it fills the meal as a dish would. */
    fun addNotCooked() = add(NewDish.NotCooked)

    /** Plans [dish] on the sheet's days. With none picked, the sheet shows its dishes as not yet tappable. */
    private fun add(dish: NewDish) {
        saveNew { date, slot ->
            when (dish) {
                is NewDish.Recipe -> mealPlanRepository.planRecipe(dish.recipe.id, date, slot, servings = DEFAULT_MEAL_SERVINGS)
                is NewDish.Words -> mealPlanRepository.planDish(dish.title, date, slot, servings = DEFAULT_MEAL_SERVINGS)
                NewDish.NotCooked -> mealPlanRepository.planNotCooked(date, slot)
            }
        }
    }


    /**
     * Plans the dish on every day of the sheet, one request per day, all at once.
     *
     * The days that went through stay planned whatever happened to the others: the sheet stays
     * open on the ones that failed alone, so trying again cannot plan a dish twice on a day.
     */
    private fun saveNew(plan: suspend (LocalDate, MealSlot) -> Result<MealPlanEntry>) {
        val adding = _uiState.value.adding ?: return
        if (adding.isSaving || adding.dates.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(adding = adding.copy(isSaving = true, saveFailed = false)) }
            val results = adding.dates.sorted()
                .map { date -> date to async { plan(date, adding.slot) } }
                .map { (date, result) -> date to result.await() }
            val planned = results.mapNotNull { (_, result) -> result.getOrNull() }
            val failed = results.filter { (_, result) -> result.isFailure }.map { (date, _) -> date }.toSet()
            _uiState.update { state ->
                // Only what falls in the week on screen: the others are drawn when their week is
                val weekEnd = state.weekStart.plus(6, DateTimeUnit.DAY)
                val shown = planned.filter { entry ->
                    runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { it in state.weekStart..weekEnd } == true
                }
                val entries = state.entries + shown
                if (failed.isEmpty()) {
                    state.copy(adding = null, entries = entries)
                } else {
                    state.copy(
                        adding = state.adding?.copy(dates = failed, isSaving = false, saveFailed = true),
                        entries = entries,
                    )
                }
            }
            if (failed.isEmpty()) searchJob?.cancel()
            if (planned.isNotEmpty()) refreshNeighbours()
        }
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    /**
     * Opens a dish's sheet on every day it is served at its meal: the tapped one straight away,
     * the rest of its month as soon as the server has said.
     */
    fun openEntry(entry: MealPlanEntry) {
        val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return
        val slot = MealSlot.of(entry.slot) ?: return
        _uiState.update {
            it.copy(editing = EditDishState(entry = entry, slot = slot, title = entry.title, planned = mapOf(date to entry)))
        }
        loadEntryMonth(date)
    }

    fun closeEntry() = _uiState.update { it.copy(editing = null) }

    /**
     * Finds the dish's days in the month [month] falls in — the six weeks the calendar draws
     * for it, which one listing covers. A month already looked at is not asked for again.
     */
    fun loadEntryMonth(month: LocalDate) {
        val editing = _uiState.value.editing ?: return
        val first = monthOf(month)
        if (first in editing.loadedMonths) return
        val grid = monthGrid(first)
        _uiState.update { it.copy(editing = editing.copy(loadedMonths = editing.loadedMonths + first, isLoadingDays = true)) }
        viewModelScope.launch {
            val found = mealPlanRepository.between(grid.first(), grid.last())
            _uiState.update { state ->
                val current = state.editing?.takeIf { it.entry.id == editing.entry.id } ?: return@update state
                found.fold(
                    onSuccess = { entries ->
                        val same = entries.filter { it.isSameDishAs(current.entry) }
                            .mapNotNull { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it to e } }
                            // Two of the same dish in one meal is one day ticked; the first is the one edited
                            .groupBy({ it.first }, { it.second })
                            .mapValues { (_, list) -> list.minBy { it.position } }
                        state.copy(editing = current.copy(planned = same + current.planned, isLoadingDays = false))
                    },
                    // Asked again the next time the cook pages to it
                    onFailure = { state.copy(editing = current.copy(loadedMonths = current.loadedMonths - first, isLoadingDays = false)) },
                )
            }
        }
    }

    /** Ticks [date] for the dish, or unticks it. */
    fun toggleEntryDate(date: LocalDate) = updateEditing { e ->
        when {
            date in e.added -> e.copy(added = e.added - date)
            date in e.removed -> e.copy(removed = e.removed - date)
            date in e.planned -> e.copy(removed = e.removed + date)
            else -> e.copy(added = e.added + date)
        }
    }

    fun setEntrySlot(slot: MealSlot) = updateEditing { it.copy(slot = slot) }
    /** Sets how many [date]'s serving is for; the days ticked after it start from the same count. */
    fun setEntryServings(date: LocalDate, servings: Int?) = updateEditing {
        it.copy(servings = it.servings + (date to servings), newDayServings = servings)
    }
    fun setEntryTitle(title: String) = updateEditing { it.copy(title = title) }

    private fun updateEditing(change: (EditDishState) -> EditDishState) = _uiState.update { state ->
        val editing = state.editing?.takeIf { !it.isSaving } ?: return@update state
        state.copy(editing = change(editing).copy(saveFailed = false))
    }

    /**
     * Saves the sheet: plans the dish on the days ticked, takes it off the days unticked, and
     * carries a new meal or name to every day it stays on, and each day's own servings.
     *
     * Only what the cook changed is sent: a day whose count, meal and name are as they were is
     * left alone, and the name is only sent when it changed on a dish shown as words — the
     * backend lets go of an out-of-reach recipe on a rename, which a save that only moved the
     * dish must not do by accident.
     */
    fun saveEntry() {
        val editing = _uiState.value.editing ?: return
        if (editing.isSaving) return
        val entry = editing.entry
        val originalSlot = MealSlot.of(entry.slot)
        val named = entry.recipe == null && entry.hasCooked
        val renamed = editing.title.trim().takeIf { named && it.isNotEmpty() && it != entry.title }
        val slotChanged = editing.slot != originalSlot
        if (named && editing.title.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(editing = editing.copy(isSaving = true, saveFailed = false)) }
            val removals = editing.removed.mapNotNull { editing.planned[it] }.map { e -> async { mealPlanRepository.remove(e.id) } }
            val updates = (editing.planned - editing.removed).values
                .filter { e ->
                    val date = LocalDate.parse(e.date)
                    slotChanged || renamed != null || editing.servingsOn(date) != e.servings
                }
                .map { e ->
                    async {
                        val date = LocalDate.parse(e.date)
                        mealPlanRepository.update(e.id, date, editing.slot, editing.servingsOn(date), renamed)
                    }
                }
            val title = renamed ?: entry.title
            val additions = editing.added.map { date ->
                async {
                    val recipe = entry.recipe
                    when {
                        !entry.hasCooked -> mealPlanRepository.planNotCooked(date, editing.slot)
                        recipe != null -> mealPlanRepository.planRecipe(recipe.id, date, editing.slot, editing.servingsOn(date))
                        else -> mealPlanRepository.planDish(title, date, editing.slot, editing.servingsOn(date))
                    }
                }
            }
            val removedIds = editing.removed.mapNotNull { editing.planned[it]?.id }
                .zip(removals.map { it.await() })
                .filter { (_, result) -> result.isSuccess }
                .map { (id, _) -> id }
                .toSet()
            val changed = (updates + additions).mapNotNull { it.await().getOrNull() }
            val failed = removedIds.size < removals.size || changed.size < updates.size + additions.size
            // Drawn from the answers straight away, rather than after the week is asked again
            _uiState.update { state ->
                val weekEnd = state.weekStart.plus(6, DateTimeUnit.DAY)
                val inWeek = changed.filter { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it in state.weekStart..weekEnd } == true }
                val changedIds = changed.map { it.id }.toSet()
                state.copy(entries = state.entries.filterNot { it.id in removedIds || it.id in changedIds } + inWeek)
            }
            if (failed) {
                // What went through is kept; the sheet starts over from what the plan now says
                _uiState.update { it.copy(editing = editing.copy(planned = emptyMap(), loadedMonths = emptySet(), added = emptySet(), removed = emptySet(), saveFailed = true)) }
                editing.loadedMonths.forEach { loadEntryMonth(it) }
            } else {
                _uiState.update { it.copy(editing = null) }
            }
            load()
            refreshNeighbours()
        }
    }

    /** Takes the dish off every day the sheet knows it is on. */
    fun removeEntry() {
        val editing = _uiState.value.editing ?: return
        if (editing.isSaving) return
        _uiState.update { it.copy(editing = editing.copy(removed = editing.planned.keys, added = emptySet())) }
        saveEntry()
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L

        fun factory(userId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { MealPlanViewModel(AppGraph.mealPlanRepository, AppGraph.recipeRepository, userId) }
        }
    }
}

/**
 * The same dish at the same meal: the same recipe, or — for a dish shown as words — the same
 * words. A recipe gone out of reach comes back as words, so it is matched by its title too.
 */
private fun MealPlanEntry.isSameDishAs(other: MealPlanEntry): Boolean {
    if (slot != other.slot) return false
    // Not cooking is one thing whatever day it falls on, and never a dish
    if (!hasCooked || !other.hasCooked) return !hasCooked && !other.hasCooked
    val mine = recipe
    val theirs = other.recipe
    return if (mine != null || theirs != null) mine?.id == theirs?.id
    else title.trim().equals(other.title.trim(), ignoreCase = true)
}

private fun LocalDate.isNeighbourOf(week: LocalDate): Boolean = abs(week.daysUntil(this)) == 7

/** Today, on the phone's calendar — which is the one a meal is planned on. */
fun currentDay(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
