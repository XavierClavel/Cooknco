package com.xavierclavel.cooknco.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.xavierclavel.cooknco.resources.Res
import com.xavierclavel.cooknco.resources.geologica_bold
import com.xavierclavel.cooknco.resources.geologica_medium
import com.xavierclavel.cooknco.resources.geologica_regular
import com.xavierclavel.cooknco.resources.geologica_semibold
// The compose-resources font loader, NOT androidx.compose.ui.text.font.Font (that one
// takes a platform resource id). This one takes a FontResource and still returns an
// androidx.compose.ui.text.font.Font, which is why FontFamily(...) below accepts it as-is.
import org.jetbrains.compose.resources.Font

/**
 * Geologica — the font the web app already sets, bundled here as four static weights
 * (400/500/600/700) instantiated from Google Fonts' variable `Geologica[...].ttf`
 * (`ofl/geologica/`, `wght` axis, CRSV/SHRP/slnt pinned to their defaults). Compose
 * Multiplatform's resource `Font()` loader takes one static file per weight, not a
 * variable font, hence the pre-instancing rather than shipping the variable file as-is.
 *
 * Only the weights the design actually uses are bundled — Light/Thin/Black are not needed.
 */
@Composable
fun cookncoFontFamily(): FontFamily {
    val regular = Font(Res.font.geologica_regular, weight = FontWeight.Normal)
    val medium = Font(Res.font.geologica_medium, weight = FontWeight.Medium)
    val semiBold = Font(Res.font.geologica_semibold, weight = FontWeight.SemiBold)
    val bold = Font(Res.font.geologica_bold, weight = FontWeight.Bold)
    return remember(regular, medium, semiBold, bold) {
        FontFamily(regular, medium, semiBold, bold)
    }
}
