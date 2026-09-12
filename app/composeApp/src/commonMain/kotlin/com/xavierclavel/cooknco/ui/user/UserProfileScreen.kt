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
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

/**
 * Reachable both as the bottom nav's own-profile tab and, via `Routes.USER`, to view
 * someone else's — [onNavigateBack] is nullable for the same reason `RecipesScreen`'s
 * is (a tab has no back stack), and [onNavigateToSettings] stays null everywhere except
 * the own-profile tab call site in `MainScreen`, since settings only makes sense there:
 * `UserProfileViewModel.isOwnProfile` alone gates the gear icon that opens it.
 */
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToEdit: (() -> Unit)? = null,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToFollowers: () -> Unit = {},
    onNavigateToFollowing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

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
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToFollowers = onNavigateToFollowers,
                onNavigateToFollowing = onNavigateToFollowing,
                onShare = { clipboardManager.setText(AnnotatedString("cooknco.eu/user?id=${uiState.user!!.id}")) },
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
    onNavigateToSettings: (() -> Unit)?,
    onNavigateToFollowers: () -> Unit,
    onNavigateToFollowing: () -> Unit,
    onShare: () -> Unit,
    onLoadMore: () -> Unit,
    allLoaded: Boolean,
    onRecipeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val gridState = rememberLazyGridState()
    val reachedEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd && !allLoaded) onLoadMore() }

    // Following someone is reversible with no real consequence to warn about; unfollowing
    // drops an established relationship, so only that direction is gated behind the shared
    // destructive-confirmation dialog (see `StickerConfirmDialog`).
    var showUnfollowConfirm by remember { mutableStateOf(false) }

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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back)
                    }
                }
                if (isOwnProfile && onNavigateToSettings != null) {
                    StickerIconButton(onClick = onNavigateToSettings, shadowOffset = 3.dp) {
                        Icon(Icons.Outlined.Settings, contentDescription = s.settings)
                    }
                }
            }
        }

        // ── Avatar + name + stats ──────────────────────────────────────────────
        item(span = { GridItemSpan(2) }) {
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
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
                            if (isOwnProfile) {
                                Row(modifier = Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                                    Text(user.recipesCount.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                                    Text("recipes", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
                                }
                            } else {
                                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    InlineStat(user.recipesCount, "recipes")
                                    InlineStat(user.followersCount, "followers")
                                }
                            }
                        }
                    }

                    // Own profile only: the mockup moves followers/following out of the
                    // inline text and into two tappable pills that open the list screens.
                    if (isOwnProfile) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPillButton(
                                count = user.followersCount,
                                label = s.followers,
                                onClick = onNavigateToFollowers,
                                modifier = Modifier.weight(1f),
                            )
                            StatPillButton(
                                count = user.followsCount,
                                label = s.following,
                                onClick = onNavigateToFollowing,
                                modifier = Modifier.weight(1f),
                            )
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

        // ── Edit profile / Share, or Follow-unfollow / Share ──────────────────
        item(span = { GridItemSpan(2) }) {
            when {
                isOwnProfile && onNavigateToEdit != null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StickerCard(
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        shadowOffset = 4.dp,
                        onClick = onNavigateToEdit,
                    ) {
                        Text(s.editProfile, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CookncoNavy, modifier = Modifier.align(Alignment.Center))
                    }
                    StickerCard(
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        shadowOffset = 4.dp,
                        onClick = onShare,
                    ) {
                        Text(s.share, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CookncoNavy, modifier = Modifier.align(Alignment.Center))
                    }
                }

                !isOwnProfile -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Tapping while following asks for confirmation first; tapping while not
                    // following calls straight through — see the dialog above.
                    val onFollowButtonClick: (() -> Unit)? = when {
                        isFollowLoading -> null
                        isFollowing -> { { showUnfollowConfirm = true } }
                        else -> onToggleFollow
                    }
                    StickerCard(
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        shadowOffset = 4.dp,
                        fillColor = if (isFollowing) CookncoBackground else CookncoOrange,
                        onClick = onFollowButtonClick,
                    ) {
                        if (isFollowLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).align(Alignment.Center),
                                strokeWidth = 2.dp,
                                color = if (isFollowing) CookncoNavy else CookncoWhite,
                            )
                        } else {
                            Text(
                                text = if (isFollowing) s.unfollow else s.follow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isFollowing) CookncoNavy else CookncoWhite,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    StickerIconButton(onClick = onShare, size = 52.dp, shape = RoundedCornerShape(14.dp), shadowOffset = 4.dp) {
                        Icon(Icons.Outlined.Share, contentDescription = s.shareProfile)
                    }
                }
            }
        }

        // ── Recipe grid ────────────────────────────────────────────────────────
        if (recipes.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = s.noRecipesYet,
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

    if (showUnfollowConfirm) {
        StickerConfirmDialog(
            icon = Icons.Outlined.PersonRemove,
            title = s.unfollowQuestion(user.username),
            message = "You'll stop seeing ${user.username}'s recipes in your feed. You can follow them again anytime.",
            confirmText = s.unfollow,
            isConfirming = isFollowLoading,
            onConfirm = onToggleFollow,
            onDismissRequest = { showUnfollowConfirm = false },
        )
    }

    // Closes itself once the unfollow actually completes, rather than on tapping Unfollow —
    // `isConfirming` (wired to `isFollowLoading` above) keeps the dialog up while in flight.
    LaunchedEffect(isFollowing) { if (!isFollowing) showUnfollowConfirm = false }
}

@Composable
private fun InlineStat(value: Int, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        Text(value.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
        Text(label, fontSize = 12.sp, color = CookncoGreenDark)
    }
}

/** The tappable "N followers ›" / "N following ›" pill on the own-profile card. */
@Composable
private fun StatPillButton(count: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(CookncoWhite)
            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(count.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            Text("›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
        }
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
                    // Always two lines of room, whether the title needs them or not: the
                    // grid puts these side by side, and a one-line title next to a two-line
                    // one leaves the shorter card stubby and the row ragged.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                LikeCount(
                    count = recipe.likesCount,
                    color = CookncoNavy.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    iconSize = 12.dp,
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
                onNavigateToSettings = {},
                onNavigateToFollowers = {},
                onNavigateToFollowing = {},
                onShare = {},
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
                onNavigateToSettings = null,
                onNavigateToFollowers = {},
                onNavigateToFollowing = {},
                onShare = {},
                onLoadMore = {},
                allLoaded = true,
                onRecipeClick = {},
            )
        }
    }
}
