package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

/**
 * Step-by-step cook mode. Ground is `#3e6b52` (dark green, turn 6 / option `6b`) rather
 * than the app's usual `#629677`, so this reads as a distinct mode rather than more
 * ordinary navigation.
 *
 * The mockup's "Screen on" toggle is left out: nothing in `platform/` offers a wake-lock
 * hook, and a toggle that doesn't actually keep the screen on is worse than none.
 */
@Composable
fun CookModeScreen(
    recipeId: Long,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: CookModeViewModel = viewModel(factory = CookModeViewModel.factory(recipeId))
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
                timerTotalSeconds = uiState.timerTotalSeconds,
                timerRemainingSeconds = uiState.timerRemainingSeconds,
                timerRunning = uiState.timerRunning,
                onToggleTimer = viewModel::toggleTimer,
                onPreviousStep = viewModel::previousStep,
                onNextStep = viewModel::nextStep,
                onClose = onNavigateBack,
            )
        }
    }
}

/** How much of each side moves a step when tapped; the middle 40% is left alone. */
private const val EDGE_TAP_FRACTION = 0.3f

@Composable
private fun CookModeContent(
    recipe: RecipeInfo,
    currentStep: Int,
    timerTotalSeconds: Int?,
    timerRemainingSeconds: Int?,
    timerRunning: Boolean,
    onToggleTimer: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val stepCount = recipe.steps.size
    val stepText = recipe.steps.getOrNull(currentStep) ?: ""
    val isLastStep = currentStep >= stepCount - 1

    Column(modifier = modifier.fillMaxSize()) {
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
                // Tap the left or right edge of the step to move through the recipe — the
                // point of cook mode is that a hand covered in flour can reach the next step
                // without aiming at a button. Before the padding, so the 18dp gutters count
                // as edge rather than as a dead strip down each side.
                //
                .edgeTaps(currentStep, stepCount) { offset, width ->
                    val edge = width * EDGE_TAP_FRACTION
                    when {
                        offset.x < edge -> onPreviousStep()
                        // The middle is deliberately inert, and so is the right edge on
                        // the last step: "Finish" closes the screen, which is not
                        // something to do to somebody who tapped to read on.
                        offset.x > width - edge && !isLastStep -> onNextStep()
                    }
                }
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

            if (timerTotalSeconds != null) {
                item {
                    val remaining = timerRemainingSeconds ?: timerTotalSeconds
                    StickerCard(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        shape = RoundedCornerShape(18.dp),
                        fillColor = CookncoGold,
                        shadowOffset = 5.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = formatTimer(remaining),
                                color = CookncoNavy,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                            )
                            Text(
                                text = s.timerFromThisStep,
                                color = CookncoNavy,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Row(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(CookncoNavy)
                                    .clickable(onClick = onToggleTimer)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (timerRunning) s.pause else s.start,
                                    color = CookncoWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }

            if (!isLastStep) {
                item {
                    Text(
                        text = s.nextIs(recipe.steps[currentStep + 1]),
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
        var claimed = false

        while (true) {
            // Final, so every other node has had its say about this event first.
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.isConsumed) claimed = true
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                moved = true
            }
            if (!change.pressed) {
                if (!moved && !claimed) onTap(down.position, size.width.toFloat())
                break
            }
        }
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
}

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Aya Amayri")
private val previewRecipe = RecipeInfo(
    id = 1L, version = 1L, title = "Harcha", dishClass = "MAIN_DISH", owner = previewOwner,
    ingredients = listOf(
        RecipeIngredientInfo(id = 1L, name = "Semoule moyen", amount = 400f, unit = "GRAM", type = "GRAIN"),
        RecipeIngredientInfo(id = 2L, name = "Beurre végétal", amount = 100f, unit = "GRAM"),
    ),
    steps = listOf(
        "Faire fondre le beurre végétal",
        "Ajouter tous les ingrédients dans un saladier",
        "Laisser reposer la pâte 30 mn",
        "Étaler la pâte et former des petits ronds",
        "Cuire chaque côté 5 minutes",
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
            timerTotalSeconds = 1800,
            timerRemainingSeconds = null,
            timerRunning = false,
            onToggleTimer = {},
            onPreviousStep = {},
            onNextStep = {},
            onClose = {},
        )
    }
}
