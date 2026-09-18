package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityItemSourceProtocol
import platform.UIKit.UIActivityType
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

/**
 * The link, and the title the destinations that have a subject line should use for it.
 *
 * Handed over as a `UIActivityItemSource` rather than as a bare list of items, for the two
 * things that only the source can say. The item is an [NSURL], so the targets that treat a
 * link as a link — Messages and the rest — show the page's own preview instead of quoting a
 * string; and `subjectForActivityType` is the only way to fill a mail's subject, which a
 * plain item list leaves empty.
 *
 * `placeholderItem` is what the sheet is built from before the user has chosen anything, so
 * it has to be the same *kind* of thing the real item will be — the URL itself here, which
 * is already in hand and costs nothing to produce.
 */
private class SharedLink(
    private val url: NSURL,
    private val subject: String,
) : NSObject(), UIActivityItemSourceProtocol {

    override fun activityViewControllerPlaceholderItem(activityViewController: UIActivityViewController): Any = url

    // The two below are one selector apart and the same one after Objective-C has erased
    // their argument types, which Kotlin reads as an accidental overload unless told.
    @ObjCSignatureOverride
    override fun activityViewController(
        activityViewController: UIActivityViewController,
        itemForActivityType: UIActivityType?,
    ): Any = url

    @ObjCSignatureOverride
    override fun activityViewController(
        activityViewController: UIActivityViewController,
        subjectForActivityType: UIActivityType?,
    ): String = subject
}

/**
 * `UIActivityViewController`: the share sheet, with AirDrop, the messaging apps and "Copy"
 * on it. The counterpart of the Android chooser, and — unlike [rememberDocumentSaver] —
 * this is the case its documentation says it is for.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberLinkSharer(): LinkSharer = remember {
    LinkSharer { subject, url ->
        val nsUrl = NSURL.URLWithString(url) ?: return@LinkSharer
        val controller = UIActivityViewController(
            activityItems = listOf(SharedLink(nsUrl, subject)),
            applicationActivities = null,
        )
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@LinkSharer
        // iPad presents this as a popover and raises if it is anchored to nothing. Anchored
        // to the middle of the screen, as the export sheet was: a Compose button has no
        // UIView to point at, and an unset rect anchors at the view's top-left corner
        // rather than falling back to anything.
        controller.popoverPresentationController?.let { popover ->
            popover.sourceView = root.view
            popover.sourceRect = root.view.bounds.useContents {
                CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
            }
        }
        root.presentViewController(controller, animated = true, completion = null)
    }
}
