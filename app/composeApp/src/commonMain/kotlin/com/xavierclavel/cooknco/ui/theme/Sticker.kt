package com.xavierclavel.cooknco.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "sticker theme" primitives (see `Cooknco Mobile.dc.html`, turn 5 / option `5a`): a
 * cream card or pill on the green ground, outlined in 3px solid navy, sitting on a hard,
 * zero-blur navy shadow offset down-right. Every screen restyled in phase 2 should reach
 * for these rather than hand-rolling `.background().border()` again.
 *
 * The shadow is a plain [Modifier], not [androidx.compose.ui.draw.shadow] — that one draws
 * a blurred, semi-transparent shadow, which is the wrong look here. [stickerShadow] instead
 * paints a solid, offset copy of the same [Shape] *before* anything else in the modifier
 * chain draws, so a subsequent opaque `background()` covers all of it except the sliver
 * that pokes out past the content's own edge — that sliver is the "hard shadow".
 */
fun Modifier.stickerShadow(
    shape: Shape,
    color: Color = CookncoNavy,
    offsetX: Dp = 6.dp,
    offsetY: Dp = 6.dp,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    translate(left = offsetX.toPx(), top = offsetY.toPx()) {
        drawPath(path, color = color)
    }
}

/**
 * A cream (by default) card outlined in navy with a hard offset shadow behind it —
 * the base unit of the sticker theme. Content clips to [shape], so an image or photo
 * placed flush against an edge still respects the corner radius.
 *
 * Radius, fill, border and shadow are all parameters rather than baked in, since
 * different screens use different values (20dp for most cards, 14-18dp for smaller
 * ones, a smaller shadow offset for compact elements) — check the mockup per element.
 */
@Composable
fun StickerCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    fillColor: Color = CookncoBackground,
    borderColor: Color = CookncoNavy,
    borderWidth: Dp = 3.dp,
    shadowColor: Color = CookncoNavy,
    shadowOffset: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .stickerShadow(shape, shadowColor, shadowOffset, shadowOffset)
            .clip(shape)
            .background(fillColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(borderWidth, borderColor, shape),
        content = content,
    )
}

/**
 * A fully-rounded sticker pill — a chip, a segmented-control tab, a primary button.
 * Cream-on-green ("Show", tag chips) and colour-fill ("LOG IN", the active nav tab)
 * are both just this with a different [fillColor]/[contentColor]; there is no separate
 * "colored" composable to keep in sync.
 *
 * [content] runs inside a [RowScope] so an icon + label pill lays out horizontally
 * with no extra wrapper, and content color defaults through [LocalContentColor] so a
 * plain `Text`/`Icon` inside picks up [contentColor] automatically.
 */
@Composable
fun StickerPill(
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fillColor: Color = CookncoBackground,
    contentColor: Color = CookncoNavy,
    borderColor: Color = CookncoNavy,
    borderWidth: Dp = 3.dp,
    shadowColor: Color = CookncoNavy,
    shadowOffset: Dp = 5.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .height(height)
            .stickerShadow(shape, shadowColor, shadowOffset, shadowOffset)
            .clip(shape)
            .background(fillColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(borderWidth, borderColor, shape)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * A square-ish sticker icon button: back/close/menu buttons (44dp, 14dp radius, 3px
 * shadow) and the raised gold Create button (58dp, 20dp radius, 5px shadow) are both
 * this with different [size]/[shape]/[fillColor]/[shadowOffset] — see the mockup for
 * the exact numbers per element, they are not all the same.
 */
@Composable
fun StickerIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    fillColor: Color = CookncoBackground,
    contentColor: Color = CookncoNavy,
    borderColor: Color = CookncoNavy,
    borderWidth: Dp = 3.dp,
    shadowColor: Color = CookncoNavy,
    shadowOffset: Dp = 3.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .stickerShadow(shape, shadowColor, shadowOffset, shadowOffset)
            .clip(shape)
            .background(fillColor)
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/**
 * A multi-line text area set straight onto the sticker surface behind it — no inner box,
 * no outline, no floating label.
 *
 * The mockup never nests a field inside a card: an editable note, a step's text and the
 * tips note are all type on cream, reading exactly as they will once saved. A Material
 * [androidx.compose.material3.TextField] there draws a second filled, outlined rectangle
 * inside the card's own outline, which is what makes those places read as a different app.
 */
@Composable
fun StickerTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.5.sp,
    lineHeight: TextUnit = 21.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    textColor: Color = CookncoNavy,
    placeholderColor: Color = CookncoGreenDark,
    cursorColor: Color = CookncoOrange,
) {
    val textStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        color = textColor,
    )
    Box(modifier = modifier) {
        if (value.isEmpty()) {
            Text(placeholder, style = textStyle.copy(color = placeholderColor))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(cursorColor),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
