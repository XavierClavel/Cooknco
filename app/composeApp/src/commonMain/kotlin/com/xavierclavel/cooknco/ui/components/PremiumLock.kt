package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard

/**
 * The action sheet row for a feature a subscription pays for — the PDF export today, and
 * whatever premium is sold with next.
 *
 * **A locked feature is shown, not hidden.** Hiding it leaves an account that could pay
 * for it with no way to learn it exists: nothing in the app names what a subscription buys,
 * so a feature only subscribers can see is a feature only subscribers hear about. Shown
 * behind a padlock, the sheet is also the same sheet for everybody, which is one fewer
 * layout to think about than one that grows a row when an account is upgraded.
 *
 * What it must not do is offer the feature and have it refused. The route behind the export
 * answers a non-subscriber `403`, and a row that fires it would spend a print's worth of
 * waiting to reach the generic "the PDF could not be prepared" — a bug's wording for
 * something working exactly as intended. So [onLocked] replaces the action rather than
 * running alongside it, and [PremiumLockDialog] is what it opens.
 *
 * One helper rather than the same three lines on each screen, so the recipe sheet and the
 * cookbook sheet cannot come to different ideas of what locked means — the same reason
 * [SheetAction.destructive] is a flag and not a colour.
 */
fun premiumSheetAction(
    label: String,
    icon: ImageVector,
    /** [com.xavierclavel.cooknco.network.dto.UserInfo.isPremium], which the backend sets. */
    isPremium: Boolean,
    onUse: () -> Unit,
    onLocked: () -> Unit,
): SheetAction = SheetAction(
    label = label,
    onClick = if (isPremium) onUse else onLocked,
    icon = icon,
    locked = !isPremium,
)

/**
 * What tapping a locked row says: which feature it was, and that a premium account is what
 * opens it.
 *
 * It offers no way to subscribe, because there is none — a grant is handed out by an admin
 * and there is no billing behind it yet (see CLAUDE.md, "Premium is two columns"). A button
 * that led nowhere would be worse than the sentence: this tells the truth about why the row
 * is locked, and when there is something to sell it is the one place a call to action goes.
 */
@Composable
fun PremiumLockDialog(
    /** The feature's own label, as the sheet printed it, so the dialog names what was tapped. */
    feature: String,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.6f))
                .padding(horizontal = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadowOffset = 8.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The badge a destructive confirmation wears, in orange rather than in
                    // the error red: a lock is not a warning, and nothing went wrong here.
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CookncoOrange)
                            .border(3.dp, CookncoNavy, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = CookncoWhite)
                    }
                    Text(
                        text = s.premiumFeatureTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = s.premiumFeatureMessage(feature),
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = CookncoNavy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    StickerCard(
                        modifier = Modifier.padding(top = 20.dp).fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        fillColor = CookncoWhite,
                        onClick = onDismissRequest,
                    ) {
                        Text(
                            text = s.close,
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
