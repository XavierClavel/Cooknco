package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import com.xavierclavel.cooknco.data.ImageCrop

/**
 * An image the user picked from their device, already read into memory.
 *
 * [crop] is the part of it they framed, uploaded beside the untouched [bytes] for the backend to
 * cut out; null keeps the centred frame the backend makes on its own. The pickers below hand
 * over none — framing is the crop dialog's, after them (`rememberImageFraming`).
 */
class PickedImage(val bytes: ByteArray, val mimeType: String, val crop: ImageCrop? = null) {
    fun framed(crop: ImageCrop?) = PickedImage(bytes, mimeType, crop)
}

fun interface ImagePickerLauncher {
    fun launch()
}

/**
 * Remembers a launcher that opens the system photo picker and reports the chosen
 * image. [onPicked] is called on the main dispatcher; cancelling is a no-op.
 */
@Composable
expect fun rememberImagePicker(onPicked: (PickedImage) -> Unit): ImagePickerLauncher

/**
 * Remembers a launcher that opens the camera and reports the picture taken.
 *
 * Separate from [rememberImagePicker] rather than a flag on it because the two are
 * different system components with different failure modes — a device with no camera, or a
 * simulator, has a photo library all the same. Both fall back to the library when the
 * camera cannot be reached, so the button always does something.
 *
 * Neither platform needs a permission granted for this: Android's capture intent hands the
 * work to a camera app that has its own, and asking for `CAMERA` ourselves would actually
 * make the intent *require* a grant we do not otherwise need. iOS does need the
 * `NSCameraUsageDescription` string, which is in `iosApp/iosApp/Info.plist`.
 */
@Composable
expect fun rememberCameraCapture(onPicked: (PickedImage) -> Unit): ImagePickerLauncher
