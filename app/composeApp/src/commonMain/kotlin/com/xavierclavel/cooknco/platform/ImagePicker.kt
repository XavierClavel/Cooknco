package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/** An image the user picked from their device, already read into memory. */
class PickedImage(val bytes: ByteArray, val mimeType: String)

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
