package com.xavierclavel.cooknco.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = CookncoOrange,
    onPrimary = CookncoWhite,
    primaryContainer = CookncoOrangeLight,
    onPrimaryContainer = CookncoOrangeDark,

    secondary = CookncoGreen,
    onSecondary = CookncoWhite,
    secondaryContainer = CookncoGreenLight,
    onSecondaryContainer = CookncoGreenDark,

    tertiary = CookncoBlue,
    onTertiary = CookncoWhite,
    tertiaryContainer = CookncoBlueLight,
    onTertiaryContainer = CookncoBlueDark,

    background = CookncoBackground,
    onBackground = CookncoNavy,

    surface = CookncoSurface,
    onSurface = CookncoNavy,
    surfaceVariant = CookncoGreenLight,
    onSurfaceVariant = CookncoGreenDark,
)

@Composable
fun CookncoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = cookncoTypography(cookncoFontFamily()),
        content = content,
    )
}
