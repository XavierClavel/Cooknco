package com.xavierclavel.cooknco.platform

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * `SCHEDULE_EXACT_ALARM`, which this app declares and Android does not grant.
 *
 * Apps targeting Android 14 and up get it denied until the user says otherwise. The
 * permission that *is* granted without asking, `USE_EXACT_ALARM`, is restricted on Play to
 * apps whose core purpose is alarms — which a recipe app's is not, whatever its timer does —
 * so the only honest way to a punctual timer here is to ask for it.
 *
 * Asked for where it is felt, and only once: see `CookModeViewModel.toggleTimer`. A cook who
 * says no keeps an approximate alarm rather than none, and the app's own countdown is exact
 * for as long as the app is in front of them.
 */
actual fun canRingTimersExactly(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val context = cookModeContext ?: return true
    return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
}

@Composable
actual fun rememberExactTimerConsent(): ExactTimerConsent {
    val context = LocalContext.current
    return remember(context) {
        ExactTimerConsent {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@ExactTimerConsent
            val packageUri = Uri.fromParts("package", context.packageName, null)
            // The screen this opens is per-app and reachable by two routes; OEM builds have
            // been known to ship without the first. Falling back to the app's own settings
            // page leaves the user somewhere they can still find the toggle, rather than on
            // an ActivityNotFoundException.
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (!opened) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}
