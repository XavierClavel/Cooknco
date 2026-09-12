package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.components.UserAvatar
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerConfirmDialog
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.stickerShadow
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun unitLabel(unit: String): String = when (unit) {
    "NONE", "UNIT" -> ""
    "GRAM" -> "g"
    "KILOGRAM" -> "kg"
    "POUND" -> "lb"
    "MILLILITERS" -> "mL"
    "CENTILITER" -> "cL"
    "LITER" -> "L"
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

// Matches the day-of-month + full month name convention HomeViewModel already uses for
// recipe dates, just without the year (the owner-actions sheet's "published 22 March").
private val publishedDateFormat = LocalDate.Format {
    day()
    char(' ')
    monthName(MonthNames.ENGLISH_FULL)
}

private fun publishedLabel(creationDate: Long): String {
    val date = Instant.fromEpochSeconds(creationDate).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "published ${publishedDateFormat.format(date)}"
}

private enum class RecipeTab { INGREDIENTS, STEPS, NOTES }

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun RecipeScreen(
    recipeId: Long,
    currentUserId: Long,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateToCookMode: (Long) -> Unit = {},
    viewModel: RecipeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    // LocalClipboardManager is deprecated in favour of LocalClipboard, but ClipEntry
    // has no common-code constructor yet, so this stays the multiplatform option.
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onNavigateBack() }

    val recipe = uiState.recipe

    Surface(modifier = modifier.fillMaxSize(), color = CookncoGreen) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            uiState.error != null && recipe == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = CookncoNavy, modifier = Modifier.padding(16.dp))
            }

            recipe != null -> RecipeContent(
                recipe = recipe,
                uiState = uiState,
                isOwner = viewModel.isOwner,
                onToggleLike = viewModel::toggleLike,
                onShare = { clipboardManager.setText(AnnotatedString("cooknco.eu/recipe?id=${recipe.id}")) },
                onEdit = { onNavigateToEdit(recipe.id) },
                onDelete = viewModel::confirmDelete,
                onYieldMinus = { viewModel.setYield(uiState.selectedYield - 1) },
                onYieldPlus = { viewModel.setYield(uiState.selectedYield + 1) },
                onStartEditNotes = viewModel::startEditNotes,
                onNotesChange = viewModel::updateNotes,
                onSaveNotes = viewModel::saveNotes,
                onCancelNoteEdit = viewModel::cancelNoteEdit,
                onNavigateToUser = onNavigateToUser,
                onNavigateBack = onNavigateBack,
                onStartCooking = { onNavigateToCookMode(recipe.id) },
            )
        }
    }

    if (uiState.showDeleteConfirm && recipe != null) {
        StickerConfirmDialog(
            icon = Icons.Outlined.Delete,
            title = "Delete this recipe?",
            message = "${recipe.title} will be removed for everyone. This cannot be undone.",
            confirmText = "Delete recipe",
            dismissText = "Keep it",
            onConfirm = { viewModel.deleteRecipe() },
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onYieldMinus: () -> Unit,
    onYieldPlus: () -> Unit,
    onStartEditNotes: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onCancelNoteEdit: () -> Unit,
    onStartCooking: () -> Unit,
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val recipeYield = recipe.yield ?: 1
    var selectedTab by rememberSaveable { mutableStateOf(RecipeTab.INGREDIENTS) }
    var showMenu by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── Banner image, overlapped by the info card, with the back / like / more overlay ──
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                RecipeImage(
                    recipeId = recipe.id,
                    version = recipe.version,
                    contentDescription = recipe.title,
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    thumbnail = false,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StickerIconButton(onClick = onToggleLike, shadowOffset = 3.dp) {
                            Icon(
                                imageVector = if (uiState.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (uiState.isLiked) CookncoOrange else CookncoNavy,
                            )
                        }
                        StickerIconButton(
                            onClick = { showMenu = true },
                            shadowOffset = 3.dp,
                            fillColor = if (showMenu) CookncoNavy else CookncoBackground,
                            contentColor = if (showMenu) CookncoWhite else CookncoNavy,
                        ) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More")
                        }
                    }
                }

                // Info card: dish class, title, description, author, meta — overlaps the
                // banner's bottom edge by 30dp, per the mockup's `margin:-30px 18px 0`.
                StickerCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, top = 250.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(CookncoGreen)
                                    .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = dishClassLabel(recipe.dishClass).uppercase(),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CookncoWhite,
                                )
                            }
                        }
                        Text(
                            text = recipe.title,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                            lineHeight = 35.sp,
                        )
                        if (recipe.description.isNotBlank()) {
                            Text(
                                text = recipe.description,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = CookncoNavy.copy(alpha = 0.72f),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AuthorChip(owner = recipe.owner, onClick = { onNavigateToUser(recipe.owner.id) })
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(CookncoOrange)
                                    .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("♥ ${recipe.likesCount}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoWhite)
                            }
                        }
                        val hasMeta = recipe.preparationTime != null || recipe.cookingTime != null || recipe.cookingTemperature != null
                        if (hasMeta) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(CookncoNavy.copy(alpha = 0.14f)),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    recipe.preparationTime?.let { MetaStat(label = "PREP", value = "$it min", modifier = Modifier.weight(1f)) }
                                    recipe.cookingTime?.let { MetaStat(label = "COOK", value = "$it min", modifier = Modifier.weight(1f)) }
                                    recipe.cookingTemperature?.let { MetaStat(label = "OVEN", value = "$it °C", modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showMenu) {
            item {
                RecipeActionSheet(
                    recipe = recipe,
                    isOwner = isOwner,
                    onShare = onShare,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onDismissRequest = { showMenu = false },
                )
            }
        }

        // ── Tabs ───────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp)
                    .stickerShadow(RoundedCornerShape(percent = 50), offsetX = 4.dp, offsetY = 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(CookncoBackground)
                    .border(3.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RecipeTab.entries.forEach { tab ->
                    RecipeTabItem(
                        label = tab.label,
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        when (selectedTab) {
            RecipeTab.INGREDIENTS -> ingredientsTab(
                recipe = recipe,
                uiState = uiState,
                recipeYield = recipeYield,
                onYieldMinus = onYieldMinus,
                onYieldPlus = onYieldPlus,
            )
            RecipeTab.STEPS -> stepsTab(recipe = recipe)
            RecipeTab.NOTES -> item {
                NotesCard(
                    tips = recipe.tips,
                    authorName = recipe.owner.username,
                    notes = uiState.notes,
                    remoteNotes = uiState.remoteNotes,
                    isEditing = uiState.isEditingNotes,
                    onStartEdit = onStartEditNotes,
                    onNotesChange = onNotesChange,
                    onSave = onSaveNotes,
                    onCancel = onCancelNoteEdit,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }

        // ── Bottom actions ───────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StickerCard(
                    modifier = Modifier.size(width = 58.dp, height = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    shadowOffset = 4.dp,
                    onClick = onShare,
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share", modifier = Modifier.align(Alignment.Center), tint = CookncoNavy)
                }
                StickerCard(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    fillColor = CookncoOrange,
                    shadowOffset = 4.dp,
                    onClick = onStartCooking,
                ) {
                    Text(
                        "Start cooking",
                        color = CookncoWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        if (uiState.error != null) {
            item {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val RecipeTab.label: String
    get() = when (this) {
        RecipeTab.INGREDIENTS -> "Ingredients"
        RecipeTab.STEPS -> "Steps"
        RecipeTab.NOTES -> "Notes"
    }

private fun dishClassLabel(dishClass: String): String = dishClass
    .lowercase()
    .split('_')
    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private fun LazyListScope.ingredientsTab(
    recipe: RecipeInfo,
    uiState: RecipeUiState,
    recipeYield: Int,
    onYieldMinus: () -> Unit,
    onYieldPlus: () -> Unit,
) {
    if (recipe.yield != null) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Scaled for", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CookncoNavy, modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(CookncoBackground)
                        .border(3.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onYieldMinus),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Remove, contentDescription = "Decrease", tint = CookncoNavy) }
                    Text(
                        "${uiState.selectedYield} pcs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CookncoNavy,
                        modifier = Modifier.width(54.dp),
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CookncoOrange)
                            .clickable(onClick = onYieldPlus),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Add, contentDescription = "Increase", tint = CookncoWhite) }
                }
            }
        }
    }

    if (recipe.ingredients.isNotEmpty()) {
        item {
            StickerCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column {
                    recipe.ingredients.forEachIndexed { index, ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            selectedYield = uiState.selectedYield,
                            recipeYield = recipeYield,
                            showDivider = index < recipe.ingredients.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.stepsTab(recipe: RecipeInfo) {
    if (recipe.steps.isEmpty()) {
        item {
            Text(
                "No steps yet",
                color = CookncoWhite.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }
        return
    }
    item {
        val totalMinutes = listOfNotNull(recipe.preparationTime, recipe.cookingTime).sum()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stepsLabel = "${recipe.steps.size} step${if (recipe.steps.size == 1) "" else "s"}" +
                if (totalMinutes > 0) " · about $totalMinutes min in total" else ""
            Text(stepsLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, modifier = Modifier.weight(1f))
            recipe.cookingTemperature?.let { temperature ->
                StickerPill(
                    height = 36.dp,
                    fillColor = CookncoGold,
                    borderWidth = 2.dp,
                    shadowOffset = 0.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("$temperature °C oven", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                }
            }
        }
    }
    itemsIndexed(recipe.steps) { index, step ->
        StepRow(index = index, step = step, modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp))
    }
}

// ── Reusable sub-composables ──────────────────────────────────────────────────

@Composable
private fun RecipeTabItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) CookncoOrange else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) CookncoWhite else CookncoNavy,
        )
    }
}

@Composable
private fun MetaStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

@Composable
private fun AuthorChip(owner: RecipeOwner, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(CookncoWhite)
            .border(2.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(start = 3.dp, top = 3.dp, end = 10.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UserAvatar(
            userId = owner.id,
            version = owner.version,
            contentDescription = null,
            modifier = Modifier.size(24.dp).clip(CircleShape),
        )
        Text(text = owner.username, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

@Composable
private fun IngredientRow(
    ingredient: RecipeIngredientInfo,
    selectedYield: Int,
    recipeYield: Int,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val amountStr = scaleAmount(ingredient.amount, selectedYield, recipeYield)
    val unitStr = unitLabel(ingredient.unit)
    val amountLabel = buildString {
        if (amountStr.isNotEmpty()) append(amountStr)
        if (unitStr.isNotEmpty()) append(if (amountStr.isEmpty()) unitStr else " $unitStr")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .border(2.5.dp, CookncoNavy, RoundedCornerShape(7.dp)),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CookncoGreenLight)
                    .border(2.dp, CookncoNavy, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (ingredient.type != null) {
                    AsyncImage(
                        model = "${ApiClient.IMAGE_URL}/ingredients/${ingredient.type}.webp",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Text("🍽", fontSize = 15.sp)
                }
            }
            Text(ingredient.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = CookncoNavy, modifier = Modifier.weight(1f))
            if (amountLabel.isNotBlank()) {
                Text(amountLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CookncoNavy)
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(2.dp)
                    .background(CookncoNavy.copy(alpha = 0.1f)),
            )
        }
    }
}

@Composable
private fun StepRow(index: Int, step: String, modifier: Modifier = Modifier) {
    StickerCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        shadowOffset = 5.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(
                text = "STEP ${index + 1}",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoGreenDark,
                letterSpacing = 1.sp,
            )
            Text(
                text = step,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                color = CookncoNavy,
            )
        }
    }
}

@Composable
private fun NotesCard(
    notes: String,
    tips: String,
    authorName: String,
    remoteNotes: String?,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The author's own tip for the recipe, if they left one — read-only, everyone sees it.
        if (tips.isNotBlank()) {
            Text(
                text = "FROM ${authorName.uppercase()}, THE AUTHOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), fillColor = CookncoGold) {
                Text(
                    text = tips,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = CookncoNavy,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (tips.isNotBlank()) 22.dp else 0.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MY NOTES · ONLY YOU SEE THESE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            if (!isEditing) {
                StickerPill(shadowOffset = 3.dp, onClick = onStartEdit) {
                    Text("Edit", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                }
            }
        }

        // The signed-in user's private note — editing, saved and empty states.
        if (isEditing) {
            TextField(
                value = notes,
                onValueChange = onNotesChange,
                placeholder = { Text("Write notes here!", color = CookncoNavy.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CookncoWhite,
                    unfocusedContainerColor = CookncoWhite,
                    focusedBorderColor = CookncoOrange,
                    unfocusedBorderColor = CookncoNavy.copy(alpha = 0.5f),
                    focusedTextColor = CookncoNavy,
                    unfocusedTextColor = CookncoNavy,
                    cursorColor = CookncoOrange,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel", color = CookncoNavy.copy(alpha = 0.7f)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CookncoOrange, contentColor = CookncoWhite),
                ) { Text("SAVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            }
        } else if (remoteNotes != null) {
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Text(
                    text = notes,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = CookncoNavy,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedNavyBorder(radius = 20.dp)
                    .clickable(onClick = onStartEdit)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Write a note here — it stays on this recipe, on every device.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CookncoNavy,
                )
            }
        }
    }
}

/** A dashed navy rounded-rect outline — the notes tab's empty-state prompt (no filled equivalent
 * exists in [com.xavierclavel.cooknco.ui.theme.Sticker], so it is drawn directly here). */
private fun Modifier.dashedNavyBorder(radius: Dp): Modifier = drawWithContent {
    drawContent()
    val strokeWidthPx = 3.dp.toPx()
    drawRoundRect(
        color = CookncoNavy,
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        ),
        cornerRadius = CornerRadius(radius.toPx()),
        topLeft = androidx.compose.ui.geometry.Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
    )
}

/**
 * The "···" overflow menu, styled as a bottom action sheet (see `Cooknco Mobile.dc.html`,
 * turn 5 / option `5a`, "Recipe — owner actions") rather than a Material [androidx.compose.material3.DropdownMenu] —
 * the mockup shows a full-width sheet over a dark scrim, not a small anchored dropdown.
 *
 * "Add to a cookbook" appears in that mockup too, but this screen has no callback for it
 * (no such navigation exists yet), so it is left out rather than wired to nothing.
 */
@Composable
private fun RecipeActionSheet(
    recipe: RecipeInfo,
    isOwner: Boolean,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                .clickable(onClick = onDismissRequest),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 26.dp),
            ) {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 12.dp)) {
                            Text(recipe.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                            Text(
                                text = if (isOwner) "Your recipe · ${publishedLabel(recipe.creationDate)}" else publishedLabel(recipe.creationDate).replaceFirstChar(Char::uppercase),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CookncoGreenDark,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        ActionSheetDivider()
                        ActionSheetRow(label = "Share link", onClick = { onDismissRequest(); onShare() })
                        if (isOwner) {
                            ActionSheetDivider()
                            ActionSheetRow(label = "Edit recipe", onClick = { onDismissRequest(); onEdit() })
                            ActionSheetDivider()
                            ActionSheetRow(
                                label = "Delete recipe",
                                textColor = MaterialTheme.colorScheme.error,
                                onClick = { onDismissRequest(); onDelete() },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    shadowOffset = 4.dp,
                    onClick = onDismissRequest,
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSheetRow(label: String, onClick: () -> Unit, textColor: Color = CookncoNavy) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun ActionSheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(CookncoNavy.copy(alpha = 0.12f)),
    )
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
        RecipeIngredientInfo(id = 3L, name = "Semoule moyen", amount = 400f, unit = "GRAM", complement = null, type = "GRAIN", allowedTypes = listOf("NONE", "WEIGHT")),
        RecipeIngredientInfo(id = null, name = "Beurre végétal", amount = 100f, unit = "GRAM", complement = null, allowedTypes = listOf("NONE", "AMOUNT", "WEIGHT", "VOLUME")),
    ),
    steps = listOf(
        "Faire fondre le beurre végétal",
        "Ajouter tous les ingrédients dans un saladier, mélanger à la main jusqu'à la formation d'une pâte homogène",
        "Laisser reposer la pâte 30 mn",
    ),
    tips = "",
    creationDate = 1742601600000L,
    likesCount = 8,
)

@Preview(showBackground = true)
@Composable
fun RecipeScreenPreview() {
    CookncoTheme {
        Surface(color = CookncoGreen) {
            RecipeContent(
                recipe = previewRecipe,
                uiState = RecipeUiState(recipe = previewRecipe, selectedYield = 8, isLoading = false),
                isOwner = true,
                onToggleLike = {},
                onShare = {},
                onEdit = {},
                onDelete = {},
                onYieldMinus = {},
                onYieldPlus = {},
                onStartEditNotes = {},
                onNotesChange = {},
                onSaveNotes = {},
                onCancelNoteEdit = {},
                onStartCooking = {},
            )
        }
    }
}
