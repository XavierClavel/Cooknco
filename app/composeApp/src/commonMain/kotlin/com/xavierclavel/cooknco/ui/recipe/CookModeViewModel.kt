package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.network.dto.RecipeStepInfo
import com.xavierclavel.cooknco.data.StepDurations
import com.xavierclavel.cooknco.data.CookTimer
import com.xavierclavel.cooknco.data.CookTimerState
import com.xavierclavel.cooknco.data.DevicePreferences
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.platform.canRingTimersExactly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookModeUiState(
    val recipe: RecipeInfo? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentStep: Int = 0,
    /** Seconds the current step's text implies, or null when none could be parsed. */
    val stepDurationSeconds: Int? = null,
    /** The one timer, while it belongs to this recipe. Null when there is none to show. */
    val timer: CookTimerState? = null,
    /** What that timer's clock reads right now. */
    val timerRemainingSeconds: Int = 0,
    /** Whether to ask, right now, for the permission that makes the timer punctual. */
    val askToRingOnTime: Boolean = false,
) {
    /**
     * The timer the step in front of the cook actually has.
     *
     * A timer belongs to the step that started it and is shown there and nowhere else. It
     * keeps counting while the cook reads ahead — that is the point of it — but the card is
     * how a step says "this step has a timer", and a card that followed the reader would say
     * that of every step. Where it says so instead is the notification, which is where
     * somebody who has walked away from the phone is looking anyway.
     */
    val stepTimer: CookTimerState? get() = timer?.takeIf { it.stepIndex == currentStep }
}

/**
 * Drives cook mode: walks a recipe's [RecipeInfo.steps] one at a time and offers a
 * best-effort countdown timer parsed out of the step text.
 *
 * The timer itself is not held here. It lives in [CookTimer], for the whole process, because
 * it has to keep counting with this screen closed, the app backgrounded, or the process gone
 * — which is the one thing a kitchen timer is for. What this view model does is decide
 * *which* timer the step in front of the cook should show: the live one whenever there is
 * one, and otherwise whatever duration this step's text mentions, not yet started.
 *
 * [RecipeInfo] has no per-step ingredient association — only the flat [RecipeInfo.ingredients]
 * list — so this deliberately does not attempt to guess which ingredients a given step
 * uses; the screen shows the full list under a plain heading instead.
 */
class CookModeViewModel(
    private val repo: RecipeRepository,
    private val cookTimer: CookTimer,
    private val devicePreferences: DevicePreferences,
    private val recipeId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CookModeUiState())
    val uiState: StateFlow<CookModeUiState> = _uiState.asStateFlow()

    /**
     * Whether the step to open on is still an open question.
     *
     * It is answered once, by the first timer this recipe turns out to have, and then never
     * again — a later answer would be the timer dragging the cook back off a step they had
     * walked to themselves.
     */
    private var openingStepDecided = false

    init {
        load()
        observeTimer()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getRecipe(recipeId)
                .onSuccess { recipe ->
                    _uiState.update {
                        // Clamped, because the step may have been chosen before the recipe
                        // arrived — see [observeTimer] — and a timer set days ago can name a
                        // step an edit since has removed.
                        val step = it.currentStep.coerceIn(0, recipe.steps.lastIndex.coerceAtLeast(0))
                        it.copy(
                            recipe = recipe,
                            isLoading = false,
                            currentStep = step,
                            stepDurationSeconds = stepDurationSeconds(recipe.steps.getOrNull(step)),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    /**
     * A timer set on another recipe is another recipe's business: it keeps running and keeps
     * its notification, but this screen does not offer to pause something the cook opened a
     * different recipe to get away from.
     *
     * This is also where the opening step is settled. Cook mode opens on the step the running
     * timer belongs to, which is the only answer that makes sense from either direction: a
     * tap on the timer's notification means "show me that", and reopening a recipe with
     * something already resting means the cook is not at the beginning of it. It has to
     * happen here rather than in [load] because on a cold start — which a notification tap
     * usually is — the timer is still being read back off the disk when the recipe arrives.
     */
    private fun observeTimer() {
        viewModelScope.launch {
            combine(cookTimer.state, cookTimer.remainingSeconds) { timer, remaining ->
                timer?.takeIf { it.recipeId == recipeId } to remaining
            }.collect { (timer, remaining) ->
                _uiState.update { state ->
                    val step = if (!openingStepDecided && timer != null) {
                        openingStepDecided = true
                        timer.stepIndex.coerceAtLeast(0)
                    } else {
                        state.currentStep
                    }
                    state.copy(
                        currentStep = step,
                        // Left alone until the recipe is here to derive it from; [load]
                        // sets it, and clamps the step, the moment it is.
                        stepDurationSeconds =
                            if (state.recipe == null) state.stepDurationSeconds
                            else stepDurationSeconds(state.recipe.steps.getOrNull(step)),
                        timer = timer,
                        timerRemainingSeconds = remaining,
                    )
                }
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

    /**
     * Moving on no longer cancels the timer. Reading ahead while something rests is the whole
     * shape of cooking, and the timer that is running says which step it belongs to.
     */
    private fun goToStep(index: Int) {
        val steps = _uiState.value.recipe?.steps ?: return
        // The cook has chosen a step, so the timer no longer gets to. See [observeTimer].
        openingStepDecided = true
        _uiState.update {
            it.copy(
                currentStep = index,
                stepDurationSeconds = stepDurationSeconds(steps.getOrNull(index)),
            )
        }
    }

    /**
     * The card's one button: pause or resume the timer that is there, or start the one this
     * step implies. Starting replaces whatever was running — see [CookTimer].
     */
    fun toggleTimer() {
        val state = _uiState.value
        if (state.stepTimer != null) {
            cookTimer.toggle()
            return
        }
        val recipe = state.recipe ?: return
        val seconds = state.stepDurationSeconds ?: return
        askToRingOnTimeIfNeeded()
        cookTimer.start(
            recipeId = recipe.id,
            recipeTitle = recipe.title,
            stepIndex = state.currentStep,
            stepText = recipe.steps.getOrNull(state.currentStep)?.text.orEmpty(),
            seconds = seconds,
        )
    }

    /** Puts the card back to this step's own duration, and takes the notification down. */
    fun stopTimer() {
        cookTimer.stop()
    }

    /**
     * Asks, the first time a timer is started on a platform that will not ring it on time.
     *
     * Here rather than anywhere earlier because this is the moment it means something: the
     * cook has just asked for a timer, so a question about timers explains itself. Asked
     * once and remembered — a refusal is an answer, and re-asking it on every timer would
     * make the feature worse than the shortcoming it is about.
     */
    private fun askToRingOnTimeIfNeeded() {
        if (canRingTimersExactly()) return
        viewModelScope.launch {
            if (devicePreferences.exactAlarmsAsked.first()) return@launch
            _uiState.update { it.copy(askToRingOnTime = true) }
        }
    }

    /** Closes that question, whichever way it was answered, and does not raise it again. */
    fun dismissRingOnTimeAsk() {
        _uiState.update { it.copy(askToRingOnTime = false) }
        viewModelScope.launch { devicePreferences.setExactAlarmsAsked() }
    }

    companion object {
        /**
         * What this step's timer is set to, or what its wording implies if it has none.
         *
         * The duration is the author's now - set or corrected in the editor and saved with
         * the step - so the stored value wins. Reading the text is what is left for recipes
         * written before steps had the field, and for anything written elsewhere; it is the
         * same rule the editor applies, so the two never disagree about the same words.
         */
        internal fun stepDurationSeconds(step: RecipeStepInfo?): Int? =
            step?.durationSeconds ?: StepDurations.parseSeconds(step?.text)

        fun factory(recipeId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CookModeViewModel(
                    repo = AppGraph.recipeRepository,
                    cookTimer = AppGraph.cookTimer,
                    devicePreferences = AppGraph.devicePreferences,
                    recipeId = recipeId,
                )
            }
        }
    }
}
