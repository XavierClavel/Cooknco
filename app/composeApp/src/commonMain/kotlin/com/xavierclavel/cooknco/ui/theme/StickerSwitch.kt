package com.xavierclavel.cooknco.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How long any switch in the app takes to move between states. Shared so the segmented
 * controls, the selection chips and the like button all settle at the same rate.
 */
const val StickerSwitchAnimationMillis = 220

/** The spec every one of those animates on — see [StickerSwitchAnimationMillis]. */
fun <T> stickerSwitchSpec(): FiniteAnimationSpec<T> =
    tween(StickerSwitchAnimationMillis, easing = FastOutSlowInEasing)

/**
 * A sticker-theme segmented switch: equally-wide segments inside one outlined pill, with
 * the highlight *sliding* from the old segment to the new one instead of being repainted
 * in place. The label colors ride the same 0..1 position, which is why they are lerped
 * rather than picked with an `if` — a hard color flip under a sliding highlight reads as
 * a glitch.
 *
 * [innerPadding] is the inset between the outline and the highlight, and it is why the
 * highlight never touches the outline on any edge; every switch in the app uses the same
 * 5dp, so they all look alike. The overall height follows from it
 * ([segmentHeight] + twice the inset) rather than being a parameter of its own.
 */
@Composable
fun <T> StickerSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50),
    segmentShape: Shape = shape,
    segmentHeight: Dp = 44.dp,
    innerPadding: Dp = 5.dp,
    spacing: Dp = 0.dp,
    fillColor: Color = CookncoBackground,
    highlightColor: Color = CookncoOrange,
    contentColor: Color = CookncoNavy,
    selectedContentColor: Color = CookncoWhite,
    borderColor: Color = CookncoNavy,
    borderWidth: Dp = 3.dp,
    shadowOffset: Dp = 4.dp,
    fontSize: TextUnit = 13.5.sp,
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = stickerSwitchSpec(),
        label = "sticker_switch_highlight",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(segmentHeight + innerPadding * 2)
            .stickerShadow(shape, borderColor, shadowOffset, shadowOffset)
            .clip(shape)
            .background(fillColor)
            .border(borderWidth, borderColor, shape)
            .padding(innerPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        val segmentWidth = (maxWidth - spacing * (options.size - 1)) / options.size
        val step = segmentWidth + spacing
        Box(
            modifier = Modifier
                .offset { IntOffset((step.toPx() * position).roundToInt(), 0) }
                .width(segmentWidth)
                .height(segmentHeight)
                .clip(segmentShape)
                .background(highlightColor),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                // 1f once the highlight has fully arrived under this segment, 0f once it
                // has fully left — mid-slide both neighbours hold a share of it.
                val arrived = (1f - abs(position - index)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(segmentHeight)
                        .clip(segmentShape)
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        fontSize = fontSize,
                        fontWeight = if (arrived > 0.5f) FontWeight.Bold else FontWeight.SemiBold,
                        color = lerp(contentColor, selectedContentColor, arrived),
                    )
                }
            }
        }
    }
}

/**
 * The sticker theme's on/off toggle (`Cooknco Mobile.dc.html`, turn 5 / option `5a`,
 * "Settings"): a 56x32 outlined pill, coral when on and cream when off, with a white
 * 22dp knob that slides across on the same spec as every other switch here. Material's
 * [androidx.compose.material3.Switch] is not it — its track, knob and optional icon are
 * Material's proportions, and no amount of `SwitchDefaults.colors` makes it this shape.
 *
 * [enabled] `false` is the "forced on, and you cannot change it" state the mockup shows
 * for auto-accept while an account is public: the whole control fades rather than
 * disappearing, so the value stays readable.
 */
@Composable
fun StickerToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(percent = 50)
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = stickerSwitchSpec(),
        label = "sticker_toggle_knob",
    )
    val outline = if (enabled) CookncoNavy else CookncoNavy.copy(alpha = 0.4f)
    val track by animateColorAsState(
        targetValue = when {
            !enabled && checked -> CookncoNavy.copy(alpha = 0.12f)
            checked -> CookncoOrange
            else -> CookncoBackground
        },
        animationSpec = stickerSwitchSpec(),
        label = "sticker_toggle_track",
    )
    Box(
        modifier = modifier
            .width(56.dp)
            .height(32.dp)
            .clip(shape)
            .background(track)
            .border(2.5.dp, outline, shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The knob travels the track minus its own width and the two 3dp insets.
        val travel = 56.dp - 6.dp - 22.dp
        Box(
            modifier = Modifier
                .offset { IntOffset((travel.toPx() * position).roundToInt(), 0) }
                .size(22.dp)
                .clip(CircleShape)
                .background(if (enabled) CookncoWhite else CookncoBackground)
                .border(2.dp, outline, CircleShape),
        )
    }
}
