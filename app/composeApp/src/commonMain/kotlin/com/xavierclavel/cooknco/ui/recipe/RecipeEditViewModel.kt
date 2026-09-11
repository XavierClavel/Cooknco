package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.RecipeIngredientSaveDto
import com.xavierclavel.cooknco.network.dto.RecipeSaveDto
import com.xavierclavel.cooknco.network.dto.UnitInfo
import com.xavierclavel.cooknco.platform.PickedImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A single row of the ingredient list: a referenced ingredient when [ingredientId] is set, a free-text one when [customName] is. */
data class EditIngredient(
    val ingredientId: Long? = null,
    val customName: String? = null,
    val ingredientName: String = "",
    val type: String = "",
    val query: String = "",
    // A fresh row carries no amount yet, and the server rejects a unit without one.
    val unit: String = "NONE",
    val amount: Float? = null,
    val complement: String = "",
    val allowedTypes: List<String> = emptyList(),
    val searchResults: List<IngredientSummary> = emptyList(),
    val showDropdown: Boolean = false,
)

data class StepItem(val id: String, val text: String)

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
    val units: List<UnitInfo> = emptyList(),
    val steps: List<StepItem> = emptyList(),
    val tips: String = "",
    val pendingImage: PickedImage? = null,
    val error: String? = null,
    val recipeId: Long? = null,
    val recipeVersion: Long? = null,
)

class RecipeEditViewModel(
    private val repo: RecipeRepository,
    private val unitRepo: UnitRepository,
    private val recipeId: Long?,
    private val userId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RecipeEditUiState(recipeId = recipeId, units = UnitRepository.DEFAULT_UNITS),
    )
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    private val searchJobs = mutableMapOf<Int, Job>()
    private var nextStepId = 0
    private fun newStepId() = "s${nextStepId++}"

    init {
        loadUnits()
        if (recipeId != null) {
            loadRecipe(recipeId)
        }
    }

    private fun loadUnits() {
        viewModelScope.launch {
            val units = unitRepo.getUnits()
            _uiState.update { it.copy(units = units) }
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
                            customName = ing.name.takeIf { ing.id == null },
                            ingredientName = ing.name,
                            type = ing.type ?: "",
                            query = ing.name,
                            unit = ing.unit,
                            amount = ing.amount,
                            complement = ing.complement ?: "",
                            allowedTypes = ing.allowedTypes,
                        )
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
                            steps = recipe.steps.map { text -> StepItem(newStepId(), text) },
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
    fun setPendingImage(image: PickedImage?) = _uiState.update { it.copy(pendingImage = image) }

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
                val row = list[index]
                list[index] = row.copy(
                    query = query,
                    ingredientId = null,
                    // A row already known to be custom keeps its name in sync with the field.
                    customName = if (row.customName == null) null else customName(query),
                    ingredientName = query,
                    showDropdown = query.isNotBlank(),
                )
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
                                list[index] = list[index].copy(searchResults = result.items, showDropdown = true)
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
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(
                    ingredientId = summary.id,
                    customName = null,
                    ingredientName = name,
                    type = summary.type,
                    query = name,
                    unit = defaultUnitFor(summary, state.units),
                    allowedTypes = summary.allowedTypes,
                    searchResults = emptyList(),
                    showDropdown = false,
                )
            }
            state.copy(ingredients = list)
        }
    }

    fun selectCustomIngredient(index: Int) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) {
                val name = customName(list[index].query)
                list[index] = list[index].copy(
                    ingredientId = null,
                    customName = name,
                    ingredientName = name,
                    query = name,
                    allowedTypes = emptyList(),
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
            if (index < list.size) {
                list[index] = list[index].copy(
                    unit = unit,
                    amount = if (unit == "NONE") null else list[index].amount,
                )
            }
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

    // Step operations
    fun addStep() = _uiState.update { it.copy(steps = it.steps + StepItem(newStepId(), "")) }

    fun removeStep(id: String) = _uiState.update { s ->
        s.copy(steps = s.steps.filter { it.id != id })
    }

    fun updateStep(id: String, text: String) = _uiState.update { s ->
        s.copy(steps = s.steps.map { if (it.id == id) it.copy(text = text) else it })
    }

    fun reorderStep(from: Int, to: Int) = _uiState.update { s ->
        s.copy(steps = s.steps.toMutableList().apply { add(to, removeAt(from)) })
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
                val customName = ing.customName?.takeIf { it.isNotBlank() }
                if (ing.ingredientId == null && customName == null) return@mapNotNull null
                RecipeIngredientSaveDto(
                    id = ing.ingredientId,
                    customName = customName.takeIf { ing.ingredientId == null },
                    unit = ing.unit,
                    amount = if (ing.unit == "NONE") null else ing.amount,
                    complement = ing.complement.ifBlank { null },
                )
            },
            steps = state.steps.filter { it.text.isNotBlank() }.map { it.text },
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
                    // The image needs a recipe id to upload against, which a brand-new
                    // recipe only gets from this save — the opposite order from a profile
                    // edit, where the id already exists going in. A failed upload here is
                    // best-effort: the recipe's own content already saved, so it doesn't
                    // block navigating on; the user can just add a photo again from Edit.
                    // Clearing it only on success keeps a later "Save draft" tap from
                    // re-uploading (and re-bumping the image version for) the same picture.
                    val imageUploaded = state.pendingImage?.let { image ->
                        repo.uploadRecipeImage(recipe.id, image.bytes, image.mimeType).isSuccess
                    } ?: false
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saved = true,
                            recipeId = recipe.id,
                            pendingImage = if (imageUploaded) null else it.pendingImage,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message) }
                }
        }
    }

    companion object {
        private const val CUSTOM_NAME_MAX_LENGTH = 50

        private fun customName(query: String) = query.trim().take(CUSTOM_NAME_MAX_LENGTH)

        private fun defaultUnitFor(summary: IngredientSummary, units: List<UnitInfo>): String =
            summary.defaultUnit
                ?: "GRAM".takeIf { "WEIGHT" in summary.allowedTypes }
                ?: units.firstOrNull { it.name != "NONE" && it.type in summary.allowedTypes }?.name
                ?: "NONE"

        fun factory(recipeId: Long?, userId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecipeEditViewModel(AppGraph.recipeRepository, AppGraph.unitRepository, recipeId, userId)
            }
        }
    }
}
