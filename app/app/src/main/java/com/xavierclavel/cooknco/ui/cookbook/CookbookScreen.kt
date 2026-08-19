package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.xavierclavel.cooknco.ui.components.CookbookImage
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = cookbook?.title ?: "",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isAdmin && cookbook != null) {
                        IconButton(onClick = { onNavigateToEdit(cookbook.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.confirmDelete() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp)
                }
            }

            uiState.error != null && cookbook == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            cookbook != null -> {
                CookbookContent(
                    cookbook = cookbook,
                    recipes = uiState.recipes,
                    isAdmin = uiState.isAdmin,
                    error = uiState.error,
                    onLeave = { viewModel.confirmLeave() },
                    onNavigateToRecipe = onNavigateToRecipe,
                    onNavigateToUser = onNavigateToUser,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }

    if (uiState.showLeaveConfirm) {
        ConfirmationDialog(
            title = "Leave Cookbook",
            body = "Are you sure you want to leave this cookbook?",
            confirmLabel = "Leave",
            onConfirm = { viewModel.leave() },
            onDismiss = { viewModel.cancelLeave() },
        )
    }

    if (uiState.showDeleteConfirm) {
        ConfirmationDialog(
            title = "Delete Cookbook",
            body = "Are you sure you want to delete this cookbook? This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete() },
            onDismiss = { viewModel.cancelDelete() },
            isDestructive = true,
        )
    }
}

@Composable
private fun CookbookContent(
    cookbook: CookbookInfo,
    recipes: List<CookbookRecipeInfo>,
    isAdmin: Boolean,
    error: String?,
    onLeave: () -> Unit,
    onNavigateToRecipe: (Long) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── Cookbook image ───────────────────────────────────────────────────
        item {
            CookbookImage(
                cookbookId = cookbook.id,
                version = cookbook.version,
                contentDescription = cookbook.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        }

        // ── Info card ────────────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CookncoGreen),
                border = BorderStroke(1.5.dp, CookncoNavy),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = cookbook.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                    )

                    if (cookbook.description.isNotBlank()) {
                        Text(
                            text = cookbook.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CookncoNavy.copy(alpha = 0.75f),
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Button(
                    onClick = onLeave,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CookncoNavy.copy(alpha = 0.08f),
                        contentColor = CookncoNavy,
                    ),
                    border = BorderStroke(1.5.dp, CookncoNavy),
                ) {
                    Icon(Icons.Outlined.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Leave", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Recipes section ──────────────────────────────────────────────────
        if (recipes.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recipes",
                    count = recipes.size,
                )
            }
            items(recipes, key = { "recipe_${it.id}" }) { recipe ->
                RecipeRow(
                    recipe = recipe,
                    onClick = { onNavigateToRecipe(recipe.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // ── Members section ──────────────────────────────────────────────────
        val members = cookbook.members
        if (members.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Members",
                    count = members.size,
                )
            }
            items(members, key = { "member_${it.id}" }) { member ->
                MemberRow(
                    member = member,
                    onClick = { onNavigateToUser(member.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        if (error != null) {
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
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
            .clip(RoundedCornerShape(50.dp))
            .background(CookncoWhite)
            .border(1.5.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = CookncoNavy,
        )
    }
}

@Composable
private fun RecipeRow(recipe: CookbookRecipeInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoWhite),
        border = BorderStroke(1.5.dp, CookncoNavy),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipeImage(
                recipeId = recipe.id,
                version = 1L,
                contentDescription = recipe.title,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Added by ${recipe.addedByUsername}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CookncoNavy.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun MemberRow(member: RecipeOwner, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoWhite),
        border = BorderStroke(1.5.dp, CookncoNavy),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserAvatar(
                userId = member.id,
                version = member.version,
                contentDescription = member.username,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )

            Text(
                text = member.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = CookncoNavy,
                modifier = Modifier.weight(1f),
            )

            if (member.role == "ADMIN") {
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
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors(containerColor = CookncoOrange),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CookncoNavy.copy(alpha = 0.7f))
            }
        },
    )
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
private val previewRecipes = listOf(
    CookbookRecipeInfo(id = 10L, title = "Harcha", addedById = 2L, addedByUsername = "Aya Amayri", additionDate = 0L),
    CookbookRecipeInfo(id = 11L, title = "Chocolate Fondant", addedById = 1L, addedByUsername = "Xavier", additionDate = 0L),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CookbookContentPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            CookbookContent(
                cookbook = previewCookbook,
                recipes = previewRecipes,
                isAdmin = true,
                error = null,
                onLeave = {},
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Cookbook - Not Admin")
@Composable
fun CookbookContentMemberPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            CookbookContent(
                cookbook = previewCookbook,
                recipes = previewRecipes,
                isAdmin = false,
                error = null,
                onLeave = {},
            )
        }
    }
}
