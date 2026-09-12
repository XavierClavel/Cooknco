package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.UnitInfo
import com.xavierclavel.cooknco.platform.PickedImage
import com.xavierclavel.cooknco.platform.rememberImagePicker
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState

private val dishClasses = listOf(
    "ENTREE" to "Entrée",
    "MAIN_DISH" to "Main dish",
    "DESERT" to "Dessert",
    "SALTY_SNACK" to "Salty snack",
    "SUGARY_SNACK" to "Sugary snack",
    "DRINK" to "Drink",
    "OTHER" to "Other",
)

private val unitLabels = mapOf(
    "NONE" to "—",
    "UNIT" to "Unit",
    "GRAM" to "g",
    "KILOGRAM" to "kg",
    "POUND" to "lb",
    "MILLILITERS" to "mL",
    "CENTILITER" to "cL",
    "LITER" to "L",
    "TEASPOON" to "tsp",
    "TABLESPOON" to "tbsp",
    "CUP" to "cup",
)

private fun unitFieldLabel(unit: String): String = unitLabels[unit] ?: unit

// Custom rows carry no capability data, so they accept the whole catalog.
private fun unitsForIngredient(allowedTypes: List<String>, units: List<UnitInfo>): List<UnitInfo> =
    if (allowedTypes.isEmpty()) units else units.filter { it.type in allowedTypes }

private enum class EditorStep(val label: String) {
    BASICS("Basics"),
    INGREDIENTS("Ingredients"),
    STEPS("Steps"),
    PHOTO("Photo"),
}

// ── Shared styling helpers ────────────────────────────────────────────────────

private val fieldShape = RoundedCornerShape(12.dp)

// The compact, label-less amount/unit "pill" fields on an added ingredient row.
private val pillFieldShape = RoundedCornerShape(10.dp)

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CookncoWhite,
    unfocusedContainerColor = CookncoWhite,
    focusedBorderColor = CookncoOrange,
    unfocusedBorderColor = CookncoNavy,
    focusedTextColor = CookncoNavy,
    unfocusedTextColor = CookncoNavy,
    focusedLabelColor = CookncoGreenDark,
    unfocusedLabelColor = CookncoGreenDark,
    cursorColor = CookncoOrange,
)

@Composable
private fun StepHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, lineHeight = 31.sp)
        Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CookncoNavy, modifier = Modifier.padding(top = 4.dp))
    }
}

// A small uppercase caption over a group of fields — "DISH CLASS", "TIMES & YIELD" —
// matching the mockup's section labels (navy, not the muted green used for field captions).
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CookncoNavy,
        letterSpacing = 0.7.sp,
        modifier = modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

// A dashed sticker-navy outline, used for the "+ Add step" affordance — Modifier.border()
// has no dashed variant, so this paints the stroke itself with a dash path effect.
private fun Modifier.dashedBorder(
    color: Color,
    shape: Shape,
    strokeWidth: Dp = 3.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 6.dp,
): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f),
    )
    drawPath(path = path, color = color, style = stroke)
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditScreen(
    recipeId: Long?,
    currentUserId: Long,
    onNavigateBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: RecipeEditViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val steps = EditorStep.entries

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            val id = uiState.recipeId ?: return@LaunchedEffect
            onSaved(id)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        // ── Top bar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerIconButton(
                onClick = { if (currentStep > 0) currentStep-- else onNavigateBack() },
                shadowOffset = 3.dp,
            ) {
                // Step 0 has nowhere to go "back" to inside the wizard, so the icon reads
                // as a close (cancel) rather than a back arrow — steps 1-3 step back instead.
                if (currentStep == 0) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancel")
                } else {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                text = if (recipeId == null) "New recipe" else "Edit recipe",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                modifier = Modifier.weight(1f),
            )
            StickerPill(onClick = { viewModel.save() }, height = 44.dp) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CookncoNavy)
                } else {
                    Text("Save draft", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                }
            }
        }

        // ── Progress segments ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            steps.forEachIndexed { index, step ->
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (index <= currentStep) CookncoGold else CookncoBackground)
                            .border(1.5.dp, CookncoNavy, RoundedCornerShape(3.dp)),
                    )
                    Text(
                        text = step.label.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (index <= currentStep) CookncoNavy else CookncoGreenDark,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }
            return@Column
        }

        Box(modifier = Modifier.weight(1f)) {
            when (steps[currentStep]) {
                EditorStep.BASICS -> BasicsStep(uiState = uiState, viewModel = viewModel)
                EditorStep.INGREDIENTS -> IngredientsStep(uiState = uiState, viewModel = viewModel)
                EditorStep.STEPS -> StepsStep(uiState = uiState, viewModel = viewModel)
                EditorStep.PHOTO -> PhotoStep(
                    uiState = uiState,
                    viewModel = viewModel,
                    pickedImage = uiState.pendingImage,
                    onImagePicked = viewModel::setPendingImage,
                )
            }
        }

        // ── Bottom navigation ─────────────────────────────────────────────────
        // Step 0 has no "Cancel" box beside the primary button — the close icon in the
        // top bar already covers that, so only the full-width primary action shows.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentStep > 0) {
                StickerCard(
                    modifier = Modifier.size(width = 100.dp, height = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    shadowOffset = 4.dp,
                    onClick = { currentStep-- },
                ) {
                    Text(
                        text = "Back",
                        color = CookncoNavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            StickerCard(
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                fillColor = CookncoOrange,
                shadowOffset = 4.dp,
                onClick = {
                    if (currentStep < steps.lastIndex) currentStep++ else viewModel.save()
                },
            ) {
                if (uiState.isSaving && currentStep == steps.lastIndex) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).align(Alignment.Center),
                        strokeWidth = 2.dp,
                        color = CookncoWhite,
                    )
                } else {
                    Text(
                        text = if (currentStep < steps.lastIndex) "Next: ${steps[currentStep + 1].label.lowercase()}" else "PUBLISH",
                        color = CookncoWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = if (currentStep == steps.lastIndex) 0.6.sp else 0.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
    }
}

// ── Step 1: Basics ─────────────────────────────────────────────────────────────

@Composable
private fun BasicsStep(uiState: RecipeEditUiState, viewModel: RecipeEditViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StepHeading(title = "The basics", subtitle = "Only the title is required — the rest can wait.")
        }
        item {
            StickerCard(modifier = Modifier.fillMaxWidth(), shadowOffset = 6.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text("Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.error?.contains("Title") == true,
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = viewModel::updateDescription,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                }
            }
        }
        item {
            Column {
                SectionLabel("Dish class")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dishClasses.forEach { (value, label) ->
                        DishClassChip(label = label, selected = uiState.dishClass == value, onClick = { viewModel.updateDishClass(value) })
                    }
                }
            }
        }
        item {
            Column {
                SectionLabel("Times & yield")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatInputField(
                            label = "Yield",
                            value = uiState.yield,
                            unit = "pcs",
                            onValueChange = viewModel::updateYield,
                            modifier = Modifier.weight(1f),
                        )
                        StatInputField(
                            label = "Prep",
                            value = uiState.prepTime,
                            unit = "min",
                            onValueChange = viewModel::updatePrepTime,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatInputField(
                            label = "Cook",
                            value = uiState.cookTime,
                            unit = "min",
                            onValueChange = viewModel::updateCookTime,
                            modifier = Modifier.weight(1f),
                        )
                        StatInputField(
                            label = "Temp",
                            value = uiState.cookTemp,
                            unit = "°C",
                            onValueChange = viewModel::updateCookTemp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DishClassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) CookncoNavy else CookncoBackground)
            .border(2.dp, CookncoNavy, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) CookncoWhite else CookncoNavy,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// One "stat" tile in the Times & Yield grid: a small caption over a big editable number
// with a unit suffix, styled like the mockup's read-outs rather than a floating-label field.
@Composable
private fun StatInputField(
    label: String,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StickerCard(modifier = modifier, shape = RoundedCornerShape(16.dp), shadowOffset = 4.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label.uppercase(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoGreenDark,
                letterSpacing = 0.5.sp,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CookncoNavy),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(CookncoNavy),
                    modifier = Modifier.widthIn(min = 20.dp),
                )
                Text(unit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CookncoGreenDark)
            }
        }
    }
}

// ── Step 2: Ingredients ─────────────────────────────────────────────────────────

@Composable
private fun IngredientsStep(uiState: RecipeEditUiState, viewModel: RecipeEditViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StepHeading(title = "Ingredients", subtitle = "Pick from the catalogue so amounts scale and lists merge.")
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Counts only rows that have actually resolved to a catalogue or custom
                // ingredient — the still-being-searched row at the bottom isn't "added" yet.
                val addedCount = uiState.ingredients.count { it.ingredientId != null || it.customName != null }
                Text(
                    text = "ADDED · $addedCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CookncoGreen)
                        .border(1.5.dp, CookncoNavy, CircleShape)
                        .clickable(onClick = viewModel::addIngredient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add ingredient", tint = CookncoNavy, modifier = Modifier.size(20.dp))
                }
            }
        }
        uiState.ingredients.forEachIndexed { index, ingredient ->
            item(key = "ingredient_$index") {
                IngredientEditCard(
                    ingredient = ingredient,
                    units = uiState.units,
                    onQueryChange = { viewModel.updateIngredientQuery(index, it) },
                    onSelect = { viewModel.selectIngredient(index, it) },
                    onSelectCustom = { viewModel.selectCustomIngredient(index) },
                    onDismiss = { viewModel.dismissDropdown(index) },
                    onUnitChange = { viewModel.updateIngredientUnit(index, it) },
                    onAmountChange = { viewModel.updateIngredientAmount(index, it) },
                    onComplementChange = { viewModel.updateIngredientComplement(index, it) },
                    onRemove = { viewModel.removeIngredient(index) },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientEditCard(
    ingredient: EditIngredient,
    units: List<UnitInfo>,
    onQueryChange: (String) -> Unit,
    onSelect: (IngredientSummary) -> Unit,
    onSelectCustom: () -> Unit,
    onDismiss: () -> Unit,
    onUnitChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onComplementChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val availableUnits = unitsForIngredient(ingredient.allowedTypes, units)
    val showAmount = ingredient.unit != "NONE"
    // A row is "resolved" once it points at a catalogue ingredient or has been confirmed as
    // a custom one. Until then it is just the search box the mockup shows — unit, amount
    // and note have nothing to scale or annotate yet, so that row stays hidden.
    val isResolved = ingredient.ingredientId != null || ingredient.customName != null

    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadowOffset = 5.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.padding(end = 8.dp).size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        !isResolved -> Box(modifier = Modifier.size(17.dp).border(2.5.dp, CookncoNavy, CircleShape))
                        ingredient.type.isNotEmpty() -> AsyncImage(
                            model = "${ApiClient.IMAGE_URL}/ingredients/${ingredient.type}.webp",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CookncoGreenLight)
                                .border(2.dp, CookncoNavy, RoundedCornerShape(8.dp)),
                        )
                        else -> Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CookncoGreenLight)
                                .border(2.dp, CookncoNavy, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = CookncoNavy.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = ingredient.showDropdown,
                    onExpandedChange = { if (!it) onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = ingredient.query,
                        onValueChange = onQueryChange,
                        label = { Text("Ingredient") },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ingredient.showDropdown) },
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                    DropdownMenu(expanded = ingredient.showDropdown, onDismissRequest = onDismiss) {
                        ingredient.searchResults.forEach { result ->
                            val name = result.name["EN"] ?: result.name.values.firstOrNull() ?: ""
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(name, modifier = Modifier.weight(1f))
                                        Text("catalogue", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CookncoGreenDark)
                                    }
                                },
                                onClick = { onSelect(result) },
                            )
                        }
                        if (ingredient.searchResults.isEmpty() && ingredient.query.isNotBlank()) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(modifier = Modifier.size(22.dp).dashedBorder(CookncoNavy, RoundedCornerShape(6.dp), strokeWidth = 2.dp))
                                        Text("Add \"${ingredient.query.trim()}\" as custom")
                                    }
                                },
                                onClick = onSelectCustom,
                            )
                        }
                    }
                }
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp).clickable(onClick = onRemove),
                )
            }

            if (isResolved) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    var unitExpanded by rememberSaveable { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it },
                        modifier = Modifier.width(96.dp),
                    ) {
                        OutlinedTextField(
                            value = unitFieldLabel(ingredient.unit),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            colors = editFieldColors(),
                            shape = pillFieldShape,
                        )
                        DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                            availableUnits.forEach { unit ->
                                DropdownMenuItem(text = { Text(unitFieldLabel(unit.name)) }, onClick = { onUnitChange(unit.name); unitExpanded = false })
                            }
                        }
                    }

                    if (showAmount) {
                        OutlinedTextField(
                            value = ingredient.amount?.toString() ?: "",
                            onValueChange = onAmountChange,
                            modifier = Modifier.width(76.dp),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = editFieldColors(),
                            shape = pillFieldShape,
                        )
                    }

                    OutlinedTextField(
                        value = ingredient.complement,
                        onValueChange = onComplementChange,
                        label = { Text("Note") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                }
            }
        }
    }
}

// ── Step 3: Steps ───────────────────────────────────────────────────────────────

@Composable
private fun StepsStep(uiState: RecipeEditUiState, viewModel: RecipeEditViewModel, modifier: Modifier = Modifier) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyColumnState(lazyListState) { from, to ->
        val steps = uiState.steps
        val fromIdx = steps.indexOfFirst { it.id == from.key }
        val toIdx = steps.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1) viewModel.reorderStep(fromIdx, toIdx)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StepHeading(title = "Steps", subtitle = "Drag to reorder. Each step becomes one card in cook mode.")
        }

        uiState.steps.forEachIndexed { index, step ->
            item(key = step.id) {
                ReorderableItem(reorderState, key = step.id) { isDragging ->
                    val elevation by animateDpAsState(targetValue = if (isDragging) 6.dp else 0.dp, label = "step_elevation")
                    val haptic = LocalHapticFeedback.current
                    StepEditCard(
                        index = index,
                        step = step,
                        elevation = elevation,
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        ),
                        onStepChange = { viewModel.updateStep(step.id, it) },
                        onRemove = { viewModel.removeStep(step.id) },
                    )
                }
            }
        }
        item {
            // The dashed "+ Add step" affordance replaces the old top-right circle button —
            // the mockup only ever shows this one way to add a step.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .dashedBorder(CookncoNavy, RoundedCornerShape(18.dp))
                    .clickable(onClick = viewModel::addStep),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                Text("Add step", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            }
        }
        item {
            SectionLabel("Tips (optional)")
        }
        item {
            OutlinedTextField(
                value = uiState.tips,
                onValueChange = viewModel::updateTips,
                label = { Text("Tips") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = editFieldColors(),
                shape = fieldShape,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// Turn 7 / option 7b: the step number lives in a gold column inside the card's own
// outline, rather than a circle badge overlapping its edge (the old treatment — see
// StepEditCard's previous version — hung the badge off the left edge, where it fought
// the drag handle for the same corner).
@Composable
private fun StepEditCard(
    index: Int,
    step: StepItem,
    elevation: Dp = 0.dp,
    dragHandleModifier: Modifier = Modifier,
    onStepChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    StickerCard(
        modifier = Modifier.fillMaxWidth().shadow(elevation, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        shadowOffset = 5.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier.fillMaxHeight().width(46.dp).background(CookncoGold),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = (index + 1).toString(), color = CookncoNavy, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
            Box(modifier = Modifier.fillMaxHeight().width(3.dp).background(CookncoNavy))
            OutlinedTextField(
                value = step.text,
                onValueChange = onStepChange,
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically).padding(8.dp),
                minLines = 2,
                colors = editFieldColors(),
                shape = fieldShape,
            )
            Column(
                modifier = Modifier.padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).then(dragHandleModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragIndicator,
                        contentDescription = "Drag to reorder",
                        tint = CookncoNavy.copy(alpha = 0.4f),
                    )
                }
                Box(
                    modifier = Modifier.size(44.dp).clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove step",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// ── Step 4: Photo ───────────────────────────────────────────────────────────────

@Composable
private fun PhotoStep(
    uiState: RecipeEditUiState,
    viewModel: RecipeEditViewModel,
    pickedImage: PickedImage?,
    onImagePicked: (PickedImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imagePicker = rememberImagePicker(onPicked = onImagePicked)
    val pickedBitmap = pickedImage?.let { picked ->
        remember(picked) { runCatching { picked.bytes.decodeToImageBitmap() }.getOrNull() }
    }
    // A version of 0 means the recipe is still on the backend's default placeholder —
    // RecipeInfo.version doubles as the image version (see ImageController on the backend).
    val hasExistingPhoto = uiState.recipeId != null && (uiState.recipeVersion ?: 0) > 0

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StepHeading(title = "Photo & publish", subtitle = "One photo of the finished dish. You can change it later.")
        }
        item {
            StickerCard(modifier = Modifier.fillMaxWidth(), shadowOffset = 6.dp) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(CookncoGreenLight)
                            .clickable { imagePicker.launch() },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            pickedBitmap != null -> Image(
                                bitmap = pickedBitmap,
                                contentDescription = "Recipe photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            hasExistingPhoto -> RecipeImage(
                                recipeId = uiState.recipeId!!,
                                version = uiState.recipeVersion!!,
                                contentDescription = "Recipe photo",
                                thumbnail = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = CookncoNavy.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                                Text("Tap to add a photo", color = CookncoNavy.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(CookncoNavy))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CookncoBackground)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PhotoActionButton(text = "Take a photo", onClick = { imagePicker.launch() }, modifier = Modifier.weight(1f))
                        PhotoActionButton(text = "Choose another", onClick = { imagePicker.launch() }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (pickedImage != null) {
            item {
                Text(
                    text = "Uploaded when you save.",
                    color = CookncoNavy.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                )
            }
        }
        item {
            Column {
                SectionLabel("Ready to publish")
                PublishChecklist(uiState = uiState)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PhotoActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CookncoWhite)
            .border(2.dp, CookncoNavy, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

// A read-only recap of what's been filled in so far, matching the mockup's "ready to
// publish" summary — it reads the same state the other three steps already collected,
// nothing new is computed or validated here.
@Composable
private fun PublishChecklist(uiState: RecipeEditUiState, modifier: Modifier = Modifier) {
    val dishClassLabel = dishClasses.firstOrNull { it.first == uiState.dishClass }?.second ?: uiState.dishClass
    val ingredientCount = uiState.ingredients.count { it.ingredientId != null || it.customName != null }
    val catalogCount = uiState.ingredients.count { it.ingredientId != null }
    val ingredientsLine = when {
        ingredientCount == 0 -> "No ingredients yet"
        catalogCount == ingredientCount -> "$ingredientCount ingredient${if (ingredientCount == 1) "" else "s"}, all from the catalogue"
        else -> "$ingredientCount ingredient${if (ingredientCount == 1) "" else "s"} added"
    }
    val stepCount = uiState.steps.count { it.text.isNotBlank() }
    val stepsLine = if (uiState.cookTime.isNotBlank()) {
        "$stepCount step${if (stepCount == 1) "" else "s"} · ${uiState.cookTime} min cook"
    } else {
        "$stepCount step${if (stepCount == 1) "" else "s"}"
    }

    StickerCard(modifier = modifier.fillMaxWidth(), shadowOffset = 6.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ChecklistRow(
                title = if (uiState.title.isNotBlank()) "${uiState.title} · ${dishClassLabel.lowercase()}" else "Untitled recipe",
                trailing = "Basics ✓",
                showDivider = true,
            )
            ChecklistRow(title = ingredientsLine, trailing = "✓", showDivider = true)
            ChecklistRow(title = stepsLine, trailing = "✓", showDivider = false)
        }
    }
}

@Composable
private fun ChecklistRow(title: String, trailing: String, showDivider: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = CookncoNavy, modifier = Modifier.weight(1f))
            Text(trailing, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoGreenDark)
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(CookncoNavy.copy(alpha = 0.1f)))
        }
    }
}
