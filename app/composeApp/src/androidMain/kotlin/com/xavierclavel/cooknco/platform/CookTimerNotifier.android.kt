package com.xavierclavel.cooknco.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.CookTimerState
import com.xavierclavel.cooknco.data.formatCookTimer
import com.xavierclavel.cooknco.ui.i18n.stringsFor

/**
 * The cook timer, as Android shows it: one ongoing notification that counts down on its own,
 * and one alarm that rings at the end.
 *
 * **Nothing here keeps the app running, and nothing needs to.** The countdown in the shade is
 * drawn by the system from `setWhen` + a counting-down chronometer, so it stays right with
 * this process frozen or killed; the ring is an `AlarmManager` alarm, which wakes the device
 * and starts the app back up if it has to. That is what Android's own timer does, and it is
 * why this is not a foreground service: a service would hold a process open for half an hour
 * to decrement a number that the clock already knows.
 *
 * Two things it depends on live in `:androidApp`, because a manifest entry cannot come from a
 * KMP library — the receiver that the actions and the alarm are addressed to, and the
 * activity the notification opens. Neither is referenced by name here: the broadcasts are
 * addressed by action within this package, and the tap reuses the launcher intent.
 */

/** Where a tapped notification's target is put on the launch intent. */
const val NOTIFICATION_LINK_EXTRA = "cooknco.notification.link"

/**
 * The actions the shade can send back. Declared in `:androidApp`'s manifest, and addressed
 * with `setPackage` — an intent aimed at one package is explicit enough for a manifest
 * receiver, which an action alone has not been since Android 8.
 */
const val ACTION_COOK_TIMER_TOGGLE = "com.xavierclavel.cooknco.COOK_TIMER_TOGGLE"
const val ACTION_COOK_TIMER_STOP = "com.xavierclavel.cooknco.COOK_TIMER_STOP"

/** Another minute on the timer. See [CookTimerState.withExtraMinute]. */
const val ACTION_COOK_TIMER_ADD_MINUTE = "com.xavierclavel.cooknco.COOK_TIMER_ADD_MINUTE"

/** What the alarm sends when the deadline arrives. */
const val ACTION_COOK_TIMER_FIRE = "com.xavierclavel.cooknco.COOK_TIMER_FIRE"

/**
 * What starts the ring — a foreground service in `:androidApp`, addressed by action because
 * this module cannot name the class. See [startRinging].
 */
const val ACTION_COOK_TIMER_RING = "com.xavierclavel.cooknco.COOK_TIMER_RING"

private const val RUNNING_CHANNEL_ID = "cooknco_cook_timer"

/**
 * Versioned, and the version is not decoration.
 *
 * A channel's sound and importance are fixed the moment it is first created — an app can
 * lower them afterwards but never raise or replace them, because that choice belongs to the
 * user from then on. So changing what the timer sounds like means a new channel and taking
 * the old one away; without the suffix, every device that had run the previous build would
 * keep ringing with the sound this one is replacing.
 */
private const val DONE_CHANNEL_ID = "cooknco_cook_timer_done_v2"
private val staleDoneChannelIds = listOf("cooknco_cook_timer_done")

/**
 * Fixed, because there is one timer: every update lands on the same notification instead of
 * stacking a new one every second, and the alert replaces the countdown it belongs to.
 */
private const val RUNNING_NOTIFICATION_ID = 8_101
private const val DONE_NOTIFICATION_ID = 8_102

private const val REQUEST_OPEN = 8_110
private const val REQUEST_TOGGLE = 8_111
private const val REQUEST_STOP = 8_112
private const val REQUEST_FIRE = 8_113
private const val REQUEST_ADD_MINUTE = 8_114

/**
 * Captured at startup — see `AppGraph.initFor`.
 *
 * The timer is driven from common code that has no `Context` to reach the notification
 * manager with, the same reason [captureAppVersion] exists.
 */
internal var cookModeContext: Context? = null
    private set

internal fun captureCookModeContext(context: Context) {
    cookModeContext = context.applicationContext
}

actual fun onCookTimerChanged(state: CookTimerState?) {
    val context = cookModeContext ?: return
    // A finished timer is the alert's business, not the countdown's: it arrives here only
    // when one is read back off the disk at launch, and there is nothing left to count.
    if (state == null || state.finished) {
        cancelAlarm(context)
        NotificationManagerCompat.from(context).cancel(RUNNING_NOTIFICATION_ID)
        // Stopping the timer is what silences a ringing one: the Stop button in the shade
        // and the one on the card both come through here, via CookTimer.stop().
        //
        // The alert goes with it. A ring the service is still holding disappears when the
        // service does, but one it has already given up on and detached — see
        // CookTimerRingService.RING_MILLIS — is an ordinary notification by then, and would
        // otherwise sit in the shade announcing a timer that no longer exists.
        if (state == null) {
            stopRinging(context)
            NotificationManagerCompat.from(context).cancel(DONE_NOTIFICATION_ID)
        }
        return
    }

    if (state.running) scheduleAlarm(context, state.endsAtEpochMillis) else cancelAlarm(context)
    stopRinging(context)
    NotificationManagerCompat.from(context).cancel(DONE_NOTIFICATION_ID)

    val s = stringsFor(AppLanguage.current.value)
    ensureChannels(context)
    val step = state.stepText.ifBlank { s.timerLabel }
    val builder = NotificationCompat.Builder(context, RUNNING_CHANNEL_ID)
        .setSmallIcon(timerIcon(context))
        .setContentTitle(state.recipeTitle)
        // The step, not a number naming it: the whole reason to glance at this rather than
        // open the app is to be told what is meant to be happening.
        .setContentText(step)
        .setStyle(NotificationCompat.BigTextStyle().bigText(step))
        // Where the countdown goes while it is not being drawn as a chronometer. Paused is
        // the one state with no time on show anywhere else.
        .setSubText(
            if (state.running) null
            else s.timerPaused + " · " + formatCookTimer(state.pausedRemainingSeconds)
        )
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        // The countdown itself. `when` is the deadline and the chronometer runs backwards to
        // it, so the shade ticks without this process being asked for anything.
        .setUsesChronometer(state.running)
        .setChronometerCountDown(state.running)
        .setWhen(state.endsAtEpochMillis)
        .setShowWhen(state.running)
        // Android 16's chip in the status bar, which is where a timer belongs — the platform
        // clock puts its own there, and a cook glancing at the phone should not have to pull
        // the shade down to see how long is left. Requested, never relied on: the system
        // decides, the user can turn Live Updates off per app, and below API 36 there is no
        // chip at all. Everything it needs is already above — ongoing, a title, a style that
        // is not a custom view, and a channel above IMPORTANCE_MIN.
        .setRequestPromotedOngoing(true)
        // What the chip says. Its content is short critical text first, then the chronometer,
        // then the bare icon — so this is left unset while running, where the countdown above
        // already fills it and ticks with this process frozen. Paused is the case with no
        // chronometer running and therefore nothing to show, hence the remaining time here.
        // Seven characters is what the chip fits whole, which mm:ss is and h:mm:ss is not.
        .setShortCriticalText(
            if (state.running) null else formatCookTimer(state.pausedRemainingSeconds)
        )
        // Ongoing so a swipe does not silently throw the timer away, and silent because the
        // thing worth a sound is the end, not each pause.
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(openCookMode(context, state.recipeId, REQUEST_OPEN))
        .addAction(
            0,
            if (state.running) s.pause else s.resume,
            broadcast(context, ACTION_COOK_TIMER_TOGGLE, REQUEST_TOGGLE),
        )
        // The button a kitchen timer is actually reached for. A pan is not done when the
        // timer says so, it is done when it looks done — and the answer to that is another
        // minute far more often than it is stopping the timer.
        .addAction(0, s.addAMinute, broadcast(context, ACTION_COOK_TIMER_ADD_MINUTE, REQUEST_ADD_MINUTE))

    // Stopping is what somebody who has paused is deciding about; a running timer is left
    // with the two buttons that are about cooking, in the order Android draws them. Dropping
    // one that is still counting is a thing to mean, and meaning it is a tap on Pause away —
    // which is also the state the card on the screen offers it in.
    if (!state.running) {
        builder.addAction(0, s.stopTimer, broadcast(context, ACTION_COOK_TIMER_STOP, REQUEST_STOP))
    }

    postNotification(context, RUNNING_NOTIFICATION_ID, builder.build())
}

actual fun onCookTimerFinished(state: CookTimerState) {
    val context = cookModeContext ?: return
    cancelAlarm(context)
    NotificationManagerCompat.from(context).cancel(RUNNING_NOTIFICATION_ID)
    if (startRinging(context)) return
    postCookTimerAlert(context, state)
}

/**
 * The timer, rung once, for when it cannot be rung until somebody stops it.
 *
 * A notification's sound plays exactly once — looping one is what the foreground service in
 * [startRinging] is for — so this is what is left when that service cannot be started, which
 * on Android 12 and up means an app in the background with no exemption to start one. A
 * cook who refused the exact-alarm permission is precisely that app, and lands here.
 *
 * Public because the service falls back to it too, rather than keeping a second copy of what
 * "time's up" looks like.
 */
fun postCookTimerAlert(context: Context, state: CookTimerState) {
    val s = stringsFor(AppLanguage.current.value)
    ensureAlertChannel(context)
    val notification = NotificationCompat.Builder(context, DONE_CHANNEL_ID)
        .setSmallIcon(timerIcon(context))
        .setContentTitle(s.timerTimeIsUp)
        .setContentText(state.stepText.ifBlank { state.recipeTitle })
        .setStyle(NotificationCompat.BigTextStyle().bigText(state.stepText.ifBlank { state.recipeTitle }))
        .setSubText(state.recipeTitle)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        // For Android 7, which has no channels to read any of this off.
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
        .setSound(timerSound(context))
        // The one notification worth reading without unlocking the phone.
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .setContentIntent(openCookMode(context, state.recipeId, REQUEST_OPEN))
        .build()

    postNotification(context, DONE_NOTIFICATION_ID, notification)
}

/**
 * Starts the thing that rings until it is stopped.
 *
 * The service is declared in `:androidApp` — a manifest entry cannot come from a KMP library
 * — and a service intent must be explicit, so the two are reconciled by resolving the one
 * component in this package that answers to [ACTION_COOK_TIMER_RING] and addressing that.
 *
 * False means it could not be started at all, which is a real outcome rather than an error:
 * from Android 12 an app in the background may not start a foreground service unless it is
 * exempt, and being woken by an *exact* alarm is one of the exemptions. So the permission
 * this app asks for buys punctuality and the ring together, and refusing it costs both.
 */
private fun startRinging(context: Context): Boolean {
    val intent = ringIntent(context) ?: return false
    return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
}

private fun stopRinging(context: Context) {
    ringIntent(context)?.let { runCatching { context.stopService(it) } }
}

private fun ringIntent(context: Context): Intent? {
    val intent = Intent(ACTION_COOK_TIMER_RING).setPackage(context.packageName)
    val service = context.packageManager.queryIntentServices(intent, 0)
        .firstOrNull()?.serviceInfo ?: return null
    return intent.setComponent(ComponentName(service.packageName, service.name))
}

/**
 * Drawing anything at all needs the runtime permission from Android 13 on. Checked rather
 * than caught, so a cook who refused notifications gets a timer that still counts on screen
 * instead of something that looks like a crash.
 */
internal fun postNotification(context: Context, id: Int, notification: android.app.Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    NotificationManagerCompat.from(context).notify(id, notification)
}

/**
 * Two channels, because they are two different things to be interrupted by: the countdown is
 * furniture and must not make a sound every time it is redrawn, the ring is the whole point.
 * Splitting them also leaves the choice with the user — silencing one in Android's settings
 * does not silence the other.
 */
private fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val s = stringsFor(AppLanguage.current.value)
    // Otherwise an earlier build's channel sits in the user's notification settings for ever,
    // named the same thing as the one actually in use.
    staleDoneChannelIds.forEach(manager::deleteNotificationChannel)

    manager.createNotificationChannel(
        NotificationChannel(RUNNING_CHANNEL_ID, s.timerChannelName, NotificationManager.IMPORTANCE_LOW)
            .apply {
                description = s.timerChannelDescription
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
    )
}

/**
 * The channel the one-shot alert goes to. Created where it is used rather than alongside the
 * countdown's, so a device that always manages to ring properly never grows a second entry
 * in its notification settings for a sound it has never made.
 */
private fun ensureAlertChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val s = stringsFor(AppLanguage.current.value)
    manager.createNotificationChannel(
        NotificationChannel(DONE_CHANNEL_ID, s.timerDoneChannelName, NotificationManager.IMPORTANCE_HIGH)
            .apply {
                description = s.timerDoneChannelDescription
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                // USAGE_ALARM for the *volume*, not for the tone: a cook mode timer is heard
                // from another room, and the notification stream is the one people turn down
                // and the one a phone face-down on a worktop silences. What it plays through
                // that stream is a timer, not the handset's wake-up alarm — see [timerSound].
                setSound(
                    timerSound(context),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build(),
                )
            }
    )
}

/**
 * A kitchen timer, bundled, because Android does not have one to ask for.
 *
 * `RingtoneManager` knows three kinds of sound — ringtone, notification, alarm — and none of
 * them is this. The alarm tone is written to wake somebody out of sleep and is long, rising
 * and slightly alarming to hear while cooking; the notification blip is a single tick that
 * goes unheard from the next room. So `res/raw/cook_timer.wav` is what plays instead: three
 * groups of three short beeps over two and a half seconds, which is what a timer sounds like
 * and is long enough to be caught.
 *
 * It rings once rather than looping. Looping until dismissed is what Android's own timer
 * does, and it costs a foreground service held open for the whole countdown — see this
 * file's note. Hence a ring with some length to it rather than a single ding.
 *
 * Looked up by name for the same reason the icon is: it lives in `:androidApp`'s resources
 * and this module has no `R` reaching them. The platform tones are the fallback, because a
 * timer that says nothing is worse than one that says the wrong thing.
 */
private fun timerSound(context: Context): Uri? {
    val id = context.resources.getIdentifier("cook_timer", "raw", context.packageName)
    if (id != 0) return Uri.parse("android.resource://${context.packageName}/$id")
    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
}

/**
 * The alarm that ends the timer.
 *
 * Exact where the user has allowed it, and approximate where they have not. Apps targeting
 * Android 14 and up are denied `SCHEDULE_EXACT_ALARM` by default, and the permission that is
 * granted without asking — `USE_EXACT_ALARM` — is Play-restricted to apps whose *purpose* is
 * alarms, which a recipe app's is not. So this asks for nothing and degrades instead: with
 * the permission, the alarm fires on the second even in Doze; without it, the system may
 * batch it late on a device that has been idle for a while, and the in-process ticker in
 * `CookTimer` covers the far commoner case of a phone that is being cooked next to.
 */
private fun scheduleAlarm(context: Context, endsAtEpochMillis: Long) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    val pending = broadcast(context, ACTION_COOK_TIMER_FIRE, REQUEST_FIRE)
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    runCatching {
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtEpochMillis, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtEpochMillis, pending)
        }
    }
}

private fun cancelAlarm(context: Context) {
    context.getSystemService(AlarmManager::class.java)
        ?.cancel(broadcast(context, ACTION_COOK_TIMER_FIRE, REQUEST_FIRE))
}

internal fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(action).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/**
 * Reopens cook mode on the recipe the timer belongs to.
 *
 * Through the launcher intent and the same `link` extra a push carries, rather than by
 * naming the activity: the activity is in `:androidApp` and this is not, and the route it
 * maps to is already `WebRoutes`' business.
 */
internal fun openCookMode(context: Context, recipeId: Long, requestCode: Int): PendingIntent? {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NOTIFICATION_LINK_EXTRA, "/recipe/cook?id=$recipeId")
        }
        ?: return null
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * The hourglass both of the timer's notifications are posted under.
 *
 * Its own mark rather than the app's, because on Android 16 this is also what is drawn in
 * the status bar chip — next to the clock, with no title and no text beside it — and there
 * it has to say "a timer is running" unaided. The app silhouette is the fallback, so a build
 * without the drawable still posts.
 */
private fun timerIcon(context: Context): Int {
    val id = context.resources.getIdentifier("ic_timer", "drawable", context.packageName)
    return if (id != 0) id else smallIcon(context)
}

/**
 * The app's own notification silhouette, looked up by name.
 *
 * It lives in `:androidApp`'s resources — this module has no `R` reaching them — and a
 * second copy here would be one more thing to keep in step for no gain. The fallback is a
 * platform icon rather than nothing: a notification with no small icon is not posted at all.
 */
internal fun smallIcon(context: Context): Int {
    val id = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
    return if (id != 0) id else android.R.drawable.ic_lock_idle_alarm
}
