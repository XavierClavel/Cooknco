package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.platform.onCookTimerChanged
import com.xavierclavel.cooknco.platform.onCookTimerFinished
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * A cook mode timer, held as a deadline rather than as a number being counted down.
 *
 * That is the whole reason this is no longer a `while (true) { delay(1000); remaining-- }`
 * inside a view model. A counter is only correct while something is running to decrement
 * it, and on a phone nothing is: the screen goes off, the app is backgrounded and frozen,
 * the process is killed to make room. A wall-clock deadline is correct with nobody watching
 * it — the countdown on screen and the one in the notification are both *read* from it, and
 * both are right the instant they are looked at again.
 *
 * [endsAtEpochMillis] therefore means something only while [running], and
 * [pausedRemainingSeconds] only while it is not. Pausing turns the one into the other.
 *
 * It is [finished] when it has stopped with nothing left — a state worth keeping rather
 * than clearing, so the screen can show 00:00 and offer to run the same timer again.
 */
@Serializable
data class CookTimerState(
    val recipeId: Long,
    val recipeTitle: String,
    /** Zero-based, to match `RecipeInfo.steps`. */
    val stepIndex: Int,
    /**
     * The step's text, carried rather than looked up.
     *
     * What reads this is the notification, and the notification is routinely drawn by a
     * process that has no recipe and no network — one the alarm woke for that alone. A step
     * that has been edited since therefore shows as it was when the timer was set, which is
     * the step the cook actually started.
     *
     * Defaulted so a timer written down by a build that predates it still decodes, rather
     * than reading as no timer at all.
     */
    val stepText: String = "",
    val totalSeconds: Int,
    val endsAtEpochMillis: Long,
    val pausedRemainingSeconds: Int,
    val running: Boolean,
) {
    val finished: Boolean get() = !running && pausedRemainingSeconds <= 0

    /**
     * Rounded *up*, so a timer with 4.2 seconds left reads 5 and the last second is shown
     * for a whole second rather than for a blink.
     */
    fun remainingSecondsAt(nowMillis: Long): Int =
        if (running) ((endsAtEpochMillis - nowMillis + 999) / 1000).coerceAtLeast(0L).toInt()
        else pausedRemainingSeconds.coerceAtLeast(0)
}

/** `mm:ss`, or `h:mm:ss` once there is an hour to show. Used on screen and in the notification. */
fun formatCookTimer(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val minutePart = (seconds / 60 % 60).toString().padStart(2, '0')
    val secondPart = (seconds % 60).toString().padStart(2, '0')
    val hours = seconds / 3600
    return if (hours > 0) "$hours:$minutePart:$secondPart" else "$minutePart:$secondPart"
}

/**
 * The one cook timer, held for the whole process rather than by the screen that started it.
 *
 * One at a time, deliberately: a second would need a second notification, a second alarm and
 * somewhere on the step to show both, and a cook running two timers at once is rarer than a
 * cook who leaves cook mode and comes back. Starting one replaces whatever was there.
 *
 * Every mutation does the same three things — move the state, write it down, and tell the
 * platform ([onCookTimerChanged]) so the notification and the alarm follow. Writing it down
 * is what lets a timer outlive its process: the notification stays up on its own, and
 * whichever entry point arrives first — the activity, or the broadcast the alarm sends —
 * reads the deadline back and carries on from it.
 */
class CookTimer(
    private val store: CookTimerStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val _state = MutableStateFlow<CookTimerState?>(null)

    /** The timer itself. Changes on a transition, not on every tick. */
    val state: StateFlow<CookTimerState?> = _state.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)

    /** What the clock reads, re-derived every second while the timer runs. */
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var ticker: Job? = null
    private val restoreLock = Mutex()
    private var restored = false

    /**
     * Reads back a timer left behind by an earlier process.
     *
     * Called from the app's entry point so the screen has it before it draws, and again —
     * through [mutate] — by anything that arrives with the process cold, which is every
     * notification action and the alarm itself.
     */
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
            _remainingSeconds.value = saved.remainingSecondsAt(now())
            // Announced again, even though the notification and the alarm normally outlive
            // the process that set them. Normally is not always: a force-stop takes both
            // away and a reboot takes the alarm, and in either case the timer on disk is
            // still the truth. Re-announcing is free because both are idempotent — the
            // notification has a fixed id and is silent, the alarm a fixed PendingIntent —
            // so this either changes nothing or repairs everything.
            onCookTimerChanged(saved)
            if (saved.running) startTicking()
        }
    }

    /** Starts [seconds] for one step, replacing any timer already running. */
    fun start(
        recipeId: Long,
        recipeTitle: String,
        stepIndex: Int,
        stepText: String,
        seconds: Int,
    ): Job = mutate {
        if (seconds <= 0) return@mutate
        apply(
            CookTimerState(
                recipeId = recipeId,
                recipeTitle = recipeTitle,
                stepIndex = stepIndex,
                stepText = stepText,
                totalSeconds = seconds,
                endsAtEpochMillis = now() + seconds * 1000L,
                pausedRemainingSeconds = seconds,
                running = true,
            )
        )
    }

    /** Pauses a running timer, resumes a paused one, and runs a finished one again. */
    fun toggle(): Job = mutate {
        val current = _state.value ?: return@mutate
        when {
            current.running -> apply(
                current.copy(
                    running = false,
                    pausedRemainingSeconds = current.remainingSecondsAt(now()),
                )
            )

            current.finished -> apply(
                current.copy(
                    running = true,
                    endsAtEpochMillis = now() + current.totalSeconds * 1000L,
                    pausedRemainingSeconds = current.totalSeconds,
                )
            )

            else -> apply(
                current.copy(
                    running = true,
                    endsAtEpochMillis = now() + current.pausedRemainingSeconds * 1000L,
                )
            )
        }
    }

    /** Drops the timer entirely: the notification goes, the alarm is cancelled. */
    fun stop(): Job = mutate { apply(null) }

    /**
     * Rings.
     *
     * Reached two ways — by the ticker below while the app is alive, and by the alarm's
     * broadcast when it is not — and it has to be safe both times, because on a device that
     * is awake both happen. The guard is the state itself: a timer that has already finished,
     * or that is gone, rings nothing.
     */
    fun finish(): Job = mutate {
        val current = _state.value ?: return@mutate
        if (current.finished) return@mutate
        val done = current.copy(running = false, pausedRemainingSeconds = 0)
        ticker?.cancel()
        _state.value = done
        _remainingSeconds.value = 0
        store.write(done)
        // Not onCookTimerChanged: finishing replaces the ongoing notification with the alert
        // rather than updating it, and cancels the alarm that may be what got us here.
        onCookTimerFinished(done)
    }

    private suspend fun apply(next: CookTimerState?) {
        ticker?.cancel()
        _state.value = next
        _remainingSeconds.value = next?.remainingSecondsAt(now()) ?: 0
        store.write(next)
        onCookTimerChanged(next)
        if (next != null && next.running) startTicking()
    }

    /**
     * Keeps [remainingSeconds] honest while the app is in front, and rings when it runs out.
     *
     * It sleeps to the next whole second of the *deadline* rather than a flat second at a
     * time, so a display resumed mid-second does not sit on the same number for two ticks,
     * and a process that was frozen for ten minutes catches up in one read rather than in
     * six hundred.
     */
    private fun startTicking() {
        ticker = scope.launch {
            while (true) {
                val current = _state.value ?: return@launch
                if (!current.running) return@launch
                val remaining = current.remainingSecondsAt(now())
                _remainingSeconds.value = remaining
                if (remaining <= 0) {
                    finish()
                    return@launch
                }
                delay((current.endsAtEpochMillis - now() - (remaining - 1) * 1000L).coerceIn(1L, 1000L))
            }
        }
    }

    /**
     * Everything goes through here so no caller has to know whether the state has been read
     * back off the disk yet. The screen always has; a notification action landing on a
     * process the system killed an hour ago never has.
     *
     * The [Job] is returned because one caller does need to know when it is over: a broadcast
     * receiver is only guaranteed its process for as long as `onReceive` has not returned,
     * and the work here outlives the call that starts it. See `CookTimerReceiver`.
     */
    private fun mutate(block: suspend () -> Unit): Job = scope.launch {
        ensureRestored()
        block()
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
