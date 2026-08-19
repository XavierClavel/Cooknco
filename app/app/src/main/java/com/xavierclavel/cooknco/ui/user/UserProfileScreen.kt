package com.xavierclavel.cooknco.ui.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToEdit: (() -> Unit)? = null,
    onNavigateToRecipe: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.user?.username ?: "Profile",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (viewModel.isOwnProfile && onNavigateToEdit != null) {
                        IconButton(onClick = onNavigateToEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit profile")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CookncoGreen,
                    titleContentColor = CookncoNavy,
                    navigationIconContentColor = CookncoNavy,
                    actionIconContentColor = CookncoNavy,
                ),
            )
        },
        containerColor = CookncoBackground,
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp) }

            uiState.error != null && uiState.user == null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text(uiState.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            uiState.user != null -> ProfileContent(
                user = uiState.user!!,
                recipes = uiState.recipes,
                isOwnProfile = viewModel.isOwnProfile,
                isFollowing = uiState.isFollowing,
                isFollowLoading = uiState.isFollowLoading,
                onToggleFollow = { viewModel.toggleFollow() },
                onNavigateToEdit = onNavigateToEdit,
                onLoadMore = { viewModel.loadMoreRecipes() },
                allLoaded = uiState.allRecipesLoaded,
                onRecipeClick = onNavigateToRecipe,
                modifier = Modifier.padding(innerPadding),
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
    onNavigateToEdit: (() -> Unit)?,
    onLoadMore: () -> Unit,
    allLoaded: Boolean,
    onRecipeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(reachedEnd) {
        if (reachedEnd && !allLoaded) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── Avatar + name + bio ──────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CookncoGreen),
                border = BorderStroke(1.5.dp, CookncoNavy),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UserAvatar(
                        userId = user.id,
                        version = user.version,
                        contentDescription = user.username,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, CookncoNavy, CircleShape),
                    )
                    Text(
                        text = user.username,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        textAlign = TextAlign.Center,
                    )
                    if (user.bio.isNotBlank()) {
                        Text(
                            text = user.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CookncoNavy.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // ── Stats row ────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(value = user.recipesCount, label = "Recipes", modifier = Modifier.weight(1f))
                StatCard(value = user.likesCount, label = "Likes", modifier = Modifier.weight(1f))
                StatCard(value = user.followsCount, label = "Following", modifier = Modifier.weight(1f))
                StatCard(value = user.followersCount, label = "Followers", modifier = Modifier.weight(1f))
            }
        }

        // ── Action button ────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isOwnProfile && onNavigateToEdit != null -> Button(
                        onClick = onNavigateToEdit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CookncoGreen,
                            contentColor = CookncoWhite,
                        ),
                        border = BorderStroke(1.5.dp, CookncoNavy),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Edit profile", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    !isOwnProfile -> Button(
                        onClick = onToggleFollow,
                        enabled = !isFollowLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) CookncoNavy.copy(alpha = 0.08f) else CookncoOrange,
                            contentColor = if (isFollowing) CookncoNavy else CookncoWhite,
                        ),
                        border = BorderStroke(1.5.dp, CookncoNavy),
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
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = if (isFollowing) "Unfollow" else "Follow",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }

        // ── Recipes section ──────────────────────────────────────────────────
        if (recipes.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Recipes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CookncoOrange)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = recipes.size.toString(),
                            color = CookncoWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            items(recipes, key = { it.id }) { recipe ->
                ProfileRecipeCard(
                    recipe = recipe,
                    onClick = { onRecipeClick(recipe.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else {
            item {
                Text(
                    text = "No recipes yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CookncoNavy.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun StatCard(value: Int, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoWhite),
        border = BorderStroke(1.5.dp, CookncoNavy),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CookncoOrange,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CookncoNavy.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileRecipeCard(
    recipe: RecipeOverview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoWhite),
        border = BorderStroke(1.5.dp, CookncoNavy),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipeImage(
                recipeId = recipe.id,
                version = recipe.version,
                contentDescription = recipe.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "❤ ${recipe.likesCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CookncoNavy.copy(alpha = 0.5f),
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
    RecipeOverview(id = 3L, version = 1L, title = "Moroccan Mint Tea", owner = RecipeOwner(1L, 2L, "Xavier Clavel"), likesCount = 5, creationDate = 0L),
)

@Preview(showBackground = true, showSystemUi = true, name = "Profile - Own")
@Composable
fun UserProfileOwnPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            ProfileContent(
                user = previewUser,
                recipes = previewRecipes,
                isOwnProfile = true,
                isFollowing = false,
                isFollowLoading = false,
                onToggleFollow = {},
                onNavigateToEdit = {},
                onLoadMore = {},
                allLoaded = true,
                onRecipeClick = {},
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Profile - Following")
@Composable
fun UserProfileFollowingPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            ProfileContent(
                user = previewUser,
                recipes = previewRecipes,
                isOwnProfile = false,
                isFollowing = true,
                isFollowLoading = false,
                onToggleFollow = {},
                onNavigateToEdit = null,
                onLoadMore = {},
                allLoaded = true,
                onRecipeClick = {},
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Profile - Not Following")
@Composable
fun UserProfileNotFollowingPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            ProfileContent(
                user = previewUser.copy(bio = ""),
                recipes = emptyList(),
                isOwnProfile = false,
                isFollowing = false,
                isFollowLoading = false,
                onToggleFollow = {},
                onNavigateToEdit = null,
                onLoadMore = {},
                allLoaded = true,
                onRecipeClick = {},
            )
        }
    }
}
