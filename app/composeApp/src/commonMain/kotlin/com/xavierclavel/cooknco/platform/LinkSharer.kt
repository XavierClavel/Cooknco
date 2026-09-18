package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/**
 * Hands a link to the platform's share sheet — the grid of apps and contacts the user
 * already knows — rather than copying it to the clipboard.
 *
 * The clipboard was the whole of "share" before this, and it is the weaker half of it: it
 * says nothing on iOS beyond a banner the system draws for any copy, it leaves the user to
 * find the app they meant to paste into, and it cannot reach the one-tap targets the sheet
 * puts first. Copying is still on the sheet, on both platforms, so nothing is lost by
 * going through it.
 *
 * [subject] is a title for the destinations that have somewhere to put one — a mail's
 * subject line, essentially — and is ignored everywhere else. It is never a substitute for
 * the link: a recipient who sees only the subject still has the URL.
 *
 * Unlike [DocumentSaver], nothing here reports success. A share sheet is dismissible and
 * every target is somebody else's app, so what became of the link is not knowable — and
 * the sheet is itself the confirmation that anything happened.
 */
fun interface LinkSharer {
    fun share(subject: String, url: String)
}

@Composable
expect fun rememberLinkSharer(): LinkSharer
