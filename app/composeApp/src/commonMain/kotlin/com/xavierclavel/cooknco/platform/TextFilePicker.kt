package com.xavierclavel.cooknco.platform

import androidx.compose.runtime.Composable

fun interface TextFilePickerLauncher {
    fun launch()
}

/**
 * Remembers a launcher that opens the system file picker and reports the chosen file's text.
 *
 * The counterpart of [rememberDocumentSaver], and deliberately not a second use of
 * [rememberImagePicker]: a photo picker shows a gallery, and a `.cook` file is not in it.
 *
 * **Text rather than bytes.** Everything this is used for is a document the backend parses,
 * so decoding once here saves every caller doing it — and gives one place for the rule that a
 * file which is not UTF-8 is read as best it can be rather than refused. A recipe file is
 * written by a person on a machine we know nothing about; the alternative to a lossy read is
 * a picker that reports nothing and says why it cannot.
 *
 * [onPicked] is called on the main dispatcher. Cancelling is a no-op, as it is for the image
 * pickers: backing out of a picker is not a failure and has nothing to report.
 *
 * Neither platform needs a permission for this — the file arrives because the user chose it,
 * which is the grant.
 */
@Composable
expect fun rememberTextFilePicker(
    /** What the picker will offer, as MIME types. */
    mimeTypes: List<String>,
    onPicked: (String) -> Unit,
): TextFilePickerLauncher

/**
 * What the picker offers for a `.cook` file.
 *
 * Cooklang has no registered media type, and a handset decides a file's type from its
 * extension — which it has never been told about. `text/plain` catches it where the provider
 * guessed, and the wildcard is what keeps the file from being greyed out where it did not:
 * offered and readable beats correctly typed and unpickable.
 */
val COOKLANG_PICKER_MIME_TYPES = listOf("text/plain", "text/*", "*/*")
