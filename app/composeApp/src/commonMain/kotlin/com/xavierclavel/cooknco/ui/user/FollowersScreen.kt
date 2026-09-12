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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

@Composable
fun FollowersScreen(
    viewModel: FollowersViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFollowing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val reachedEnd by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 400 }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd && !uiState.allLoaded) viewModel.loadMore() }

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
            FollowersTabs(
                followersCount = uiState.profileUser?.followersCount ?: 0,
                followingCount = uiState.profileUser?.followsCount ?: 0,
                onFollowingClick = onNavigateToFollowing,
                modifier = Modifier.padding(top = 4.dp),
            )

            val pending = uiState.pending
            val accepted = uiState.accepted

            if (pending.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp, start = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "PENDING REQUESTS",
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
                        Text(pending.size.toString(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                    }
                }
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowOffset = 6.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        pending.forEachIndexed { index, entry ->
                            PendingFollowerRow(
                                entry = entry,
                                isActioning = uiState.actioningUserId == entry.user.id,
                                onAccept = { viewModel.accept(entry.user.id) },
                                onDecline = { viewModel.decline(entry.user.id) },
                            )
                            if (index != pending.lastIndex) {
                                HorizontalDivider(color = CookncoNavy.copy(alpha = 0.1f), thickness = 2.dp)
                            }
                        }
                    }
                }
            }

            Text(
                text = "ACCEPTED · ${uiState.profileUser?.followersCount ?: accepted.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp),
            )
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowOffset = 6.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (accepted.isEmpty() && !uiState.isLoading) {
                        Text(
                            "No followers yet",
                            color = CookncoNavy.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        accepted.forEachIndexed { index, entry ->
                            FollowUserRow(user = entry.user, meta = followedSinceLabel("Following since", entry.followedSince))
                            if (index != accepted.lastIndex) {
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
}

@Composable
private fun PendingFollowerRow(
    entry: FollowInfoDto,
    isActioning: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    FollowUserRow(
        user = entry.user,
        meta = followedSinceLabel("Requested", entry.followedSince),
    ) {
        if (isActioning) {
            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        } else {
            FollowRowIconAction(onClick = onAccept, fillColor = CookncoOrange, contentColor = CookncoWhite) {
                Icon(Icons.Outlined.Check, contentDescription = "Accept")
            }
            Spacer(Modifier.size(8.dp))
            FollowRowIconAction(onClick = onDecline, fillColor = CookncoWhite, contentColor = CookncoNavy) {
                Icon(Icons.Outlined.Close, contentDescription = "Decline")
            }
        }
    }
}
