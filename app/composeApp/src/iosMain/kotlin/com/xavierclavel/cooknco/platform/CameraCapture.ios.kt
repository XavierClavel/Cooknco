package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

/**
 * PHPickerViewController, which the library picker uses, cannot open the camera at all —
 * it only reads the photo library, which is what lets it run without a permission prompt.
 * A capture is still UIImagePickerController, deprecated for libraries and the only API
 * for this.
 */
private class CameraDelegate(
    private val onPicked: (PickedImage) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        // Re-encoded rather than passed through: a camera image has no file behind it, and
        // the backend is told image/jpeg either way.
        val data = UIImageJPEGRepresentation(image, 0.9) ?: return
        onPicked(PickedImage(data.toByteArray(), "image/jpeg"))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

@Composable
actual fun rememberCameraCapture(onPicked: (PickedImage) -> Unit): ImagePickerLauncher {
    val callback = rememberUpdatedState(onPicked)
    // Held across recompositions: the controller keeps only a weak delegate reference.
    val delegate = remember { CameraDelegate { callback.value(it) } }
    val gallery = rememberImagePicker { callback.value(it) }

    return remember(delegate, gallery) {
        ImagePickerLauncher {
            val source = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            if (!UIImagePickerController.isSourceTypeAvailable(source)) {
                // No camera: every simulator, and an iPad with the camera restricted.
                gallery.launch()
                return@ImagePickerLauncher
            }
            val controller = UIImagePickerController().apply {
                sourceType = source
                setDelegate(delegate)
            }
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(controller, animated = true, completion = null)
        }
    }
}
