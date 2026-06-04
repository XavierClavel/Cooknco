package com.xavierclavel.cooknco.ui.cookbook

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner

@Composable
fun CookbookScreen(
    cookbookId: Long,
    currentUserId: Long,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CookbookViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.left) {
        if (uiState.left) onNavigateBack()
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onNavigateBack()
    }

    when {
        uiState.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        uiState.error != null && uiState.cookbook == null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        uiState.cookbook != null -> {
            val cookbook = uiState.cookbook!!
            LazyColumn(modifier = modifier.fillMaxSize()) {

                // Header: image + metadata
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AsyncImage(
                            model = "${ApiClient.IMAGE_URL}/cookbooks/${cookbook.id}-v${cookbook.version}.webp",
                            contentDescription = cookbook.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = cookbook.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (cookbook.description.isNotBlank()) {
                                Text(
                                    text = cookbook.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("${cookbook.recipesCount} recipes") },
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("${cookbook.usersCount} members") },
                                )
                            }
                        }
                    }
                }

                // Action buttons
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (uiState.isAdmin) {
                            FilledTonalButton(
                                onClick = { onNavigateToEdit(cookbook.id) },
                            ) {
                                Text("Edit")
                            }
                            Button(
                                onClick = { viewModel.confirmDelete() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Delete")
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.confirmLeave() },
                        ) {
                            Text("Leave")
                        }
                    }
                }

                // Recipes section
                val recipes = uiState.recipes
                if (recipes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recipes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(recipes, key = { "recipe_${it.id}" }) { recipe ->
                        CookbookRecipeRow(recipe = recipe)
                    }
                }

                // Members section
                val members = cookbook.members
                if (members.isNotEmpty()) {
                    item {
                        Text(
                            text = "Members",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(members, key = { "member_${it.id}" }) { member ->
                        MemberRow(member = member)
                    }
                }

                // Error note
                if (uiState.error != null) {
                    item {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // Leave confirmation dialog
    if (uiState.showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelLeave() },
            title = { Text("Leave Cookbook") },
            text = { Text("Are you sure you want to leave this cookbook?") },
            confirmButton = {
                Button(onClick = { viewModel.leave() }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLeave() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete Cookbook") },
            text = { Text("Are you sure you want to delete this cookbook? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.delete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CookbookRecipeRow(recipe: CookbookRecipeInfo) {
    ListItem(
        leadingContent = {
            AsyncImage(
                model = "${ApiClient.IMAGE_URL}/recipes-thumbnails/${recipe.id}-v1.webp",
                contentDescription = recipe.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        },
        headlineContent = { Text(recipe.title) },
        supportingContent = { Text("Added by ${recipe.addedByUsername}") },
    )
}

@Composable
private fun MemberRow(member: RecipeOwner) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
                AsyncImage(
                    model = "${ApiClient.IMAGE_URL}/users/${member.id}-v${member.version}.webp",
                    contentDescription = member.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            }
        },
        headlineContent = { Text(member.username) },
        trailingContent = if (member.role == "ADMIN") ({
            SuggestionChip(
                onClick = {},
                label = { Text("Admin") },
            )
        }) else null,
    )
}
