package com.xavierclavel.cooknco

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.AppLocale
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.di.initFor
import com.xavierclavel.cooknco.platform.ACTION_COOK_TIMER_FIRE
import com.xavierclavel.cooknco.platform.ACTION_COOK_TIMER_STOP
import com.xavierclavel.cooknco.platform.ACTION_COOK_TIMER_TOGGLE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The far end of the cook timer's notification: its two buttons, and the alarm that ends it.
 *
 * It lives here rather than beside the code that builds those intents because a manifest
 * entry cannot come from a KMP library — the same reason [CookncoMessagingService] does. It
 * carries no logic of its own: what each action means is `CookTimer`'s business, and the
 * timer it applies to is read back off the disk rather than passed along on the intent,
 * since the one case this exists for is the process having been gone.
 *
 * Which is what shapes the rest of it. An alarm set half an hour ago routinely lands on a
 * process Android started for this broadcast alone: nothing is initialised, nothing has been
 * read, and the process is only guaranteed to live until `onReceive` returns — while every
 * useful thing here happens after it does.
 */
class CookTimerReceiver : BroadcastReceiver() {

    /** Its own, for the same reason [CookncoMessagingService] has one: a receiver is not a
     * lifecycle owner, and this work outlives the callback that starts it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in handled) return
        AppGraph.initFor(context.applicationContext)

        // goAsync() is what buys the time: without it, reading the timer back and posting the
        // notification are a race against being killed the moment this method returns.
        val pending = goAsync()
        scope.launch {
            try {
                // Before anything is drawn. On a process started by the alarm, nothing has
                // resolved the language yet, and the notification would come out in the
                // handset's rather than the one the account chose. See [AppLanguage].
                AppGraph.devicePreferences.language.first()?.let { AppLanguage.set(AppLocale.of(it)) }
                val timer = AppGraph.cookTimer
                when (action) {
                    ACTION_COOK_TIMER_TOGGLE -> timer.toggle()
                    ACTION_COOK_TIMER_STOP -> timer.stop()
                    else -> timer.finish()
                }.join()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val handled = setOf(ACTION_COOK_TIMER_TOGGLE, ACTION_COOK_TIMER_STOP, ACTION_COOK_TIMER_FIRE)
    }
}
