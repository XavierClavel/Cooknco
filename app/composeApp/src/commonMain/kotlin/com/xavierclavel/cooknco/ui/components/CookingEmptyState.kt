package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.resources.Res
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerPill
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/**
 * What a screen says when it has nothing to list, filling it: [CookingEmptyState], centred.
 *
 * Scrollable though it never overflows on a phone: a pull-to-refresh around it only hears a pull
 * from a child that scrolls, and a short landscape screen does overflow. The column is at least
 * as tall as the screen so it can centre itself in it.
 */
@Composable
fun CookingEmptyScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        CookingEmptyState(
            title = title,
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
            secondaryActionLabel = secondaryActionLabel,
            onSecondaryAction = onSecondaryAction,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 24.dp),
        )
    }
}

/**
 * What a list says when there is nothing in it: a kitchen at work, what would fill it, and the
 * way to it.
 *
 * One composable for every list that has one, so the feed, the cookbooks and a profile cannot
 * come to look like three different apps' idea of "empty". Only the words and the actions
 * change. This one takes the room it needs and no more, which is what an item inside a list that
 * already scrolls needs; [CookingEmptyScreen] is the one that fills a screen.
 *
 * [actionLabel] is the orange pill, the one thing the screen most wants done; the secondary
 * action is cream, the way Cancel sits beside Save. Both are optional: on somebody else's
 * profile there is nothing for the reader to do about it.
 */
@Composable
fun CookingEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CookingAnimation(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(22.dp))
        Text(
            text = title,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            textAlign = TextAlign.Center,
            lineHeight = 29.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = CookncoNavy,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        if (actionLabel == null) return@Column
        Spacer(Modifier.height(26.dp))
        // Stacked rather than side by side, which neither language's labels fit, and as wide as
        // the wider of the two so they read as a pair rather than as two stray pills.
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StickerPill(
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
                shadowOffset = 4.dp,
                fillColor = CookncoOrange,
                contentColor = CookncoWhite,
                contentPadding = PaddingValues(horizontal = 26.dp),
                onClick = onAction,
            ) {
                Text(actionLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            if (secondaryActionLabel != null) {
                StickerPill(
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                    shadowOffset = 4.dp,
                    contentPadding = PaddingValues(horizontal = 26.dp),
                    onClick = onSecondaryAction,
                ) {
                    Text(secondaryActionLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

/**
 * "Cooking" by Aravind Chowdary, from LottieFiles under the Lottie Simple License
 * (https://lottiefiles.com/free-animation/cooking-pi4fdhrTp0): three kitchens in turn, on a loop
 * of about thirty seconds.
 *
 * Played by `compottie-lite`, which leaves out the expression engine. The file's only
 * expressions are a `wiggle()` on the sparks over the wok, and without them the sparks still fly
 * along their keyframes: side by side, the two renders are hard to tell apart.
 *
 * As published, the file paints a white solid behind everything. That layer is the one edit made
 * to it, removed so the kitchen stands on the screen's own green rather than in a white box. The
 * two white solids left inside the wok scene are mattes for the spices and are never drawn.
 *
 * Its proportions are fixed here rather than read off the composition, so the picture takes its
 * room before the 400 KB of JSON has been parsed and the text beneath it does not jump.
 */
@Composable
private fun CookingAnimation(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(Res.readBytes("files/lottie/cooking.json").decodeToString())
    }
    Image(
        painter = rememberLottiePainter(composition = composition, iterations = Compottie.IterateForever),
        // Decoration: the title under it says what the screen means.
        contentDescription = null,
        modifier = modifier.aspectRatio(375f / 216f),
    )
}
