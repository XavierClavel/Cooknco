package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One line on the shopping list — a single ingredient pulled in from a recipe.
 *
 * [aisle] is the ingredient's catalogue `type` (e.g. `GRAIN`), which the screen groups
 * rows under exactly as the design's "aisle" grouping does; a custom or type-less
 * ingredient falls back to [OTHER_AISLE].
 */
data class ShoppingItem(
    val id: Long,
    val name: String,
    val amount: Float?,
    val unit: String,
    val aisle: String,
    val fromRecipe: String,
    val checked: Boolean = false,
)

data class ShoppingListUiState(
    val items: List<ShoppingItem> = emptyList(),
    val recipeCount: Int = 0,
)

/**
 * The shopping list, held in memory for the life of the process.
 *
 * There is no backend endpoint for a persisted list — see `CLAUDE.md` on scope — so this
 * is deliberately client-side and app-session-scoped only: it resets on a process death
 * and is never synced across devices. Recipe screens call [addFromRecipe]; the list is
 * exposed as a single [state] the way [AppGraph][com.xavierclavel.cooknco.di.AppGraph]'s
 * other repositories expose theirs, so [com.xavierclavel.cooknco.ui.shopping.ShoppingListScreen]
 * just collects it.
 */
class ShoppingListRepository {

    companion object {
        const val OTHER_AISLE = "Other"
    }

    private val _state = MutableStateFlow(ShoppingListUiState())
    val state: StateFlow<ShoppingListUiState> = _state.asStateFlow()

    private var nextId = 0L
    private val recipesAdded = mutableSetOf<String>()

    fun addFromRecipe(recipeTitle: String, ingredients: List<RecipeIngredientInfo>) {
        if (ingredients.isEmpty()) return
        val newItems = ingredients.map { ingredient ->
            ShoppingItem(
                id = nextId++,
                name = ingredient.name,
                amount = ingredient.amount,
                unit = ingredient.unit,
                aisle = ingredient.type?.takeIf { it.isNotBlank() } ?: OTHER_AISLE,
                fromRecipe = recipeTitle,
            )
        }
        recipesAdded += recipeTitle
        _state.update { it.copy(items = it.items + newItems, recipeCount = recipesAdded.size) }
    }

    fun setChecked(id: Long, checked: Boolean) {
        _state.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.copy(checked = checked) else it })
        }
    }

    fun clear() {
        recipesAdded.clear()
        _state.update { ShoppingListUiState() }
    }
}
