package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.platform.onCookSessionChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * A recipe being cooked, as something outside the app can both show and drive.
 *
 * The whole recipe is carried here — every step, its ingredients already written out, its
 * duration — rather than an id to look one up by. That is the same choice
 * [CookTimerState.stepText] makes, for a sharper reason: what moves this is a notification
 * action, and a notification action routinely lands on a process Android started for that
 * broadcast alone. It has no recipe, no session and no network, and it has to redraw the
 * next step before it is allowed to stop running. Anything it would have to fetch is
 * something the cook would watch fail.
 *
 * The ingredient lines are *words*, not amounts, for the same reason once removed. An amount
 * is scaled to the portions the cook chose and converted onto their ladder before it can be
 * written down ([com.xavierclavel.cooknco.ui.recipe.amountLabel]), which needs the unit
 * catalogue and the language — neither of which that process has resolved. So they are
 * rendered once, on the screen that has all of it, and stored as the line the cook reads.
 */
@Serializable
data class CookSessionState(
    val recipeId: Long,
    val recipeTitle: String,
    val stepIndex: Int = 0,
    val steps: List<CookSessionStep> = emptyList(),
) {
    val stepCount: Int get() = steps.size
    val step: CookSessionStep? get() = steps.getOrNull(stepIndex)
    val isFirstStep: Boolean get() = stepIndex <= 0
    val isLastStep: Boolean get() = stepIndex >= steps.lastIndex

    /**
     * The same session, on another step, clamped.
     *
     * Clamped rather than refused because the step asked for can be out of range honestly: a
     * session written down yesterday names a step an edit has since removed, and the buttons
     * in the shade are drawn from a state that may be one redraw behind the taps.
     */
    fun at(index: Int): CookSessionState =
        copy(stepIndex = index.coerceIn(0, steps.lastIndex.coerceAtLeast(0)))
}

/** One step of a session, with everything shown about it already written out. */
@Serializable
data class CookSessionStep(
    val text: String = "",
    /** Whole lines, amounts and all — "120 g Flour". See [CookSessionState]. */
    val ingredients: List<String> = emptyList(),
    val durationSeconds: Int? = null,
    /**
     * Which of those lines have gone into the pan, by position in [ingredients].
     *
     * By position rather than by the recipe's ingredient id, because the same ingredient can
     * be used twice by one step — and because position is the one thing the screen and the
     * notification are certain to agree on: both take the step's ingredients in order and
     * drop the same unresolvable ones.
     *
     * Ticks live here, in the session, rather than in the screen that used to hold them. They
     * have to: what toggles one may be a broadcast arriving on a process with no screens at
     * all. It costs them nothing — a session ends when cook mode is left, which is exactly
     * when the screen used to forget them, so they still last one pass at one pan and no
     * longer.
     */
    val checked: Set<Int> = emptySet(),
) {
    fun toggled(position: Int): CookSessionStep =
        copy(checked = if (position in checked) checked - position else checked + position)
}

/**
 * The ticks of [previous] carried onto a freshly rendered set of steps.
 *
 * Reopening cook mode renders the recipe again — it may have been edited, the portions
 * changed, the units switched — and the ticks are the one thing on a session that is the
 * cook's rather than the recipe's, so they have to survive that.
 *
 * A tick is carried only where the line it was on still reads exactly the same. Anything else
 * is guesswork: a row whose words changed is not obviously the row that was ticked, and a
 * wrongly kept tick says an ingredient went in when it did not — which is the one mistake this
 * checklist exists to prevent.
 */
internal fun List<CookSessionStep>.carryTicksFrom(previous: List<CookSessionStep>): List<CookSessionStep> =
    mapIndexed { index, step ->
        val before = previous.getOrNull(index) ?: return@mapIndexed step
        step.copy(
            checked = step.ingredients.indices
                .filterTo(mutableSetOf()) { it in before.checked && before.ingredients.getOrNull(it) == step.ingredients[it] }
        )
    }

/**
 * The one recipe being cooked, held for the whole process rather than by the screen showing it.
 *
 * Shaped like [CookTimer], and for the same reasons: one at a time, every mutation moves the
 * state, writes it down and tells the platform ([onCookSessionChanged]), and writing it down
 * is what lets the notification outlive the process that posted it. A cook who put the phone
 * on the worktop half an hour ago presses Next on a notification with nothing behind it any
 * more — whichever entry point arrives first reads the session back and carries on from it.
 *
 * Unlike the timer it has no deadline and nothing to catch up on. A session is *where the
 * cook is*, which changes only when somebody says so.
 */
class CookSession(
    private val store: CookSessionStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val _state = MutableStateFlow<CookSessionState?>(null)

    /** The session, or null when no recipe is being followed. */
    val state: StateFlow<CookSessionState?> = _state.asStateFlow()

    private val restoreLock = Mutex()
    private var restored = false

    /** Reads back a session left behind by an earlier process. See [CookTimer.restore]. */
    fun restore() {
        scope.launch { ensureRestored() }
    }

    private suspend fun ensureRestored() {
        if (restored) return
        restoreLock.withLock {
            if (restored) return
            restored = true
            val saved = store.read() ?: return
            _state.value = saved
            // Re-announced, like the timer's: the notification normally outlives its process,
            // but a force-stop takes it away and the session on disk is still the truth. The
            // id is fixed and the post is silent, so this either changes nothing or repairs it.
            onCookSessionChanged(saved)
        }
    }

    /** The session as it stands, read back off the disk first. See [CookTimer.current]. */
    suspend fun current(): CookSessionState? {
        ensureRestored()
        return _state.value
    }

    /**
     * Opens [recipeId] in the shade, keeping the cook's place if this is the recipe they were
     * already on.
     *
     * Keeping the place is what makes reopening cook mode — from the app, or from a tap on
     * the notification — land where the cook is rather than at step one. The steps are taken
     * again either way, because the recipe may have been edited, the portions changed or the
     * units switched since. What survives that is what belongs to the cook rather than to the
     * recipe: [CookSessionState.stepIndex], clamped, and the ticks — see [carryTicksFrom].
     */
    fun startOrResume(
        recipeId: Long,
        recipeTitle: String,
        steps: List<CookSessionStep>,
        stepIndex: Int,
    ): Job = mutate {
        if (steps.isEmpty()) return@mutate
        val current = _state.value?.takeIf { it.recipeId == recipeId }
        apply(
            CookSessionState(
                recipeId = recipeId,
                recipeTitle = recipeTitle,
                steps = current?.let { steps.carryTicksFrom(it.steps) } ?: steps,
            ).at(current?.stepIndex ?: stepIndex)
        )
    }

    /**
     * Moves the session, from the screen.
     *
     * It names the recipe it believes it is driving, and does nothing when that is not the
     * one being cooked. Two cases need that: cook mode left open on a recipe the cook has
     * since finished from the shade, and a screen whose session has been replaced by another
     * recipe's. Neither should move somebody else's session.
     */
    fun goToStep(recipeId: Long, index: Int): Job = mutate {
        val current = _state.value?.takeIf { it.recipeId == recipeId } ?: return@mutate
        apply(current.at(index))
    }

    /**
     * Ticks one of the current step's ingredients off, or back on.
     *
     * Named by *position in the step*, which is what both ends of this have in common. The
     * notification sends the row the cook pressed; the screen sends the row it drew. Neither
     * sends an ingredient id, because a step can use the same ingredient twice.
     *
     * The step is named too, rather than assumed to be the current one: a tick from a screen
     * is about the step that screen is showing, and the notification can have moved on
     * between the press and this running.
     */
    fun toggleIngredient(recipeId: Long, stepIndex: Int, position: Int): Job = mutate {
        val current = _state.value?.takeIf { it.recipeId == recipeId } ?: return@mutate
        val step = current.steps.getOrNull(stepIndex) ?: return@mutate
        if (position !in step.ingredients.indices) return@mutate
        apply(
            current.copy(
                steps = current.steps.toMutableList().also { it[stepIndex] = step.toggled(position) }
            )
        )
    }

    /** Forward, from the notification. Nothing at the end — there is no step to move to. */
    fun next(): Job = mutate {
        val current = _state.value ?: return@mutate
        if (current.isLastStep) return@mutate
        apply(current.at(current.stepIndex + 1))
    }

    /** Back, from the notification. */
    fun previous(): Job = mutate {
        val current = _state.value ?: return@mutate
        if (current.isFirstStep) return@mutate
        apply(current.at(current.stepIndex - 1))
    }

    /** Done cooking: the notification goes. */
    fun stop(): Job = mutate { apply(null) }

    /**
     * The same, from a screen that is closing, and only if it is still the recipe being
     * cooked. See [goToStep] — leaving one recipe must not take down a session already
     * started on another.
     */
    fun stopIfCooking(recipeId: Long): Job = mutate {
        if (_state.value?.recipeId != recipeId) return@mutate
        apply(null)
    }

    private suspend fun apply(next: CookSessionState?) {
        // The screen pushes its step at every change, including the ones it adopted from
        // here — so the commonest call is the one that changes nothing, and it must not cost
        // a write and a redraw.
        if (_state.value == next) return
        _state.value = next
        store.write(next)
        onCookSessionChanged(next)
    }

    /**
     * Everything goes through here so no caller has to know whether the session has been read
     * back off the disk yet — see [CookTimer], including why the [Job] is returned.
     */
    private fun mutate(block: suspend () -> Unit): Job = scope.launch {
        ensureRestored()
        block()
    }
}
