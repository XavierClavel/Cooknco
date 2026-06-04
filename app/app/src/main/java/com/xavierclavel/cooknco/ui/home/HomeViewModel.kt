package com.xavierclavel.cooknco.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val formatter = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val groups = LinkedHashMap<String, MutableList<RecipeOverview>>()

        for (recipe in recipes) {
            val recipeMs = recipe.creationDate * 1000
            val recipeDay = Calendar.getInstance().apply {
                timeInMillis = recipeMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffDays = ((today.timeInMillis - recipeDay.timeInMillis) / 86_400_000L).toInt()
            val dayOfWeek = recipeDay.get(Calendar.DAY_OF_WEEK)

            val label = when {
                diffDays == 0 -> "Today"
                diffDays == 1 -> "Yesterday"
                diffDays in 2..6 -> dayName(dayOfWeek)
                else -> formatter.format(Date(recipeMs))
            }

            groups.getOrPut(label) { mutableListOf() }.add(recipe)
        }

        return groups.map { (label, list) -> DateGroup(label, list) }
    }

    private fun dayName(dayOfWeek: Int) = when (dayOfWeek) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> "Sunday"
    }

    companion object {
        fun factory(context: Context, userId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenDataStore = TokenDataStore(context.applicationContext)
                    val recipeApi = RecipeApi(ApiClient.httpClient)
                    val repo = RecipeRepository(recipeApi, tokenDataStore)
                    return HomeViewModel(repo, userId) as T
                }
            }
    }
}
