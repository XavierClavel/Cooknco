package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xavierclavel.cooknco.ui.components.LikeCount
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.sheetScrim
import com.xavierclavel.cooknco.ui.theme.swallowTaps
import com.xavierclavel.cooknco.ui.user.FollowUserRow
import com.xavierclavel.cooknco.ui.user.daysSince

/**
 * Who liked a recipe, newest first, opened from its like count. A row opens that cook's
 * profile; the list pages in as it is scrolled, like the followers list it borrows its rows from.
 */
@Composable
fun LikersSheet(
    state: LikersState,
    likesCount: Int,
    onLoadMore: () -> Unit,
    onUserClick: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                .sheetScrim(onDismissRequest),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .swallowTaps()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 26.dp),
            ) {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 12.dp)) {
                            Text(s.likedBy, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                            LikeCount(count = likesCount, modifier = Modifier.align(Alignment.CenterEnd))
                        }
                        Line()
                        when {
                            state.likers.isEmpty() && state.isLoading -> Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp) }

                            state.likers.isEmpty() -> Text(
                                text = if (state.failed) s.somethingWentWrong else s.noLikesYet,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CookncoGreenDark,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 26.dp),
                            )

                            else -> LikersList(state = state, onLoadMore = onLoadMore, onUserClick = onUserClick)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    shadowOffset = 4.dp,
                    onClick = onDismissRequest,
                ) {
                    Text(
                        text = s.done,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun LikersList(state: LikersState, onLoadMore: () -> Unit, onUserClick: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val loadMore by rememberUpdatedState(onLoadMore)
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }.collect { if (it) loadMore() }
    }
    LazyColumn(
        state = listState,
        // Capped, so a much-liked recipe scrolls rather than running off the top of the screen
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
    ) {
        items(state.likers, key = { it.user.id }) { like ->
            FollowUserRow(
                user = like.user.toSummary(),
                meta = strings().likedSince(daysSince(like.creationDate)),
                onClick = { onUserClick(like.user.id) },
            )
            Line()
        }
        if (state.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun Line() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(CookncoNavy.copy(alpha = 0.12f)))
}
