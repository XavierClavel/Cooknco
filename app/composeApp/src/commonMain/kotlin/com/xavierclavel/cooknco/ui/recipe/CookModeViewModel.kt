package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.network.dto.RecipeStepIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeStepInfo
import com.xavierclavel.cooknco.network.dto.UnitInfo
import com.xavierclavel.cooknco.data.StepDurations
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.AppUnitSystem
import com.xavierclavel.cooknco.data.AppUnits
import com.xavierclavel.cooknco.data.CookSession
import com.xavierclavel.cooknco.data.CookSessionState
import com.xavierclavel.cooknco.data.CookSessionStep
import com.xavierclavel.cooknco.data.CookTimer
import com.xavierclavel.cooknco.data.CookTimerState
import com.xavierclavel.cooknco.data.DevicePreferences
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.platform.canRingTimersExactly
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.stringsFor
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
    /**
     * How many portions the cook is cooking, which every amount on this screen is scaled to.
     * Carried from the recipe screen, and the recipe's own yield when it says nothing.
     */
    val servings: Int = 0,
    /**
     * What has been ticked off, by step and then by the ingredient's position within it.
     *
     * Kept per step rather than per ingredient because the same ingredient can be used by two
     * steps — 60 g of the butter now and 40 g later is two separate things to do, and ticking
     * the first must not cross the second off a step the cook has not reached.
     *
     * It survives moving between steps: reading ahead and coming back is the ordinary way to
     * use this screen, and losing the ticks for it would make them useless.
     *
     * This is a copy. The ticks themselves live on the session ([CookSessionStep.checked]),
     * because the notification has the same boxes and a broadcast with no screen behind it is
     * what toggles half of them. They are still a checklist for one pass at one pan: the
     * session ends when cook mode is left, which is exactly when this used to be forgotten.
     */
    val checkedIngredients: Map<Int, Set<Int>> = emptyMap(),
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
 * Nor is the step. It is [CookSession]'s, for the same reason and one more: the notification
 * this screen puts in the shade is not a readout of the step but a second way of walking it,
 * so the cook can move through the whole recipe with the app closed. Which makes the step a
 * thing two places can change, and one place has to hold — see [observeSession].
 *
 * [RecipeInfo] has no per-step ingredient association — only the flat [RecipeInfo.ingredients]
 * list — so this deliberately does not attempt to guess which ingredients a given step
 * uses; the screen shows the full list under a plain heading instead.
 */
class CookModeViewModel(
    private val repo: RecipeRepository,
    private val cookTimer: CookTimer,
    private val cookSession: CookSession,
    private val devicePreferences: DevicePreferences,
    private val recipeId: Long,
    servings: Int,
) : ViewModel() {

    // Seeded with the portions the cook already chose on the recipe screen, if they did.
    private val _uiState = MutableStateFlow(CookModeUiState(servings = servings.coerceAtLeast(0)))
    val uiState: StateFlow<CookModeUiState> = _uiState.asStateFlow()

    init {
        load()
        observeTimer()
        observeSession()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getRecipe(recipeId)
                .onSuccess { recipe ->
                    // Where to open. Both stores are read back first — each of these waits
                    // for its own restore — so the answer does not depend on which of two
                    // disk reads won a race against the network.
                    val resumed = cookSession.current()?.takeIf { it.recipeId == recipeId }?.stepIndex
                        ?: cookTimer.current()?.takeIf { it.recipeId == recipeId }?.stepIndex
                    _uiState.update {
                        // Clamped, because a session or a timer from days ago can name a step
                        // an edit has since removed.
                        val step = (resumed ?: it.currentStep)
                            .coerceIn(0, recipe.steps.lastIndex.coerceAtLeast(0))
                        it.copy(
                            recipe = recipe,
                            isLoading = false,
                            // The recipe's own yield unless the cook already picked a number
                            // on the recipe screen and brought it here.
                            servings = it.servings.takeIf { chosen -> chosen > 0 }
                                ?: recipe.yield?.takeIf { y -> y > 0 } ?: 1,
                            currentStep = step,
                            stepDurationSeconds = stepDurationSeconds(recipe.steps.getOrNull(step)),
                        )
                    }
                    // Opening cook mode is what puts the recipe in the shade, and closing it
                    // is what takes it away ([onCleared]). The steps are handed over written
                    // out, portions and units and all, because what redraws them later has
                    // neither — see [CookSession].
                    val opened = _uiState.value
                    cookSession.startOrResume(
                        recipeId = recipe.id,
                        recipeTitle = recipe.title,
                        steps = sessionSteps(
                            recipe = recipe,
                            servings = opened.servings,
                            system = AppUnits.system.value,
                            units = AppUnits.catalog.value,
                            s = stringsFor(AppLanguage.current.value),
                        ),
                        stepIndex = opened.currentStep,
                    )
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    /**
     * The other end of the notification: a step moved from the shade moves this screen.
     *
     * Both directions matter, and they are the same step. A cook presses Next on the phone
     * lying on the worktop, picks the phone up a minute later, and the screen they open is on
     * the step the notification is showing — anything else would be two answers to where they
     * are.
     *
     * A session on another recipe is ignored rather than followed: starting one replaces
     * whatever was there, so this screen only ever hears about its own.
     */
    private fun observeSession() {
        viewModelScope.launch {
            cookSession.state.collect { session ->
                val current = session?.takeIf { it.recipeId == recipeId } ?: return@collect
                val step = current.stepIndex
                // The ticks come back with it: a box filled in the shade fills in here, and
                // reopening cook mode restores what was ticked before it was closed.
                val ticks = current.steps.mapIndexed { index, it -> index to it.checked }
                    .filter { (_, checked) -> checked.isNotEmpty() }
                    .toMap()
                _uiState.update { state ->
                    if (state.currentStep == step && state.checkedIngredients == ticks) return@update state
                    state.copy(
                        currentStep = step,
                        stepDurationSeconds =
                            if (state.recipe == null) state.stepDurationSeconds
                            else stepDurationSeconds(state.recipe.steps.getOrNull(step)),
                        checkedIngredients = ticks,
                    )
                }
            }
        }
    }

    /**
     * A timer set on another recipe is another recipe's business: it keeps running and keeps
     * its notification, but this screen does not offer to pause something the cook opened a
     * different recipe to get away from.
     *
     * Which step to *open* on is settled in [load], where the session and the timer are both
     * read back before anything is drawn. It used to be settled here, because on a cold start
     * — which a notification tap usually is — the timer was still being read off the disk when
     * the recipe arrived; awaiting the read instead makes the rule an ordering rather than a
     * race, which matters now that two things can answer it.
     */
    private fun observeTimer() {
        viewModelScope.launch {
            combine(cookTimer.state, cookTimer.remainingSeconds) { timer, remaining ->
                timer?.takeIf { it.recipeId == recipeId } to remaining
            }.collect { (timer, remaining) ->
                _uiState.update { it.copy(timer = timer, timerRemainingSeconds = remaining) }
            }
        }
    }

    /**
     * The ingredients this step uses, paired with the recipe line each one refers to.
     *
     * A position that names no line is dropped: an ingredient can be deleted from a recipe
     * after a step was told to use it, and the server drops those links on the next save
     * rather than refusing the save.
     */
    fun stepIngredients(state: CookModeUiState): List<Pair<RecipeStepIngredientInfo, RecipeIngredientInfo>> {
        val recipe = state.recipe ?: return emptyList()
        val step = recipe.steps.getOrNull(state.currentStep) ?: return emptyList()
        // Blanks resolved here, over the whole recipe - see [RecipeInfo.blankStepAmounts].
        val blanks = recipe.blankStepAmounts()
        return step.ingredients.mapNotNull { used ->
            recipe.ingredients.getOrNull(used.index)?.let {
                used.copy(amount = used.amount ?: blanks[used.index]) to it
            }
        }
    }

    /**
     * Ticks an ingredient of the step in front of the cook off, or back on.
     *
     * [position] is where the row sits in the step, not which of the recipe's ingredients it
     * is. That is what the notification's boxes send back too, and the two lists are built by
     * the same rule from the same step — so a box ticked in the shade is the box that fills
     * in here, and the other way round.
     */
    fun toggleIngredientChecked(position: Int) {
        val state = _uiState.value
        val step = state.currentStep
        val ticked = state.checkedIngredients[step].orEmpty()
        _uiState.update {
            it.copy(
                checkedIngredients = it.checkedIngredients + (
                    step to (if (position in ticked) ticked - position else ticked + position)
                    ),
            )
        }
        cookSession.toggleIngredient(recipeId, step, position)
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
        _uiState.update {
            it.copy(
                currentStep = index,
                stepDurationSeconds = stepDurationSeconds(steps.getOrNull(index)),
            )
        }
        // And the notification follows, so the two never disagree about where the cook is.
        // It names the recipe: a session the cook finished from the shade while this screen
        // was still open is not one a tap here should bring back. See [CookSession.goToStep].
        cookSession.goToStep(recipeId, index)
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
     * Leaving cook mode is what ends the session, and with it the recipe in the shade.
     *
     * Here rather than on the close button because there are several ways out — the cross,
     * Finish on the last step, the system back gesture — and only one of them is a button.
     * What they have in common is this view model being cleared, which is also what does
     * *not* happen when the app is merely backgrounded or its process reclaimed: the cook who
     * puts the phone down mid-recipe keeps the notification, which is the whole point of it.
     *
     * The timer is deliberately left running. A recipe finished with something still in the
     * oven is the ordinary case, and the timer says which step it belongs to.
     */
    override fun onCleared() {
        // Named, because by the time this runs the cook may already have started cooking
        // something else. See [CookSession.stopIfCooking].
        cookSession.stopIfCooking(recipeId)
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

        /**
         * The recipe as the notification will say it: every step, with its ingredients
         * already scaled to the portions being cooked and written on the reader's own ladder.
         *
         * Rendered on the way *in* to a session because this is the last place that has all
         * of it — the recipe, the portions, the unit catalogue and the language. A broadcast
         * receiver woken an hour later to draw step nine has none of them, which is why it is
         * handed words. See [CookSessionState].
         *
         * Everything it is given is a parameter rather than read from the ambient state, so
         * that the same rules a screen renders under can be handed to it in a test.
         */
        internal fun sessionSteps(
            recipe: RecipeInfo,
            servings: Int,
            system: AppUnitSystem,
            units: List<UnitInfo>,
            s: Strings,
        ): List<CookSessionStep> {
            val blanks = recipe.blankStepAmounts()
            return recipe.steps.map { step ->
                CookSessionStep(
                    text = step.text,
                    ingredients = step.ingredients.mapNotNull { used ->
                        // A position naming no line is dropped, as on the screen: an
                        // ingredient can be deleted after a step was told to use it.
                        recipe.ingredients.getOrNull(used.index)?.let { ingredient ->
                            // No amount is a real answer — two steps sharing an ingredient
                            // without saying how it divides — and it is checked before the
                            // label rather than after it, because [amountLabel] writes down a
                            // bare unit when it has no number to put in front of it. "Butter"
                            // is a working line; "g Butter" is not. See [blankStepAmounts].
                            val amount = (used.amount ?: blanks[used.index])?.let {
                                amountLabel(
                                    amount = it,
                                    unit = ingredient.unit,
                                    selectedYield = servings,
                                    recipeYield = recipe.yield ?: 1,
                                    system = system,
                                    units = units,
                                    s = s,
                                )
                            }
                            if (amount.isNullOrEmpty()) ingredient.name else "$amount ${ingredient.name}"
                        }
                    },
                    durationSeconds = stepDurationSeconds(step),
                )
            }
        }

        fun factory(recipeId: Long, servings: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CookModeViewModel(
                    repo = AppGraph.recipeRepository,
                    cookTimer = AppGraph.cookTimer,
                    cookSession = AppGraph.cookSession,
                    devicePreferences = AppGraph.devicePreferences,
                    recipeId = recipeId,
                    servings = servings,
                )
            }
        }
    }
}
