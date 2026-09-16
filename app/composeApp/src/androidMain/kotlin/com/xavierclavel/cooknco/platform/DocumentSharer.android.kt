package com.xavierclavel.cooknco.platform

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Staged in the cache directory and shared through the FileProvider, for the same reason a
 * camera capture is (see [rememberCameraCapture]): a `file://` URI handed to another app has
 * thrown FileUriExposedException since Android 7, and this app's floor is Android 7. The
 * directory is declared in `res/xml/file_paths.xml`.
 *
 * The file is deliberately *not* deleted afterwards: the receiving app reads it after this
 * returns, and the system reclaims a cache directory on its own. The name is reused per
 * document, so exporting the same recipe twice leaves one file rather than a pile.
 */
@Composable
actual fun rememberDocumentSharer(): DocumentSharer {
    val context = LocalContext.current
    return remember(context) {
        DocumentSharer { filename, bytes ->
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            // Last segment only: the name arrives in a response header, and the one thing
            // it must not be able to do is point at a file outside the shared directory.
            val file = File(directory, filename.substringAfterLast('/')).apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // The chooser is what carries the read grant to whichever app is picked.
            val chooser = Intent.createChooser(intent, null)
            // Normally the Activity hosting Compose, which needs no flag; anything else
            // cannot start an activity without one.
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
