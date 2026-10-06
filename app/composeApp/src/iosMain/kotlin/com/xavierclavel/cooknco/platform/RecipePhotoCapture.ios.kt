package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.VisionKit.VNDocumentCameraScan
import platform.VisionKit.VNDocumentCameraViewController
import platform.VisionKit.VNDocumentCameraViewControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * The delegate [rememberRecipeScanner]'s is, handing the pages over as JPEGs instead of
 * reading them: held across recompositions because the controller keeps only a weak
 * reference to it.
 */
private class PhotoCaptureDelegate(
    private val scope: CoroutineScope,
    private val onResult: (PhotoCaptureResult) -> Unit,
) : NSObject(), VNDocumentCameraViewControllerDelegateProtocol {

    override fun documentCameraViewController(
        controller: VNDocumentCameraViewController,
        didFinishWithScan: VNDocumentCameraScan,
    ) {
        controller.dismissViewControllerAnimated(true, null)
        val pages = (0uL until didFinishWithScan.pageCount).map { didFinishWithScan.imageOfPageAtIndex(it) }
        scope.launch {
            val encoded = withContext(Dispatchers.Default) { pages.mapNotNull(::encodePage) }
            onResult(if (encoded.isEmpty()) PhotoCaptureResult.Failed else PhotoCaptureResult.Captured(encoded))
        }
    }

    override fun documentCameraViewControllerDidCancel(controller: VNDocumentCameraViewController) {
        controller.dismissViewControllerAnimated(true, null)
    }

    override fun documentCameraViewController(
        controller: VNDocumentCameraViewController,
        didFailWithError: NSError,
    ) {
        controller.dismissViewControllerAnimated(true, null)
        onResult(PhotoCaptureResult.Failed)
    }
}

@Composable
actual fun rememberRecipePhotoCapture(onCaptured: (PhotoCaptureResult) -> Unit): RecipeScannerLauncher {
    val callback = rememberUpdatedState(onCaptured)
    val scope = rememberCoroutineScope()
    val delegate = remember(scope) { PhotoCaptureDelegate(scope) { callback.value(it) } }

    return remember(delegate) {
        RecipeScannerLauncher {
            if (!VNDocumentCameraViewController.isSupported()) {
                callback.value(PhotoCaptureResult.Failed)
                return@RecipeScannerLauncher
            }
            val controller = VNDocumentCameraViewController().apply { setDelegate(delegate) }
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (root == null) {
                callback.value(PhotoCaptureResult.Failed)
                return@RecipeScannerLauncher
            }
            root.presentViewController(controller, animated = true, completion = null)
        }
    }
}

/**
 * One page as a JPEG no longer than [PHOTO_IMPORT_MAX_EDGE] points on its long side.
 *
 * VisionKit hands the page over upright and already flattened, so all that is left is the
 * size: drawn at scale 1 into a smaller context, which is the one resize UIKit offers that
 * every iOS this app supports has.
 */
@OptIn(ExperimentalForeignApi::class)
private fun encodePage(image: UIImage): CapturedPage? {
    val (width, height) = image.size.useContents { width to height }
    val longest = maxOf(width, height)
    if (longest <= 0.0) return null
    val scale = minOf(1.0, PHOTO_IMPORT_MAX_EDGE / longest)
    val resized = if (scale < 1.0) {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width * scale, height * scale), true, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
        val drawn = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        drawn ?: image
    } else image
    val data = UIImageJPEGRepresentation(resized, PHOTO_IMPORT_JPEG_QUALITY / 100.0) ?: return null
    return CapturedPage(data.toBytes())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toBytes(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
