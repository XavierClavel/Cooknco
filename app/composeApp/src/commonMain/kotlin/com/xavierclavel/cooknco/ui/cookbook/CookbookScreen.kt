package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.network.dto.CookbookUserInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.components.CookbookImage
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill

@Composable
fun CookbookScreen(
    cookbookId: Long,
    currentUserId: Long,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    viewModel: CookbookViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.left) { if (uiState.left) onNavigateBack() }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onNavigateBack() }

    val cookbook = uiState.cookbook

    Surface(modifier = modifier.fillMaxSize(), color = CookncoGreen) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            uiState.error != null && cookbook == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = CookncoNavy, modifier = Modifier.padding(16.dp))
            }

            cookbook != null -> CookbookContent(
                cookbook = cookbook,
                recipes = uiState.recipes,
                members = uiState.members,
                isAdmin = uiState.isAdmin,
                error = uiState.error,
                onLeave = viewModel::confirmLeave,
                onEdit = { onNavigateToEdit(cookbook.id) },
                onDelete = viewModel::confirmDelete,
                onNavigateToRecipe = onNavigateToRecipe,
                onNavigateToUser = onNavigateToUser,
                onNavigateBack = onNavigateBack,
            )
        }
    }

    if (uiState.showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelLeave() },
            title = { Text("Leave Cookbook", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave this cookbook?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.leave() },
                    colors = ButtonDefaults.buttonColors(containerColor = CookncoOrange, contentColor = CookncoWhite),
                ) { Text("Leave", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLeave() }) {
                    Text("Cancel", color = CookncoNavy.copy(alpha = 0.7f))
                }
            },
        )
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete Cookbook", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this cookbook? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.delete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("Cancel", color = CookncoNavy.copy(alpha = 0.7f))
                }
            },
        )
    }
}

// ── Cookbook content ────────────────────────────────────────────────────────────

@Composable
private fun CookbookContent(
    cookbook: CookbookInfo,
    recipes: List<CookbookRecipeInfo>,
    members: List<CookbookUserInfo>,
    isAdmin: Boolean,
    error: String?,
    onLeave: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── Banner image with the back / edit / delete overlay ───────────────
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                CookbookImage(
                    cookbookId = cookbook.id,
                    version = cookbook.version,
                    contentDescription = cookbook.title,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    if (isAdmin) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StickerIconButton(onClick = onEdit, shadowOffset = 3.dp) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                            }
                            StickerIconButton(onClick = onDelete, shadowOffset = 3.dp) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // ── Info card ────────────────────────────────────────────────────────
        item {
            StickerCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Text(
                        text = cookbook.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        lineHeight = 31.sp,
                    )
                    if (cookbook.description.isNotBlank()) {
                        Text(
                            text = cookbook.description,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = CookncoNavy.copy(alpha = 0.72f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip("${cookbook.recipesCount} recipe${if (cookbook.recipesCount != 1) "s" else ""}")
                        StatChip("${cookbook.usersCount} member${if (cookbook.usersCount != 1) "s" else ""}")
                    }
                }
            }
        }

        // ── Leave button ─────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                StickerPill(onClick = onLeave, height = 40.dp, shadowOffset = 3.dp) {
                    Icon(Icons.Outlined.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Leave", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // ── Recipes section ──────────────────────────────────────────────────
        if (recipes.isNotEmpty()) {
            item {
                SectionHeader(title = "Recipes", count = recipes.size)
            }
            items(recipes, key = { "recipe_${it.id}" }) { recipe ->
                RecipeRow(
                    recipe = recipe,
                    onClick = { onNavigateToRecipe(recipe.id) },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
        }

        // ── Members section ──────────────────────────────────────────────────
        if (members.isNotEmpty()) {
            item {
                SectionHeader(title = "Members", count = members.size)
            }
            items(members, key = { "member_${it.id}" }) { member ->
                MemberRow(
                    member = member,
                    onClick = { onNavigateToUser(member.id) },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
        }

        if (error != null) {
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(CookncoOrange)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = CookncoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun StatChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(CookncoWhite)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = CookncoNavy,
        )
    }
}

@Composable
private fun RecipeRow(recipe: CookbookRecipeInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StickerCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipeImage(
                recipeId = recipe.id,
                version = 1L,
                contentDescription = recipe.title,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = recipe.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Added by ${recipe.addedByUsername}",
                    fontSize = 12.sp,
                    color = CookncoNavy.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun MemberRow(member: CookbookUserInfo, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    StickerCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserAvatar(
                userId = member.id,
                version = 1L,
                contentDescription = member.username,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )

            Text(
                text = member.username,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = CookncoNavy,
                modifier = Modifier.weight(1f),
            )

            if (member.isAdmin) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CookncoOrange)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Admin",
                        color = CookncoWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                    )
                }
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Xavier")
private val previewCookbook = CookbookInfo(
    id = 1L, version = 1L,
    title = "My Favourites",
    description = "A collection of my absolute favourite recipes.",
    recipesCount = 3,
    usersCount = 2,
    members = listOf(
        previewOwner,
        RecipeOwner(id = 2L, version = 1L, username = "Aya Amayri", role = "ADMIN"),
    ),
)
private val previewMembers = listOf(
    CookbookUserInfo(id = 1L, username = "Xavier", isAdmin = true, joinDate = 0L),
    CookbookUserInfo(id = 2L, username = "Aya Amayri", isAdmin = false, joinDate = 0L),
)
private val previewRecipes = listOf(
    CookbookRecipeInfo(id = 10L, title = "Harcha", addedById = 2L, addedByUsername = "Aya Amayri", additionDate = 0L),
    CookbookRecipeInfo(id = 11L, title = "Chocolate Fondant", addedById = 1L, addedByUsername = "Xavier", additionDate = 0L),
)

@Preview(showBackground = true)
@Composable
fun CookbookContentPreview() {
    CookncoTheme {
        Surface(color = CookncoGreen) {
            CookbookContent(
                cookbook = previewCookbook,
                recipes = previewRecipes,
                members = previewMembers,
                isAdmin = true,
                error = null,
                onLeave = {},
                onEdit = {},
                onDelete = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Cookbook - Not Admin")
@Composable
fun CookbookContentMemberPreview() {
    CookncoTheme {
        Surface(color = CookncoGreen) {
            CookbookContent(
                cookbook = previewCookbook,
                recipes = previewRecipes,
                members = previewMembers,
                isAdmin = false,
                error = null,
                onLeave = {},
                onEdit = {},
                onDelete = {},
            )
        }
    }
}
