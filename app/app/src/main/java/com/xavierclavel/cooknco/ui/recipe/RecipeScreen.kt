package com.xavierclavel.cooknco.ui.recipe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.CustomIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import kotlin.math.roundToInt

private fun unitLabel(unit: String): String = when (unit) {
    "NONE" -> ""
    "UNIT" -> ""
    "GRAM" -> "g"
    "POUND" -> "lb"
    "MILLILITERS" -> "mL"
    "TEASPOON" -> "tsp"
    "TABLESPOON" -> "tbsp"
    "CUP" -> "cup"
    else -> unit
}

private fun scaleAmount(amount: Float?, selectedYield: Int, recipeYield: Int): String {
    if (amount == null) return ""
    val scaled = amount * selectedYield.toFloat() / recipeYield.toFloat()
    return if (scaled == scaled.roundToInt().toFloat()) {
        scaled.roundToInt().toString()
    } else {
        // Round to 2 decimal places
        val rounded = (scaled * 100).roundToInt() / 100f
        if (rounded == rounded.roundToInt().toFloat()) rounded.roundToInt().toString()
        else rounded.toString().trimEnd('0').trimEnd('.')
    }
}

@Composable
fun RecipeScreen(
    recipeId: Long,
    currentUserId: Long,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RecipeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onNavigateBack()
    }

    when {
        uiState.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        uiState.error != null && uiState.recipe == null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        uiState.recipe != null -> {
            val recipe = uiState.recipe!!
            RecipeContent(
                recipe = recipe,
                uiState = uiState,
                isOwner = viewModel.isOwner,
                onToggleLike = { viewModel.toggleLike() },
                onShare = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Recipe URL", "cooknco.eu/recipe?id=${recipe.id}"))
                },
                onYieldMinus = { viewModel.setYield(uiState.selectedYield - 1) },
                onYieldPlus = { viewModel.setYield(uiState.selectedYield + 1) },
                onStartEditNotes = { viewModel.startEditNotes() },
                onNotesChange = { viewModel::updateNotes.invoke(it) },
                onSaveNotes = { viewModel.saveNotes() },
                onCancelNoteEdit = { viewModel.cancelNoteEdit() },
                onEditRecipe = { onNavigateToEdit(recipe.id) },
                onDeleteRecipe = { viewModel.confirmDelete() },
                modifier = modifier,
            )
        }
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text("Delete Recipe") },
            text = { Text("Are you sure you want to delete this recipe? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteRecipe() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun RecipeContent(
    recipe: RecipeInfo,
    uiState: RecipeUiState,
    isOwner: Boolean,
    onToggleLike: () -> Unit,
    onShare: () -> Unit,
    onYieldMinus: () -> Unit,
    onYieldPlus: () -> Unit,
    onStartEditNotes: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onCancelNoteEdit: () -> Unit,
    onEditRecipe: () -> Unit,
    onDeleteRecipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipeYield = recipe.yield ?: 1
    val selectedYield = uiState.selectedYield

    LazyColumn(modifier = modifier.fillMaxSize()) {

        // Title + owner
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = "${ApiClient.IMAGE_URL}/users/${recipe.owner.id}-v${recipe.owner.version}.webp",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = recipe.owner.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // Full-width recipe image
        item {
            AsyncImage(
                model = "${ApiClient.IMAGE_URL}/recipes/${recipe.id}-v${recipe.version}.webp",
                contentDescription = recipe.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            )
        }

        // Action buttons row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (uiState.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (uiState.isLiked) "Unlike" else "Like",
                        tint = if (uiState.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = recipe.likesCount.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                    )
                }
            }
        }

        // Meta chips
        item {
            val hasAnyMeta = recipe.preparationTime != null || recipe.cookingTime != null || recipe.cookingTemperature != null
            if (hasAnyMeta) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recipe.preparationTime?.let {
                        SuggestionChip(onClick = {}, label = { Text("Prep: ${it}min") })
                    }
                    recipe.cookingTime?.let {
                        SuggestionChip(onClick = {}, label = { Text("Cook: ${it}min") })
                    }
                    recipe.cookingTemperature?.let {
                        SuggestionChip(onClick = {}, label = { Text("${it}°C") })
                    }
                }
            }
        }

        // Yield stepper
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Serves",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(onClick = onYieldMinus, enabled = selectedYield > 1) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Decrease yield")
                }
                Text(
                    text = selectedYield.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onYieldPlus) {
                    Icon(Icons.Outlined.Add, contentDescription = "Increase yield")
                }
            }
        }

        // Ingredients section
        val hasIngredients = recipe.ingredients.isNotEmpty() || recipe.customIngredients.isNotEmpty()
        if (hasIngredients) {
            item {
                Text(
                    text = "Ingredients",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            itemsIndexed(recipe.ingredients) { _, ingredient ->
                IngredientRow(ingredient = ingredient, selectedYield = selectedYield, recipeYield = recipeYield)
            }

            itemsIndexed(recipe.customIngredients) { _, customIngredient ->
                CustomIngredientRow(ingredient = customIngredient, selectedYield = selectedYield, recipeYield = recipeYield)
            }
        }

        // Steps section
        if (recipe.steps.isNotEmpty()) {
            item {
                Text(
                    text = "Steps",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            itemsIndexed(recipe.steps) { index, step ->
                ListItem(
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    },
                    headlineContent = { Text(step) },
                )
            }
        }

        // Tips
        if (recipe.tips.isNotBlank()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Tips",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = recipe.tips,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Notes section
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                if (uiState.isEditingNotes) {
                    OutlinedTextField(
                        value = uiState.notes,
                        onValueChange = onNotesChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("Add your personal notes…") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSaveNotes) { Text("Save") }
                        OutlinedButton(onClick = onCancelNoteEdit) { Text("Cancel") }
                    }
                } else {
                    if (uiState.remoteNotes != null) {
                        Text(
                            text = uiState.notes,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                    } else {
                        Text(
                            text = "No notes yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    OutlinedButton(onClick = onStartEditNotes) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Edit notes")
                    }
                }
            }
        }

        // Owner actions
        if (isOwner) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onEditRecipe) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    Button(
                        onClick = onDeleteRecipe,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }

        // Error snackbar-style note
        if (uiState.error != null) {
            item {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun IngredientRow(
    ingredient: RecipeIngredientInfo,
    selectedYield: Int,
    recipeYield: Int,
) {
    val amountStr = scaleAmount(ingredient.amount, selectedYield, recipeYield)
    val unitStr = unitLabel(ingredient.unit)
    val amountDisplay = buildString {
        if (amountStr.isNotEmpty()) append(amountStr)
        if (unitStr.isNotEmpty()) {
            if (amountStr.isNotEmpty()) append(" ")
            append(unitStr)
        }
    }
    val subtitle = buildString {
        if (amountDisplay.isNotEmpty()) append(amountDisplay)
        ingredient.complement?.let { if (it.isNotBlank()) append(if (amountDisplay.isEmpty()) it else ", $it") }
    }

    ListItem(
        headlineContent = { Text(ingredient.name) },
        supportingContent = if (subtitle.isNotBlank()) ({ Text(subtitle) }) else null,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("🥗", style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@Composable
private fun CustomIngredientRow(
    ingredient: CustomIngredientInfo,
    selectedYield: Int,
    recipeYield: Int,
) {
    val amountStr = scaleAmount(ingredient.amount, selectedYield, recipeYield)
    val unitStr = unitLabel(ingredient.unit)
    val subtitle = buildString {
        if (amountStr.isNotEmpty()) append(amountStr)
        if (unitStr.isNotEmpty()) {
            if (amountStr.isNotEmpty()) append(" ")
            append(unitStr)
        }
    }

    ListItem(
        headlineContent = { Text(ingredient.name) },
        supportingContent = if (subtitle.isNotBlank()) ({ Text(subtitle) }) else null,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("🍽️", style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}
