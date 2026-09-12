package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
 * [destructive] is the only styling a caller gets to choose, and it is a property of the
 * action rather than a colour argument: the sheet decides what destructive looks like, so
 * deleting a recipe and deleting a cookbook cannot end up different reds.
 */
data class SheetAction(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
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
                        Column(modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 12.dp)) {
                            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
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
private fun ActionSheetRow(label: String, onClick: () -> Unit, textColor: Color = CookncoNavy) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = textColor)
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
