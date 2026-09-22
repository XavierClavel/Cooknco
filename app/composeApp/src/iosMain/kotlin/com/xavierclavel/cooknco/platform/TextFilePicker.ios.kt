package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/**
 * The delegate the picker reports to.
 *
 * Reading happens inside the security scope the picker hands over: a file chosen from
 * iCloud or from another app's container is not readable without it, and the scope is only
 * open between these two calls.
 */
@OptIn(ExperimentalForeignApi::class)
private class TextPickerDelegate(
    private val onPicked: (String) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            // UTF-8 first, then the system's guess. A recipe file written in Latin-1 on an
            // old machine is still a recipe, and reading it approximately beats refusing it.
            val text = NSString.stringWithContentsOfURL(url, encoding = NSUTF8StringEncoding, error = null)
                ?: NSString.stringWithContentsOfURL(url, usedEncoding = null, error = null)
            if (text != null) onPicked(text as String)
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }

    /** Backing out is not a failure, and has nothing to report. */
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

/**
 * `UIDocumentPickerViewController(forOpeningContentTypes:)` — the Files picker, which reaches
 * iCloud and every provider installed, not only what is on the device.
 *
 * The MIME types the common declaration takes are Android's language; iOS names the same
 * thing with uniform type identifiers, so they are mapped here rather than carried twice.
 * `UTTypeData` is the catch-all a `.cook` file falls into: it has no registered type, so a
 * picker restricted to plain text alone would grey it out on some providers.
 */
@Composable
actual fun rememberTextFilePicker(
    mimeTypes: List<String>,
    onPicked: (String) -> Unit,
): TextFilePickerLauncher {
    val callback = rememberUpdatedState(onPicked)
    // Held across recompositions: the picker keeps only a weak delegate reference.
    val delegate = remember { TextPickerDelegate { callback.value(it) } }
    return remember(delegate) {
        TextFilePickerLauncher {
            val types = listOf<UTType>(UTTypePlainText, UTTypeData)
            val controller = UIDocumentPickerViewController(forOpeningContentTypes = types).apply {
                this.delegate = delegate
                allowsMultipleSelection = false
            }
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
                ?: return@TextFilePickerLauncher
            root.presentViewController(controller, animated = true, completion = null)
        }
    }
}
