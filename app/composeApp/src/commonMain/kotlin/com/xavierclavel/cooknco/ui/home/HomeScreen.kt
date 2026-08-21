package com.xavierclavel.cooknco.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

    val timelineColor = CookncoNavy.copy(alpha = 0.2f)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Vertical timeline line, aligned to the center of the date-group circles
                val lineX = 44.dp.toPx()
                drawLine(
                    color = timelineColor,
                    start = Offset(lineX, 0f),
                    end = Offset(lineX, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            },
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Welcome header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 78.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            ) {
                Text(
                    text = "Welcome, ${user.username}! ✨",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                )
                Text(
                    text = "What's cooking, good looking?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CookncoNavy.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Light,
                )
            }
        }

        uiState.dateGroups.forEach { group ->
            item(key = "header_${group.label}") {
                DateGroupHeader(label = group.label, count = group.recipes.size)
            }
            items(group.recipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onClick = { onRecipeClick(recipe.id) },
                    onUserClick = onUserClick,
                    modifier = Modifier.padding(
                        start = 78.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 4.dp,
                    ),
                )
            }
            item(key = "spacer_${group.label}") {
                Spacer(Modifier.height(16.dp))
            }
        }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp)
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
    }
}

@Composable
private fun DateGroupHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Orange count circle — centered on the timeline line (x = 44dp = 20 + 24 radius)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CookncoOrange)
                .border(2.dp, CookncoNavy, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = CookncoWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            fontSize = 17.sp,
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoGreen),
        border = BorderStroke(1.5.dp, CookncoNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Recipe photo
            RecipeImage(
                recipeId = recipe.id,
                version = recipe.version,
                contentDescription = recipe.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )

            // Green footer: title + author chip
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AuthorChip(owner = recipe.owner, onClick = { onUserClick(recipe.owner.id) })
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
            .border(1.5.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UserAvatar(
            userId = owner.id,
            version = owner.version,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        )
        Text(
            text = owner.username,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
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
    DateGroup("March 22, 2026", previewRecipes.take(1)),
    DateGroup("February 28, 2026", previewRecipes.drop(1)),
)

private val previewState = HomeUiState(dateGroups = previewGroups)

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    CookncoTheme {
        // Standalone preview — renders the layout without a real ViewModel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoBackground),
        ) {
            HomeScreenContent(
                user = previewUser,
                uiState = previewState,
                onRecipeClick = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Home - Loading")
@Composable
fun HomeScreenLoadingPreview() {
    CookncoTheme {
        Box(modifier = Modifier.fillMaxSize().background(CookncoBackground)) {
            HomeScreenContent(user = previewUser, uiState = HomeUiState(isLoading = true), onRecipeClick = {})
        }
    }
}

/**
 * Stateless version used for previews (no ViewModel dependency).
 */
@Composable
private fun HomeScreenContent(
    user: UserInfo,
    uiState: HomeUiState,
    onRecipeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timelineColor = CookncoNavy.copy(alpha = 0.2f)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val lineX = 44.dp.toPx()
                drawLine(timelineColor, Offset(lineX, 0f), Offset(lineX, size.height), 2.dp.toPx())
            },
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 78.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            ) {
                Text(
                    "Welcome, ${user.username}! ✨",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                )
                Text(
                    "What's cooking, good looking?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CookncoNavy.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Light,
                )
            }
        }

        uiState.dateGroups.forEach { group ->
            item { DateGroupHeader(label = group.label, count = group.recipes.size) }
            items(group.recipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onClick = { onRecipeClick(recipe.id) },
                    onUserClick = {},
                    modifier = Modifier.padding(start = 78.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        if (uiState.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp)
                }
            }
        }
    }
}
