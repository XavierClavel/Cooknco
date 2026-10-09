package com.xavierclavel.cooknco.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.MealPlanRepository
import com.xavierclavel.cooknco.data.MealSlot
import com.xavierclavel.cooknco.data.PlannedDay
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.planWeek
import com.xavierclavel.cooknco.data.weekStartOf
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import com.xavierclavel.cooknco.network.dto.RecipeOverview
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
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

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

/** The "add a dish" sheet, while it is open. */
data class AddDishState(
    val date: LocalDate,
    val slot: MealSlot,
    val query: String = "",
    /** The cook's own recipes, liked ones and cookbooks' that match [query]. */
    val recipes: List<RecipeOverview> = emptyList(),
    /** What they planned before as words, matching [query]. */
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** A planned dish's sheet, while it is open. */
data class EditDishState(
    val entry: MealPlanEntry,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

data class MealPlanUiState(
    val today: LocalDate,
    /** The Monday of the week on screen. */
    val weekStart: LocalDate,
    val entries: List<MealPlanEntry> = emptyList(),
    val isLoading: Boolean = false,
    /** The server has answered for [weekStart], so an empty day says nothing is planned rather than nothing is known. */
    val hasLoaded: Boolean = false,
    val loadFailed: Boolean = false,
    val adding: AddDishState? = null,
    val editing: EditDishState? = null,
) {
    val days: List<PlannedDay> get() = planWeek(weekStart, entries)
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

    /**
     * Asks for the week on screen again. The spinner is for a week's first answer only; after
     * that the days stay where they are while the new answer is fetched.
     */
    fun load() {
        loadJob?.cancel()
        val week = _uiState.value.weekStart
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

    private fun showWeek(start: LocalDate) {
        if (start == _uiState.value.weekStart) return
        _uiState.update { it.copy(weekStart = start, entries = emptyList(), hasLoaded = false, loadFailed = false) }
        load()
    }

    // ── Adding ────────────────────────────────────────────────────────────────

    fun openAdd(day: PlannedDay) {
        _uiState.update { it.copy(adding = AddDishState(date = day.date, slot = day.nextOpenSlot)) }
        setQuery("")
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

    fun addRecipe(recipe: RecipeOverview) = saveNew { date, slot ->
        mealPlanRepository.planRecipe(recipe.id, date, slot, servings = null)
    }

    /** What was typed, as it is. */
    fun addTypedDish() {
        val title = _uiState.value.adding?.query?.trim().orEmpty()
        if (title.isEmpty()) return
        saveNew { date, slot -> mealPlanRepository.planDish(title, date, slot, servings = null) }
    }

    fun addSuggestion(title: String) = saveNew { date, slot ->
        mealPlanRepository.planDish(title, date, slot, servings = null)
    }

    private fun saveNew(plan: suspend (LocalDate, MealSlot) -> Result<MealPlanEntry>) {
        val adding = _uiState.value.adding ?: return
        if (adding.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(adding = adding.copy(isSaving = true, saveFailed = false)) }
            plan(adding.date, adding.slot)
                .onSuccess { entry ->
                    searchJob?.cancel()
                    _uiState.update { it.copy(adding = null, entries = it.entries + entry) }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(adding = state.adding?.copy(isSaving = false, saveFailed = true)) }
                }
        }
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    fun openEntry(entry: MealPlanEntry) = _uiState.update { it.copy(editing = EditDishState(entry)) }

    fun closeEntry() = _uiState.update { it.copy(editing = null) }

    /**
     * Saves the sheet. The name is only sent when it changed on a dish shown as words: the
     * backend lets go of an out-of-reach recipe on a rename, which a save that only moved the
     * dish must not do by accident.
     */
    fun saveEntry(draft: MealEntryDraft) = changeEntry { entry ->
        val renamed = draft.title.trim().takeIf { entry.recipe == null && it != entry.title }
        mealPlanRepository.update(entry.id, draft.date, draft.slot, draft.servings, renamed)
            .map { updated -> { entries: List<MealPlanEntry> -> entries.map { if (it.id == updated.id) updated else it } } }
    }

    fun removeEntry() = changeEntry { entry ->
        mealPlanRepository.remove(entry.id)
            .map { { entries: List<MealPlanEntry> -> entries.filterNot { it.id == entry.id } } }
    }

    private fun changeEntry(change: suspend (MealPlanEntry) -> Result<(List<MealPlanEntry>) -> List<MealPlanEntry>>) {
        val editing = _uiState.value.editing ?: return
        if (editing.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(editing = editing.copy(isSaving = true, saveFailed = false)) }
            change(editing.entry)
                .onSuccess { apply -> _uiState.update { it.copy(editing = null, entries = apply(it.entries)) } }
                .onFailure {
                    _uiState.update { state -> state.copy(editing = state.editing?.copy(isSaving = false, saveFailed = true)) }
                }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L

        fun factory(userId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { MealPlanViewModel(AppGraph.mealPlanRepository, AppGraph.recipeRepository, userId) }
        }
    }
}

/** Today, on the phone's calendar — which is the one a meal is planned on. */
fun currentDay(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
