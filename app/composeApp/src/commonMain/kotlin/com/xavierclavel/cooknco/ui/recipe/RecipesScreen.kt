package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.IngredientSort
import com.xavierclavel.cooknco.network.RecipeSort
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.network.dto.UserSummary
import com.xavierclavel.cooknco.network.dto.displayName
import com.xavierclavel.cooknco.ui.components.CookbookImage
import com.xavierclavel.cooknco.ui.components.LikeCount
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoOrangeDark
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerDropdownMenu
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.stickerSwitchSpec

/**
 * Also embedded as the bottom nav's Search tab (see `MainScreen`), which has nowhere to
 * pop back to — hence [onNavigateBack] being nullable, the way `UserProfileScreen`'s
 * already is: null renders no back button, non-null (the standalone `Routes.RECIPES`
 * push route) renders one.
 *
 * The scope pills switch between a combined "everything" view (recipes, people,
 * cookbooks, ingredients — sectioned) and one entity type at a time. See
 * `Cooknco Mobile.dc.html`, turn 5 / option `5a`.
 */
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onRecipeClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit = {},
    onCookbookClick: (Long) -> Unit = {},
    onIngredientClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val ingredientSort by viewModel.ingredientSort.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val usersState by viewModel.usersState.collectAsState()
    val cookbooksState by viewModel.cookbooksState.collectAsState()
    val ingredientsState by viewModel.ingredientsState.collectAsState()

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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back)
                }
            }
            SearchField(
                query = query,
                onQueryChange = { viewModel.query.value = it },
                onClear = { viewModel.query.value = "" },
                placeholder = if (scope == SearchScope.RECIPES) s.searchARecipe else s.searchEllipsis,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
        }

        ScopePillsRow(
            scope = scope,
            uiState = uiState,
            usersState = usersState,
            cookbooksState = cookbooksState,
            ingredientsState = ingredientsState,
            onScopeSelected = { viewModel.onScopeSelected(it) },
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
        )

        when (scope) {
            SearchScope.ALL -> AllScopeContent(
                query = query,
                onIngredientClick = onIngredientClick,
                uiState = uiState,
                usersState = usersState,
                cookbooksState = cookbooksState,
                ingredientsState = ingredientsState,
                onRecipeClick = onRecipeClick,
                onUserClick = onUserClick,
                onCookbookClick = onCookbookClick,
                onSeeAll = { viewModel.onScopeSelected(it) },
                modifier = Modifier.weight(1f),
            )
            SearchScope.RECIPES -> RecipesScopeContent(
                uiState = uiState,
                query = query,
                activeSort = sort,
                onSortChange = { viewModel.onSortPicked(it) },
                onRecipeClick = onRecipeClick,
                onLoadMore = { viewModel.loadMore() },
                modifier = Modifier.weight(1f),
            )
            SearchScope.USERS -> UsersScopeContent(
                state = usersState,
                query = query,
                onUserClick = onUserClick,
                modifier = Modifier.weight(1f),
            )
            SearchScope.COOKBOOKS -> CookbooksScopeContent(
                state = cookbooksState,
                query = query,
                onCookbookClick = onCookbookClick,
                modifier = Modifier.weight(1f),
            )
            SearchScope.INGREDIENTS -> IngredientsScopeContent(
                state = ingredientsState,
                query = query,
                onIngredientClick = onIngredientClick,
                activeSort = ingredientSort,
                onSortChange = viewModel::onIngredientSortPicked,
                modifier = Modifier.weight(1f),
            )
        }
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
    placeholder: String = "Search a recipe…",
) {
    val s = strings()
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
                Text(placeholder, color = CookncoNavy.copy(alpha = 0.42f), fontSize = 16.sp)
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
                contentDescription = s.clear,
                tint = CookncoNavy,
                modifier = Modifier.size(16.dp).clickable(onClick = onClear),
            )
        }
    }
}

// ── Scope pills ──────────────────────────────────────────────────────────────

/**
 * Approximate vs. exact: `/recipe` and `/cookbook` answer a plain list with no total
 * match count (unlike `/user` and `/ingredient`, wrapped in `SearchResult`), so their
 * pill shows the loaded page size with a trailing "+" once that page is full — there
 * may be more than what is currently buffered.
 */
private data class PillCount(val value: Int, val approximate: Boolean)

@Composable
private fun ScopePillsRow(
    scope: SearchScope,
    uiState: RecipesUiState,
    usersState: SimpleListUiState<UserSummary>,
    cookbooksState: SimpleListUiState<CookbookInfo>,
    ingredientsState: SimpleListUiState<IngredientSummary>,
    onScopeSelected: (SearchScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val recipesCount = if (uiState.isLoading && uiState.recipes.isEmpty()) {
        null
    } else {
        PillCount(uiState.recipes.size, approximate = !uiState.allLoaded)
    }
    val usersCount = if (usersState.isLoading && usersState.items.isEmpty()) {
        null
    } else {
        PillCount(usersState.count, approximate = false)
    }
    val cookbooksCount = if (cookbooksState.isLoading && cookbooksState.items.isEmpty()) {
        null
    } else {
        PillCount(cookbooksState.count, approximate = cookbooksState.items.size >= 20)
    }
    val ingredientsCount = if (ingredientsState.isLoading && ingredientsState.items.isEmpty()) {
        null
    } else {
        PillCount(ingredientsState.count, approximate = false)
    }
    val allCount = if (recipesCount != null && usersCount != null && cookbooksCount != null && ingredientsCount != null) {
        PillCount(
            recipesCount.value + usersCount.value + cookbooksCount.value + ingredientsCount.value,
            approximate = recipesCount.approximate || usersCount.approximate || cookbooksCount.approximate || ingredientsCount.approximate,
        )
    } else {
        null
    }
    val counts = mapOf(
        SearchScope.ALL to allCount,
        SearchScope.RECIPES to recipesCount,
        SearchScope.USERS to usersCount,
        SearchScope.COOKBOOKS to cookbooksCount,
        SearchScope.INGREDIENTS to ingredientsCount,
    )

    val scrollState = rememberScrollState()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val copy = strings()
        SearchScope.entries.forEach { pill ->
            val selected = pill == scope
            val count = counts[pill]
            val fill by animateColorAsState(
                targetValue = if (selected) CookncoNavy else CookncoBackground,
                animationSpec = stickerSwitchSpec(),
                label = "scope_fill",
            )
            val content by animateColorAsState(
                targetValue = if (selected) CookncoWhite else CookncoNavy,
                animationSpec = stickerSwitchSpec(),
                label = "scope_content",
            )
            StickerPill(
                height = 40.dp,
                borderWidth = 2.dp,
                shadowOffset = 0.dp,
                fillColor = fill,
                contentColor = content,
                contentPadding = PaddingValues(horizontal = 13.dp),
                onClick = { onScopeSelected(pill) },
            ) {
                val name = copy.searchScopeName(pill)
                val label = if (count != null) "$name ${count.value}${if (count.approximate) "+" else ""}" else name
                Text(text = label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── "Everything" scope ───────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, trailing: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSeeAll).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
        )
        Text(text = trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

@Composable
private fun SearchEmptyState(query: String, browseLabel: String, modifier: Modifier = Modifier) {
    val s = strings()
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🔍", fontSize = 48.sp)
        Text(
            text = if (query.isBlank()) browseLabel else s.noResultsFor(query),
            fontWeight = FontWeight.Bold,
            color = CookncoNavy.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AllScopeContent(
    query: String,
    onIngredientClick: (Long) -> Unit,
    uiState: RecipesUiState,
    usersState: SimpleListUiState<UserSummary>,
    cookbooksState: SimpleListUiState<CookbookInfo>,
    ingredientsState: SimpleListUiState<IngredientSummary>,
    onRecipeClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onCookbookClick: (Long) -> Unit,
    onSeeAll: (SearchScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val nothingLoadedYet = uiState.recipes.isEmpty() && usersState.items.isEmpty() &&
        cookbooksState.items.isEmpty() && ingredientsState.items.isEmpty()
    val stillLoading = uiState.isLoading || usersState.isLoading || cookbooksState.isLoading || ingredientsState.isLoading

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
    ) {
        if (nothingLoadedYet && stillLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
                }
            }
        }
        if (nothingLoadedYet && !stillLoading) {
            item { SearchEmptyState(query = query, browseLabel = s.nothingHereYet) }
        }

        if (ingredientsState.items.isNotEmpty()) {
            item {
                SectionHeader(title = s.ingredientsCaps, trailing = "${ingredientsState.count} →", onSeeAll = { onSeeAll(SearchScope.INGREDIENTS) })
            }
            item {
                FlowRowIngredients(onIngredientClick = onIngredientClick, ingredients = ingredientsState.items.take(3))
            }
        }

        if (uiState.recipes.isNotEmpty()) {
            item {
                SectionHeader(title = s.recipesCaps, trailing = s.seeAll(uiState.recipes.size, !uiState.allLoaded), onSeeAll = { onSeeAll(SearchScope.RECIPES) })
            }
            item {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column {
                        uiState.recipes.take(3).forEach { recipe ->
                            RecipeResultRow(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
                        }
                    }
                }
            }
        }

        if (usersState.items.isNotEmpty()) {
            item {
                SectionHeader(title = s.peopleCaps, trailing = "${usersState.count} →", onSeeAll = { onSeeAll(SearchScope.USERS) })
            }
            item {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column {
                        usersState.items.take(3).forEach { user ->
                            UserResultRow(user = user, onClick = { onUserClick(user.id) })
                        }
                    }
                }
            }
        }

        if (cookbooksState.items.isNotEmpty()) {
            item {
                val trailing = "${cookbooksState.count}${if (cookbooksState.items.size >= 20) "+" else ""} →"
                SectionHeader(title = s.cookbooksCaps, trailing = trailing, onSeeAll = { onSeeAll(SearchScope.COOKBOOKS) })
            }
            items(cookbooksState.items.take(2), key = { "all-${it.id}" }) { cookbook ->
                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                    CookbookResultCard(cookbook = cookbook, onClick = { onCookbookClick(cookbook.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowIngredients(ingredients: List<IngredientSummary>, onIngredientClick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ingredients.forEach { ingredient ->
            IngredientChip(ingredient = ingredient, onClick = { onIngredientClick(ingredient.id) })
        }
    }
}

// ── Recipes scope (unchanged behaviour: sort chips, infinite scroll) ─────────

@Composable
private fun RecipesScopeContent(
    uiState: RecipesUiState,
    query: String,
    activeSort: RecipeSort,
    onSortChange: (RecipeSort) -> Unit,
    onRecipeClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
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
        // ── Result count, and the sort behind it ──────────────────────────────
        // Only once there are results: "0 recipes" next to an order to sort them by, above
        // an empty state that already says there is nothing, is three ways of saying it.
        if (uiState.recipes.isNotEmpty()) item {
            var sortExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s.recipeCount(uiState.recipes.size),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CookncoNavy,
                    modifier = Modifier.weight(1f),
                )
                // The artboard puts the sort here as one line of type that opens a menu,
                // not as a row of chips: three chips spent a whole line announcing orders
                // nobody had asked for, above results that are the point of the screen.
                StickerDropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                    items = RecipeSort.entries.filter { it != RecipeSort.BEST_MATCH || query.isNotBlank() },
                    label = { s.recipeSortName(it) },
                    selected = { it == activeSort },
                    onSelect = { onSortChange(it); sortExpanded = false },
                    alignEnd = true,
                    width = 196.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { sortExpanded = !sortExpanded }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(s.recipeSortName(activeSort), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                        Text(
                            text = if (sortExpanded) "\u25b4" else "\u25be",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                        )
                    }
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
                        text = if (query.isBlank()) s.noRecipesYet else s.noResultsFor(query),
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ── Result rows, one sticker card. The count lives on the sort line above. ──
        if (uiState.recipes.isNotEmpty()) {
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
private fun RecipeResultRow(recipe: RecipeOverview, onClick: () -> Unit) {
    val s = strings()
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
            Text(text = s.byAuthor(recipe.owner.username), fontSize = 12.5.sp, color = CookncoNavy.copy(alpha = 0.62f))
            LikeCount(count = recipe.likesCount, color = CookncoOrangeDark, fontSize = 12.sp)
        }
    }
}

// ── Users scope ──────────────────────────────────────────────────────────────

@Composable
private fun UsersScopeContent(
    state: SimpleListUiState<UserSummary>,
    query: String,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
    ) {
        if (!state.isLoading && state.items.isEmpty()) {
            item { SearchEmptyState(query = query, browseLabel = s.noUsersYet) }
        }
        if (state.items.isNotEmpty()) {
            item {
                Text(
                    text = s.userCount(state.count),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CookncoNavy,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column {
                        state.items.forEach { user -> UserResultRow(user = user, onClick = { onUserClick(user.id) }) }
                    }
                }
            }
        }
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun UserResultRow(user: UserSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatar(
            userId = user.id,
            version = user.version,
            contentDescription = user.username,
            modifier = Modifier.size(44.dp).clip(CircleShape).border(2.dp, CookncoNavy, CircleShape),
        )
        Text(
            text = user.username,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Cookbooks scope ──────────────────────────────────────────────────────────

@Composable
private fun CookbooksScopeContent(
    state: SimpleListUiState<CookbookInfo>,
    query: String,
    onCookbookClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
    ) {
        if (!state.isLoading && state.items.isEmpty()) {
            item { SearchEmptyState(query = query, browseLabel = "No cookbooks yet") }
        }
        if (state.items.isNotEmpty()) {
            item {
                val suffix = if (state.items.size >= 20) "+" else ""
                Text(
                    text = "${state.count}$suffix cookbook${if (state.count == 1 && suffix.isEmpty()) "" else "s"}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CookncoNavy,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(state.items, key = { it.id }) { cookbook ->
                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                    CookbookResultCard(cookbook = cookbook, onClick = { onCookbookClick(cookbook.id) })
                }
            }
        }
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun CookbookResultCard(cookbook: CookbookInfo, onClick: () -> Unit) {
    val s = strings()
    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CookbookImage(
                cookbookId = cookbook.id,
                version = cookbook.version,
                contentDescription = cookbook.title,
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, CookncoNavy, RoundedCornerShape(14.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = cookbook.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = s.recipeCount(cookbook.recipesCount) + " · " + s.memberCount(cookbook.usersCount),
                    fontSize = 12.5.sp,
                    color = CookncoNavy.copy(alpha = 0.62f),
                )
            }
        }
    }
}

// ── Ingredients ("food") scope ───────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientsScopeContent(
    state: SimpleListUiState<IngredientSummary>,
    query: String,
    onIngredientClick: (Long) -> Unit,
    activeSort: IngredientSort,
    onSortChange: (IngredientSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
    ) {
        if (!state.isLoading && state.items.isEmpty()) {
            item { SearchEmptyState(query = query, browseLabel = "No ingredients yet") }
        }
        if (state.items.isNotEmpty()) {
            item {
                var sortExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = s.ingredientCount(state.count),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CookncoNavy,
                        modifier = Modifier.weight(1f),
                    )
                    StickerDropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                        items = IngredientSort.entries,
                        label = { s.ingredientSortName(it) },
                        selected = { it == activeSort },
                        onSelect = { onSortChange(it); sortExpanded = false },
                        alignEnd = true,
                        width = 196.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { sortExpanded = !sortExpanded }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = s.ingredientSortName(activeSort),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CookncoNavy,
                            )
                            Text(
                                text = if (sortExpanded) "\u25b4" else "\u25be",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CookncoNavy,
                            )
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.items.forEach { ingredient ->
                        IngredientChip(ingredient = ingredient, onClick = { onIngredientClick(ingredient.id) })
                    }
                }
            }
        }
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
                }
            }
        }
    }
}

/** No detail screen exists for a single ingredient, so this chip is display-only. */
@Composable
private fun IngredientChip(ingredient: IngredientSummary, onClick: () -> Unit) {
    val name = ingredient.displayName()
    StickerPill(height = 44.dp, contentPadding = PaddingValues(start = 6.dp, end = 14.dp), onClick = onClick) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(CookncoGreenLight).border(2.dp, CookncoNavy, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = name.take(1).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
        }
        Spacer(Modifier.width(8.dp))
        Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
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
            RecipesScopeContent(
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
            RecipesScopeContent(
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
