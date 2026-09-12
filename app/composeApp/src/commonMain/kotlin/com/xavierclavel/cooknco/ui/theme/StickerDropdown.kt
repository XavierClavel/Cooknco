package com.xavierclavel.cooknco.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
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
 * [anchor] is drawn in place and the menu is positioned under it by its measured height, so
 * a caller only says what opens the menu, never where the menu goes. [alignEnd] hangs it
 * from the anchor's trailing edge, which is what a narrow chip at the right of a row needs.
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
    alignEnd: Boolean = false,
    width: Dp? = null,
    maxHeight: Dp = 340.dp,
    gap: Dp = 8.dp,
    anchor: @Composable () -> Unit,
) {
    var anchorHeight by remember { mutableStateOf(0) }
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }

    Box(modifier = modifier) {
        Box(modifier = Modifier.onSizeChanged { anchorHeight = it.height }) {
            anchor()
        }
        if (expanded && items.isNotEmpty()) {
            Popup(
                alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
                offset = IntOffset(0, anchorHeight + gapPx),
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                StickerCard(
                    modifier = if (width != null) Modifier.width(width) else Modifier,
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
private fun StickerDropdownRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
