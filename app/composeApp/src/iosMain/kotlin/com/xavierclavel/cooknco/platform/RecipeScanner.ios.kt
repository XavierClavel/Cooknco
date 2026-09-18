package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * The languages the reader is told to expect, best first.
 *
 * Both of the app's own, rather than the one it is being shown in: the language a cookbook
 * is written in has nothing to do with the language its owner set the app to, and a French
 * recipe read as English loses every accent it has. Vision takes an ordered list and uses it
 * to break ties, so naming both costs nothing but the order.
 */
private val RECOGNITION_LANGUAGES = listOf("fr-FR", "en-US")

/**
 * VisionKit's scanner is the same component Notes uses, and it needs a view controller to
 * present rather than an activity result, so the delegate is what carries the answer back.
 * It is held across recompositions for the reason the camera's is: the controller keeps only
 * a weak reference to it.
 */
private class ScanDelegate(
    private val scope: CoroutineScope,
    private val onResult: (ScanResult) -> Unit,
) : NSObject(), VNDocumentCameraViewControllerDelegateProtocol {

    override fun documentCameraViewController(
        controller: VNDocumentCameraViewController,
        didFinishWithScan: VNDocumentCameraScan,
    ) {
        controller.dismissViewControllerAnimated(true, null)
        // The pages come off the scan here, on the main thread it hands them over on; the
        // reading is what moves, because an accurate Vision pass over a full page takes long
        // enough to freeze the editor it is about to fill in.
        val pages = (0uL until didFinishWithScan.pageCount).map {
            didFinishWithScan.imageOfPageAtIndex(it)
        }
        scope.launch {
            val lines = withContext(Dispatchers.Default) { pages.flatMapIndexed(::readPage) }
            onResult(ScanResult.Read(lines))
        }
    }

    /** Backing out says nothing, the way cancelling the photo picker says nothing. */
    override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) {
        controller.dismissViewControllerAnimated(true, null)
    }

    override fun documentCameraViewController(
        controller: VNDocumentCameraViewController,
        didFailWithError: NSError,
    ) {
        controller.dismissViewControllerAnimated(true, null)
        onResult(ScanResult.Failed)
    }
}

@Composable
actual fun rememberRecipeScanner(onScanned: (ScanResult) -> Unit): RecipeScannerLauncher {
    val callback = rememberUpdatedState(onScanned)
    val scope = rememberCoroutineScope()
    val delegate = remember(scope) { ScanDelegate(scope) { callback.value(it) } }

    return remember(delegate) {
        RecipeScannerLauncher {
            // Every simulator, and any device whose camera is restricted. There is no
            // scanning a page without one, so this is the same answer Android gives when
            // Play services cannot supply the scanner.
            if (!VNDocumentCameraViewController.isSupported()) {
                callback.value(ScanResult.Failed)
                return@RecipeScannerLauncher
            }
            val controller = VNDocumentCameraViewController().apply { setDelegate(delegate) }
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (root == null) {
                callback.value(ScanResult.Failed)
                return@RecipeScannerLauncher
            }
            root.presentViewController(controller, animated = true, completion = null)
        }
    }
}

/**
 * Reads one page's text with Vision, on-device.
 *
 * `Accurate` rather than `Fast`, and with language correction on: this runs once on a page
 * somebody is waiting to see turned into a recipe, so the extra moment buys more than it
 * costs — `Fast` is for a live camera feed, which this is not.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun readPage(page: Int, image: UIImage): List<OcrLine> {
    val cgImage = image.CGImage ?: return emptyList()
    val request = VNRecognizeTextRequest().apply {
        recognitionLevel = VNRequestTextRecognitionLevelAccurate
        usesLanguageCorrection = true
        recognitionLanguages = RECOGNITION_LANGUAGES
    }
    val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
    val performed = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        handler.performRequests(listOf(request), error.ptr) && error.value == null
    }
    if (!performed) return emptyList()

    val boxed = request.results.orEmpty()
        .filterIsInstance<VNRecognizedTextObservation>()
        .mapNotNull { observation ->
            val text = (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)
                ?.string
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            // Normalised to the image already, but bottom-left: y grows upwards, so the box's
            // origin is its *bottom* and the two edges swap on the way to a top-left space.
            observation.boundingBox.useContents {
                Box(
                    text = text,
                    left = origin.x.toFloat(),
                    top = (1.0 - (origin.y + size.height)).toFloat(),
                    right = (origin.x + size.width).toFloat(),
                    bottom = (1.0 - origin.y).toFloat(),
                )
            }
        }
    return normalise(page, boxed)
}

private class Box(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Re-normalises a page's boxes against the text on it rather than against the image.
 *
 * Vision's are already `0..1` of the image, so this is not a change of scale so much as one
 * of what the numbers are a fraction *of*: the margins the scanner left around the page
 * would otherwise shift the gutter [com.xavierclavel.cooknco.data.RecipeScan] looks for off
 * centre by however much of the desk got into frame. The Android side normalises against the
 * same thing for the same reason, which is what lets one parser read both.
 */
private fun normalise(page: Int, boxed: List<Box>): List<OcrLine> {
    if (boxed.isEmpty()) return emptyList()
    val minX = boxed.minOf { it.left }
    val maxX = boxed.maxOf { it.right }
    val minY = boxed.minOf { it.top }
    val maxY = boxed.maxOf { it.bottom }
    // A page holding a single line spans nothing on that axis; everything then lands at 0,
    // which reads as one column, which it is.
    val width = (maxX - minX).takeIf { it > 0f } ?: 1f
    val height = (maxY - minY).takeIf { it > 0f } ?: 1f
    return boxed.map {
        OcrLine(
            text = it.text,
            page = page,
            left = (it.left - minX) / width,
            top = (it.top - minY) / height,
            right = (it.right - minX) / width,
            bottom = (it.bottom - minY) / height,
        )
    }
}
