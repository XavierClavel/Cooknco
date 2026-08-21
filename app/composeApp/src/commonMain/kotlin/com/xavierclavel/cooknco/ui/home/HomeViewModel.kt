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

data class DateGroup(val label: String, val recipes: List<RecipeOverview>)

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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            recipeRepository.listRecipes(userId, currentPage)
                .onSuccess { items ->
                    if (items.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, allLoaded = true) }
                    } else {
                        allRecipes.addAll(items)
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
        val groups = LinkedHashMap<String, MutableList<RecipeOverview>>()

        for (recipe in recipes) {
            val recipeDay = Instant.fromEpochSeconds(recipe.creationDate)
                .toLocalDateTime(timeZone)
                .date
            val diffDays = today.toEpochDays() - recipeDay.toEpochDays()

            val label = when {
                diffDays == 0L -> "Today"
                diffDays == 1L -> "Yesterday"
                diffDays in 2L..6L -> dayName(recipeDay.dayOfWeek)
                else -> fullDateFormat.format(recipeDay)
            }

            groups.getOrPut(label) { mutableListOf() }.add(recipe)
        }

        return groups.map { (label, list) -> DateGroup(label, list) }
    }

    private fun dayName(dayOfWeek: DayOfWeek) = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        else -> "Sunday"
    }

    companion object {
        private val fullDateFormat = LocalDate.Format {
            day()
            char(' ')
            monthName(MonthNames.ENGLISH_FULL)
            char(' ')
            year()
        }

        fun factory(userId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(AppGraph.recipeRepository, userId) }
        }
    }
}
