package com.xavierclavel.cooknco.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.ui.i18n.strings
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The sticker theme's destructive-action confirmation modal (see `Cooknco Mobile.dc.html`,
 * turn 5 / option `5a`, "Delete recipe — confirmation"): a navy scrim over the whole
 * screen, a cream card with a hard offset shadow, a colored icon badge, a title, a body,
 * and two full-width stacked buttons — the destructive action on top, the safe way out
 * below it. Every destructive confirmation in the app (delete recipe, delete cookbook,
 * unfollow) uses this one composable rather than each screen rolling its own.
 *
 * Built on [Dialog] directly rather than [androidx.compose.material3.AlertDialog], so the
 * whole thing can be sticker-styled instead of inheriting Material's dialog chrome —
 * [DialogProperties.usePlatformDefaultWidth] is off so the card can be full-width like
 * the mockup rather than clamped to Material's default dialog width.
 */
@Composable
fun StickerConfirmDialog(
    icon: ImageVector,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Null takes the catalogue's word for it. A literal default here would be one English
     * string every caller inherits without ever naming it — which is exactly how "Cancel"
     * survived the translation of every screen that shows this dialog.
     */
    dismissText: String? = null,
    isConfirming: Boolean = false,
    iconColor: Color = MaterialTheme.colorScheme.error,
    confirmColor: Color = MaterialTheme.colorScheme.error,
) {
    Dialog(
        // Nothing dismisses this once the action is in flight — same reasoning as every
        // other in-flight confirm in this app: there's often nothing sensible to go back
        // to once the request has been sent.
        onDismissRequest = { if (!isConfirming) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.6f))
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            StickerCard(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                shadowOffset = 8.dp,
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(iconColor)
                            .border(3.dp, CookncoNavy, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = CookncoWhite)
                    }
                    Text(
                        text = title,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = message,
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = CookncoNavy,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Column(
                        modifier = Modifier.padding(top = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StickerCard(
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            fillColor = confirmColor,
                            onClick = if (!isConfirming) onConfirm else null,
                        ) {
                            if (isConfirming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp).align(Alignment.Center),
                                    color = CookncoWhite,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = confirmText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CookncoWhite,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                        StickerCard(
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            fillColor = CookncoWhite,
                            onClick = if (!isConfirming) onDismissRequest else null,
                        ) {
                            Text(
                                text = dismissText ?: strings().cancel,
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
    }
}
