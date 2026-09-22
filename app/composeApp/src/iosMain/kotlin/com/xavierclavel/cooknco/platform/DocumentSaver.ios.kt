package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

/**
 * "Save to Files": `UIDocumentPickerViewController(forExportingURLs:)`, which is what a
 * download is on a system with no downloads folder. Not `UIActivityViewController`, whose
 * job is sending the document to somebody.
 *
 * The picker exports a file rather than bytes, so the document is written to the temporary
 * directory first and handed over from there. Exporting *moves* it, which is the right way
 * round for a staging file: what the picker does not take, iOS reclaims on its own.
 *
 * The MIME type is unused here: the picker reads the kind off the file it is given, and the
 * name it is given carries the extension. It is a parameter because Android's contract needs
 * one.
 */
@Composable
actual fun rememberDocumentSaver(mimeType: String): DocumentSaver = remember {
    DocumentSaver { filename, bytes ->
        // Last segment only: the name arrives in a response header, and it must not be able
        // to write anywhere but the directory chosen here.
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + filename.substringAfterLast('/'))
        if (!bytes.toNSData().writeToURL(url, atomically = true)) return@DocumentSaver
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url))
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@DocumentSaver
        root.presentViewController(picker, animated = true, completion = null)
    }
}
