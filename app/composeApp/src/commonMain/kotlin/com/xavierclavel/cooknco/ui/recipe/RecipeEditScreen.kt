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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
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

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CookncoWhite,
    unfocusedContainerColor = CookncoWhite,
    focusedBorderColor = CookncoOrange,
    unfocusedBorderColor = CookncoNavy,
    focusedTextColor = CookncoNavy,
    unfocusedTextColor = CookncoNavy,
    focusedLabelColor = CookncoOrange,
    unfocusedLabelColor = CookncoNavy.copy(alpha = 0.6f),
    cursorColor = CookncoOrange,
)

@Composable
private fun StepHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, lineHeight = 31.sp)
        Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CookncoNavy, modifier = Modifier.padding(top = 4.dp))
    }
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
    // Local-only preview: there is no recipe-image upload endpoint wired up on the
    // mobile client yet (only the user avatar has one, via UserRepository), so the
    // Photo step lets you pick an image but does not persist it — see the PR body.
    var pickedImage by remember { mutableStateOf<PickedImage?>(null) }
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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
                        color = if (index <= currentStep) CookncoNavy else CookncoNavy.copy(alpha = 0.6f),
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
                    pickedImage = pickedImage,
                    onImagePicked = { pickedImage = it },
                )
            }
        }

        // ── Bottom navigation ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StickerCard(
                modifier = Modifier.size(width = 100.dp, height = 56.dp),
                shape = RoundedCornerShape(16.dp),
                shadowOffset = 4.dp,
                onClick = { if (currentStep > 0) currentStep-- else onNavigateBack() },
            ) {
                Text(
                    text = if (currentStep == 0) "Cancel" else "Back",
                    color = CookncoNavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
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
                        text = if (currentStep < steps.lastIndex) "Next: ${steps[currentStep + 1].label}" else "Save recipe",
                        color = CookncoWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
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
            StepHeading(title = "Basics", subtitle = "What is this recipe, at a glance?")
        }
        item {
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
        }
        item {
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
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dish class", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoNavy.copy(alpha = 0.75f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dishClasses.forEach { (value, label) ->
                        DishClassChip(label = label, selected = uiState.dishClass == value, onClick = { viewModel.updateDishClass(value) })
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.yield,
                    onValueChange = viewModel::updateYield,
                    label = { Text("Yield") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
                OutlinedTextField(
                    value = uiState.prepTime,
                    onValueChange = viewModel::updatePrepTime,
                    label = { Text("Prep (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.cookTime,
                    onValueChange = viewModel::updateCookTime,
                    label = { Text("Cook (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
                OutlinedTextField(
                    value = uiState.cookTemp,
                    onValueChange = viewModel::updateCookTemp,
                    label = { Text("Temp (°C)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DishClassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) CookncoOrange else CookncoWhite)
            .border(1.5.dp, if (selected) CookncoNavy else CookncoNavy.copy(alpha = 0.35f), RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) CookncoWhite else CookncoNavy, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
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
                Text(
                    text = "ADDED · ${uiState.ingredients.size}",
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

    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadowOffset = 5.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CookncoGreenLight)
                        .border(2.dp, CookncoNavy, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ingredient.type.isNotEmpty()) {
                        AsyncImage(
                            model = "${ApiClient.IMAGE_URL}/ingredients/${ingredient.type}.webp",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = CookncoNavy.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
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
                            DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(result) })
                        }
                        if (ingredient.searchResults.isEmpty() && ingredient.query.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Add \"${ingredient.query.trim()}\" as custom") },
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var unitExpanded by rememberSaveable { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.width(110.dp),
                ) {
                    OutlinedTextField(
                        value = unitFieldLabel(ingredient.unit),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        colors = editFieldColors(),
                        shape = fieldShape,
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
                        label = { Text("Amount") },
                        modifier = Modifier.width(90.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = editFieldColors(),
                        shape = fieldShape,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepHeading(title = "Steps", subtitle = "Drag to reorder.", modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CookncoGreen)
                        .border(1.5.dp, CookncoNavy, CircleShape)
                        .clickable(onClick = viewModel::addStep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add step", tint = CookncoNavy, modifier = Modifier.size(20.dp))
                }
            }
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
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StepEditCard(
    index: Int,
    step: StepItem,
    elevation: Dp = 0.dp,
    dragHandleModifier: Modifier = Modifier,
    onStepChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        StickerCard(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp).shadow(elevation, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            shadowOffset = 4.dp,
        ) {
            Row(modifier = Modifier.padding(start = 28.dp, top = 8.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = step.text,
                    onValueChange = onStepChange,
                    label = { Text("Step ${index + 1}") },
                    modifier = Modifier.weight(1f),
                    minLines = 2,
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
                Icon(
                    imageVector = Icons.Outlined.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = CookncoNavy.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 4.dp).size(24.dp).then(dragHandleModifier),
                )
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove step",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onRemove),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(CookncoGreen)
                .border(2.dp, CookncoNavy, CircleShape)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = (index + 1).toString(), color = CookncoWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StepHeading(title = "Photo", subtitle = "A picture makes this recipe worth clicking on.")
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(CookncoGreenLight)
                    .border(3.dp, CookncoNavy, RoundedCornerShape(18.dp))
                    .clickable { imagePicker.launch() },
                contentAlignment = Alignment.Center,
            ) {
                if (pickedBitmap != null) {
                    Image(bitmap = pickedBitmap, contentDescription = "Recipe photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = CookncoNavy.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                        Text("Tap to add a photo", color = CookncoNavy.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        if (pickedImage != null) {
            item {
                Text(
                    text = "Saved locally for now — uploading a recipe photo isn't wired up on mobile yet.",
                    color = CookncoNavy.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                )
            }
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
