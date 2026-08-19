package com.xavierclavel.cooknco.ui.recipe

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.RecipeSort
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onNavigateBack: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    SearchBar(
                        query = query,
                        onQueryChange = { viewModel.query.value = it },
                        onClear = { viewModel.query.value = "" },
                        focusRequester = focusRequester,
                        onSearch = { keyboard?.hide() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CookncoGreen,
                    navigationIconContentColor = CookncoNavy,
                ),
            )
        },
        containerColor = CookncoBackground,
    ) { innerPadding ->
        RecipesContent(
            uiState = uiState,
            query = query,
            activeSort = sort,
            onSortChange = { viewModel.sort.value = it },
            onRecipeClick = onRecipeClick,
            onUserClick = onUserClick,
            onLoadMore = { viewModel.loadMore() },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

// ── Search bar embedded in TopAppBar ─────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(CookncoWhite)
            .border(1.5.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = CookncoNavy.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search a recipe…",
                        color = CookncoNavy.copy(alpha = 0.4f),
                        fontSize = 15.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = CookncoNavy,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    cursorBrush = SolidColor(CookncoOrange),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Clear",
                    tint = CookncoNavy.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onClear),
                )
            }
        }
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun RecipesContent(
    uiState: RecipesUiState,
    query: String,
    activeSort: RecipeSort,
    onSortChange: (RecipeSort) -> Unit,
    onRecipeClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit = {},
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val reachedEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(reachedEnd) {
        if (reachedEnd) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Sort chips ────────────────────────────────────────────────────────
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecipeSort.entries.forEach { sortOption ->
                    SortChip(
                        label = sortOption.label,
                        selected = activeSort == sortOption,
                        onClick = { onSortChange(sortOption) },
                    )
                }
            }
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (!uiState.isLoading && uiState.recipes.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🍳", fontSize = 48.sp)
                    Text(
                        text = if (query.isBlank()) "No recipes yet" else "No results for \"$query\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = CookncoNavy.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ── Recipe cards ──────────────────────────────────────────────────────
        items(uiState.recipes, key = { it.id }) { recipe ->
            RecipeGridCard(recipe = recipe, onClick = { onRecipeClick(recipe.id) }, onUserClick = onUserClick)
        }

        // ── Loading indicator ─────────────────────────────────────────────────
        if (uiState.isLoading) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp)
                }
            }
        }
    }
}

// ── Sort chip ─────────────────────────────────────────────────────────────────

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) CookncoOrange else CookncoWhite)
            .border(
                1.5.dp,
                if (selected) CookncoNavy else CookncoNavy.copy(alpha = 0.35f),
                RoundedCornerShape(50.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) CookncoWhite else CookncoNavy,
        )
    }
}

// ── Recipe grid card ──────────────────────────────────────────────────────────

@Composable
private fun RecipeGridCard(recipe: RecipeOverview, onClick: () -> Unit, onUserClick: (Long) -> Unit = {}) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoGreen),
        border = BorderStroke(1.5.dp, CookncoNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Thumbnail
            RecipeImage(
                recipeId = recipe.id,
                version = recipe.version,
                contentDescription = recipe.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            // Info footer
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )

                // Author chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(CookncoWhite)
                        .border(1.dp, CookncoNavy.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                        .clickable { onUserClick(recipe.owner.id) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    UserAvatar(
                        userId = recipe.owner.id,
                        version = recipe.owner.version,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        text = recipe.owner.username,
                        style = MaterialTheme.typography.labelSmall,
                        color = CookncoNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Like count
                Text(
                    text = "❤ ${recipe.likesCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CookncoNavy.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Aya Amayri")
private val previewRecipes = (1..6).map { i ->
    RecipeOverview(
        id = i.toLong(),
        version = 1L,
        title = listOf("Harcha", "Chocolate Fondant", "Mint Tea", "Couscous", "Baklava", "Tagine")[i - 1],
        owner = previewOwner,
        likesCount = i * 3,
        creationDate = 0L,
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RecipesScreenGridPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            RecipesContent(
                uiState = RecipesUiState(recipes = previewRecipes, isLoading = false),
                query = "",
                activeSort = RecipeSort.RECENT,
                onSortChange = {},
                onRecipeClick = {},
                onLoadMore = {},
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Grid - Loading")
@Composable
fun RecipesScreenLoadingPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            RecipesContent(
                uiState = RecipesUiState(isLoading = true),
                query = "",
                activeSort = RecipeSort.RECENT,
                onSortChange = {},
                onRecipeClick = {},
                onLoadMore = {},
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Grid - Empty")
@Composable
fun RecipesScreenEmptyPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            RecipesContent(
                uiState = RecipesUiState(recipes = emptyList(), isLoading = false),
                query = "spaghetti",
                activeSort = RecipeSort.BEST_MATCH,
                onSortChange = {},
                onRecipeClick = {},
                onLoadMore = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Search Bar")
@Composable
fun SearchBarPreview() {
    CookncoTheme {
        Surface(color = CookncoGreen, modifier = Modifier.padding(12.dp)) {
            SearchBar(
                query = "harcha",
                onQueryChange = {},
                onClear = {},
                onSearch = {},
                focusRequester = remember { FocusRequester() },
            )
        }
    }
}
