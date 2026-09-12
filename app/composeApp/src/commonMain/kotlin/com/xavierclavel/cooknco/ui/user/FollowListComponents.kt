package com.xavierclavel.cooknco.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.UserSummary
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerSegmentedControl
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One row of the Followers/Following lists (`Cooknco Mobile.dc.html`, lines 1090-1183):
 * a 44dp avatar, name + [meta], and whatever trailing action(s) that row needs — the
 * accept/decline pair, a "Cancel"/"Unfollow" pill, or nothing for a plain accepted row.
 */
@Composable
fun FollowUserRow(
    user: UserSummary,
    meta: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The whole row, not just the avatar or the name: the trailing actions handle
            // their own taps, so anything that is not one of them opens the account.
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatar(
            userId = user.id,
            version = user.version,
            contentDescription = user.username,
            modifier = Modifier.size(44.dp).clip(CircleShape).border(2.dp, CookncoNavy, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(user.username, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            if (meta.isNotBlank()) {
                Text(meta, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = CookncoNavy.copy(alpha = 0.6f))
            }
        }
        trailing()
    }
}

/** A flat (shadow-less) square accept/decline action, 44dp, matching the compact list rows. */
@Composable
fun FollowRowIconAction(
    onClick: () -> Unit,
    fillColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fillColor)
            .border(2.5.dp, CookncoNavy, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/** The pill-shaped "Cancel"/"Unfollow" text action used in the accepted/requested rows. */
@Composable
fun FollowRowTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(CookncoWhite)
            .border(2.5.dp, CookncoNavy, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

/** The "Followers N | Following N" switch at the top of [FollowListScreen]. */
@Composable
fun FollowTabs(
    followersCount: Int,
    followingCount: Int,
    active: FollowTab,
    onSelect: (FollowTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    StickerSegmentedControl(
        options = FollowTab.entries,
        selected = active,
        onSelect = onSelect,
        label = { tab ->
            when (tab) {
                FollowTab.FOLLOWERS -> s.followersTab(followersCount)
                FollowTab.FOLLOWING -> s.followingTab(followingCount)
            }
        },
        modifier = modifier,
    )
}

/**
 * A short "since"/"ago" label for a `FollowInfo.followedSince` epoch-seconds timestamp —
 * there is no relative-time formatter elsewhere in the client to share, so this mirrors
 * `HomeViewModel.groupByDate`'s day-diff approach rather than inventing a new one.
 */
fun followedSinceLabel(prefix: String, epochSeconds: Long): String {
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date
    val day = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(timeZone).date
    val diffDays = today.toEpochDays() - day.toEpochDays()
    return when {
        diffDays <= 0L -> "$prefix today"
        diffDays == 1L -> "$prefix yesterday"
        diffDays < 30L -> "$prefix $diffDays days ago"
        diffDays < 365L -> "$prefix ${diffDays / 30} mo ago"
        else -> "$prefix ${diffDays / 365}y ago"
    }
}
