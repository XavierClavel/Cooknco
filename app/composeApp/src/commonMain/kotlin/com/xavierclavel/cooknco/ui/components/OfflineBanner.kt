package com.xavierclavel.cooknco.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.data.OfflineState
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import kotlin.time.Clock

/**
 * Says that what is on screen came off this phone, and how old it is.
 *
 * The age is the whole reason this exists. A cook who cannot reach the server and is shown
 * their recipes anyway has been done a favour; a cook who is shown the version from before
 * last night's correction, with nothing saying so, has been misled — and the second is a worse
 * outcome than an error screen, because they will cook from it.
 *
 * Drawn wherever content can come from the store, which is the four tabs and the recipe
 * screen. It takes no space at all when there is a connection.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val offline by OfflineState.isOffline.collectAsState()
    val s = strings()
    // Read only while the banner is up: it is a file read, and online it would be a file read
    // on every launch for a line nobody sees.
    val syncedAt by produceState(0L, offline) {
        value = if (offline) AppGraph.offlineStore.readIndex()?.syncedAt ?: 0L else 0L
    }

    AnimatedVisibility(
        visible = offline,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CookncoGold)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = CookncoNavy,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = offlineBannerText(s, syncedAt),
                color = CookncoNavy,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The banner's sentence for a given sync time.
 *
 * Coarse on purpose — "an hour ago", "yesterday" — because the question it answers is "can I
 * trust this", and no cook has ever needed that to the minute. Zero means the store has never
 * been written, and says so rather than claiming an age of nearly sixty years.
 */
internal fun offlineBannerText(s: Strings, syncedAt: Long, now: Long = Clock.System.now().epochSeconds): String {
    if (syncedAt <= 0L) return s.offlineShowingSavedUnknown
    val seconds = (now - syncedAt).coerceAtLeast(0L)
    val age = when {
        seconds < 3600 -> s.offlineJustNow
        seconds < 86_400 -> s.offlineHoursAgo((seconds / 3600).toInt())
        else -> s.offlineDaysAgo((seconds / 86_400).toInt())
    }
    return s.offlineShowingSaved(age)
}
