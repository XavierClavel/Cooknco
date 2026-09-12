package com.xavierclavel.cooknco.ui.user

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.FollowInfoDto
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.stickerSwitchSpec

/**
 * Followers and Following are two tabs of *one* screen, not two screens: the switch at the
 * top swaps the list in place, and the back arrow always returns to the profile the lists
 * belong to. Navigating between them instead would stack them on the back stack, and the
 * back arrow would then walk back through every tab the user had tried.
 *
 * The route decides which tab opens; from there the switch is local state in the view model.
 */
@Composable
fun FollowListScreen(
    viewModel: FollowListViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back)
            }
            Text(
                text = uiState.profileUser?.username ?: "",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                modifier = Modifier.weight(1f),
            )
        }

        FollowTabs(
            followersCount = uiState.profileUser?.followersCount ?: 0,
            followingCount = uiState.profileUser?.followsCount ?: 0,
            active = uiState.tab,
            onSelect = viewModel::select,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 6.dp),
        )

        // The list follows the highlight: it slides in from the side the highlight moved
        // towards, on the same spec, so the switch reads as one movement.
        AnimatedContent(
            targetState = uiState.tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enter = slideInHorizontally(stickerSwitchSpec()) { width -> if (forward) width else -width } +
                    fadeIn(stickerSwitchSpec())
                val exit = slideOutHorizontally(stickerSwitchSpec()) { width -> if (forward) -width else width } +
                    fadeOut(stickerSwitchSpec())
                enter togetherWith exit
            },
            modifier = Modifier.weight(1f).clipToBounds(),
            label = "follow_tab_content",
        ) { tab ->
            when (tab) {
                FollowTab.FOLLOWERS -> FollowersList(
                    section = uiState.followers,
                    followersCount = uiState.profileUser?.followersCount,
                    actioningUserId = uiState.actioningUserId,
                    onAccept = viewModel::accept,
                    onDecline = viewModel::decline,
                    onUserClick = onNavigateToUser,
                    onLoadMore = { viewModel.loadMore(FollowTab.FOLLOWERS) },
                )
                FollowTab.FOLLOWING -> FollowingList(
                    section = uiState.following,
                    followingCount = uiState.profileUser?.followsCount,
                    actioningUserId = uiState.actioningUserId,
                    onCancelOrUnfollow = viewModel::cancelOrUnfollow,
                    onUserClick = onNavigateToUser,
                    onLoadMore = { viewModel.loadMore(FollowTab.FOLLOWING) },
                )
            }
        }
    }
}

/** The accounts following this profile: the requests waiting on them, then the accepted ones. */
@Composable
private fun FollowersList(
    section: FollowSection,
    followersCount: Int?,
    actioningUserId: Long?,
    onAccept: (Long) -> Unit,
    onDecline: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val s = strings()
    FollowScrollColumn(section = section, onLoadMore = onLoadMore) {
        val pending = section.pending
        val accepted = section.accepted

        if (pending.isNotEmpty()) {
            FollowSectionHeader(title = s.pendingRequests, badge = pending.size)
            FollowSectionCard {
                pending.forEachIndexed { index, entry ->
                    PendingFollowerRow(
                        entry = entry,
                        isActioning = actioningUserId == entry.user.id,
                        onAccept = { onAccept(entry.user.id) },
                        onDecline = { onDecline(entry.user.id) },
                        onClick = { onUserClick(entry.user.id) },
                    )
                    if (index != pending.lastIndex) FollowRowDivider()
                }
            }
        }

        FollowSectionHeader(title = s.acceptedCount(followersCount ?: accepted.size))
        FollowSectionCard {
            if (accepted.isEmpty() && !section.isLoading) {
                FollowEmptyState(s.noFollowersYet)
            } else {
                accepted.forEachIndexed { index, entry ->
                    FollowUserRow(
                        user = entry.user,
                        meta = followedSinceLabel(s.followingSince, entry.followedSince),
                        onClick = { onUserClick(entry.user.id) },
                    )
                    if (index != accepted.lastIndex) FollowRowDivider()
                }
            }
        }
    }
}

/** The accounts this profile follows: the requests it is still waiting on, then the rest. */
@Composable
private fun FollowingList(
    section: FollowSection,
    followingCount: Int?,
    actioningUserId: Long?,
    onCancelOrUnfollow: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val s = strings()
    // Unfollowing an accepted entry is destructive (see UserProfileScreen); cancelling a
    // still-pending request you sent is not, so only this state gates the confirm dialog.
    var pendingUnfollow by remember { mutableStateOf<FollowInfoDto?>(null) }

    FollowScrollColumn(section = section, onLoadMore = onLoadMore) {
        val requested = section.requested
        val following = section.accepted

        if (requested.isNotEmpty()) {
            FollowSectionHeader(title = s.requestedWaiting, badge = requested.size)
            FollowSectionCard {
                requested.forEachIndexed { index, entry ->
                    FollowUserRow(
                        user = entry.user,
                        meta = followedSinceLabel(s.requested, entry.followedSince),
                        onClick = { onUserClick(entry.user.id) },
                    ) {
                        if (actioningUserId == entry.user.id) {
                            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            FollowRowTextAction(text = s.cancel, onClick = { onCancelOrUnfollow(entry.user.id) })
                        }
                    }
                    if (index != requested.lastIndex) FollowRowDivider()
                }
            }
        }

        FollowSectionHeader(title = s.followingCount(followingCount ?: following.size))
        FollowSectionCard {
            if (following.isEmpty() && !section.isLoading) {
                FollowEmptyState(s.notFollowingAnyone)
            } else {
                following.forEachIndexed { index, entry ->
                    FollowUserRow(
                        user = entry.user,
                        meta = followedSinceLabel(s.since, entry.followedSince),
                        onClick = { onUserClick(entry.user.id) },
                    ) {
                        if (actioningUserId == entry.user.id) {
                            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            FollowRowTextAction(text = s.unfollow, onClick = { pendingUnfollow = entry })
                        }
                    }
                    if (index != following.lastIndex) FollowRowDivider()
                }
            }
        }
    }

    // Closes itself once the entry is actually gone, rather than on tapping Unfollow — the
    // dialog's own isConfirming lock (see StickerConfirmDialog) keeps it up while in flight.
    LaunchedEffect(section.entries) {
        val stillThere = pendingUnfollow?.let { p -> section.entries.any { it.user.id == p.user.id } } ?: true
        if (!stillThere) pendingUnfollow = null
    }

    pendingUnfollow?.let { entry ->
        StickerConfirmDialog(
            icon = Icons.Outlined.PersonRemove,
            title = s.unfollowQuestion(entry.user.username),
            message = s.unfollowMessage(entry.user.username),
            confirmText = s.unfollow,
            isConfirming = actioningUserId == entry.user.id,
            onConfirm = { onCancelOrUnfollow(entry.user.id) },
            onDismissRequest = { pendingUnfollow = null },
        )
    }
}

/** `requested` reads better than `pending` on the Following side; same entries either way. */
private val FollowSection.requested get() = pending

@Composable
private fun FollowScrollColumn(
    section: FollowSection,
    onLoadMore: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    val reachedEnd by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 400 }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd && !section.allLoaded) onLoadMore() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 18.dp)) {
        content()
        if (section.isLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun FollowSectionHeader(title: String, badge: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .background(CookncoGold, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            ) {
                Text(badge.toString(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            }
        }
    }
}

@Composable
private fun FollowSectionCard(content: @Composable () -> Unit) {
    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowOffset = 6.dp) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun FollowRowDivider() {
    HorizontalDivider(color = CookncoNavy.copy(alpha = 0.1f), thickness = 2.dp)
}

@Composable
private fun FollowEmptyState(text: String) {
    Text(
        text,
        color = CookncoNavy.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PendingFollowerRow(
    entry: FollowInfoDto,
    isActioning: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onClick: () -> Unit,
) {
    val s = strings()
    FollowUserRow(
        user = entry.user,
        meta = followedSinceLabel(s.requested, entry.followedSince),
        onClick = onClick,
    ) {
        if (isActioning) {
            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        } else {
            FollowRowIconAction(onClick = onAccept, fillColor = CookncoOrange, contentColor = CookncoWhite) {
                Icon(Icons.Outlined.Check, contentDescription = s.accept)
            }
            Spacer(Modifier.size(8.dp))
            FollowRowIconAction(onClick = onDecline, fillColor = CookncoWhite, contentColor = CookncoNavy) {
                Icon(Icons.Outlined.Close, contentDescription = s.decline)
            }
        }
    }
}
