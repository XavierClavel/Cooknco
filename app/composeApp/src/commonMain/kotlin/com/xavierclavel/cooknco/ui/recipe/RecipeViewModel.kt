package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.CookbookRecipeStatus
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
    /**
     * This cook's cookbooks and whether each already holds the recipe.
     *
     * Loaded with the recipe rather than when the sheet opens, because the bookmark in the
     * banner is drawn from it: a bookmark that only knows whether it is filled once you
     * have opened the thing it opens is not telling you anything.
     */
    val cookbooks: List<CookbookRecipeStatus> = emptyList(),
    val cookbookPicker: CookbookPickerState? = null,
) {
    /** Whether the recipe is filed anywhere, which is what the bookmark shows. */
    val isBookmarked: Boolean get() = cookbooks.any { it.hasRecipe }
}

/**
 * The "Add to a cookbook" sheet, while it is open. Null means closed.
 *
 * It holds no cookbooks of its own — those live in [RecipeUiState.cookbooks], since the
 * banner's bookmark reads them too. What is here is only true while the sheet is up:
 * whether the list is being refreshed, which rows have a request in flight, and what went
 * wrong.
 *
 * A row is shown in its *new* state immediately and reverted if the request fails, so
 * tapping several in a row does not mean waiting for each: this is a list of checkboxes,
 * not a form.
 */
data class CookbookPickerState(
    val isLoading: Boolean = true,
    val busy: Set<Long> = emptySet(),
    val error: String? = null,
)

class RecipeViewModel(
    private val repo: RecipeRepository,
    private val cookbookRepo: CookbookRepository,
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
            // Failure is silent and means "filed nowhere": signed out, or offline. The
            // bookmark is then hollow and opening it says what went wrong, which beats an
            // error banner over a recipe that otherwise loaded.
            val cookbooksDeferred = async { cookbookRepo.recipeStatusInCookbooks(recipeId) }

            val recipeResult = recipeDeferred.await()
            val likedResult = likedDeferred.await()
            val notesResult = notesDeferred.await()
            val cookbooks = cookbooksDeferred.await().getOrDefault(emptyList())

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

            _uiState.update { it.copy(cookbooks = cookbooks) }
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

    // ── Add to a cookbook ────────────────────────────────────────────────────

    fun openCookbookPicker() {
        // Opens on what was loaded with the recipe and refreshes underneath: a cookbook can
        // have been created or left since, but showing the list a moment stale beats showing
        // a spinner over one that is almost certainly right.
        val known = _uiState.value.cookbooks
        _uiState.update { it.copy(cookbookPicker = CookbookPickerState(isLoading = known.isEmpty())) }
        viewModelScope.launch {
            cookbookRepo.recipeStatusInCookbooks(recipeId)
                .onSuccess { cookbooks ->
                    _uiState.update {
                        // The list is kept either way — the bookmark reads it — but the
                        // sheet's own state is not resurrected if it has been closed.
                        it.copy(
                            cookbooks = cookbooks,
                            cookbookPicker = it.cookbookPicker?.copy(isLoading = false),
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        if (it.cookbookPicker == null) it
                        else it.copy(cookbookPicker = it.cookbookPicker.copy(isLoading = false, error = err.message))
                    }
                }
        }
    }

    fun closeCookbookPicker() {
        _uiState.update { it.copy(cookbookPicker = null) }
    }

    /**
     * Puts the recipe in the cookbook, or takes it out.
     *
     * Taking it out can be refused where putting it in cannot — the backend lets an admin,
     * or whoever added it, remove a recipe and nobody else — so the row goes back to where
     * it was and says why.
     */
    fun toggleCookbook(cookbookId: Long) {
        val state = _uiState.value
        val picker = state.cookbookPicker ?: return
        if (cookbookId in picker.busy) return
        val current = state.cookbooks.firstOrNull { it.id == cookbookId } ?: return
        val target = !current.hasRecipe

        updatePicker { it.copy(busy = it.busy + cookbookId, error = null) }
        setMembership(cookbookId, target)

        viewModelScope.launch {
            val result =
                if (target) cookbookRepo.addRecipeToCookbook(cookbookId, recipeId)
                else cookbookRepo.removeRecipeFromCookbook(cookbookId, recipeId)
            result.onFailure { err ->
                setMembership(cookbookId, !target)
                updatePicker { it.copy(error = err.message) }
            }
            updatePicker { it.copy(busy = it.busy - cookbookId) }
        }
    }

    private fun setMembership(cookbookId: Long, hasRecipe: Boolean) {
        _uiState.update { state ->
            state.copy(
                cookbooks = state.cookbooks.map {
                    if (it.id == cookbookId) it.copy(hasRecipe = hasRecipe) else it
                },
            )
        }
    }

    private fun updatePicker(block: (CookbookPickerState) -> CookbookPickerState) {
        _uiState.update { state ->
            state.cookbookPicker?.let { state.copy(cookbookPicker = block(it)) } ?: state
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
            initializer {
                RecipeViewModel(AppGraph.recipeRepository, AppGraph.cookbookRepository, recipeId, userId)
            }
        }
    }
}
