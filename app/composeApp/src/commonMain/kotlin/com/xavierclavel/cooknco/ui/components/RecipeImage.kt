package com.xavierclavel.cooknco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.data.ImageUrls
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight

@Composable
fun StepImage(
    stepId: Long,
    version: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // No thumbnail and no placeholder: a step picture is drawn at one size, and a step
    // without one shows nothing rather than a stand-in for something that was never there.
    // Callers check [RecipeStepInfo.imageVersion] before asking for it.
    AsyncImage(
        model = AppGraph.offlineImages.resolve(ImageUrls.step(stepId, version)),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
fun RecipeImage(
    recipeId: Long,
    version: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    thumbnail: Boolean = true,
) {
    // Whatever the offline store holds for it, or the URL when it holds nothing. A pinned
    // recipe's picture is a file this app owns rather than a cache entry it hopes survives —
    // see [com.xavierclavel.cooknco.data.OfflineImages].
    val url = AppGraph.offlineImages.resolve(
        if (thumbnail) ImageUrls.recipeThumbnail(recipeId, version)
        else ImageUrls.recipe(recipeId, version)
    )
    Box(
        modifier = modifier.background(CookncoGreenLight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Restaurant,
            contentDescription = null,
            tint = CookncoGreen.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp),
        )
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
