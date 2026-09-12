package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.RecipeSort
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IngredientUiState(
    val ingredient: IngredientSummary? = null,
    /** This cook's own recipes built on it, newest first. */
    val mine: List<RecipeOverview> = emptyList(),
    /** Everyone's, most liked first — the same query without the owner. */
    val popular: List<RecipeOverview> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Backs [IngredientScreen].
 *
 * The three requests go out together rather than in sequence: none of them needs an answer
 * from another, and the page is worth nothing until all three are back.
 *
 * "Popular with this" is asked for without an owner and sorted by likes — it is the same
 * `RecipeFilter.ingredient` query as "in your recipes", which is why neither needed a new
 * endpoint. It can therefore repeat a recipe already shown above; the screen drops those,
 * since a list called "popular with this" that is mostly your own is not telling you
 * anything you did not know.
 */
class IngredientViewModel(
    private val repo: RecipeRepository,
    private val ingredientId: Long,
    private val currentUserId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IngredientUiState())
    val uiState: StateFlow<IngredientUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val ingredient = async { repo.getIngredient(ingredientId) }
            val mine = async { repo.recipesWithIngredient(ingredientId, ownerId = currentUserId) }
            val popular = async { repo.recipesWithIngredient(ingredientId, sort = RecipeSort.MOST_LIKED) }

            val ingredientResult = ingredient.await()
            val mineList = mine.await().getOrDefault(emptyList())
            val popularList = popular.await().getOrDefault(emptyList())
            val mineIds = mineList.mapTo(HashSet()) { it.id }

            ingredientResult
                .onSuccess { found ->
                    _uiState.update {
                        it.copy(
                            ingredient = found,
                            mine = mineList,
                            popular = popularList.filterNot { recipe -> recipe.id in mineIds },
                            isLoading = false,
                        )
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    companion object {
        fun factory(ingredientId: Long, currentUserId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { IngredientViewModel(AppGraph.recipeRepository, ingredientId, currentUserId) }
        }
    }
}
