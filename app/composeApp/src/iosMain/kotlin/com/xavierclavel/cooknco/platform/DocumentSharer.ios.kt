package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

/**
 * UIActivityViewController over a file in the temporary directory, rather than over the
 * bytes: handed an NSData the sheet offers to send *something*, while a file URL is what
 * gives it a name, a PDF preview and "Save to Files".
 *
 * The file is left where it is — iOS empties the temporary directory on its own, and the
 * sheet reads it after this returns. The name is reused per document, so exporting the same
 * recipe twice overwrites rather than accumulates.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberDocumentSharer(): DocumentSharer = remember {
    DocumentSharer { filename, bytes ->
        // Last segment only: the name arrives in a response header, and it must not be able
        // to write anywhere but the directory chosen here.
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + filename.substringAfterLast('/'))
        if (!bytes.toNSData().writeToURL(url, atomically = true)) return@DocumentSharer
        val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@DocumentSharer
        // iPad presents this as a popover and raises if it is anchored to nothing. Anchored
        // to the middle of the screen: the sheet is opened from a modal of our own, so
        // there is no bar button or row still on screen to point at.
        controller.popoverPresentationController?.let { popover ->
            popover.sourceView = root.view
            popover.sourceRect = root.view.bounds.useContents {
                CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
            }
        }
        root.presentViewController(controller, animated = true, completion = null)
    }
}
