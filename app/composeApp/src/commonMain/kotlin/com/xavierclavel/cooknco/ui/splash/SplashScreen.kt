package com.xavierclavel.cooknco.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.xavierclavel.cooknco.ui.components.BrandLogo
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import kotlinx.coroutines.delay

/**
 * What is on screen while the stored session is checked.
 *
 * It is the second half of one launch, not a screen of its own: the platform's launch
 * window is showing the tomato on CookncoBackground, and this draws the full logo on the
 * same colour, so nothing flashes as one is swapped for the other. Android's side of that
 * is `Theme.Cooknco.Splash` in `androidApp/src/main/res/values/themes.xml`.
 *
 * The spinner is deliberately late. `AuthRepository.getCurrentUser` is a network call with
 * no timeout configured, so this can be up for a second or for however long the platform
 * takes to give up on a dead network — but on a warm launch it is up for two frames, and a
 * spinner that appears and vanishes inside 100ms is worse than none. So it fades in only
 * once the wait has become long enough to be worth admitting to.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SpinnerDelayMillis)
        showProgress = true
    }

    Surface(modifier = modifier.fillMaxSize(), color = CookncoBackground) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BrandLogo(widthFraction = 0.72f, maxWidth = 340.dp)
                // Faded rather than added: the space it occupies is held from the first
                // frame, so the logo does not jump off centre when the wait turns out to
                // be a long one.
                val progressAlpha by animateFloatAsState(if (showProgress) 1f else 0f)
                Box(
                    modifier = Modifier.height(64.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = CookncoGreen,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp).alpha(progressAlpha),
                    )
                }
            }
        }
    }
}

private const val SpinnerDelayMillis = 600L
