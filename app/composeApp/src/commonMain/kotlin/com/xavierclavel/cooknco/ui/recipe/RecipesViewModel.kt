package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.RecipeSort
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipesUiState(
    val recipes: List<RecipeOverview> = emptyList(),
    val isLoading: Boolean = true,
    val allLoaded: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RecipesViewModel(
    private val recipeApi: RecipeApi,
    private val tokenDataStore: TokenDataStore,
    initialQuery: String = "",
) : ViewModel() {

    val query = MutableStateFlow(initialQuery)
    val sort = MutableStateFlow(RecipeSort.RECENT)

    private val _uiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = _uiState.asStateFlow()

    private val buffer = mutableListOf<RecipeOverview>()
    private var page = 0
    private val pageSize = 20

    init {
        viewModelScope.launch {
            // Debounce text; sort changes apply immediately
            val debouncedQuery = query.debounce(350L)
            combine(debouncedQuery, sort) { q, s -> q to s }
                .collectLatest { (q, s) -> resetAndLoad(q, s) }
        }
    }

    private suspend fun resetAndLoad(q: String, s: RecipeSort) {
        buffer.clear()
        page = 0
        _uiState.update { it.copy(recipes = emptyList(), isLoading = true, allLoaded = false, error = null) }
        loadPage(q, s)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.allLoaded) return
        viewModelScope.launch { loadPage(query.value, sort.value) }
    }

    private suspend fun loadPage(q: String, s: RecipeSort) {
        _uiState.update { it.copy(isLoading = true) }
        runCatching {
            val token = tokenDataStore.tokenFlow.first()
            recipeApi.searchRecipes(token = token, query = q, sort = s, page = page, size = pageSize)
        }.onSuccess { results ->
            buffer.addAll(results)
            page++
            _uiState.update {
                it.copy(
                    recipes = buffer.toList(),
                    isLoading = false,
                    allLoaded = results.size < pageSize,
                )
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isLoading = false, error = err.message) }
        }
    }

    companion object {
        fun factory(initialQuery: String = ""): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipesViewModel(AppGraph.recipeApi, AppGraph.tokenDataStore, initialQuery) }
        }
    }
}
