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
