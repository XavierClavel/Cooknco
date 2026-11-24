package com.xavierclavel.cooknco.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * The sticker theme's dropdown (`Cooknco Mobile.dc.html`, turn 5 / option `5a`, "Editor —
 * unit dropdown open"): a cream card on a hard navy shadow, hung under whatever opened it,
 * with 44dp rows split by 2px rules and the current choice filled coral with a tick.
 *
 * Every dropdown in the app is this one — the unit picker, the member search — rather than
 * Material's [androidx.compose.material3.DropdownMenu], whose white elevated surface and
 * 48dp list items are the one piece of Material chrome a sticker screen cannot absorb.
 *
 * [anchor] is drawn in place and the menu hangs under it — over it when the keyboard would
 * cover it there, see [DropdownPosition] — so a caller only says what opens the menu, never
 * where the menu goes. [alignEnd] hangs it from the anchor's trailing edge, which is what a
 * narrow chip at the right of a row needs.
 *
 * [width] defaults to the anchor's own measured width, which is what a menu hung under a
 * field should be. It has to be *some* width: the rows fill their parent and a [Popup] is
 * measured against the window, so an unconstrained menu is a screen-wide one — which is
 * what the member search was, being the only caller not passing an explicit width.
 */
@Composable
fun <T> StickerDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selected: (T) -> Boolean = { false },
    /** The caps heading a row sits under, or null for an ungrouped list. */
    sectionOf: (T) -> String? = { null },
    /**
     * The icon a row leads with, or null for one that reads as words alone.
     *
     * Null by default, and per row rather than per menu: a list of units or of dish classes
     * is a set of words with nothing to picture, while a list of *things to do* reads faster
     * with one. A menu where only some rows have an icon would look broken, so a caller
     * either gives every row one or gives none.
     */
    iconOf: (T) -> ImageVector? = { null },
    alignEnd: Boolean = false,
    /** Null means "as wide as the anchor". */
    width: Dp? = null,
    maxHeight: Dp = 340.dp,
    gap: Dp = 8.dp,
    /**
     * Whether the open menu takes focus — right for a menu a tap opens, wrong for one hung
     * under a field still being typed in: a focusable popup takes focus from the field as soon
     * as the first results arrive, which closes the keyboard and drops every key after the first.
     */
    focusable: Boolean = true,
    anchor: @Composable () -> Unit,
) {
    var anchorWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val menuWidth = width ?: with(density) { anchorWidth.toDp() }.takeIf { it > 0.dp }
    val position = DropdownPosition(
        gap = with(density) { gap.roundToPx() },
        alignEnd = alignEnd,
        keyboardHeight = WindowInsets.ime.getBottom(density),
    )

    Box(modifier = modifier) {
        Box(modifier = Modifier.onSizeChanged { anchorWidth = it.width }) {
            anchor()
        }
        if (expanded && items.isNotEmpty()) {
            Popup(
                popupPositionProvider = position,
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = focusable),
            ) {
                StickerCard(
                    modifier = if (menuWidth != null) Modifier.width(menuWidth) else Modifier,
                    shape = RoundedCornerShape(18.dp),
                    shadowOffset = 6.dp,
                ) {
                    Column(modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                        var lastSection: String? = null
                        items.forEachIndexed { index, item ->
                            val section = sectionOf(item)
                            if (section != null && section != lastSection) {
                                StickerDropdownSection(section)
                                lastSection = section
                            }
                            StickerDropdownRow(
                                label = label(item),
                                selected = selected(item),
                                icon = iconOf(item),
                                onClick = { onSelect(item) },
                            )
                            if (index != items.lastIndex) {
                                HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.12f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hangs the menu under its anchor, or over it when the keyboard would cover it there.
 *
 * The app draws edge to edge and nothing resizes the window for the keyboard, so the window
 * runs on under the keys: whether a menu fits below its anchor is measured against the
 * keyboard's top, not the window's bottom. A menu under a field being typed in — the member
 * search — sits right above the keyboard, and opened below it would be drawn behind the keys.
 */
internal data class DropdownPosition(
    val gap: Int,
    val alignEnd: Boolean,
    val keyboardHeight: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val below = anchorBounds.bottom + gap
        val fitsBelow = below + popupContentSize.height <= windowSize.height - keyboardHeight
        val y = if (fitsBelow) below else anchorBounds.top - gap - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceAtLeast(0),
        )
    }
}

@Composable
private fun StickerDropdownSection(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = CookncoGreenDark,
        letterSpacing = 0.9.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun StickerDropdownRow(
    label: String,
    selected: Boolean,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (selected) CookncoOrange else CookncoBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) CookncoWhite else CookncoGreenDark,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) CookncoWhite else CookncoNavy,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CookncoWhite)
        }
    }
}
