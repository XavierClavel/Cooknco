package com.xavierclavel.cooknco.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoNavy

/**
 * Placeholder — phase 2 builds the real shopping list (aisle-grouped items pulled from
 * the cookbooks added to it, plus the checked-off "basket" section; see the mockup's
 * "Shopping list" screen).
 */
@Composable
fun ShoppingListScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CookncoBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text("Shopping list — TODO", color = CookncoNavy)
    }
}
