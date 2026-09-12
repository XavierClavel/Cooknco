package com.xavierclavel.cooknco.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a day is called is the UI's business — see [DateGroupKey]. */
data class DateGroup(val key: DateGroupKey, val recipes: List<RecipeOverview>)

/** When a group of recipes was posted, before anybody has put it into words. */
sealed interface DateGroupKey {
    data object Today : DateGroupKey
    data object Yesterday : DateGroupKey
    data class Weekday(val dayOfWeek: DayOfWeek) : DateGroupKey
    data class On(val date: LocalDate) : DateGroupKey
}

data class HomeUiState(
    val dateGroups: List<DateGroup> = emptyList(),
    val isLoading: Boolean = false,
    val allLoaded: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val recipeRepository: RecipeRepository,
    private val userId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val allRecipes = mutableListOf<RecipeOverview>()
    private var currentPage = 0

    init {
        loadMore()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.allLoaded) return
        // Marked before the coroutine starts: two scroll-triggered calls in the same frame
        // would both pass the guard above and both append the same page otherwise.
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            recipeRepository.listRecipes(userId, currentPage)
                .onSuccess { items ->
                    if (items.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, allLoaded = true) }
                    } else {
                        // Offset paging over a list that changes repeats rows; the feed
                        // keys its items by recipe id, and a repeat there is a crash.
                        val known = allRecipes.mapTo(HashSet()) { it.id }
                        allRecipes.addAll(items.filterNot { it.id in known })
                        currentPage++
                        _uiState.update { it.copy(
                            isLoading = false,
                            dateGroups = groupByDate(allRecipes),
                        ) }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    private fun groupByDate(recipes: List<RecipeOverview>): List<DateGroup> {
        val timeZone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(timeZone).date
        val groups = LinkedHashMap<DateGroupKey, MutableList<RecipeOverview>>()

        for (recipe in recipes) {
            val recipeDay = Instant.fromEpochSeconds(recipe.creationDate)
                .toLocalDateTime(timeZone)
                .date
            val diffDays = today.toEpochDays() - recipeDay.toEpochDays()

            // A key, not a label: the feed is regrouped by *when*, and what that is
            // called depends on a language that can change while the screen is open.
            val key = when {
                diffDays == 0L -> DateGroupKey.Today
                diffDays == 1L -> DateGroupKey.Yesterday
                diffDays in 2L..6L -> DateGroupKey.Weekday(recipeDay.dayOfWeek)
                else -> DateGroupKey.On(recipeDay)
            }

            groups.getOrPut(key) { mutableListOf() }.add(recipe)
        }

        return groups.map { (key, list) -> DateGroup(key, list) }
    }

    companion object {
        fun factory(userId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(AppGraph.recipeRepository, userId) }
        }
    }
}
