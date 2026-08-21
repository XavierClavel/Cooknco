package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

private const val IMAGE_TYPE_IDENTIFIER = "public.image"

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

private class PhotoPickerDelegate(
    private val onPicked: (PickedImage) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE_IDENTIFIER) { data, _ ->
            val bytes = data?.toByteArray() ?: return@loadDataRepresentationForTypeIdentifier
            // The provider calls back off the main thread; Compose state must not be touched there.
            dispatch_async(dispatch_get_main_queue()) {
                onPicked(PickedImage(bytes, "image/jpeg"))
            }
        }
    }
}

@Composable
actual fun rememberImagePicker(onPicked: (PickedImage) -> Unit): ImagePickerLauncher {
    val callback = rememberUpdatedState(onPicked)
    // Held across recompositions: PHPickerViewController keeps only a weak delegate reference.
    val delegate = remember { PhotoPickerDelegate { callback.value(it) } }
    return remember(delegate) {
        ImagePickerLauncher {
            val configuration = PHPickerConfiguration().apply {
                selectionLimit = 1
                filter = PHPickerFilter.imagesFilter()
            }
            val controller = PHPickerViewController(configuration).apply {
                setDelegate(delegate)
            }
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(controller, animated = true, completion = null)
        }
    }
}
