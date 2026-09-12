package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeUiState(
    val recipe: RecipeInfo? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isLiked: Boolean = false,
    val selectedYield: Int = 1,
    val notes: String = "",
    val remoteNotes: String? = null,
    val isEditingNotes: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val deleted: Boolean = false,
)

class RecipeViewModel(
    private val repo: RecipeRepository,
    private val recipeId: Long,
    private val currentUserId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeUiState())
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    val isOwner: Boolean
        get() = _uiState.value.recipe?.owner?.id == currentUserId

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val recipeDeferred = async { repo.getRecipe(recipeId) }
            val likedDeferred = async { repo.isLiked(recipeId) }
            val notesDeferred = async { repo.getNotes(recipeId) }

            val recipeResult = recipeDeferred.await()
            val likedResult = likedDeferred.await()
            val notesResult = notesDeferred.await()

            recipeResult
                .onSuccess { recipe ->
                    _uiState.update { state ->
                        state.copy(
                            recipe = recipe,
                            isLoading = false,
                            selectedYield = recipe.yield ?: 1,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }

            likedResult.onSuccess { liked ->
                _uiState.update { it.copy(isLiked = liked) }
            }

            notesResult.onSuccess { notes ->
                _uiState.update { it.copy(notes = notes ?: "", remoteNotes = notes) }
            }
        }
    }

    /**
     * Flips the heart and the count together, before the request goes out.
     *
     * The count matters as much as the heart: it is the only thing on the screen that says
     * the tap did anything to the recipe rather than to the button, and nothing reloads the
     * recipe afterwards. Both halves are put back if the call fails.
     */
    fun toggleLike() {
        val currentlyLiked = _uiState.value.isLiked
        _uiState.update { it.copy(isLiked = !currentlyLiked, recipe = it.recipe?.withLikeDelta(if (currentlyLiked) -1 else 1)) }
        viewModelScope.launch {
            val result = if (currentlyLiked) repo.removeLike(recipeId) else repo.addLike(recipeId)
            result.onFailure {
                _uiState.update {
                    it.copy(isLiked = currentlyLiked, recipe = it.recipe?.withLikeDelta(if (currentlyLiked) 1 else -1))
                }
            }
        }
    }

    private fun RecipeInfo.withLikeDelta(delta: Int) =
        copy(likesCount = (likesCount + delta).coerceAtLeast(0))

    fun setYield(n: Int) {
        if (n >= 1) {
            _uiState.update { it.copy(selectedYield = n) }
        }
    }

    fun startEditNotes() {
        _uiState.update { it.copy(isEditingNotes = true) }
    }

    fun cancelNoteEdit() {
        _uiState.update { it.copy(isEditingNotes = false, notes = it.remoteNotes ?: "") }
    }

    fun updateNotes(text: String) {
        _uiState.update { it.copy(notes = text) }
    }

    fun saveNotes() {
        val notesText = _uiState.value.notes
        val isCreate = _uiState.value.remoteNotes == null
        viewModelScope.launch {
            repo.saveNotes(recipeId, notesText, isCreate)
                .onSuccess { saved ->
                    _uiState.update { it.copy(isEditingNotes = false, remoteNotes = saved, notes = saved) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun confirmDelete() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun deleteRecipe() {
        viewModelScope.launch {
            repo.deleteRecipe(recipeId)
                .onSuccess {
                    _uiState.update { it.copy(deleted = true, showDeleteConfirm = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, showDeleteConfirm = false) }
                }
        }
    }

    companion object {
        fun factory(recipeId: Long, userId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeViewModel(AppGraph.recipeRepository, recipeId, userId) }
        }
    }
}
