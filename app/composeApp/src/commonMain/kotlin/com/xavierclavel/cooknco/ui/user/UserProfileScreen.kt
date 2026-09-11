package com.xavierclavel.cooknco.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

private enum class ProfileFilter { RECIPES, LIKED, BOOKS }

/**
 * Reachable both as the bottom nav's own-profile tab and, via `Routes.USER`, to view
 * someone else's — [onNavigateBack] is nullable for the same reason `RecipesScreen`'s
 * is (a tab has no back stack), and [onLogoutClick] stays null everywhere except the
 * own-profile tab call site in `MainScreen`, since logging out only makes sense there:
 * `UserProfileViewModel.isOwnProfile` alone gates the gear icon that opens it.
 */
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToEdit: (() -> Unit)? = null,
    onNavigateToRecipe: (Long) -> Unit = {},
    onLogoutClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            uiState.error != null && uiState.user == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = CookncoNavy, modifier = Modifier.padding(16.dp))
            }

            uiState.user != null -> ProfileContent(
                user = uiState.user!!,
                recipes = uiState.recipes,
                isOwnProfile = viewModel.isOwnProfile,
                isFollowing = uiState.isFollowing,
                isFollowLoading = uiState.isFollowLoading,
                onToggleFollow = { viewModel.toggleFollow() },
                onNavigateBack = onNavigateBack,
                onNavigateToEdit = onNavigateToEdit,
                onLogoutClick = onLogoutClick,
                onLoadMore = { viewModel.loadMoreRecipes() },
                allLoaded = uiState.allRecipesLoaded,
                onRecipeClick = onNavigateToRecipe,
            )
        }
    }
}

@Composable
private fun ProfileContent(
    user: UserInfo,
    recipes: List<RecipeOverview>,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    isFollowLoading: Boolean,
    onToggleFollow: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    onNavigateToEdit: (() -> Unit)?,
    onLogoutClick: (() -> Unit)?,
    onLoadMore: () -> Unit,
    allLoaded: Boolean,
    onRecipeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(ProfileFilter.RECIPES) }
    val gridState = rememberLazyGridState()
    val reachedEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd && !allLoaded) onLoadMore() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Top bar: back (if any) + gear (own profile only) ──────────────────
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp),
                horizontalArrangement = if (onNavigateBack != null) Arrangement.SpaceBetween else Arrangement.End,
            ) {
                if (onNavigateBack != null) {
                    StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
                if (isOwnProfile && onLogoutClick != null) {
                    StickerIconButton(onClick = onLogoutClick, shadowOffset = 3.dp) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            }
        }

        // ── Avatar + name + stats ──────────────────────────────────────────────
        item(span = { GridItemSpan(2) }) {
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    UserAvatar(
                        userId = user.id,
                        version = user.version,
                        contentDescription = user.username,
                        modifier = Modifier.size(82.dp).clip(CircleShape).border(3.dp, CookncoNavy, CircleShape),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.username, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, lineHeight = 27.sp)
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProfileStat(value = user.recipesCount, label = "recipes")
                            ProfileStat(value = user.followersCount, label = "followers")
                            ProfileStat(value = user.followsCount, label = "following")
                        }
                    }
                }
            }
        }

        if (user.bio.isNotBlank()) {
            item(span = { GridItemSpan(2) }) {
                Text(user.bio, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CookncoNavy, lineHeight = 21.sp)
            }
        }

        // ── Edit profile / Follow-unfollow ────────────────────────────────────
        item(span = { GridItemSpan(2) }) {
            when {
                isOwnProfile && onNavigateToEdit != null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StickerCard(
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        shadowOffset = 4.dp,
                        onClick = onNavigateToEdit,
                    ) {
                        Text("Edit profile", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CookncoNavy, modifier = Modifier.align(Alignment.Center))
                    }
                }

                !isOwnProfile -> Button(
                    onClick = onToggleFollow,
                    enabled = !isFollowLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) CookncoNavy.copy(alpha = 0.08f) else CookncoOrange,
                        contentColor = if (isFollowing) CookncoNavy else CookncoWhite,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isFollowLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CookncoNavy)
                    } else {
                        Icon(
                            imageVector = if (isFollowing) Icons.Outlined.PersonRemove else Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isFollowing) "Unfollow" else "Follow",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        // ── Filter pills — only Recipes is backed by real data in this pass ───
        item(span = { GridItemSpan(2) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileFilterPill(label = "Recipes", selected = filter == ProfileFilter.RECIPES, onClick = { filter = ProfileFilter.RECIPES })
                ProfileFilterPill(label = "Liked", selected = filter == ProfileFilter.LIKED, onClick = { filter = ProfileFilter.LIKED })
                ProfileFilterPill(label = "Books", selected = filter == ProfileFilter.BOOKS, onClick = { filter = ProfileFilter.BOOKS })
            }
        }

        // ── Recipe grid ────────────────────────────────────────────────────────
        if (filter != ProfileFilter.RECIPES) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Coming soon",
                    color = CookncoNavy.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else if (recipes.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "No recipes yet",
                    color = CookncoNavy.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(recipes, key = { it.id }) { recipe ->
                ProfileRecipeCard(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
            }
        }
    }
}

@Composable
private fun ProfileStat(value: Int, label: String) {
    Column {
        Text(value.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
        Text(label, fontSize = 12.sp, color = CookncoNavy.copy(alpha = 0.6f))
    }
}

@Composable
private fun ProfileFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) CookncoOrange else CookncoBackground)
            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) CookncoWhite else CookncoNavy)
    }
}

@Composable
private fun ProfileRecipeCard(recipe: RecipeOverview, onClick: () -> Unit) {
    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadowOffset = 5.dp, onClick = onClick) {
        Column {
            RecipeImage(
                recipeId = recipe.id,
                version = recipe.version,
                contentDescription = recipe.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.15f),
            )
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(
                    text = recipe.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Text(
                    text = "♥ ${recipe.likesCount}",
                    fontSize = 11.5.sp,
                    color = CookncoNavy.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewUser = UserInfo(
    id = 1L, version = 2L, username = "Xavier Clavel",
    role = "USER", joinDate = 0L,
    bio = "Passionate about food and cooking since forever 🍳",
    recipesCount = 12, likesCount = 45, cookbooksCount = 3,
    followersCount = 28, followsCount = 7,
)

private val previewRecipes = listOf(
    RecipeOverview(id = 1L, version = 1L, title = "Harcha", owner = RecipeOwner(1L, 2L, "Xavier Clavel"), likesCount = 8, creationDate = 0L),
    RecipeOverview(id = 2L, version = 1L, title = "Chocolate Fondant with Vanilla Ice Cream", owner = RecipeOwner(1L, 2L, "Xavier Clavel"), likesCount = 23, creationDate = 0L),
)

@Preview(showBackground = true, name = "Profile - Own")
@Composable
fun UserProfileOwnPreview() {
    CookncoTheme {
        Box(modifier = Modifier.background(CookncoGreen)) {
            ProfileContent(
                user = previewUser,
                recipes = previewRecipes,
                isOwnProfile = true,
                isFollowing = false,
                isFollowLoading = false,
                onToggleFollow = {},
                onNavigateBack = null,
                onNavigateToEdit = {},
                onLogoutClick = {},
                onLoadMore = {},
                allLoaded = true,
                onRecipeClick = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Profile - Other")
@Composable
fun UserProfileOtherPreview() {
    CookncoTheme {
        Box(modifier = Modifier.background(CookncoGreen)) {
            ProfileContent(
                user = previewUser,
                recipes = previewRecipes,
                isOwnProfile = false,
                isFollowing = true,
                isFollowLoading = false,
                onToggleFollow = {},
                onNavigateBack = {},
                onNavigateToEdit = null,
                onLogoutClick = null,
                onLoadMore = {},
                allLoaded = true,
                onRecipeClick = {},
            )
        }
    }
}
