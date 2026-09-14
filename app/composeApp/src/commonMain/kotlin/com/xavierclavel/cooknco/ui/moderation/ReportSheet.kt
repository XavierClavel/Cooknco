package com.xavierclavel.cooknco.ui.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xavierclavel.cooknco.network.ReportReason
import com.xavierclavel.cooknco.network.ReportTargetType
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.StickerTextArea
import com.xavierclavel.cooknco.ui.theme.sheetScrim
import com.xavierclavel.cooknco.ui.theme.swallowTaps

/**
 * Sends a recipe or an account to the moderation queue.
 *
 * The one user-facing end of moderation in the app: everything a moderator can then do
 * about it happens in the backoffice, and the app never learns what was decided — which is
 * why the acknowledgement promises a look rather than an outcome.
 *
 * Built like the other sheets ([com.xavierclavel.cooknco.ui.components.StickerActionSheet],
 * `AddToCookbookSheet`) — a full-screen [Dialog] over a navy scrim with a cream panel at
 * the bottom — rather than as a confirmation dialog, because this one has a form in it: a
 * reason, and an optional note for whoever reads the queue.
 *
 * [targetLabel] is the recipe's title or the account's name, shown under the heading so
 * there is no reporting the wrong thing from a screen that shows several.
 */
@Composable
fun ReportSheet(
    targetType: ReportTargetType,
    targetId: Long,
    targetLabel: String,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    // Keyed by target: this is opened from screens that can show more than one thing worth
    // reporting, and an unkeyed view model would hand the second one the first one's draft.
    val viewModel: ReportViewModel = viewModel(
        key = "report-${targetType.value}-$targetId",
        factory = ReportViewModel.factory(targetType, targetId),
    )
    val state by viewModel.uiState.collectAsState()

    // The view model belongs to the screen behind the sheet, so it outlives the sheet.
    // Without this, dismissing a half-written report and opening it again would show the
    // draft — or worse, the acknowledgement for a report that is already filed.
    DisposableEffect(Unit) { onDispose { viewModel.reset() } }

    Dialog(
        // Nothing dismisses this while the report is in flight: it is one request, and
        // there is no way to tell afterwards whether it went.
        onDismissRequest = { if (!state.isSending) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                .sheetScrim { if (!state.isSending) onDismissRequest() },
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .swallowTaps()
                    // The panel carries a text field, so it rides above the keyboard rather
                    // than sitting under it.
                    .imePadding()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 26.dp),
            ) {
                StickerCard(
                    // fill = false so the panel stays as tall as its content until that
                    // content is taller than the screen, and only then gives way to the
                    // scroll inside it.
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    if (state.sent) {
                        ReportSentPanel(onClose = onDismissRequest)
                    } else {
                        ReportForm(
                            targetLabel = targetLabel,
                            state = state,
                            onSelectReason = viewModel::selectReason,
                            onCommentChange = viewModel::updateComment,
                            onSend = viewModel::send,
                        )
                    }
                }
                // The acknowledgement has its own way out, and a second one under it would
                // read as a choice between them.
                if (!state.sent) {
                    Spacer(Modifier.height(10.dp))
                    StickerCard(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        shadowOffset = 4.dp,
                        onClick = if (state.isSending) null else onDismissRequest,
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
}

@Composable
private fun ReportForm(
    targetLabel: String,
    state: ReportUiState,
    onSelectReason: (ReportReason) -> Unit,
    onCommentChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val s = strings()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
    ) {
        Text(
            text = s.reportTitle,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
        )
        Text(
            text = targetLabel,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = CookncoGreenDark,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = s.reportDescription,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = CookncoNavy,
            modifier = Modifier.padding(top = 12.dp),
        )

        SheetSectionLabel(s.reportReasonLabel)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportReason.entries.forEach { reason ->
                ReasonRow(
                    label = s.reportReasonName(reason),
                    selected = reason == state.reason,
                    onClick = { onSelectReason(reason) },
                )
            }
        }

        SheetSectionLabel(s.reportDetails)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CookncoWhite)
                .border(2.dp, CookncoNavy, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            StickerTextArea(
                value = state.comment,
                onValueChange = onCommentChange,
                placeholder = s.reportDetailsPlaceholder,
            )
        }

        if (state.error != null) {
            Text(
                text = state.error,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        StickerPill(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            height = 52.dp,
            fillColor = CookncoOrange,
            contentColor = CookncoWhite,
            onClick = if (state.isSending) null else onSend,
        ) {
            if (state.isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = CookncoWhite,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(text = s.sendReport, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** What the sheet becomes once the report is filed. */
@Composable
private fun ReportSentPanel(onClose: () -> Unit) {
    val s = strings()
    Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CookncoOrange)
                .border(3.dp, CookncoNavy, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = CookncoWhite)
        }
        Text(
            text = s.reportSentTitle,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = s.reportSentMessage,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            color = CookncoNavy,
            modifier = Modifier.padding(top = 8.dp),
        )
        StickerCard(
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            fillColor = CookncoWhite,
            onClick = onClose,
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

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = CookncoGreenDark,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun ReasonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) CookncoOrange else CookncoWhite)
            .clickable(onClick = onClick)
            .border(2.dp, CookncoNavy, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            lineHeight = 19.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) CookncoWhite else CookncoNavy,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = CookncoWhite,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
