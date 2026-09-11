package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoWhite

/**
 * Placeholder — phase 2 builds the real step-by-step cook mode (see the mockup's "Cook
 * mode" screen and turn 6 / option `6b` for its `#3e6b52` ground, picked over the plain
 * app green so the mode reads as distinct from ordinary navigation).
 */
@Composable
fun CookModeScreen(
    recipeId: Long,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CookncoGreenDark),
        contentAlignment = Alignment.Center,
    ) {
        Text("Cook mode — TODO (recipe $recipeId)", color = CookncoWhite)
    }
}
