package com.xavierclavel.cooknco.ui.shopping

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xavierclavel.cooknco.data.ShoppingItem
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import kotlin.math.roundToInt

/**
 * Client-side, in-memory shopping list — see [com.xavierclavel.cooknco.data.ShoppingListRepository]
 * for why: there is no backend endpoint to persist one, so this resets when the app
 * process dies. That is an accepted limitation of this pass, not a bug.
 */
@Composable
fun ShoppingListScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.factory())
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val basket = uiState.items.filter { it.checked }
    val toBuy = uiState.items.filterNot { it.checked }
    val aisles = toBuy.groupBy { it.aisle }.toSortedMap()

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Shopping list", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, lineHeight = 33.sp)
                Text(
                    text = "From ${uiState.recipeCount} recipe${if (uiState.recipeCount == 1) "" else "s"} · " +
                        "${basket.size} of ${uiState.items.size} in the basket",
                    fontSize = 13.sp,
                    color = CookncoNavy,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Box {
                StickerIconButton(onClick = { showMenu = true }, shadowOffset = 3.dp) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Clear list") },
                        onClick = { showMenu = false; showClearConfirm = true },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (uiState.items.isEmpty()) {
                item {
                    Text(
                        text = "Nothing here yet — add a recipe's ingredients from its Ingredients tab.",
                        color = CookncoNavy.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }

            aisles.forEach { (aisle, items) ->
                item(key = "aisle_$aisle") {
                    Text(
                        text = aisle.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                    )
                    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column {
                            items.forEach { item ->
                                ShoppingRow(item = item, onToggle = { viewModel.setChecked(item.id, true) })
                            }
                        }
                    }
                }
            }

            if (basket.isNotEmpty()) {
                item {
                    Text(
                        text = "IN THE BASKET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(basket, key = { it.id }) { item ->
                    BasketRow(
                        item = item,
                        onToggle = { viewModel.setChecked(item.id, false) },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
        }

        // "Add a cookbook to the list" would bulk-add every recipe in a cookbook; that
        // needs a cookbook picker and iterating its recipes, which is out of scope for
        // this pass (see the PR description) — kept as a visual affordance for now.
        StickerCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp),
            shadowOffset = 4.dp,
        ) {
            Text(
                text = "Add a cookbook to the list",
                color = CookncoNavy,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear shopping list", fontWeight = FontWeight.Bold) },
            text = { Text("This removes every item, checked or not.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clear(); showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CookncoNavy),
                ) { Text("Clear", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = CookncoNavy.copy(alpha = 0.7f))
                }
            },
        )
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(24.dp).clip(CircleShape).border(2.5.dp, CookncoNavy, CircleShape))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CookncoNavy)
            Text(item.fromRecipe, fontSize = 11.5.sp, color = CookncoNavy.copy(alpha = 0.6f))
        }
        Text(itemQuantityLabel(item), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
    }
}

@Composable
private fun BasketRow(item: ShoppingItem, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CookncoGreenDark)
            .border(3.dp, CookncoNavy, RoundedCornerShape(20.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(CookncoGold)
                .border(2.dp, CookncoNavy, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(14.dp))
        }
        Text(
            text = item.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = CookncoBackground,
            textDecoration = TextDecoration.LineThrough,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun itemQuantityLabel(item: ShoppingItem): String {
    val amount = item.amount ?: return ""
    val rounded = if (amount == amount.roundToInt().toFloat()) amount.roundToInt().toString() else amount.toString()
    val unit = unitAbbrev(item.unit)
    return if (unit.isEmpty()) rounded else "$rounded $unit"
}

private fun unitAbbrev(unit: String): String = when (unit) {
    "NONE", "UNIT" -> ""
    "GRAM" -> "g"
    "KILOGRAM" -> "kg"
    "MILLILITERS" -> "mL"
    "CENTILITER" -> "cL"
    "LITER" -> "L"
    else -> unit.lowercase()
}

@Preview(showBackground = true, name = "Shopping row")
@Composable
fun ShoppingRowPreview() {
    CookncoTheme {
        Column(modifier = Modifier.background(CookncoGreen).padding(16.dp)) {
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column {
                    ShoppingRow(
                        item = ShoppingItem(id = 1L, name = "Semoule moyen", amount = 400f, unit = "GRAM", aisle = "GRAIN", fromRecipe = "Harcha"),
                        onToggle = {},
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            BasketRow(
                item = ShoppingItem(id = 3L, name = "Salt", amount = null, unit = "NONE", aisle = "Other", fromRecipe = "Harcha", checked = true),
                onToggle = {},
            )
        }
    }
}
