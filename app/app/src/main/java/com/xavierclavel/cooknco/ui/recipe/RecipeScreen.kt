package com.xavierclavel.cooknco.ui.recipe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.CustomIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import kotlin.math.roundToInt

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun unitLabel(unit: String): String = when (unit) {
    "NONE", "UNIT" -> ""
    "GRAM" -> "g"
    "POUND" -> "lb"
    "MILLILITERS" -> "mL"
    "TEASPOON" -> "teaspoons"
    "TABLESPOON" -> "tablespoons"
    "CUP" -> "cup"
    else -> unit
}

private fun scaleAmount(amount: Float?, selectedYield: Int, recipeYield: Int): String {
    if (amount == null) return ""
    val scaled = amount * selectedYield.toFloat() / recipeYield.toFloat()
    return if (scaled == scaled.roundToInt().toFloat()) scaled.roundToInt().toString()
    else ((scaled * 100).roundToInt() / 100f).toString().trimEnd('0').trimEnd('.')
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    recipeId: Long,
    currentUserId: Long,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToUser: (Long) -> Unit = {},
    viewModel: RecipeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onNavigateBack() }

    val recipe = uiState.recipe

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = recipe?.title ?: "",
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
                    if (viewModel.isOwner && recipe != null) {
                        IconButton(onClick = { onNavigateToEdit(recipe.id) }) {
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
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp) }

            uiState.error != null && recipe == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text(uiState.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            recipe != null -> RecipeContent(
                recipe = recipe,
                uiState = uiState,
                isOwner = viewModel.isOwner,
                onToggleLike = viewModel::toggleLike,
                onShare = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("Recipe URL", "cooknco.eu/recipe?id=${recipe.id}"))
                },
                onYieldMinus = { viewModel.setYield(uiState.selectedYield - 1) },
                onYieldPlus = { viewModel.setYield(uiState.selectedYield + 1) },
                onStartEditNotes = viewModel::startEditNotes,
                onNotesChange = viewModel::updateNotes,
                onSaveNotes = viewModel::saveNotes,
                onCancelNoteEdit = viewModel::cancelNoteEdit,
                onNavigateToUser = onNavigateToUser,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text("Delete Recipe", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this recipe? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteRecipe() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text("Cancel", color = CookncoNavy.copy(alpha = 0.7f))
                }
            },
        )
    }
}

// ── Recipe content ────────────────────────────────────────────────────────────

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
    onNavigateToUser: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val recipeYield = recipe.yield ?: 1

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── Full-width banner image ──────────────────────────────────────────
        item {
            RecipeImage(
                recipeId = recipe.id,
                version = recipe.version,
                contentDescription = recipe.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                thumbnail = false,
            )
        }

        // ── Green info card: title, description, author, meta ────────────────
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                    )

                    if (recipe.description.isNotBlank()) {
                        Text(
                            text = recipe.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CookncoNavy.copy(alpha = 0.8f),
                        )
                    }

                    // Author chip
                    AuthorChip(owner = recipe.owner, onClick = { onNavigateToUser(recipe.owner.id) })

                    // Cooking meta chips
                    val hasMeta = recipe.preparationTime != null || recipe.cookingTime != null || recipe.cookingTemperature != null
                    if (hasMeta) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            recipe.cookingTime?.let {
                                MetaChip(icon = Icons.Outlined.Timer, label = "$it min")
                            }
                            recipe.preparationTime?.let {
                                MetaChip(icon = Icons.Outlined.Timer, label = "$it min prep")
                            }
                            recipe.cookingTemperature?.let {
                                MetaChip(icon = Icons.Outlined.Thermostat, label = "$it °C")
                            }
                        }
                    }
                }
            }
        }

        // ── Action buttons: like, share, (owner: edit, delete) ───────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    icon = if (uiState.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = recipe.likesCount.toString(),
                    onClick = onToggleLike,
                    tint = if (uiState.isLiked) CookncoOrange else CookncoNavy,
                )
                ActionButton(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    onClick = onShare,
                )
                Spacer(Modifier.weight(1f))
                if (isOwner) {
                    ActionButton(
                        icon = Icons.Outlined.Edit,
                        label = "Edit",
                        onClick = { /* handled via top bar */ },
                    )
                    ActionButton(
                        icon = Icons.Outlined.Delete,
                        label = "Delete",
                        onClick = { /* handled via top bar */ },
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // ── Yield stepper ────────────────────────────────────────────────────
        if (recipe.yield != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CookncoWhite),
                    border = BorderStroke(1.5.dp, CookncoNavy),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = onYieldMinus,
                            enabled = uiState.selectedYield > 1,
                        ) {
                            Icon(
                                Icons.Outlined.Remove,
                                contentDescription = "Decrease",
                                tint = if (uiState.selectedYield > 1) CookncoNavy else CookncoNavy.copy(alpha = 0.3f),
                            )
                        }
                        Text(
                            text = uiState.selectedYield.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        IconButton(onClick = onYieldPlus) {
                            Icon(Icons.Outlined.Add, contentDescription = "Increase", tint = CookncoNavy)
                        }
                    }
                }
            }
        }

        // ── Ingredients ──────────────────────────────────────────────────────
        val hasIngredients = recipe.ingredients.isNotEmpty() || recipe.customIngredients.isNotEmpty()
        if (hasIngredients) {
            item {
                SectionHeader(
                    title = "Ingredients",
                    count = recipe.ingredients.size + recipe.customIngredients.size,
                )
            }
            itemsIndexed(recipe.ingredients) { _, ingredient ->
                IngredientRow(
                    ingredient = ingredient,
                    selectedYield = uiState.selectedYield,
                    recipeYield = recipeYield,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            itemsIndexed(recipe.customIngredients) { _, ci ->
                CustomIngredientRow(
                    ingredient = ci,
                    selectedYield = uiState.selectedYield,
                    recipeYield = recipeYield,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // ── Steps ────────────────────────────────────────────────────────────
        if (recipe.steps.isNotEmpty()) {
            item { SectionHeader(title = "Steps", count = recipe.steps.size) }
            itemsIndexed(recipe.steps) { index, step ->
                StepRow(
                    index = index,
                    step = step,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // ── Tips ─────────────────────────────────────────────────────────────
        if (recipe.tips.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CookncoGreen),
                    border = BorderStroke(1.5.dp, CookncoNavy),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CookncoNavy)
                        Spacer(Modifier.height(6.dp))
                        Text(recipe.tips, style = MaterialTheme.typography.bodyMedium, color = CookncoNavy.copy(alpha = 0.85f))
                    }
                }
            }
        }

        // ── Notes ────────────────────────────────────────────────────────────
        item {
            NotesCard(
                notes = uiState.notes,
                remoteNotes = uiState.remoteNotes,
                isEditing = uiState.isEditingNotes,
                onStartEdit = onStartEditNotes,
                onNotesChange = onNotesChange,
                onSave = onSaveNotes,
                onCancel = onCancelNoteEdit,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (uiState.error != null) {
            item {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ── Reusable sub-composables ──────────────────────────────────────────────────

@Composable
private fun AuthorChip(owner: RecipeOwner, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(CookncoWhite)
            .border(1.5.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UserAvatar(
            userId = owner.id,
            version = owner.version,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(CircleShape),
        )
        Text(
            text = owner.username,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = CookncoNavy,
        )
    }
}

@Composable
private fun MetaChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(CookncoWhite)
            .border(1.5.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = CookncoNavy)
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = CookncoNavy)
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = CookncoNavy,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoWhite),
        border = BorderStroke(1.5.dp, CookncoNavy),
    ) {
        Column(
            modifier = Modifier
                .size(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
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
            Text(count.toString(), color = CookncoWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun IngredientRow(
    ingredient: RecipeIngredientInfo,
    selectedYield: Int,
    recipeYield: Int,
    modifier: Modifier = Modifier,
) {
    val amountStr = scaleAmount(ingredient.amount, selectedYield, recipeYield)
    val unitStr = unitLabel(ingredient.unit)
    val subtitle = buildString {
        if (amountStr.isNotEmpty()) append(amountStr)
        if (unitStr.isNotEmpty()) append(if (amountStr.isEmpty()) unitStr else " $unitStr")
        ingredient.complement?.takeIf { it.isNotBlank() }?.let {
            append(if (isEmpty()) it else ", $it")
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoWhite),
        border = BorderStroke(1.5.dp, CookncoNavy),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Ingredient type icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CookncoGreenLight),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = "${ApiClient.IMAGE_URL}/ingredients/${ingredient.type}.webp",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(ingredient.name, fontWeight = FontWeight.Bold, color = CookncoNavy, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookncoNavy.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun CustomIngredientRow(
    ingredient: CustomIngredientInfo,
    selectedYield: Int,
    recipeYield: Int,
    modifier: Modifier = Modifier,
) {
    val amountStr = scaleAmount(ingredient.amount, selectedYield, recipeYield)
    val unitStr = unitLabel(ingredient.unit)
    val subtitle = buildString {
        if (amountStr.isNotEmpty()) append(amountStr)
        if (unitStr.isNotEmpty()) append(if (amountStr.isEmpty()) unitStr else " $unitStr")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoWhite),
        border = BorderStroke(1.5.dp, CookncoNavy),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(CookncoGreenLight),
                contentAlignment = Alignment.Center,
            ) {
                Text("🍽️", fontSize = 22.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(ingredient.name, fontWeight = FontWeight.Bold, color = CookncoNavy, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CookncoNavy.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, step: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        // White card offset to leave room for the overlapping circle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CookncoWhite),
            border = BorderStroke(1.5.dp, CookncoNavy),
        ) {
            Text(
                text = step,
                modifier = Modifier.padding(start = 32.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = CookncoNavy,
            )
        }
        // Green numbered circle overlapping the card's left edge
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CookncoGreen)
                .border(2.dp, CookncoNavy, CircleShape)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                color = CookncoWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun NotesCard(
    notes: String,
    remoteNotes: String?,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoGreen),
        border = BorderStroke(1.5.dp, CookncoNavy),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
            )

            if (isEditing) {
                TextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    placeholder = { Text("Write notes here!", color = CookncoNavy.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CookncoBackground,
                        unfocusedContainerColor = CookncoBackground,
                        focusedBorderColor = CookncoOrange,
                        unfocusedBorderColor = CookncoNavy.copy(alpha = 0.5f),
                        focusedTextColor = CookncoNavy,
                        unfocusedTextColor = CookncoNavy,
                        cursorColor = CookncoOrange,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = CookncoNavy.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CookncoWhite,
                            contentColor = CookncoNavy,
                        ),
                        border = BorderStroke(1.5.dp, CookncoNavy),
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            } else {
                Text(
                    text = if (remoteNotes != null) notes else "Write notes here!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (remoteNotes != null) CookncoNavy else CookncoNavy.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CookncoBackground)
                        .padding(12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onStartEdit,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CookncoWhite,
                            contentColor = CookncoNavy,
                        ),
                        border = BorderStroke(1.5.dp, CookncoNavy),
                    ) {
                        Text("EDIT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

// ── Preview ──────────────────────────────────────────────────────────────────

private val previewOwner = RecipeOwner(id = 1L, version = 1L, username = "Aya Amayri")
private val previewRecipe = RecipeInfo(
    id = 1L, version = 1L,
    title = "Harcha",
    dishClass = "MAIN_DISH",
    owner = previewOwner,
    description = "Petits pains marocain à la semoule que l'on mange traditionnellement avec du thé à la menthe et du miel.",
    yield = 8,
    preparationTime = 5,
    cookingTime = 35,
    cookingTemperature = 100,
    ingredients = listOf(
        RecipeIngredientInfo(id = 1L, name = "Salt", amount = 1f, unit = "NONE", complement = "1 pincée", type = "SALT", allowAmount = true, allowWeight = false, allowVolume = false),
        RecipeIngredientInfo(id = 2L, name = "Baking powder", amount = 1f, unit = "TEASPOON", complement = null, type = "BAKING_POWDER", allowAmount = true, allowWeight = false, allowVolume = true),
        RecipeIngredientInfo(id = 3L, name = "Semoule moyen", amount = 400f, unit = "GRAM", complement = null, type = "GRAIN", allowAmount = false, allowWeight = true, allowVolume = false),
    ),
    steps = listOf(
        "Faire fondre le beurre végétal",
        "Ajouter tous les ingrédients dans un saladier, mélanger à la main jusqu'à la formation d'une pâte homogène",
        "Laisser reposer la pâte 30 mn",
        "Étaler la pâte et former des petits ronds à l'aide d'un verre",
        "Sur une poêle à feu moyen, cuire chaque côtés pendant environ 5 minutes, jusqu'à ce que les harcha soient bien dorés.",
    ),
    tips = "",
    creationDate = 1742601600000L,
    likesCount = 8,
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RecipeScreenPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            RecipeContent(
                recipe = previewRecipe,
                uiState = RecipeUiState(recipe = previewRecipe, selectedYield = 8, isLoading = false),
                isOwner = true,
                onToggleLike = {},
                onShare = {},
                onYieldMinus = {},
                onYieldPlus = {},
                onStartEditNotes = {},
                onNotesChange = {},
                onSaveNotes = {},
                onCancelNoteEdit = {},
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Recipe - Notes Editing")
@Composable
fun RecipeNotesEditingPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            NotesCard(
                notes = "This is my note about the recipe…",
                remoteNotes = "existing",
                isEditing = true,
                onStartEdit = {},
                onNotesChange = {},
                onSave = {},
                onCancel = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
