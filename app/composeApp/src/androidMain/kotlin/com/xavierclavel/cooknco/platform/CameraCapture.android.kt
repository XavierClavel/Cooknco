package com.xavierclavel.cooknco.platform

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The capture intent writes to a file we name, and hands back only a boolean. So the
 * destination is decided before launching and read afterwards, rather than arriving in the
 * result the way a picked image does.
 *
 * It goes in the cache directory: this is a staging file for an upload that happens moments
 * later, and the system is free to reclaim it whenever it likes afterwards. The path is
 * shared through a FileProvider — see the `<provider>` in AndroidManifest.xml and
 * `res/xml/file_paths.xml` — because a `file://` URI handed to another app has thrown
 * FileUriExposedException since Android 7, and this app's floor is Android 7.
 */
@Composable
actual fun rememberCameraCapture(onPicked: (PickedImage) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)

    // The file the *pending* capture writes to. Held in a holder rather than passed
    // through the contract, which carries the URI out and the boolean back.
    val pending = remember { arrayOfNulls<File>(1) }

    val gallery = rememberImagePicker { callback.value(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pending[0]
        pending[0] = null
        if (!saved || file == null) {
            file?.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                val bytes = file.takeIf { it.length() > 0 }?.readBytes()
                file.delete()
                bytes?.let { PickedImage(it, "image/jpeg") }
            }
            if (picked != null) callback.value(picked)
        }
    }

    return remember(camera, gallery) {
        ImagePickerLauncher {
            val directory = File(context.cacheDir, "captures").apply { mkdirs() }
            val file = File(directory, "capture_${System.currentTimeMillis()}.jpg")
            pending[0] = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            try {
                camera.launch(uri)
            } catch (e: ActivityNotFoundException) {
                // No camera app — a tablet, a locked-down device, an emulator image built
                // without one. The library is the next best answer to "add a picture".
                pending[0] = null
                file.delete()
                gallery.launch()
            }
        }
    }
}
