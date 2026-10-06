package com.xavierclavel.cooknco.platform

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * The scanner [rememberRecipeScanner] opens, with the same options — page limit, gallery
 * import, the full editing flow — so the two ways in look and behave identically up to the
 * moment the pages are handed over.
 */
@Composable
actual fun rememberRecipePhotoCapture(onCaptured: (PhotoCaptureResult) -> Unit): RecipeScannerLauncher {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onCaptured)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            ?.pages.orEmpty()
            .map { it.imageUri }
        scope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { pages.map { encodePage(context, it) } } }
            callback.value(
                read.map { if (it.isEmpty()) PhotoCaptureResult.Failed else PhotoCaptureResult.Captured(it) }
                    .getOrElse { PhotoCaptureResult.Failed }
            )
        }
    }

    return remember(launcher, activity) {
        RecipeScannerLauncher {
            val host = activity
            if (host == null) {
                callback.value(PhotoCaptureResult.Failed)
                return@RecipeScannerLauncher
            }
            GmsDocumentScanning.getClient(scannerOptions)
                .getStartScanIntent(host)
                .addOnSuccessListener { sender ->
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener { callback.value(PhotoCaptureResult.Failed) }
        }
    }
}

/**
 * One page as a JPEG no longer than [RECIPE_SCAN_MAX_EDGE] on its long side, upright.
 *
 * Decoded at a power-of-two sample first, so a 12-megapixel page is never held in memory at
 * full size just to be shrunk. The EXIF orientation is applied by hand because
 * `BitmapFactory` ignores it, and a gallery photo the scanner passes through untouched can
 * carry one: a page sent on its side is a page a model reads worse and bills the same.
 */
private fun encodePage(context: Context, uri: Uri): CapturedPage {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = max(bounds.outWidth, bounds.outHeight)
    require(longest > 0) { "Unreadable page" }

    var sample = 1
    while (longest / (sample * 2) >= RECIPE_SCAN_MAX_EDGE) sample *= 2
    val decoded = resolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Unreadable page")

    val rotation = resolver.openInputStream(uri).use { stream ->
        when (stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    val scale = minOf(1f, RECIPE_SCAN_MAX_EDGE.toFloat() / max(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation)
    }
    val page = if (scale < 1f || rotation != 0f) {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() }
    } else decoded

    return try {
        val out = ByteArrayOutputStream()
        page.compress(Bitmap.CompressFormat.JPEG, RECIPE_SCAN_JPEG_QUALITY, out)
        CapturedPage(out.toByteArray())
    } finally {
        page.recycle()
    }
}

