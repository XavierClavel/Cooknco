package com.xavierclavel.cooknco.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Storage Access Framework's open-document picker, which needs no storage permission on
 * any version this app runs on: the file arrives because the user picked it.
 *
 * `OpenDocument` rather than `GetContent`, because it is the one that offers the file
 * providers — Drive, Files, a downloads folder — rather than only what is on the device. A
 * `.cook` file is far more likely to have been sent to somebody than created on the handset.
 */
@Composable
actual fun rememberTextFilePicker(
    mimeTypes: List<String>,
    onPicked: (String) -> Unit,
): TextFilePickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The callback as it is *now*, so a picker that was launched before a recomposition
    // still reports to the current one rather than to a captured stale lambda.
    val callback = rememberUpdatedState(onPicked)
    val types = remember(mimeTypes) { mimeTypes.toTypedArray() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Null is the user backing out, which is not a failure.
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            if (text != null) callback.value(text)
        }
    }

    return remember(picker, types) { TextFilePickerLauncher { picker.launch(types) } }
}
