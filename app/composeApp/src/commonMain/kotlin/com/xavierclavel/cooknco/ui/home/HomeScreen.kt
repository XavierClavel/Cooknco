package com.xavierclavel.cooknco.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.ui.components.LikeCount
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.i18n.EnStrings
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


@Composable
fun HomeScreen(
    user: UserInfo,
    viewModel: HomeViewModel,
    onRecipeClick: (Long) -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    /**
     * Where the greeting's avatar goes. Not [onUserClick] with your own id: that pushes
     * the other-people's-profile route, which carries a back arrow and no settings gear,
     * so it opened a second, lesser copy of a screen the bottom bar already holds.
     */
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val s = strings()
    val gridState = rememberLazyGridState()

    val reachedEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(reachedEnd) {
        if (reachedEnd && !uiState.allLoaded) viewModel.loadMore()
    }

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        HomeHeader(user = user, onAvatarClick = onProfileClick)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            uiState.dateGroups.forEach { group ->
                // A date is a heading over the rows beneath it, not a cell beside one.
                item(key = "header_${group.key}", span = { GridItemSpan(2) }) {
                    DateGroupHeader(label = s.dateGroup(group.key), count = group.recipes.size)
                }
                items(group.recipes, key = { it.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        onClick = { onRecipeClick(recipe.id) },
                        onUserClick = onUserClick,
                    )
                }
            }

            if (uiState.isLoading) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
                    }
                }
            }

            if (uiState.error != null) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HomeHeader(user: UserInfo, onAvatarClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = strings()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
        // Top, not Bottom: the greeting runs to two lines in some languages, and an
        // avatar hung off the bottom of a block that tall sits well below the date it is
        // meant to be level with.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = s.fullDate(today.date).uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = s.whatsCooking,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                lineHeight = 34.sp,
            )
        }
        UserAvatar(
            userId = user.id,
            version = user.version,
            contentDescription = s.yourProfile,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(2.5.dp, CookncoNavy, CircleShape)
                .clickable(onClick = onAvatarClick),
        )
    }
}

@Composable
private fun DateGroupHeader(label: String, count: Int) {
    val s = strings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(CookncoBackground)
                .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                .padding(horizontal = 13.dp, vertical = 5.dp),
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
        }
        Text(
            text = s.newRecipesCount(count),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = CookncoNavy,
        )
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeOverview,
    onClick: () -> Unit,
    onUserClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    StickerCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        shadowOffset = 5.dp,
        onClick = onClick,
    ) {
        Column {
            RecipeImage(
                recipeId = recipe.id,
                version = recipe.version,
                contentDescription = recipe.title,
                // An aspect rather than a fixed height, so the picture is the same shape in
                // both columns however wide the handset is.
                modifier = Modifier.fillMaxWidth().aspectRatio(1.15f),
            )
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(CookncoNavy))

            Column(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = recipe.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    // Two lines of room whether the title needs them or not: side by side, a
                    // one-line title next to a two-line one leaves the row ragged and the
                    // shorter card stubby.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // The author is what makes this a feed rather than a list of recipes, so
                    // it stays. It yields its width rather than taking it: a long username
                    // ellipsises instead of pushing the likes off the card.
                    AuthorChip(
                        owner = recipe.owner,
                        onClick = { onUserClick(recipe.owner.id) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(CookncoOrange)
                            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        LikeCount(count = recipe.likesCount, color = CookncoWhite, fontSize = 11.5.sp, iconSize = 12.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorChip(owner: RecipeOwner, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(CookncoWhite)
            .border(2.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        UserAvatar(
            userId = owner.id,
            version = owner.version,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape),
        )
        Text(
            text = owner.username,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

private val previewUser = UserInfo(
    id = 1L, version = 1L, username = "Xavier Clavel",
    role = "USER", joinDate = 0L, bio = "",
    recipesCount = 5, likesCount = 12, cookbooksCount = 2,
    followersCount = 3, followsCount = 7,
)

private val previewOwner = RecipeOwner(id = 2L, version = 1L, username = "Aya Amayri")

private val previewRecipes = listOf(
    RecipeOverview(id = 1L, version = 1L, title = "Harcha", owner = previewOwner, likesCount = 8, creationDate = 1742601600000L),
    RecipeOverview(id = 2L, version = 2L, title = "Chocolate Fondant", owner = previewOwner, likesCount = 23, creationDate = 1740182400000L),
)

private val previewGroups = listOf(
    DateGroup(DateGroupKey.Today, previewRecipes.take(1)),
    DateGroup(DateGroupKey.Yesterday, previewRecipes.drop(1)),
)

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    CookncoTheme {
        Column(modifier = Modifier.fillMaxSize().background(CookncoGreen)) {
            HomeHeader(user = previewUser, onAvatarClick = {})
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                previewGroups.forEach { group ->
                    item(span = { GridItemSpan(2) }) {
                        DateGroupHeader(label = EnStrings.dateGroup(group.key), count = group.recipes.size)
                    }
                    items(group.recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe = recipe, onClick = {})
                    }
                }
            }
        }
    }
}
