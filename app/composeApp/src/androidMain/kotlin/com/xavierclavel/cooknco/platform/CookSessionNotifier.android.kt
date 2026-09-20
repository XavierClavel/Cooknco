package com.xavierclavel.cooknco.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.CookSessionState
import com.xavierclavel.cooknco.data.CookSessionStep
import com.xavierclavel.cooknco.data.formatCookTimer
import com.xavierclavel.cooknco.shared.R
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.stringsFor

/**
 * The recipe being cooked, as Android shows it: one notification carrying the whole of the
 * current step, the controls that walk the recipe, and a tick box per ingredient.
 *
 * It is a notification rather than a screen because of where the cook is. Hands are in a
 * bowl, the phone is face-up on the worktop three feet away or in a pocket, and the app is
 * behind whatever they last looked at. Pulling the shade down is one gesture; finding the
 * app, waiting for the recipe and finding the step is several — and the step is short enough
 * to fit in a notification whole, which is the reason this works at all.
 *
 * **Nothing keeps a process alive for it.** The session is written down
 * ([com.xavierclavel.cooknco.data.CookSessionStore]), the notification stays up on its own,
 * and every press lands on whatever process Android starts for the broadcast — which reads the
 * session back, changes it, and redraws this. So a recipe is followed to the end with the app
 * killed the whole time.
 *
 * **The expanded view is the app's own `RemoteViews`**, which is a cost worth naming. The
 * standard template offers text and up to three action buttons, and a checklist is neither: an
 * ingredient ticked off as it goes into the pan is a row to press, one per line, and no
 * notification style has one. Drawing it means owning its appearance — see
 * `res/layout/cooknco_cook_session.xml`, which is where the reasoning about ordering and
 * colour lives — and it buys two things beyond the boxes: controls with an outline, which the
 * system's bare-glyph chips did not read as, and a row of four rather than three, since the
 * cap belongs to the template rather than to notifications.
 *
 * The collapsed view is still the system's, and still says the recipe, the step and how far
 * through it is.
 *
 * It *starts* a step's timer and does not otherwise carry the timer's controls. Starting one
 * is part of following the recipe — the step says "bake for 25 minutes" and the cook is
 * standing at the oven, not at the phone. Everything after that is the timer's own
 * notification, which appears directly below this one with the countdown, the status bar chip
 * and Pause (`CookTimerNotifier.android.kt`). A Pause and a Stop repeated here would be a
 * second set of controls over something this notification does not own, and a Stop that stops
 * a different thing from the one beside it.
 *
 * As with the timer, the receiver the presses are addressed to lives in `:androidApp` — a
 * manifest entry cannot come from a KMP library — and is reached by action within this
 * package rather than by name.
 */

/** What the shade sends back. Declared in `:androidApp`'s manifest. */
const val ACTION_COOK_SESSION_NEXT = "com.xavierclavel.cooknco.COOK_SESSION_NEXT"
const val ACTION_COOK_SESSION_PREVIOUS = "com.xavierclavel.cooknco.COOK_SESSION_PREVIOUS"
const val ACTION_COOK_SESSION_STOP = "com.xavierclavel.cooknco.COOK_SESSION_STOP"

/** Starts the current step's own timer. See [CookSessionStep.durationSeconds]. */
const val ACTION_COOK_SESSION_TIMER = "com.xavierclavel.cooknco.COOK_SESSION_TIMER"

/** Ticks one ingredient off, or back on. Carries [EXTRA_INGREDIENT]. */
const val ACTION_COOK_SESSION_TICK = "com.xavierclavel.cooknco.COOK_SESSION_TICK"

/** Which row was pressed: a position in [CookSessionStep.ingredients]. */
const val EXTRA_INGREDIENT = "cooknco.session.ingredient"

/**
 * The two controls that are typed rather than drawn.
 *
 * Words were tried and do not fit: four of them wrap, crowd each other out of the row, and are
 * read rather than recognised. A mark is one shape in a fixed place, which is what survives
 * being glanced at from across a kitchen by somebody holding a spoon.
 *
 * These two are glyphs because they come out at the size and height they are given. The
 * arrows did not — an arrow in the system font is set on a text baseline, and next to a tick
 * and a stopwatch it reads as small and low — so those two are vector drawables instead
 * (`cooknco_arrow_previous`), which are the size they are told and centred in their box.
 *
 * Text rather than emoji, so they take the accent the outline is drawn in rather than
 * arriving as colour pictures. No plain glyph for a stopwatch is reliably in an Android system
 * font, which is why that one is the emoji it is.
 *
 * None of the four is on its own, though: every control carries a `contentDescription` in the
 * cook's language, which is what a screen reader announces. That is also why these are not
 * the system's action buttons — an action's label *is* its description, so a mark there is a
 * mark read out.
 */
private const val MARK_FINISH = "✓"
private const val MARK_TIMER = "⏱"

/**
 * How many ingredients the shade shows before the rest become a count.
 *
 * A custom view is clipped when it outgrows the expanded notification, from the bottom, and
 * the controls are at the bottom — so this is what keeps a step with twelve ingredients from
 * pushing its own way out of the recipe off the end of the notification. Four and a count is
 * what fits beside three lines of step, a timer and the row of controls; nearly every step is
 * under it, and what is past it is read in the app, where the whole list is.
 */
private const val MAX_INGREDIENT_ROWS = 4

private const val CHANNEL_ID = "cooknco_cook_session"

/** Fixed: there is one session, and every step lands on the same notification. */
private const val NOTIFICATION_ID = 8_201

private const val REQUEST_OPEN = 8_210
private const val REQUEST_PREVIOUS = 8_211
private const val REQUEST_NEXT = 8_212
private const val REQUEST_STOP = 8_213
private const val REQUEST_TIMER = 8_214

/**
 * One per row, because a `PendingIntent` is identified by everything *except* its extras: six
 * ticks sharing a request code would be six handles on the same intent, and every box would
 * toggle whichever one was built last.
 */
private const val REQUEST_TICK_BASE = 8_220

actual fun onCookSessionChanged(state: CookSessionState?) {
    val context = cookModeContext ?: return
    // A session with no steps is not one a notification can do anything with, and it is what
    // a recipe whose steps were all deleted decodes to.
    if (state == null || state.steps.isEmpty()) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        return
    }

    val s = stringsFor(AppLanguage.current.value)
    ensureChannel(context)

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        // The app's own silhouette rather than the timer's hourglass: the two sit together
        // in the shade while a step is counting down, and they are not the same thing.
        .setSmallIcon(smallIcon(context))
        .setContentTitle(state.recipeTitle)
        // Collapsed, and drawn by the system: what to do, and how far through. Expanded is
        // [bigContentView].
        .setContentText(state.step?.text.orEmpty())
        .setSubText(s.stepProgress(state.stepIndex + 1, state.stepCount))
        // No setProgress. The standard template draws its bar *below* the custom view, so
        // asking for one put a second bar at the foot of the expanded notification, under the
        // controls. The bar at the top of [bigContentView] is this one's, where it was asked
        // to be. What the collapsed view says instead is the sub-text above: the same count,
        // in words.
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomBigContentView(bigContentView(context, state, s))
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setOnlyAlertOnce(true)
        // Silent because everything it says is something the cook just did.
        .setSilent(true)
        // There is no moment here worth stamping — a session is where the cook is, not when.
        .setShowWhen(false)
        // Swiping it away is finishing, and it goes through the same broadcast the tick does,
        // so there is one way to end a session rather than two that can disagree.
        //
        // Dismissible, unlike the timer's, which is ongoing because a swipe there would
        // silently throw away something still counting that is going to ring. A session has
        // nothing hidden to lose: "take this off my phone" and "I have finished cooking" are
        // the same sentence.
        .setDeleteIntent(broadcast(context, ACTION_COOK_SESSION_STOP, REQUEST_STOP))
        .setContentIntent(openCookMode(context, state.recipeId, REQUEST_OPEN))
        .build()

    postNotification(context, NOTIFICATION_ID, notification)
}

/**
 * The expanded body: the step, its controls, and its ingredients as a checklist.
 *
 * Everything conditional here is a visibility rather than a view that is or is not added,
 * because `RemoteViews` addresses views by id and an absent id is a silent no-op. The rows are
 * the exception — there is no knowing how many a step has — so they are built one at a time
 * into a container that is emptied first.
 */
private fun bigContentView(context: Context, state: CookSessionState, s: Strings): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.cooknco_cook_session)
    val step = state.step
    val accent = deviceAccent(context)

    // The same count the collapsed view shows, at the top of the expanded one: how far
    // through the recipe is the first thing to know and the one thing readable at a glance.
    views.setProgressBar(R.id.cooknco_session_progress, state.stepCount, state.stepIndex + 1, false)
    views.accent(R.id.cooknco_session_progress, accent, "setProgressTintList")
    views.setTextViewText(R.id.cooknco_session_step, step?.text.orEmpty())

    val duration = step?.durationSeconds
    views.setViewVisibility(R.id.cooknco_session_timer, if (duration == null) View.GONE else View.VISIBLE)
    if (duration != null) {
        views.setTextViewText(R.id.cooknco_session_timer, s.timerLabel + " · " + formatCookTimer(duration))
    }

    // Only the moves that exist. A control that answers a press by doing nothing is worse
    // than an absent one, and on the last step Finish stands where Next was — which is where
    // the screen puts it too, so the button under the cook's thumb does not move.
    drawnControl(
        views,
        R.id.cooknco_session_previous,
        accent,
        s.previousStep,
        broadcast(context, ACTION_COOK_SESSION_PREVIOUS, REQUEST_PREVIOUS),
        visible = !state.isFirstStep,
    )
    control(
        views,
        R.id.cooknco_session_timer_start,
        MARK_TIMER,
        accent,
        s.startTimer,
        broadcast(context, ACTION_COOK_SESSION_TIMER, REQUEST_TIMER),
        // On the steps that have one, and on no others. It starts the timer rather than
        // standing in for it: pressing it again while that timer runs does nothing — see
        // CookSessionReceiver, which will not restart a countdown somebody is relying on.
        visible = duration != null,
    )
    drawnControl(
        views,
        R.id.cooknco_session_next,
        accent,
        s.nextStep,
        broadcast(context, ACTION_COOK_SESSION_NEXT, REQUEST_NEXT),
        visible = !state.isLastStep,
    )
    control(
        views,
        R.id.cooknco_session_finish,
        MARK_FINISH,
        accent,
        s.finish,
        broadcast(context, ACTION_COOK_SESSION_STOP, REQUEST_STOP),
        visible = state.isLastStep,
    )

    views.removeAllViews(R.id.cooknco_session_ingredients)
    val shown = step?.ingredients.orEmpty().take(MAX_INGREDIENT_ROWS)
    shown.forEachIndexed { position, line ->
        views.addView(
            R.id.cooknco_session_ingredients,
            ingredientRow(
                context,
                line,
                checked = position in step?.checked.orEmpty(),
                position = position,
                accent = accent,
                s = s,
            ),
        )
    }

    val hidden = step?.ingredients.orEmpty().size - shown.size
    views.setViewVisibility(R.id.cooknco_session_more, if (hidden > 0) View.VISIBLE else View.GONE)
    if (hidden > 0) views.setTextViewText(R.id.cooknco_session_more, s.andMoreIngredients(hidden))

    return views
}

/** One outlined control: its mark, what a screen reader says instead, and what it sends. */
private fun control(
    views: RemoteViews,
    id: Int,
    mark: String,
    accent: Int?,
    description: String,
    intent: PendingIntent,
    visible: Boolean,
) {
    views.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
    if (!visible) return
    views.setTextViewText(id, mark)
    accent?.let { views.setTextColor(id, it) }
    views.accent(id, accent, "setBackgroundTintList")
    views.setContentDescription(id, description)
    views.setOnClickPendingIntent(id, intent)
}

/**
 * The same, for the two whose mark is a drawable rather than a glyph. The picture is in the
 * layout, since it never changes; what is set here is its colour and what a press is worth.
 */
private fun drawnControl(
    views: RemoteViews,
    id: Int,
    accent: Int?,
    description: String,
    intent: PendingIntent,
    visible: Boolean,
) {
    views.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
    if (!visible) return
    accent?.let { views.setInt(id, "setColorFilter", it) }
    views.accent(id, accent, "setBackgroundTintList")
    views.setContentDescription(id, description)
    views.setOnClickPendingIntent(id, intent)
}

/**
 * The accent this notification draws itself in: the handset's own, read from the palette
 * Android builds for every app on the device.
 *
 * The layout asks the shade's theme for one and does not get this. Resolved there,
 * colorAccent comes out a tone or two down — paler, less saturated than the accent the
 * system's own notification furniture is drawn in — so everything the app drew read as faded
 * beside it. The palette below is the source that furniture is coloured from, so taking the
 * tone straight is what makes the two agree.
 *
 * Which tone depends on the shade rather than on the app: 600 is meant to be read against a
 * light surface and 200 against a dark one, and the shade follows the system's dark theme,
 * which is what the configuration here reports.
 *
 * Null below Android 12, where there is no such palette to read. The theme attribute in the
 * layout stands in, which is what those devices have always shown.
 */
private fun deviceAccent(context: Context): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    return runCatching {
        context.getColor(
            if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
        )
    }.getOrNull()
}

/**
 * Tints one view with it, through whichever setter that view takes its colour from.
 *
 * A no-op when there is no palette to read, which leaves the view the colour its layout asked
 * the theme for — see [deviceAccent]. setColorStateList arrived in the same release the
 * palette did, so the two are never apart.
 */
private fun RemoteViews.accent(id: Int, accent: Int?, method: String) {
    if (accent == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    setColorStateList(id, method, ColorStateList.valueOf(accent))
}

/**
 * One ingredient, ticked or not.
 *
 * The whole row is the target, not the box — see the layout. The ticked state is two swaps
 * rather than a `CheckBox`: a real one would toggle itself when pressed *and* send the
 * broadcast, so a press that the session refused would leave a box ticked over a line that is
 * not, and the shade would be lying about what is in the pan.
 */
private fun ingredientRow(
    context: Context,
    line: String,
    checked: Boolean,
    position: Int,
    accent: Int?,
    s: Strings,
): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.cooknco_cook_session_ingredient)
    // The box is a picture, so what it is showing has to be said in words for the row to mean
    // anything read aloud — the label alone would announce an ingredient and nothing about
    // whether it has gone in.
    row.setContentDescription(R.id.cooknco_ingredient_row, s.ingredientTicked(line, checked))
    row.setImageViewResource(
        R.id.cooknco_ingredient_box,
        if (checked) R.drawable.cooknco_tick_box_checked else R.drawable.cooknco_tick_box,
    )
    accent?.let { row.setInt(R.id.cooknco_ingredient_box, "setColorFilter", it) }
    row.setTextViewText(R.id.cooknco_ingredient_label, line)
    // Dimmed once it is in, which is what the strikethrough does on the screen. Left alone
    // otherwise, so an unticked line keeps the colour the shade chose for its own theme.
    if (checked) {
        row.setTextColor(
            R.id.cooknco_ingredient_label,
            context.resources.getColor(R.color.cooknco_notification_muted, null),
        )
    }
    row.setOnClickPendingIntent(
        R.id.cooknco_ingredient_row,
        PendingIntent.getBroadcast(
            context,
            REQUEST_TICK_BASE + position,
            Intent(ACTION_COOK_SESSION_TICK)
                .setPackage(context.packageName)
                .putExtra(EXTRA_INGREDIENT, position),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )
    return row
}

/**
 * Its own channel, so that silencing the recipe in Android's settings does not silence the
 * timer that tells the cook the oven is done — and so that somebody who wants only the timer
 * can turn this one off and keep it.
 */
private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val s = stringsFor(AppLanguage.current.value)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, s.cookSessionChannelName, NotificationManager.IMPORTANCE_LOW)
            .apply {
                description = s.cookSessionChannelDescription
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
    )
}
