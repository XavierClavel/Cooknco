package com.xavierclavel.cooknco.ui.recipe

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val dishClasses = listOf(
    "ENTREE" to "Entrée",
    "MAIN_DISH" to "Main dish",
    "DESERT" to "Dessert",
    "SALTY_SNACK" to "Salty snack",
    "SUGARY_SNACK" to "Sugary snack",
    "DRINK" to "Drink",
    "OTHER" to "Other",
)

private val allUnits = listOf(
    "NONE" to "",
    "UNIT" to "Unit",
    "GRAM" to "g",
    "POUND" to "lb",
    "MILLILITERS" to "mL",
    "TEASPOON" to "tsp",
    "TABLESPOON" to "tbsp",
    "CUP" to "cup",
)

private fun unitsForIngredient(allowAmount: Boolean, allowWeight: Boolean, allowVolume: Boolean): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    if (allowAmount) {
        result.add("NONE" to "")
        result.add("UNIT" to "Unit")
    }
    if (allowWeight) {
        result.add("GRAM" to "g")
        result.add("POUND" to "lb")
    }
    if (allowVolume) {
        result.add("MILLILITERS" to "mL")
        result.add("TEASPOON" to "tsp")
        result.add("TABLESPOON" to "tbsp")
        result.add("CUP" to "cup")
    }
    if (result.isEmpty()) result.addAll(allUnits)
    return result
}

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
            val id = uiState.recipeId
            if (id != null) onSaved(id)
        }
    }

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Title
        item {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.error?.contains("Title") == true,
            )
        }

        // Description
        item {
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        }

        // Dish class
        item {
            Text(
                text = "Dish class",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                dishClasses.forEach { (value, label) ->
                    FilterChip(
                        selected = uiState.dishClass == value,
                        onClick = { viewModel.updateDishClass(value) },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Numeric fields row 1: yield + prep time
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.yield,
                    onValueChange = { viewModel.updateYield(it) },
                    label = { Text("Yield") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = uiState.prepTime,
                    onValueChange = { viewModel.updatePrepTime(it) },
                    label = { Text("Prep (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        // Numeric fields row 2: cook time + temperature
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.cookTime,
                    onValueChange = { viewModel.updateCookTime(it) },
                    label = { Text("Cook (min)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = uiState.cookTemp,
                    onValueChange = { viewModel.updateCookTemp(it) },
                    label = { Text("Temp (°C)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        // Ingredients header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ingredients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { viewModel.addIngredient() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add ingredient")
                }
            }
        }

        // Ingredient rows
        val ingredients = uiState.ingredients
        ingredients.forEachIndexed { index, ingredient ->
            item(key = "ingredient_$index") {
                IngredientEditRow(
                    index = index,
                    ingredient = ingredient,
                    onQueryChange = { viewModel.updateIngredientQuery(index, it) },
                    onSelect = { viewModel.selectIngredient(index, it) },
                    onDismiss = { viewModel.dismissDropdown(index) },
                    onUnitChange = { viewModel.updateIngredientUnit(index, it) },
                    onAmountChange = { viewModel.updateIngredientAmount(index, it) },
                    onComplementChange = { viewModel.updateIngredientComplement(index, it) },
                    onRemove = { viewModel.removeIngredient(index) },
                )
            }
        }

        // Custom ingredients header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Custom ingredients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { viewModel.addCustomIngredient() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add custom ingredient")
                }
            }
        }

        // Custom ingredient rows
        val customIngredients = uiState.customIngredients
        customIngredients.forEachIndexed { index, ci ->
            item(key = "custom_ingredient_$index") {
                CustomIngredientEditRow(
                    index = index,
                    ingredient = ci,
                    onNameChange = { viewModel.updateCustomIngredientName(index, it) },
                    onUnitChange = { viewModel.updateCustomIngredientUnit(index, it) },
                    onAmountChange = { viewModel.updateCustomIngredientAmount(index, it) },
                    onRemove = { viewModel.removeCustomIngredient(index) },
                )
            }
        }

        // Steps header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Steps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { viewModel.addStep() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add step")
                }
            }
        }

        // Step rows
        val steps = uiState.steps
        steps.forEachIndexed { index, step ->
            item(key = "step_$index") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    OutlinedTextField(
                        value = step,
                        onValueChange = { viewModel.updateStep(index, it) },
                        label = { Text("Step ${index + 1}") },
                        modifier = Modifier.weight(1f),
                        minLines = 2,
                    )
                    IconButton(onClick = { viewModel.removeStep(index) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove step", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Tips
        item {
            OutlinedTextField(
                value = uiState.tips,
                onValueChange = { viewModel.updateTips(it) },
                label = { Text("Tips") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }

        // Error
        if (uiState.error != null) {
            item {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSaving,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .width(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientEditRow(
    index: Int,
    ingredient: EditIngredient,
    onQueryChange: (String) -> Unit,
    onSelect: (com.xavierclavel.cooknco.network.dto.IngredientSummary) -> Unit,
    onDismiss: () -> Unit,
    onUnitChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onComplementChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val units = unitsForIngredient(ingredient.allowAmount, ingredient.allowWeight, ingredient.allowVolume)
    val showAmount = ingredient.unit != "NONE"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Ingredient name autocomplete
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
                )
                ExposedDropdownMenu(
                    expanded = ingredient.showDropdown,
                    onDismissRequest = onDismiss,
                ) {
                    ingredient.searchResults.forEach { result ->
                        val name = result.name["EN"] ?: result.name.values.firstOrNull() ?: ""
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { onSelect(result) },
                        )
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Unit dropdown
            var unitExpanded by rememberSaveable { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = it },
                modifier = Modifier.width(120.dp),
            ) {
                OutlinedTextField(
                    value = units.find { it.first == ingredient.unit }?.let { (k, v) -> if (v.isEmpty()) k else v } ?: ingredient.unit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unit") },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false },
                ) {
                    units.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(if (label.isEmpty()) key else label) },
                            onClick = {
                                onUnitChange(key)
                                unitExpanded = false
                            },
                        )
                    }
                }
            }

            // Amount field
            if (showAmount) {
                OutlinedTextField(
                    value = ingredient.amount?.toString() ?: "",
                    onValueChange = onAmountChange,
                    label = { Text("Amount") },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            // Complement field
            OutlinedTextField(
                value = ingredient.complement,
                onValueChange = onComplementChange,
                label = { Text("Note") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomIngredientEditRow(
    index: Int,
    ingredient: EditCustomIngredient,
    onNameChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val showAmount = ingredient.unit != "NONE"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ingredient.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var unitExpanded by rememberSaveable { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = it },
                modifier = Modifier.width(120.dp),
            ) {
                OutlinedTextField(
                    value = allUnits.find { it.first == ingredient.unit }?.let { (k, v) -> if (v.isEmpty()) k else v } ?: ingredient.unit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unit") },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false },
                ) {
                    allUnits.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(if (label.isEmpty()) key else label) },
                            onClick = {
                                onUnitChange(key)
                                unitExpanded = false
                            },
                        )
                    }
                }
            }

            if (showAmount) {
                OutlinedTextField(
                    value = ingredient.amount?.toString() ?: "",
                    onValueChange = onAmountChange,
                    label = { Text("Amount") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
    }
}
