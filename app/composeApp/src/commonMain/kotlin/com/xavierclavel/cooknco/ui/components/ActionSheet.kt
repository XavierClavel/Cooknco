package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.swallowTaps
import com.xavierclavel.cooknco.ui.theme.sheetScrim

/**
 * One line of a [StickerActionSheet].
 *
 * [destructive] and [locked] are the only styling a caller gets to choose, and they are
 * properties of the action rather than colour arguments: the sheet decides what each looks
 * like, so deleting a recipe and deleting a cookbook cannot end up different reds, and two
 * locked features cannot end up two different kinds of unavailable.
 */
data class SheetAction(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    /**
     * Optional, and the sheet lays out the same either way: a row with no icon keeps the
     * same text indent as one with, so a partly-iconned sheet does not come out ragged.
     */
    val icon: ImageVector? = null,
    /**
     * Shown dimmed, behind a padlock, and still tappable — a locked row is how a feature
     * this account cannot use is advertised rather than hidden.
     *
     * The sheet does not decide what tapping one does: an action that is locked is expected
     * to carry an [onClick] that explains the lock, not one that runs the feature and is
     * refused. Which is why this dims rather than disables — a row nothing happens on reads
     * as a broken sheet, and leaves the person no way to find out why.
     */
    val locked: Boolean = false,
)

/**
 * The "···" overflow menu: a full-width sheet over a dark scrim, headed by what is being
 * acted on (see `Cooknco Mobile.dc.html`, turn 5 / option `5a`, "Recipe — owner actions").
 *
 * Not a Material `DropdownMenu`: the mockup is a sheet, and a small anchored dropdown in
 * the corner of a banner photo is a different thing entirely.
 *
 * The sheet dismisses itself before running an action. Several of them navigate away or
 * open a confirmation, and a sheet still on screen behind either is a sheet the user has
 * to dismiss twice.
 */
@Composable
fun StickerActionSheet(
    title: String,
    actions: List<SheetAction>,
    onDismissRequest: () -> Unit,
    subtitle: String? = null,
) {
    val s = strings()
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                .sheetScrim(onDismissRequest),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .swallowTaps()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 26.dp),
            ) {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // The header has to be bigger than the rows, not merely first.
                        // At 16sp bold navy over 15.5sp bold navy it was the same text as an
                        // action, so a sheet with no subtitle - a profile, say - read as a
                        // list whose top entry happened to do nothing when tapped.
                        Column(modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 14.dp)) {
                            Text(
                                text = title,
                                fontSize = 20.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = CookncoNavy,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CookncoGreenDark,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        actions.forEach { action ->
                            ActionSheetDivider()
                            ActionSheetRow(
                                label = action.label,
                                icon = action.icon,
                                locked = action.locked,
                                textColor = if (action.destructive) MaterialTheme.colorScheme.error else CookncoNavy,
                                onClick = { onDismissRequest(); action.onClick() },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    shadowOffset = 4.dp,
                    onClick = onDismissRequest,
                ) {
                    Text(
                        text = s.cancel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSheetRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    locked: Boolean = false,
    textColor: Color = CookncoNavy,
) {
    val s = strings()
    // Faded rather than greyed: the sheet is cream, so a grey would read as a second colour
    // in it. The same navy at half strength reads as the same row, turned down.
    val tint = if (locked) textColor.copy(alpha = 0.45f) else textColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // A Box either way, so the labels line up whether or not a given action has an icon.
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
            }
        }
        Text(text = label, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = tint)
        if (locked) {
            // On the trailing edge, so the row still opens with what it does and the lock
            // qualifies it — and the leading icon stays what it was, so a locked export and
            // an unlocked one are recognisably the same action.
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = s.premiumLocked,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ActionSheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(CookncoNavy.copy(alpha = 0.12f)),
    )
}
