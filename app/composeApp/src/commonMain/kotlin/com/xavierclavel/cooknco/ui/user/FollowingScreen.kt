package com.xavierclavel.cooknco.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.FollowInfoDto
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

@Composable
fun FollowingScreen(
    viewModel: FollowingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFollowers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val reachedEnd by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 400 }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd && !uiState.allLoaded) viewModel.loadMore() }

    // Unfollowing an accepted entry is destructive (see UserProfileScreen); cancelling a
    // still-pending request you sent is not, so only this state gates the confirm dialog.
    var pendingUnfollow by remember { mutableStateOf<FollowInfoDto?>(null) }

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = uiState.profileUser?.username ?: "",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 18.dp),
        ) {
            FollowingTabs(
                followersCount = uiState.profileUser?.followersCount ?: 0,
                followingCount = uiState.profileUser?.followsCount ?: 0,
                onFollowersClick = onNavigateToFollowers,
                modifier = Modifier.padding(top = 4.dp),
            )

            val requested = uiState.requested
            val following = uiState.following

            if (requested.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp, start = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "REQUESTED — WAITING FOR THEM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .background(CookncoGold, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                    ) {
                        Text(requested.size.toString(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                    }
                }
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowOffset = 6.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        requested.forEachIndexed { index, entry ->
                            FollowUserRow(user = entry.user, meta = followedSinceLabel("Requested", entry.followedSince)) {
                                if (uiState.actioningUserId == entry.user.id) {
                                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                } else {
                                    FollowRowTextAction(text = "Cancel", onClick = { viewModel.cancelOrUnfollow(entry.user.id) })
                                }
                            }
                            if (index != requested.lastIndex) {
                                HorizontalDivider(color = CookncoNavy.copy(alpha = 0.1f), thickness = 2.dp)
                            }
                        }
                    }
                }
            }

            Text(
                text = "FOLLOWING · ${uiState.profileUser?.followsCount ?: following.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp),
            )
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowOffset = 6.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (following.isEmpty() && !uiState.isLoading) {
                        Text(
                            "Not following anyone yet",
                            color = CookncoNavy.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        following.forEachIndexed { index, entry ->
                            FollowUserRow(user = entry.user, meta = followedSinceLabel("Since", entry.followedSince)) {
                                if (uiState.actioningUserId == entry.user.id) {
                                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                } else {
                                    FollowRowTextAction(text = "Unfollow", onClick = { pendingUnfollow = entry })
                                }
                            }
                            if (index != following.lastIndex) {
                                HorizontalDivider(color = CookncoNavy.copy(alpha = 0.1f), thickness = 2.dp)
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    // Closes itself once the entry is actually gone, rather than on tapping Unfollow — the
    // dialog's own isConfirming lock (see StickerConfirmDialog) keeps it up while in flight.
    LaunchedEffect(uiState.entries) {
        val stillThere = pendingUnfollow?.let { p -> uiState.entries.any { it.user.id == p.user.id } } ?: true
        if (!stillThere) pendingUnfollow = null
    }

    pendingUnfollow?.let { entry ->
        StickerConfirmDialog(
            icon = Icons.Outlined.PersonRemove,
            title = "Unfollow ${entry.user.username}?",
            message = "They won't show up in your feed anymore. You can follow ${entry.user.username} again anytime.",
            confirmText = "Unfollow",
            isConfirming = uiState.actioningUserId == entry.user.id,
            onConfirm = { viewModel.cancelOrUnfollow(entry.user.id) },
            onDismissRequest = { pendingUnfollow = null },
        )
    }
}
