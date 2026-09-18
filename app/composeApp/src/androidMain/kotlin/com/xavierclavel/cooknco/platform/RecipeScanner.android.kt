package com.xavierclavel.cooknco.platform

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How many pages one scan may take.
 *
 * A recipe that runs over a spread is the reason this is not one; a number much past it
 * would be somebody scanning a chapter into a single recipe, and every page is another image
 * held in memory while its text is read.
 */
private const val MAX_PAGES = 4

/**
 * The scanner is delivered by Google Play services rather than bundled, so the module is
 * downloaded the first time it is asked for and the app carries none of it. On a device
 * without Play services, or with too little memory for it, `getStartScanIntent` fails and
 * the launch reports [ScanResult.Failed] — there is no second way to do this on Android.
 *
 * `SCANNER_MODE_FULL` is what gives the flow its filters, its retake and its page
 * reordering; gallery import is on because a photographed page is a scan somebody already
 * took, and refusing it would send them back to the camera for a picture they have.
 *
 * Only the JPEG pages are requested. The PDF the scanner can also produce is another copy of
 * the same thing, and nothing here keeps the scan: the pages are read for their text and the
 * file is the system's to reclaim.
 */
private val scannerOptions = GmsDocumentScannerOptions.Builder()
    .setGalleryImportAllowed(true)
    .setPageLimit(MAX_PAGES)
    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
    .build()

@Composable
actual fun rememberRecipeScanner(onScanned: (ScanResult) -> Unit): RecipeScannerLauncher {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onScanned)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Backing out of the scanner is not a failure and says nothing, the way cancelling
        // the photo picker says nothing.
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            ?.pages.orEmpty()
            .map { it.imageUri }
        scope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { readPages(context, pages) } }
            callback.value(read.map { ScanResult.Read(it) }.getOrElse { ScanResult.Failed })
        }
    }

    return remember(launcher, activity) {
        RecipeScannerLauncher {
            val host = activity
            if (host == null) {
                callback.value(ScanResult.Failed)
                return@RecipeScannerLauncher
            }
            GmsDocumentScanning.getClient(scannerOptions)
                .getStartScanIntent(host)
                .addOnSuccessListener { sender ->
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener { callback.value(ScanResult.Failed) }
        }
    }
}

/**
 * Reads every page's text, blocking on each recognition in turn.
 *
 * `Tasks.await` rather than a suspending wrapper: this already runs on the IO dispatcher, and
 * awaiting the task there costs a parked thread instead of another dependency
 * (`kotlinx-coroutines-play-services`) pulled in for one call. The pages are read one after
 * another because they are read once, at a moment the user is waiting on.
 */
private fun readPages(context: Context, pages: List<Uri>): List<OcrLine> {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        pages.flatMapIndexed { page, uri ->
            val image = InputImage.fromFilePath(context, uri)
            val text = Tasks.await(recognizer.process(image))
            val boxed = text.textBlocks
                .flatMap { it.lines }
                .mapNotNull { line -> line.boundingBox?.let { line.text to it } }
                .filter { (text, _) -> text.isNotBlank() }
            normalise(page, boxed)
        }
    } finally {
        recognizer.close()
    }
}

/**
 * Normalises a page's boxes against the text on it rather than against the image.
 *
 * The obvious denominator is the image's own width and height, and it is the wrong one
 * twice over. ML Kit reports boxes in the upright coordinate space it recognised in, while
 * `InputImage` reports the dimensions of the file as it was decoded, so a page whose EXIF
 * says it is rotated normalises to numbers past 1 on one axis and a fraction of the page on
 * the other. And even when they agree, the margins the scanner left around the page are
 * counted in, which moves the gutter [com.xavierclavel.cooknco.data.RecipeScan] looks for
 * off centre by however much of the desk got into frame.
 *
 * The union of the boxes is the text block, which is what both readings of the geometry
 * actually mean: the widest line spans the column or the page, and the first and last lines
 * bound the top and the bottom.
 */
private fun normalise(page: Int, boxed: List<Pair<String, android.graphics.Rect>>): List<OcrLine> {
    if (boxed.isEmpty()) return emptyList()
    val minX = boxed.minOf { it.second.left }.toFloat()
    val maxX = boxed.maxOf { it.second.right }.toFloat()
    val minY = boxed.minOf { it.second.top }.toFloat()
    val maxY = boxed.maxOf { it.second.bottom }.toFloat()
    // A single line, or a column one line tall, spans nothing on that axis. Dividing by the
    // span would be a division by zero; everything lands at 0 instead, which reads as one
    // column, which it is.
    val width = (maxX - minX).takeIf { it > 0f } ?: 1f
    val height = (maxY - minY).takeIf { it > 0f } ?: 1f
    return boxed.map { (text, box) ->
        OcrLine(
            text = text,
            page = page,
            left = (box.left - minX) / width,
            top = (box.top - minY) / height,
            right = (box.right - minX) / width,
            bottom = (box.bottom - minY) / height,
        )
    }
}
