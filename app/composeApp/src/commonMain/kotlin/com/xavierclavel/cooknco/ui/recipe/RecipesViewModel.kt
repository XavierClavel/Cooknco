package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.RecipeSort
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.UserSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Everything" (all four entity types, sectioned) or one entity type at a time — see
 * `Cooknco Mobile.dc.html`, turn 5 / option `5a`, "Search — everything" and
 * "Search — recipes scope". [RECIPES] is the default so opening the screen with a blank
 * query behaves exactly as it always has; the rest are opt-in via the scope pills.
 */
enum class SearchScope(val label: String) {
    ALL("All"),
    RECIPES("Recipes"),
    USERS("Users"),
    COOKBOOKS("Books"),
    INGREDIENTS("Food"),
}

data class RecipesUiState(
    val recipes: List<RecipeOverview> = emptyList(),
    val isLoading: Boolean = true,
    val allLoaded: Boolean = false,
    val error: String? = null,
)

/**
 * One page, no further pagination — unlike [RecipesUiState] there is no infinite scroll
 * here, only a single refresh per debounced query. [count] is the real total match count
 * when the backend reports one (users, ingredients); for cookbooks — whose list endpoint
 * answers no total, the same way recipes' does not — it is the loaded page size instead.
 */
data class SimpleListUiState<T>(
    val items: List<T> = emptyList(),
    val count: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RecipesViewModel(
    private val recipeApi: RecipeApi,
    private val recipeRepository: RecipeRepository,
    private val cookbookRepository: CookbookRepository,
    private val tokenDataStore: TokenDataStore,
    initialQuery: String = "",
) : ViewModel() {

    val query = MutableStateFlow(initialQuery)
    val scope = MutableStateFlow(SearchScope.RECIPES)

    /** Sort explicitly picked from the chips, null while the default applies. */
    private val pickedSort = MutableStateFlow<RecipeSort?>(null)

    /** Sort actually sent to the backend, and shown as selected in the chips. */
    val sort: StateFlow<RecipeSort> =
        combine(query, pickedSort) { q, picked -> effectiveSort(q, picked) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, effectiveSort(initialQuery, null))

    private val _uiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = _uiState.asStateFlow()

    private val _usersState = MutableStateFlow(SimpleListUiState<UserSummary>())
    val usersState: StateFlow<SimpleListUiState<UserSummary>> = _usersState.asStateFlow()

    private val _cookbooksState = MutableStateFlow(SimpleListUiState<CookbookInfo>())
    val cookbooksState: StateFlow<SimpleListUiState<CookbookInfo>> = _cookbooksState.asStateFlow()

    private val _ingredientsState = MutableStateFlow(SimpleListUiState<IngredientSummary>())
    val ingredientsState: StateFlow<SimpleListUiState<IngredientSummary>> = _ingredientsState.asStateFlow()

    private val buffer = mutableListOf<RecipeOverview>()
    private var page = 0
    private val pageSize = 20

    // Parameters the buffered pages were loaded with: further pages must use the same
    // ones, not the current (possibly still debouncing) query.
    private var loadedQuery = initialQuery
    private var loadedSort = effectiveSort(initialQuery, null)

    init {
        viewModelScope.launch {
            // Debounce text; sort changes apply immediately
            val debouncedQuery = query.debounce(350L)
            combine(debouncedQuery, pickedSort) { q, picked -> q to effectiveSort(q, picked) }
                .distinctUntilChanged()
                .collectLatest { (q, s) -> resetAndLoad(q, s) }
        }

        // Users, cookbooks and ingredients: one page each, refreshed on every debounced
        // query change regardless of which scope is actually showing — the scope pills'
        // counts stay live no matter which tab is active, matching the mockup (every
        // pill always carries a count, not just the selected one). `collectLatest`
        // cancels these three launches together the moment a newer query comes in.
        viewModelScope.launch {
            query.debounce(350L).distinctUntilChanged().collectLatest { q ->
                launch { loadUsers(q) }
                launch { loadCookbooks(q) }
                launch { loadIngredients(q) }
            }
        }
    }

    fun onSortPicked(s: RecipeSort) {
        pickedSort.value = s
    }

    fun onScopeSelected(s: SearchScope) {
        scope.value = s
    }

    private suspend fun resetAndLoad(q: String, s: RecipeSort) {
        buffer.clear()
        page = 0
        loadedQuery = q
        loadedSort = s
        _uiState.update { it.copy(recipes = emptyList(), isLoading = true, allLoaded = false, error = null) }
        loadPage(q, s)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.allLoaded) return
        viewModelScope.launch { loadPage(loadedQuery, loadedSort) }
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

    private suspend fun loadUsers(q: String) {
        _usersState.update { it.copy(isLoading = true) }
        cookbookRepository.searchUsers(q)
            .onSuccess { result ->
                _usersState.update { it.copy(items = result.items, count = result.count, isLoading = false, error = null) }
            }
            .onFailure { err ->
                _usersState.update { it.copy(isLoading = false, error = err.message) }
            }
    }

    private suspend fun loadCookbooks(q: String) {
        _cookbooksState.update { it.copy(isLoading = true) }
        cookbookRepository.searchCookbooks(q)
            .onSuccess { results ->
                _cookbooksState.update { it.copy(items = results, count = results.size, isLoading = false, error = null) }
            }
            .onFailure { err ->
                _cookbooksState.update { it.copy(isLoading = false, error = err.message) }
            }
    }

    private suspend fun loadIngredients(q: String) {
        _ingredientsState.update { it.copy(isLoading = true) }
        recipeRepository.searchIngredients(q)
            .onSuccess { result ->
                _ingredientsState.update { it.copy(items = result.items, count = result.count, isLoading = false, error = null) }
            }
            .onFailure { err ->
                _ingredientsState.update { it.copy(isLoading = false, error = err.message) }
            }
    }

    companion object {
        /**
         * Relevance ranking only means something against a search term, so BEST_MATCH is
         * the default while searching and is unavailable when simply browsing.
         */
        private fun effectiveSort(query: String, picked: RecipeSort?): RecipeSort =
            if (query.isBlank()) {
                if (picked == null || picked == RecipeSort.BEST_MATCH) RecipeSort.RECENT else picked
            } else {
                picked ?: RecipeSort.BEST_MATCH
            }

        fun factory(initialQuery: String = ""): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecipesViewModel(
                    AppGraph.recipeApi,
                    AppGraph.recipeRepository,
                    AppGraph.cookbookRepository,
                    AppGraph.tokenDataStore,
                    initialQuery,
                )
            }
        }
    }
}
