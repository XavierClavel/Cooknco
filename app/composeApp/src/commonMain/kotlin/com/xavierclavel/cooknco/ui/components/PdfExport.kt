package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.network.ApiException
import com.xavierclavel.cooknco.network.ExportedDocument
import com.xavierclavel.cooknco.platform.rememberDocumentSaver
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard

/**
 * An export, from the moment it is asked for to the moment the picker has the file.
 *
 * One shape for both screens that offer one: a recipe sheet and a whole cookbook differ in
 * what is printed and in nothing else the user can see.
 */
data class PdfExportState(
    val isExporting: Boolean = false,
    /** The finished PDF, waiting for a composition to hand it to the platform. */
    val document: ExportedDocument? = null,
    val error: String? = null,
)

/**
 * What to tell the person who asked for the export, when it did not come.
 *
 * The backend answers with a cause rather than a sentence, which is what makes this
 * translatable — and only the two causes a user can act on are named: a cookbook that is
 * too long to print, and a renderer that is busy right now. Anything else is one apology,
 * because "try again" is the only advice there is for it.
 */
fun pdfExportFailure(throwable: Throwable, s: Strings = stringsFor(AppLanguage.current.value)): String {
    val body = (throwable as? ApiException)?.body ?: throwable.message ?: return s.exportFailed
    return when {
        "cookbook_too_large_to_export" in body -> s.cookbookTooLargeToExport
        "pdf_renderer_busy" in body -> s.exportRendererBusy
        else -> s.exportFailed
    }
}

/**
 * Mounts what an export shows: a modal while it prints, the platform's save-a-file picker
 * when it is done, and a modal saying so when it is not.
 *
 * The saving happens here rather than in the view model because handing a file to the
 * system needs a platform handle a composition has and a view model does not — the document
 * is parked in the state, offered, and then [onSaved] takes it back out so a recomposition
 * cannot offer it twice.
 */
@Composable
fun PdfExportHost(
    state: PdfExportState,
    onSaved: () -> Unit,
    onErrorDismissed: () -> Unit,
) {
    val s = strings()
    val saver = rememberDocumentSaver()

    LaunchedEffect(state.document) {
        val document = state.document ?: return@LaunchedEffect
        saver.save(document.filename, document.bytes)
        onSaved()
    }

    if (state.isExporting) {
        PdfExportDialog(onDismissRequest = {}) {
            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            Text(
                text = s.preparingPdf,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    if (state.error != null) {
        PdfExportDialog(onDismissRequest = onErrorDismissed) {
            Text(
                text = s.exportFailedTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.error,
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
                fillColor = MaterialTheme.colorScheme.error,
                onClick = onErrorDismissed,
            ) {
                Text(
                    text = s.close,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoWhite,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * The card both of the above sit in. A plain [Dialog] rather than an AlertDialog, like
 * every other modal in the app, so it is sticker-styled rather than Material-styled.
 */
@Composable
private fun PdfExportDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                    verticalArrangement = Arrangement.Center,
                    content = content,
                )
            }
        }
    }
}
