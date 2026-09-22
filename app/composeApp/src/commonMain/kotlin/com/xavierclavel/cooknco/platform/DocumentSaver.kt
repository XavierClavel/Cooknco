package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

/**
 * Hands a finished document to the platform's *save a file* flow, and asks where it goes.
 *
 * Not the share sheet, which is a different action: an export is a download, and a sheet
 * offering to message it to somebody is a longer way round to a file the user then has to
 * go and find. Both platforms have a picker for this — the system Downloads UI on Android,
 * "Save to Files" on iOS — and both need no permission to use it.
 *
 * The bytes are passed rather than a path because nothing else in the app has any use for a
 * file on disk: where the document ends up is the picker's answer, not ours.
 */
fun interface DocumentSaver {
    fun save(filename: String, bytes: ByteArray)
}

/**
 * @param mimeType what kind of file the picker is being opened for. Defaulted to the PDF the
 *   exports started as, so that every existing caller means exactly what it did before, and
 *   passed explicitly by the ones saving something else — a `.cook` file, today. Android puts
 *   it on the create-document contract; iOS exports a file URL and has no use for it.
 */
@Composable
expect fun rememberDocumentSaver(mimeType: String = PDF_MIME_TYPE): DocumentSaver

const val PDF_MIME_TYPE = "application/pdf"

/**
 * Cooklang's own convention — the format has no registered media type. `text/plain` would be
 * true and would also invite the picker to treat the file as something to read.
 */
const val COOKLANG_MIME_TYPE = "text/x-cooklang"
