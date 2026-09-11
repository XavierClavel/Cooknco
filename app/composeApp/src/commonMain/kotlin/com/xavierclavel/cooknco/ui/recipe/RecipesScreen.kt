package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.RecipeSort
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoOrangeDark
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

/**
 * Also embedded as the bottom nav's Search tab (see `MainScreen`), which has nowhere to
 * pop back to — hence [onNavigateBack] being nullable, the way `UserProfileScreen`'s
 * already is: null renders no back button, non-null (the standalone `Routes.RECIPES`
 * push route) renders one.
 */
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onRecipeClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onNavigateBack != null) {
                StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
            SearchField(
                query = query,
                onQueryChange = { viewModel.query.value = it },
                onClear = { viewModel.query.value = "" },
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
        }

        RecipesContent(
            uiState = uiState,
            query = query,
            activeSort = sort,
            onSortChange = { viewModel.onSortPicked(it) },
            onRecipeClick = onRecipeClick,
            onUserClick = onUserClick,
            onLoadMore = { viewModel.loadMore() },
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Search field ─────────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CookncoBackground)
            .border(3.dp, CookncoNavy, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(18.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search a recipe…", color = CookncoNavy.copy(alpha = 0.42f), fontSize = 16.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = CookncoNavy, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(CookncoOrange),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Clear",
                tint = CookncoNavy,
                modifier = Modifier.size(16.dp).clickable(onClick = onClear),
            )
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
    val listState = rememberLazyListState()
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd) onLoadMore() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
    ) {
        // ── Sort chips ────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val sortOptions = RecipeSort.entries.filter {
                    it != RecipeSort.BEST_MATCH || query.isNotBlank()
                }
                sortOptions.forEach { sortOption ->
                    SortChip(label = sortOption.label, selected = activeSort == sortOption, onClick = { onSortChange(sortOption) })
                }
            }
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (!uiState.isLoading && uiState.recipes.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🍳", fontSize = 48.sp)
                    Text(
                        text = if (query.isBlank()) "No recipes yet" else "No results for \"$query\"",
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ── Results count + result rows, one sticker card ────────────────────
        if (uiState.recipes.isNotEmpty()) {
            item {
                Text(
                    text = "${uiState.recipes.size} recipe${if (uiState.recipes.size == 1) "" else "s"}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CookncoNavy,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column {
                        uiState.recipes.forEach { recipe ->
                            RecipeResultRow(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
                        }
                    }
                }
            }
        }

        if (uiState.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) CookncoNavy else CookncoBackground)
            .border(2.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) CookncoWhite else CookncoNavy,
        )
    }
}

@Composable
private fun RecipeResultRow(recipe: RecipeOverview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecipeImage(
            recipeId = recipe.id,
            version = recipe.version,
            contentDescription = recipe.title,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, CookncoNavy, RoundedCornerShape(12.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = recipe.title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "by ${recipe.owner.username}", fontSize = 12.5.sp, color = CookncoNavy.copy(alpha = 0.62f))
            Text(text = "♥ ${recipe.likesCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CookncoOrangeDark)
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Aya Amayri")
private val previewRecipes = (1..4).map { i ->
    RecipeOverview(
        id = i.toLong(),
        version = 1L,
        title = listOf("Harcha", "Chocolate Fondant", "Mint Tea", "Couscous")[i - 1],
        owner = previewOwner,
        likesCount = i * 3,
        creationDate = 0L,
    )
}

@Preview(showBackground = true)
@Composable
fun RecipesScreenListPreview() {
    CookncoTheme {
        Box(modifier = Modifier.background(CookncoGreen)) {
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

@Preview(showBackground = true, name = "List - Empty")
@Composable
fun RecipesScreenEmptyPreview() {
    CookncoTheme {
        Box(modifier = Modifier.background(CookncoGreen)) {
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
