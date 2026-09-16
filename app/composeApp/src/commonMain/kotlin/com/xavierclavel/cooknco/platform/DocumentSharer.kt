package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/**
 * Hands a finished document to the platform, which offers to open it, save it or send it on.
 *
 * The share sheet rather than a download: a phone has no downloads folder a user would then
 * go looking in, and both platforms' sheets already offer "save to Files" alongside every
 * app that reads a PDF. The bytes are passed rather than a path because nothing else in the
 * app has any use for a file on disk — the actuals stage one and let the system reclaim it.
 */
fun interface DocumentSharer {
    fun share(filename: String, bytes: ByteArray)
}

@Composable
expect fun rememberDocumentSharer(): DocumentSharer
