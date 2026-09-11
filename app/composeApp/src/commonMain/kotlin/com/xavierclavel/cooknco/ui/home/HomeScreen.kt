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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
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
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

private val headerDateFormat = LocalDateTime.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_FULL)
    char(',')
    char(' ')
    day()
    char(' ')
    monthName(MonthNames.ENGLISH_FULL)
}

@Composable
fun HomeScreen(
    user: UserInfo,
    viewModel: HomeViewModel,
    onRecipeClick: (Long) -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(reachedEnd) {
        if (reachedEnd && !uiState.allLoaded) viewModel.loadMore()
    }

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        HomeHeader(user = user, onAvatarClick = { onUserClick(user.id) })

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        ) {
            uiState.dateGroups.forEach { group ->
                item(key = "header_${group.label}") {
                    DateGroupHeader(label = group.label, count = group.recipes.size)
                }
                items(group.recipes, key = { it.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        onClick = { onRecipeClick(recipe.id) },
                        onUserClick = onUserClick,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
                    }
                }
            }

            if (uiState.error != null) {
                item {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HomeHeader(user: UserInfo, onAvatarClick: () -> Unit, modifier: Modifier = Modifier) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headerDateFormat.format(today).uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = "What's cooking?",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                lineHeight = 34.sp,
            )
        }
        UserAvatar(
            userId = user.id,
            version = user.version,
            contentDescription = "Your profile",
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
            text = "$count new recipe${if (count == 1) "" else "s"}",
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
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Column {
            RecipeImage(
                recipeId = recipe.id,
                version = recipe.version,
                contentDescription = recipe.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
            )
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(CookncoNavy))

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = recipe.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthorChip(owner = recipe.owner, onClick = { onUserClick(recipe.owner.id) })
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(CookncoOrange)
                            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("♥ ${recipe.likesCount}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoWhite)
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
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UserAvatar(
            userId = owner.id,
            version = owner.version,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape),
        )
        Text(
            text = owner.username,
            fontSize = 12.5.sp,
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
    DateGroup("Today", previewRecipes.take(1)),
    DateGroup("Yesterday", previewRecipes.drop(1)),
)

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    CookncoTheme {
        Column(modifier = Modifier.fillMaxSize().background(CookncoGreen)) {
            HomeHeader(user = previewUser, onAvatarClick = {})
            LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp)) {
                previewGroups.forEach { group ->
                    item { DateGroupHeader(label = group.label, count = group.recipes.size) }
                    items(group.recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe = recipe, onClick = {}, modifier = Modifier.padding(bottom = 16.dp))
                    }
                }
            }
        }
    }
}
