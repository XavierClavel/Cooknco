package com.xavierclavel.cooknco.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Storage Access Framework's create-document picker: the system screen that offers
 * Downloads and every provider the handset has, with the name already filled in.
 *
 * Chosen over writing straight into `MediaStore.Downloads`, which would be closer to a
 * browser download and is the wrong trade twice over: it is API 29 and up, while this app
 * runs from 24, and it puts the file somewhere the user never agreed to with nothing on
 * screen to say it happened. The picker needs no storage permission on any version.
 *
 * The contract carries the *name* out and a URI back, so — as with a camera capture — the
 * bytes waiting to be written are held beside the launcher rather than passed through it.
 */
@Composable
actual fun rememberDocumentSaver(mimeType: String): DocumentSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The document the *pending* picker is for. One slot: a picker is modal, so there is
    // never a second save waiting behind it.
    val pending = remember { arrayOfNulls<ByteArray>(1) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        val bytes = pending[0]
        pending[0] = null
        // Null is the user backing out of the picker, which is not a failure and has
        // nothing to report.
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            }
        }
    }

    return remember(picker) {
        DocumentSaver { filename, bytes ->
            pending[0] = bytes
            picker.launch(filename)
        }
    }
}
