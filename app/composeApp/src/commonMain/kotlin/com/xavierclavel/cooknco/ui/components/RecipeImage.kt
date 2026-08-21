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
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight

@Composable
fun RecipeImage(
    recipeId: Long,
    version: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    thumbnail: Boolean = true,
) {
    val url = if (thumbnail) {
        "${ApiClient.IMAGE_URL}/recipes-thumbnails/$recipeId-v$version.webp"
    } else {
        "${ApiClient.IMAGE_URL}/recipes/$recipeId-v$version.webp"
    }
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
