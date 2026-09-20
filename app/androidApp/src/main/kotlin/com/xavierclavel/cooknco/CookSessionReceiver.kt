package com.xavierclavel.cooknco

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.di.initFor
import com.xavierclavel.cooknco.platform.ACTION_COOK_SESSION_NEXT
import com.xavierclavel.cooknco.platform.ACTION_COOK_SESSION_PREVIOUS
import com.xavierclavel.cooknco.platform.ACTION_COOK_SESSION_STOP
import com.xavierclavel.cooknco.platform.ACTION_COOK_SESSION_TICK
import com.xavierclavel.cooknco.platform.ACTION_COOK_SESSION_TIMER
import com.xavierclavel.cooknco.platform.EXTRA_INGREDIENT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The far end of the cook session's notification: the buttons that walk the recipe.
 *
 * The same shape as [CookTimerReceiver], for the same reasons — it lives here because a
 * manifest entry cannot come from a KMP library, it carries no logic of its own, and the
 * session it moves is read back off the disk rather than passed along on the intent.
 *
 * That last part is the whole point here. Following a recipe from the shade means pressing
 * Next every few minutes for an hour, and nothing guarantees the app is still running by the
 * second one: this routinely lands on a process Android started for this broadcast alone,
 * with nothing initialised, and is guaranteed to live only until `onReceive` returns — while
 * reading the session, moving it and redrawing the notification all happen after that.
 */
class CookSessionReceiver : BroadcastReceiver() {

    /** Its own, like [CookTimerReceiver]'s: a receiver is not a lifecycle owner. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in handled) return
        AppGraph.initFor(context.applicationContext)

        // Without goAsync() the redraw is a race against being killed the moment this returns,
        // and losing it leaves the notification a step behind what the cook pressed.
        val pending = goAsync()
        scope.launch {
            try {
                // Before anything is drawn: on a process started by this broadcast nothing has
                // resolved the language, and the step would come out headed in the handset's
                // rather than the one the account chose. See [AppLanguage].
                AppLanguage.restore(AppGraph.devicePreferences)
                val session = AppGraph.cookSession
                when (action) {
                    ACTION_COOK_SESSION_NEXT -> session.next().join()
                    ACTION_COOK_SESSION_PREVIOUS -> session.previous().join()
                    ACTION_COOK_SESSION_TIMER -> startStepTimer()
                    ACTION_COOK_SESSION_TICK -> tickIngredient(intent.getIntExtra(EXTRA_INGREDIENT, -1))
                    else -> session.stop().join()
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Starts the current step's timer, which is the one button here that is about something
     * other than the session.
     *
     * It lives on this side rather than in the notification because only this side can see
     * both: the timer it would start, and the one that may already be running. **A second
     * press does nothing.** The button is drawn on every step that has a duration — a button
     * that came and went as a timer started would move the two beside it — so pressing it
     * again while that timer counts is an ordinary thing to do, and restarting a countdown
     * somebody has walked away from is not something to do by accident. What answers them is
     * the timer's own notification, already showing what is left.
     *
     * A finished timer is not one of those: running the same step again is exactly what the
     * card on the screen offers, and it is what a cook who burnt the first batch wants.
     */
    private suspend fun startStepTimer() {
        val session = AppGraph.cookSession.current() ?: return
        val seconds = session.step?.durationSeconds ?: return
        val timer = AppGraph.cookTimer.current()
        if (timer != null && !timer.finished &&
            timer.recipeId == session.recipeId && timer.stepIndex == session.stepIndex
        ) {
            return
        }
        AppGraph.cookTimer.start(
            recipeId = session.recipeId,
            recipeTitle = session.recipeTitle,
            stepIndex = session.stepIndex,
            // The step as the session carries it, so the timer's notification says what this
            // one says rather than what the recipe says now. See [CookTimerState.stepText].
            stepText = session.step?.text.orEmpty(),
            seconds = seconds,
        ).join()
    }

    /**
     * Ticks the pressed ingredient off the step it belongs to, or back on.
     *
     * Which step that is comes from the session rather than from the intent. The two agree
     * almost always — the row was drawn on the step that is showing — but a press landing
     * just after a Next would otherwise tick a line on the step the cook has left, and a
     * checklist that is wrong about that is worse than one nobody filled in.
     */
    private suspend fun tickIngredient(position: Int) {
        if (position < 0) return
        val session = AppGraph.cookSession.current() ?: return
        AppGraph.cookSession
            .toggleIngredient(session.recipeId, session.stepIndex, position)
            .join()
    }

    private companion object {
        val handled = setOf(
            ACTION_COOK_SESSION_NEXT,
            ACTION_COOK_SESSION_PREVIOUS,
            ACTION_COOK_SESSION_TIMER,
            ACTION_COOK_SESSION_TICK,
            ACTION_COOK_SESSION_STOP,
        )
    }
}
