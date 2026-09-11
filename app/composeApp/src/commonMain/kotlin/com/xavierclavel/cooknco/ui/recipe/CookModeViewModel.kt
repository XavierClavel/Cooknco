package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookModeUiState(
    val recipe: RecipeInfo? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentStep: Int = 0,
    /** Seconds the current step's text implies, or null when none could be parsed. */
    val timerTotalSeconds: Int? = null,
    val timerRemainingSeconds: Int? = null,
    val timerRunning: Boolean = false,
)

/**
 * Drives cook mode: walks a recipe's [RecipeInfo.steps] one at a time and offers a
 * best-effort countdown timer parsed out of the step text.
 *
 * [RecipeInfo] has no per-step ingredient association — only the flat [RecipeInfo.ingredients]
 * list — so this deliberately does not attempt to guess which ingredients a given step
 * uses; the screen shows the full list under a plain heading instead.
 */
class CookModeViewModel(
    private val repo: RecipeRepository,
    private val recipeId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookModeUiState())
    val uiState: StateFlow<CookModeUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getRecipe(recipeId)
                .onSuccess { recipe ->
                    _uiState.update {
                        it.copy(
                            recipe = recipe,
                            isLoading = false,
                            timerTotalSeconds = parseStepDurationSeconds(recipe.steps.getOrNull(0)),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun nextStep() {
        val state = _uiState.value
        val steps = state.recipe?.steps ?: return
        if (state.currentStep >= steps.size - 1) return
        goToStep(state.currentStep + 1)
    }

    fun previousStep() {
        val state = _uiState.value
        if (state.currentStep <= 0) return
        goToStep(state.currentStep - 1)
    }

    private fun goToStep(index: Int) {
        timerJob?.cancel()
        val steps = _uiState.value.recipe?.steps ?: return
        _uiState.update {
            it.copy(
                currentStep = index,
                timerTotalSeconds = parseStepDurationSeconds(steps.getOrNull(index)),
                timerRemainingSeconds = null,
                timerRunning = false,
            )
        }
    }

    fun toggleTimer() {
        val state = _uiState.value
        val total = state.timerTotalSeconds ?: return
        if (state.timerRunning) {
            timerJob?.cancel()
            _uiState.update { it.copy(timerRunning = false) }
            return
        }
        val start = state.timerRemainingSeconds ?: total
        _uiState.update { it.copy(timerRunning = true, timerRemainingSeconds = start) }
        timerJob = viewModelScope.launch {
            var remaining = start
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(timerRemainingSeconds = remaining) }
            }
            _uiState.update { it.copy(timerRunning = false) }
        }
    }

    companion object {
        // Best-effort — French and English, "1h30", "30 mn"/"30 min"/"30 minutes",
        // "1 h"/"1 hour"/"1 heure". Not a robust NLP parse: a step with no stated
        // duration simply gets no timer card, which is the intended fallback.
        private val hoursPattern = Regex("""(\d+)\s*(?:h|hours?|heures?)\b""", RegexOption.IGNORE_CASE)
        private val minutesPattern = Regex("""(\d+)\s*(?:mn|min(?:ute)?s?)\b""", RegexOption.IGNORE_CASE)

        internal fun parseStepDurationSeconds(step: String?): Int? {
            if (step.isNullOrBlank()) return null
            var totalSeconds = 0
            var found = false
            hoursPattern.find(step)?.let { totalSeconds += it.groupValues[1].toInt() * 3600; found = true }
            minutesPattern.find(step)?.let { totalSeconds += it.groupValues[1].toInt() * 60; found = true }
            return totalSeconds.takeIf { found }
        }

        fun factory(recipeId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { CookModeViewModel(AppGraph.recipeRepository, recipeId) }
        }
    }
}
