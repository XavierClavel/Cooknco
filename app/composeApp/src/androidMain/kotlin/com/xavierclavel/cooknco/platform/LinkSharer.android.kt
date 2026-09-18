package com.xavierclavel.cooknco.platform

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * `ACTION_SEND` wrapped in a chooser: the system sheet, with the handset's share targets
 * and the direct-share contacts above them.
 *
 * The chooser is explicit rather than left to the system to decide, because an `ACTION_SEND`
 * started on its own resolves to whatever the user once set as default — which for a
 * `text/plain` send is usually the app they last shared to, opening it with no sheet and no
 * way back to the choice.
 *
 * The URL goes in `EXTRA_TEXT`, where every target reads it from, and the title in
 * `EXTRA_SUBJECT`, which only the mail apps look at. Putting the title in the text instead
 * would prefix the link with a sentence in every messaging app, and — worse — stops some of
 * them recognising the link at all, so the preview the website's own tags would have earned
 * never appears.
 */
@Composable
actual fun rememberLinkSharer(): LinkSharer {
    val context = LocalContext.current
    return remember(context) {
        LinkSharer { subject, url ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, url)
            }
            // Null title: the chooser names itself ("Share") in the user's language, which
            // is one string this app then does not have to hold in both of its own.
            val chooser = Intent.createChooser(send, null)
            // Normally the Activity hosting Compose, which needs no flag; anything else
            // cannot start an activity without one.
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
