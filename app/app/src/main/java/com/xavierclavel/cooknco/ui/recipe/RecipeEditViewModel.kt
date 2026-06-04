package com.xavierclavel.cooknco.ui.recipe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.CustomIngredientSaveDto
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.RecipeIngredientSaveDto
import com.xavierclavel.cooknco.network.dto.RecipeSaveDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditIngredient(
    val ingredientId: Long? = null,
    val ingredientName: String = "",
    val query: String = "",
    val unit: String = "UNIT",
    val amount: Float? = null,
    val complement: String = "",
    val allowAmount: Boolean = true,
    val allowWeight: Boolean = true,
    val allowVolume: Boolean = true,
    val searchResults: List<IngredientSummary> = emptyList(),
    val showDropdown: Boolean = false,
)

data class EditCustomIngredient(
    val name: String = "",
    val unit: String = "UNIT",
    val amount: Float? = null,
)

data class RecipeEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val title: String = "",
    val description: String = "",
    val dishClass: String = "MAIN_DISH",
    val yield: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val cookTemp: String = "",
    val ingredients: List<EditIngredient> = emptyList(),
    val customIngredients: List<EditCustomIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val tips: String = "",
    val error: String? = null,
    val recipeId: Long? = null,
    val recipeVersion: Long? = null,
)

class RecipeEditViewModel(
    private val repo: RecipeRepository,
    private val recipeId: Long?,
    private val userId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeEditUiState(recipeId = recipeId))
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    private val searchJobs = mutableMapOf<Int, Job>()

    init {
        if (recipeId != null) {
            loadRecipe(recipeId)
        }
    }

    private fun loadRecipe(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getRecipe(id)
                .onSuccess { recipe ->
                    val editIngredients = recipe.ingredients.map { ing ->
                        EditIngredient(
                            ingredientId = ing.id,
                            ingredientName = ing.name,
                            query = ing.name,
                            unit = ing.unit,
                            amount = ing.amount,
                            complement = ing.complement ?: "",
                            allowAmount = ing.allowAmount,
                            allowWeight = ing.allowWeight,
                            allowVolume = ing.allowVolume,
                        )
                    }
                    val editCustomIngredients = recipe.customIngredients.map { ci ->
                        EditCustomIngredient(name = ci.name, unit = ci.unit, amount = ci.amount)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = recipe.title,
                            description = recipe.description,
                            dishClass = recipe.dishClass,
                            yield = recipe.yield?.toString() ?: "",
                            prepTime = recipe.preparationTime?.toString() ?: "",
                            cookTime = recipe.cookingTime?.toString() ?: "",
                            cookTemp = recipe.cookingTemperature?.toString() ?: "",
                            ingredients = editIngredients,
                            customIngredients = editCustomIngredients,
                            steps = recipe.steps.toMutableList(),
                            tips = recipe.tips,
                            recipeId = recipe.id,
                            recipeVersion = recipe.version,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun updateDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun updateDishClass(value: String) = _uiState.update { it.copy(dishClass = value) }
    fun updateYield(value: String) = _uiState.update { it.copy(yield = value) }
    fun updatePrepTime(value: String) = _uiState.update { it.copy(prepTime = value) }
    fun updateCookTime(value: String) = _uiState.update { it.copy(cookTime = value) }
    fun updateCookTemp(value: String) = _uiState.update { it.copy(cookTemp = value) }
    fun updateTips(value: String) = _uiState.update { it.copy(tips = value) }

    // Ingredient operations
    fun addIngredient() {
        _uiState.update { it.copy(ingredients = it.ingredients + EditIngredient()) }
    }

    fun removeIngredient(index: Int) {
        searchJobs[index]?.cancel()
        searchJobs.remove(index)
        _uiState.update { state ->
            state.copy(ingredients = state.ingredients.toMutableList().also { it.removeAt(index) })
        }
    }

    fun updateIngredientQuery(index: Int, query: String) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(query = query, ingredientId = null, ingredientName = query, showDropdown = query.isNotBlank())
            }
            state.copy(ingredients = list)
        }
        searchJobs[index]?.cancel()
        if (query.isNotBlank()) {
            searchJobs[index] = viewModelScope.launch {
                delay(300)
                repo.searchIngredients(query)
                    .onSuccess { result ->
                        _uiState.update { state ->
                            val list = state.ingredients.toMutableList()
                            if (index < list.size) {
                                list[index] = list[index].copy(searchResults = result.items, showDropdown = result.items.isNotEmpty())
                            }
                            state.copy(ingredients = list)
                        }
                    }
            }
        } else {
            _uiState.update { state ->
                val list = state.ingredients.toMutableList()
                if (index < list.size) {
                    list[index] = list[index].copy(searchResults = emptyList(), showDropdown = false)
                }
                state.copy(ingredients = list)
            }
        }
    }

    fun selectIngredient(index: Int, summary: IngredientSummary) {
        val name = summary.name["EN"] ?: summary.name.values.firstOrNull() ?: ""
        val defaultUnit = when {
            summary.allowAmount -> "UNIT"
            summary.allowWeight -> "GRAM"
            summary.allowVolume -> "MILLILITERS"
            else -> "NONE"
        }
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(
                    ingredientId = summary.id,
                    ingredientName = name,
                    query = name,
                    unit = defaultUnit,
                    allowAmount = summary.allowAmount,
                    allowWeight = summary.allowWeight,
                    allowVolume = summary.allowVolume,
                    searchResults = emptyList(),
                    showDropdown = false,
                )
            }
            state.copy(ingredients = list)
        }
    }

    fun updateIngredientUnit(index: Int, unit: String) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(unit = unit)
            state.copy(ingredients = list)
        }
    }

    fun updateIngredientAmount(index: Int, amount: String) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(amount = amount.toFloatOrNull())
            state.copy(ingredients = list)
        }
    }

    fun updateIngredientComplement(index: Int, complement: String) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(complement = complement)
            state.copy(ingredients = list)
        }
    }

    fun dismissDropdown(index: Int) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(showDropdown = false)
            state.copy(ingredients = list)
        }
    }

    // Custom ingredient operations
    fun addCustomIngredient() {
        _uiState.update { it.copy(customIngredients = it.customIngredients + EditCustomIngredient()) }
    }

    fun removeCustomIngredient(index: Int) {
        _uiState.update { state ->
            state.copy(customIngredients = state.customIngredients.toMutableList().also { it.removeAt(index) })
        }
    }

    fun updateCustomIngredientName(index: Int, name: String) {
        _uiState.update { state ->
            val list = state.customIngredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(name = name)
            state.copy(customIngredients = list)
        }
    }

    fun updateCustomIngredientUnit(index: Int, unit: String) {
        _uiState.update { state ->
            val list = state.customIngredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(unit = unit)
            state.copy(customIngredients = list)
        }
    }

    fun updateCustomIngredientAmount(index: Int, amount: String) {
        _uiState.update { state ->
            val list = state.customIngredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(amount = amount.toFloatOrNull())
            state.copy(customIngredients = list)
        }
    }

    // Step operations
    fun addStep() {
        _uiState.update { it.copy(steps = it.steps + "") }
    }

    fun removeStep(index: Int) {
        _uiState.update { state ->
            state.copy(steps = state.steps.toMutableList().also { it.removeAt(index) })
        }
    }

    fun updateStep(index: Int, text: String) {
        _uiState.update { state ->
            val list = state.steps.toMutableList()
            if (index < list.size) list[index] = text
            state.copy(steps = list)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "Title is required") }
            return
        }

        val dto = RecipeSaveDto(
            title = state.title.trim(),
            description = state.description.trim(),
            dishClass = state.dishClass,
            yield = state.yield.toIntOrNull(),
            preparationTime = state.prepTime.toIntOrNull(),
            cookingTime = state.cookTime.toIntOrNull(),
            cookingTemperature = state.cookTemp.toIntOrNull(),
            ingredients = state.ingredients.mapNotNull { ing ->
                val id = ing.ingredientId ?: return@mapNotNull null
                RecipeIngredientSaveDto(
                    id = id,
                    unit = ing.unit,
                    amount = ing.amount,
                    complement = ing.complement.ifBlank { null },
                )
            },
            customIngredients = state.customIngredients
                .filter { it.name.isNotBlank() }
                .map { ci ->
                    CustomIngredientSaveDto(name = ci.name, unit = ci.unit, amount = ci.amount)
                },
            steps = state.steps.filter { it.isNotBlank() },
            tips = state.tips.trim(),
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = if (state.recipeId != null) {
                repo.updateRecipe(state.recipeId, dto)
            } else {
                repo.createRecipe(dto)
            }
            result
                .onSuccess { recipe ->
                    _uiState.update { it.copy(isSaving = false, saved = true, recipeId = recipe.id) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message) }
                }
        }
    }

    companion object {
        fun factory(context: Context, recipeId: Long?, userId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenDataStore = TokenDataStore(context.applicationContext)
                    val recipeApi = RecipeApi(ApiClient.httpClient)
                    val repo = RecipeRepository(recipeApi, tokenDataStore)
                    return RecipeEditViewModel(repo, recipeId, userId) as T
                }
            }
    }
}
