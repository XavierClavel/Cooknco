package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import com.xavierclavel.cooknco.data.CookTimerState
import com.xavierclavel.cooknco.data.formatCookTimer
import com.xavierclavel.cooknco.network.dto.RecipeStepIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeStepInfo
import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.platform.rememberExactTimerConsent
import com.xavierclavel.cooknco.ui.components.StepImage
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

/**
 * Step-by-step cook mode. Ground is `#3e6b52` (dark green, turn 6 / option `6b`) rather
 * than the app's usual `#629677`, so this reads as a distinct mode rather than more
 * ordinary navigation.
 *
 * The mockup's "Screen on" toggle is left out: nothing in `platform/` offers a wake-lock
 * hook, and a toggle that doesn't actually keep the screen on is worse than none.
 *
 * A timer belongs to the step that started it, and the card is shown on that step only. It
 * goes on counting while the cook reads ahead — see `CookTimer` — but what says so then is
 * the notification, not a card that would otherwise follow them onto every step and claim a
 * timer each one does not have.
 */
@Composable
fun CookModeScreen(
    recipeId: Long,
    /** Portions the cook picked on the recipe screen; 0 to use the recipe's own yield. */
    servings: Int = 0,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: CookModeViewModel = viewModel(factory = CookModeViewModel.factory(recipeId, servings))
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(CookncoGreenDark)) {
        when {
            uiState.isLoading -> CircularProgressIndicator(
                color = CookncoWhite,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center),
            )

            uiState.error != null && uiState.recipe == null -> Text(
                text = uiState.error!!,
                color = CookncoWhite,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )

            uiState.recipe != null -> CookModeContent(
                recipe = uiState.recipe!!,
                currentStep = uiState.currentStep,
                stepIngredients = viewModel.stepIngredients(uiState),
                checkedIngredients = uiState.checkedIngredients[uiState.currentStep].orEmpty(),
                servings = uiState.servings,
                onToggleIngredient = viewModel::toggleIngredientChecked,
                stepDurationSeconds = uiState.stepDurationSeconds,
                timer = uiState.stepTimer,
                timerRemainingSeconds = uiState.timerRemainingSeconds,
                onToggleTimer = viewModel::toggleTimer,
                onStopTimer = viewModel::stopTimer,
                onPreviousStep = viewModel::previousStep,
                onNextStep = viewModel::nextStep,
                onClose = onNavigateBack,
            )
        }

        if (uiState.askToRingOnTime) {
            val s = strings()
            val consent = rememberExactTimerConsent()
            // Not a destructive confirmation, so it borrows the shape and not the colour:
            // gold, like the timer card it is about, rather than the error red.
            StickerConfirmDialog(
                icon = Icons.Outlined.Alarm,
                title = s.timerRingOnTimeTitle,
                message = s.timerRingOnTimeMessage,
                confirmText = s.timerRingOnTimeConfirm,
                dismissText = s.timerRingOnTimeDismiss,
                iconColor = CookncoGold,
                confirmColor = CookncoOrange,
                onConfirm = {
                    consent.request()
                    viewModel.dismissRingOnTimeAsk()
                },
                onDismissRequest = viewModel::dismissRingOnTimeAsk,
            )
        }
    }
}

/**
 * How much of each side moves a step when tapped; the middle is left alone to read.
 *
 * Wide, because it costs nothing: the detector never consumes, so the list still scrolls
 * from anywhere including the strips, and the only thing the middle buys is somewhere to
 * rest a thumb without changing the step.
 */
private const val EDGE_TAP_FRACTION = 0.38f

@Composable
private fun CookModeContent(
    recipe: RecipeInfo,
    currentStep: Int,
    stepIngredients: List<Pair<RecipeStepIngredientInfo, RecipeIngredientInfo>> = emptyList(),
    checkedIngredients: Set<Int> = emptySet(),
    servings: Int = 0,
    onToggleIngredient: (Int) -> Unit = {},
    stepDurationSeconds: Int?,
    timer: CookTimerState?,
    timerRemainingSeconds: Int,
    onToggleTimer: () -> Unit,
    onStopTimer: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val stepCount = recipe.steps.size
    val step = recipe.steps.getOrNull(currentStep)
    val stepText = step?.text ?: ""
    val isLastStep = currentStep >= stepCount - 1

    Column(
        modifier = modifier
            .fillMaxSize()
            // On the whole screen, not on the list of steps.
            //
            // The list is only the middle band: the title bar and the progress segments are
            // above it and the two buttons are below, and on a short step — one line, no
            // timer — the text sits at the very top of that band, right under the strip of
            // header that was not listening. Tapping beside what you are reading is the
            // obvious thing to do, and it was landing in the one place that did nothing.
            //
            // Everything that should keep its own tap still does, because it consumes the
            // release: close, the two step buttons, the timer's start.
            .edgeTaps(currentStep, stepCount) { offset, width ->
                val edge = width * EDGE_TAP_FRACTION
                when {
                    offset.x < edge -> onPreviousStep()
                    // The middle is deliberately inert, and so is the right edge on the
                    // last step: "Finish" closes the screen, which is not something to do
                    // to somebody who tapped to read on.
                    offset.x > width - edge && !isLastStep -> onNextStep()
                }
            },
    ) {
        // ── Top bar: close, title ──────────────────────────────────────────────
        // The mockup also shows a "Screen on" toggle here — dropped for now, since
        // nothing in platform/ offers a wake-lock hook to back it with real behavior.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerIconButton(onClick = onClose, shadowOffset = 0.dp) {
                Icon(Icons.Outlined.Close, contentDescription = s.close)
            }
            Text(
                text = recipe.title,
                color = CookncoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Progress segments ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(stepCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (index <= currentStep) CookncoGold else CookncoBackground)
                        .border(1.5.dp, CookncoNavy, RoundedCornerShape(3.dp)),
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Column {
                    Text(
                        text = s.stepOf(currentStep + 1, stepCount),
                        color = CookncoWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = stepText,
                        color = CookncoWhite,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // Between the words and the ingredients: it shows what the step is asking for,
            // which is worth more mid-cook than a list of what goes into it. Only drawn when
            // there is one - a step without a picture gets no frame and no placeholder.
            val stepImageId = step?.id
            val stepImageVersion = step?.imageVersion ?: 0
            if (stepImageId != null && stepImageVersion > 0) {
                item {
                    StickerCard(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        shape = RoundedCornerShape(18.dp),
                        shadowOffset = 6.dp,
                    ) {
                        StepImage(
                            stepId = stepImageId,
                            version = stepImageVersion,
                            contentDescription = s.recipeStepPhoto,
                            // The shape the bucket stores them in, so nothing is cropped
                            // twice - see ImageBucket.RECIPE_STEP.
                            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                        )
                    }
                }
            }

            if (stepIngredients.isNotEmpty()) {
                item {
                    Text(
                        text = s.usedInThisStep,
                        color = CookncoWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    StickerCard(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(18.dp),
                        shadowOffset = 6.dp,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            stepIngredients.forEachIndexed { position, (used, ingredient) ->
                                StepIngredientRow(
                                    ingredient = ingredient,
                                    // Already resolved by the view model.
                                    amount = used.amount,
                                    servings = servings,
                                    recipeYield = recipe.yield ?: 1,
                                    checked = used.index in checkedIngredients,
                                    onToggle = { onToggleIngredient(used.index) },
                                    showDivider = position < stepIngredients.lastIndex,
                                )
                            }
                        }
                    }
                }
            }

            if (timer != null || stepDurationSeconds != null) {
                item {
                    CookTimerCard(
                        timer = timer,
                        stepDurationSeconds = stepDurationSeconds,
                        remainingSeconds = timerRemainingSeconds,
                        onToggle = onToggleTimer,
                        onStop = onStopTimer,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    )
                }
            }

            if (!isLastStep) {
                item {
                    Text(
                        text = s.nextIs(recipe.steps[currentStep + 1].text),
                        color = CookncoWhite,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 16.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── Bottom navigation ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerCard(
                modifier = Modifier.size(width = 72.dp, height = 58.dp),
                shape = RoundedCornerShape(16.dp),
                shadowOffset = 4.dp,
                onClick = onPreviousStep,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = s.back,
                    tint = CookncoNavy,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            StickerCard(
                modifier = Modifier.weight(1f).height(58.dp),
                shape = RoundedCornerShape(16.dp),
                fillColor = CookncoOrange,
                shadowOffset = 4.dp,
                onClick = if (isLastStep) onClose else onNextStep,
            ) {
                Text(
                    text = if (isLastStep) s.finish else s.nextStep,
                    color = CookncoWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * One ingredient this step uses, to be ticked off as it goes in.
 *
 * The row is the artboard's — the ingredient's picture, its name, and its amount at the
 * trailing edge, on a cream card with hairline dividers between rows. What the artboard does
 * not have is the tick box, which is the point of showing the list here at all: hands are
 * busy and a cook looks back having forgotten whether the butter went in. It sits at the
 * leading edge and takes the whole row as its target, so it can be hit without looking.
 *
 * The amount is scaled to the portions being cooked, like every amount in the product, and is
 * simply absent when the server had nothing to work it out from: two steps sharing an
 * ingredient without saying how it divides are worth no number each, and inventing an even
 * split would be inventing a measurement.
 */
@Composable
private fun StepIngredientRow(
    ingredient: RecipeIngredientInfo,
    amount: Float?,
    servings: Int,
    recipeYield: Int,
    checked: Boolean,
    onToggle: () -> Unit,
    showDivider: Boolean,
) {
    val s = strings()
    val scaled = scaleAmount(amount, servings, recipeYield)
    val unit = if (ingredient.unit == "NONE" || ingredient.unit == "UNIT") "" else s.unitName(ingredient.unit)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (checked) CookncoOrange else CookncoWhite)
                    .border(2.dp, CookncoNavy, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = CookncoWhite,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Image(
                painter = painterResource(ingredientIcon(ingredient.type)),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(CookncoGreenLight)
                    .border(2.dp, CookncoNavy, RoundedCornerShape(9.dp))
                    .padding(5.dp),
            )
            Text(
                text = ingredient.name,
                color = if (checked) CookncoGreenDark.copy(alpha = 0.55f) else CookncoNavy,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (scaled.isNotEmpty()) {
                Text(
                    text = if (unit.isEmpty()) scaled else "$scaled $unit",
                    color = if (checked) CookncoGreenDark.copy(alpha = 0.55f) else CookncoNavy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(CookncoNavy.copy(alpha = 0.1f)),
            )
        }
    }
}

/**
 * The timer, in whichever of its four states it is in: not started, running, paused, rung.
 *
 * [timer] is this step's, or null when it has none started — in which case the card offers
 * whatever duration the step's text mentions ([stepDurationSeconds]). The button says what
 * tapping it does rather than what the timer is doing, which are opposites.
 *
 * Rung turns it orange. Nothing else on this screen is, so it reads across a kitchen from
 * the shape alone — which matters for the one state the cook is not holding the phone for.
 */
@Composable
private fun CookTimerCard(
    timer: CookTimerState?,
    stepDurationSeconds: Int?,
    remainingSeconds: Int,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val seconds = if (timer != null) remainingSeconds else stepDurationSeconds ?: return
    val label = when {
        timer == null || timer.running -> s.timerLabel
        timer.finished -> s.timerTimeIsUp
        else -> s.timerPaused
    }
    val action = when {
        timer == null || timer.finished -> s.start
        timer.running -> s.pause
        else -> s.resume
    }

    StickerCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        fillColor = if (timer?.finished == true) CookncoOrange else CookncoGold,
        shadowOffset = 5.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatCookTimer(seconds),
                color = CookncoNavy,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                // Tabular figures. Without them the glyphs are proportional, so "28:11" is
                // narrower than "28:00" and the label beside it twitches left and right once
                // a second — on the one element of this screen nobody is meant to have to
                // watch closely. Copied from the ambient style rather than built fresh, so
                // the theme's typeface survives.
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                text = label,
                color = CookncoNavy,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (timer != null) {
                // Only once there is something to stop. Dropping the timer is also the only
                // way back to the step in front of the cook having its own card again.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                        .clickable(onClick = onStop),
                ) {
                    Icon(
                        Icons.Outlined.Stop,
                        contentDescription = s.stopTimer,
                        tint = CookncoNavy,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(CookncoNavy)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = action,
                    color = CookncoWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * Taps on the left and right of the step, over a list that also scrolls.
 *
 * This was `detectTapGestures`, and on a long step it stopped working in places. That
 * detector abandons the gesture the moment *anything* consumes an event in it, and the
 * list underneath consumes in two situations that have nothing to do with the user's
 * intent: it takes the press outright when a fling is still settling, so that a tap stops
 * the scroll, and it takes the movement as soon as a finger wanders past the touch slop.
 * Neither happens on a step short enough to fit the screen — which is why the same spot
 * answered on one step and not the next.
 *
 * So the rule here is what it should have been: a tap moves a step unless the finger
 * actually moved, or something in front claimed the press for itself — a button, the timer.
 * Movement is measured rather than inferred from consumption, and nothing is consumed here,
 * so dragging still scrolls the list exactly as before.
 *
 * [onTap] is handed the position and the width it should be read against, since the node
 * this sits on is wider than the padded content inside it.
 */
private fun Modifier.edgeTaps(
    vararg keys: Any?,
    onTap: (Offset, Float) -> Unit,
): Modifier = pointerInput(*keys) {
    awaitEachGesture {
        // Not `requireUnconsumed`: a press that lands while the list is settling is
        // consumed before it gets here, and that press is exactly the one a cook means.
        val down = awaitFirstDown(requireUnconsumed = false)
        var moved = false

        while (true) {
            // Final, so every other node has had its say about this event first.
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                moved = true
            }
            if (!change.pressed) {
                // Consumption is only asked about here, on the release, and never on the
                // press or the moves in between. A button in front consumes the release,
                // which is what should hand it the tap; the list consumes the *press* while
                // a fling settles and consumes *moves* once a drag begins, and treating
                // either as "somebody else wanted this" is what kept eating the tap.
                if (!moved && !change.isConsumed) onTap(down.position, size.width.toFloat())
                break
            }
        }
    }
}

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Aya Amayri")
private val previewRecipe = RecipeInfo(
    id = 1L, version = 1L, title = "Harcha", dishClass = "MAIN_DISH", owner = previewOwner,
    ingredients = listOf(
        RecipeIngredientInfo(id = 1L, name = "Semoule moyen", amount = 400f, unit = "GRAM", type = "GRAIN"),
        RecipeIngredientInfo(id = 2L, name = "Beurre végétal", amount = 100f, unit = "GRAM"),
    ),
    steps = listOf(
        RecipeStepInfo("Faire fondre le beurre végétal"),
        RecipeStepInfo("Ajouter tous les ingrédients dans un saladier"),
        RecipeStepInfo("Laisser reposer la pâte 30 mn", durationSeconds = 1800),
        RecipeStepInfo("Étaler la pâte et former des petits ronds"),
        RecipeStepInfo("Cuire chaque côté 5 minutes", durationSeconds = 300),
    ),
    creationDate = 0L, likesCount = 8,
)

@Preview(showBackground = true)
@Composable
fun CookModeScreenPreview() {
    CookncoTheme {
        CookModeContent(
            recipe = previewRecipe,
            currentStep = 2,
            stepDurationSeconds = 1800,
            timer = null,
            timerRemainingSeconds = 0,
            onToggleTimer = {},
            onStopTimer = {},
            onPreviousStep = {},
            onNextStep = {},
            onClose = {},
        )
    }
}
