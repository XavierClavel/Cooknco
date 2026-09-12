package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.xavierclavel.cooknco.resources.Res
import com.xavierclavel.cooknco.resources.logo
import org.jetbrains.compose.resources.painterResource

/**
 * The Cook'n'Co lockup, sized as a fraction of the width it is given.
 *
 * The [aspectRatio] is what makes that work, and leaving it out is why the logo was coming
 * out at postage-stamp size on the screens that show it biggest. `logo.png` is 205x128 and
 * carries no density qualifier, so Compose reads it as 205x128 **dp**; with a fixed width
 * and a loose height — which is every one of these screens, the sign-in pages being inside
 * a `verticalScroll` and so unbounded outright — `ContentScale.Fit` takes the smaller of
 * the two scale factors, and the height's is 1. The image stayed at its intrinsic size and
 * sat centred in a box twice as wide. Fixing the height to the width makes both dimensions
 * exact, so there is nothing left for Fit to clamp against.
 *
 * The cap is there so the lockup does not become a billboard on a tablet.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.66f,
    maxWidth: androidx.compose.ui.unit.Dp = 310.dp,
) {
    Image(
        painter = painterResource(Res.drawable.logo),
        contentDescription = "Cook'n'Co",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .widthIn(max = maxWidth)
            .aspectRatio(LogoAspectRatio),
    )
}

/** 205x128, the pixel size of `logo.png` — the source of truth for how wide the mark is. */
private const val LogoAspectRatio = 205f / 128f
