package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.UnitInfo
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
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

// ── Shared styling helpers ────────────────────────────────────────────────────

private val fieldShape = RoundedCornerShape(12.dp)

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CookncoWhite,
    unfocusedContainerColor = CookncoWhite,
    focusedBorderColor = CookncoOrange,
    unfocusedBorderColor = CookncoNavy.copy(alpha = 0.5f),
    focusedTextColor = CookncoNavy,
    unfocusedTextColor = CookncoNavy,
    focusedLabelColor = CookncoOrange,
    unfocusedLabelColor = CookncoNavy.copy(alpha = 0.6f),
    cursorColor = CookncoOrange,
)

@Composable
private fun SectionHeader(title: String, count: Int, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CookncoOrange)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(count.toString(), color = CookncoWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CookncoGreen)
                .border(1.5.dp, CookncoNavy, CircleShape)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add", tint = CookncoNavy, modifier = Modifier.size(20.dp))
        }
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

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            val id = uiState.recipeId ?: return@LaunchedEffect
            onSaved(id)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (recipeId == null) "New Recipe" else "Edit Recipe",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isSaving,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CookncoNavy,
                            )
                        } else {
                            Icon(Icons.Outlined.Check, contentDescription = "Save")
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = CookncoOrange, strokeWidth = 3.dp) }
            return@Scaffold
        }

        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyColumnState(lazyListState) { from, to ->
            val steps = uiState.steps
            val fromIdx = steps.indexOfFirst { it.id == from.key }
            val toIdx = steps.indexOfFirst { it.id == to.key }
            if (fromIdx != -1 && toIdx != -1) viewModel.reorderStep(fromIdx, toIdx)
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Basic info ───────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.updateTitle(it) },
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
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
            }

            // ── Dish class ───────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Dish class",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = CookncoNavy.copy(alpha = 0.7f),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        dishClasses.forEach { (value, label) ->
                            DishClassChip(
                                label = label,
                                selected = uiState.dishClass == value,
                                onClick = { viewModel.updateDishClass(value) },
                            )
                        }
                    }
                }
            }

            // ── Numeric fields ───────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = uiState.yield,
                        onValueChange = { viewModel.updateYield(it) },
                        label = { Text("Yield") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                    OutlinedTextField(
                        value = uiState.prepTime,
                        onValueChange = { viewModel.updatePrepTime(it) },
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
                        onValueChange = { viewModel.updateCookTime(it) },
                        label = { Text("Cook (min)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                    OutlinedTextField(
                        value = uiState.cookTemp,
                        onValueChange = { viewModel.updateCookTemp(it) },
                        label = { Text("Temp (°C)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                }
            }

            // ── Ingredients ──────────────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Ingredients",
                    count = uiState.ingredients.size,
                    onAdd = { viewModel.addIngredient() },
                )
            }

            uiState.ingredients.forEachIndexed { index, ingredient ->
                item(key = "ingredient_$index") {
                    IngredientEditCard(
                        index = index,
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

            // ── Steps ────────────────────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Steps",
                    count = uiState.steps.size,
                    onAdd = { viewModel.addStep() },
                )
            }

            uiState.steps.forEachIndexed { index, step ->
                item(key = step.id) {
                    ReorderableItem(reorderState, key = step.id) { isDragging ->
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 6.dp else 0.dp,
                            label = "step_elevation",
                        )
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

            // ── Tips ─────────────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = uiState.tips,
                    onValueChange = { viewModel.updateTips(it) },
                    label = { Text("Tips") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = editFieldColors(),
                    shape = fieldShape,
                )
            }

            // ── Error ────────────────────────────────────────────────────────
            if (uiState.error != null) {
                item {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ── Cancel button ────────────────────────────────────────────────
            item {
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CookncoNavy.copy(alpha = 0.08f),
                        contentColor = CookncoNavy,
                    ),
                    border = BorderStroke(1.5.dp, CookncoNavy),
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Edit card composables ─────────────────────────────────────────────────────

@Composable
private fun DishClassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) CookncoOrange else CookncoWhite)
            .border(
                1.5.dp,
                if (selected) CookncoNavy else CookncoNavy.copy(alpha = 0.35f),
                RoundedCornerShape(50.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) CookncoWhite else CookncoNavy,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientEditCard(
    index: Int,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CookncoWhite),
        border = BorderStroke(1.5.dp, CookncoNavy),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 1: type icon + name autocomplete + delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CookncoGreenLight),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ingredient.type.isNotEmpty()) {
                        AsyncImage(
                            model = "${ApiClient.IMAGE_URL}/ingredients/${ingredient.type}.webp",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = CookncoNavy.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp),
                        )
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
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                            .fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ingredient.showDropdown) },
                        colors = editFieldColors(),
                        shape = fieldShape,
                    )
                    ExposedDropdownMenu(
                        expanded = ingredient.showDropdown,
                        onDismissRequest = onDismiss,
                    ) {
                        ingredient.searchResults.forEach { result ->
                            val name = result.name["EN"] ?: result.name.values.firstOrNull() ?: ""
                            DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(result) })
                        }
                        if (ingredient.searchResults.isEmpty() && ingredient.query.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Add \"${ingredient.query.trim()}\" as custom ingredient") },
                                onClick = onSelectCustom,
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Row 2: unit, amount, complement
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        availableUnits.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unitFieldLabel(unit.name)) },
                                onClick = { onUnitChange(unit.name); unitExpanded = false },
                            )
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

@Composable
private fun StepEditCard(
    index: Int,
    step: StepItem,
    elevation: Dp = 0.dp,
    dragHandleModifier: Modifier = Modifier,
    onStepChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        // White card offset for the overlapping step circle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp)
                .shadow(elevation, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CookncoWhite),
            border = BorderStroke(1.5.dp, CookncoNavy),
        ) {
            Row(
                modifier = Modifier.padding(start = 28.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    tint = CookncoNavy.copy(alpha = 0.35f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(24.dp)
                        .then(dragHandleModifier),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove step", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        // Green numbered circle
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

// ── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun RecipeEditTopBarPreview() {
    CookncoTheme {
        Surface(color = CookncoBackground) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New Recipe", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = CookncoNavy)
                OutlinedTextField(
                    value = "Harcha",
                    onValueChange = {},
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CookncoWhite,
                        unfocusedContainerColor = CookncoWhite,
                        unfocusedBorderColor = CookncoNavy.copy(alpha = 0.5f),
                        unfocusedTextColor = CookncoNavy,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = "Petits pains marocain…",
                    onValueChange = {},
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CookncoWhite,
                        unfocusedContainerColor = CookncoWhite,
                        unfocusedBorderColor = CookncoNavy.copy(alpha = 0.5f),
                        unfocusedTextColor = CookncoNavy,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}
