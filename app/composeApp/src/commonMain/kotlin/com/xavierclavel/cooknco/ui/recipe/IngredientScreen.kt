package com.xavierclavel.cooknco.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.ui.components.LikeCount
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoOrangeDark
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton

/**
 * One ingredient (`Cooknco Mobile.dc.html`, "Ingredient"): what it is, what it is measured
 * in, and what has been cooked with it — the caller's own recipes first, then everyone's.
 *
 * Both lists are the same `RecipeFilter.ingredient` query, which is why this screen needed
 * no new endpoint: one is narrowed to the signed-in cook and sorted by date, the other is
 * not narrowed and sorted by likes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IngredientScreen(
    viewModel: IngredientViewModel,
    onNavigateBack: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val uiState by viewModel.uiState.collectAsState()
    val ingredient = uiState.ingredient

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StickerIconButton(onClick = onNavigateBack, shadowOffset = 3.dp) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back)
            }
        }

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            ingredient == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: s.somethingWentWrong, color = CookncoNavy, modifier = Modifier.padding(16.dp))
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                IngredientCard(ingredient = ingredient, s = s)

                if (uiState.units.isNotEmpty()) {
                    SectionHeading(s.measuredIn)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // The default first and gold, the rest cream: the picker in the
                        // editor opens on that one, so the page says which it is.
                        val defaultUnit = uiState.units.firstOrNull { it.name == ingredient.defaultUnit }
                        defaultUnit?.let { unit ->
                            UnitChip(label = s.unitDefault(s.unitName(unit.name)), isDefault = true)
                        }
                        uiState.units
                            .filterNot { it.name == defaultUnit?.name || it.name == "NONE" }
                            .forEach { UnitChip(label = s.unitName(it.name), isDefault = false) }
                    }
                }

                if (uiState.mine.isNotEmpty()) {
                    SectionHeading(s.inYourRecipes, trailing = uiState.mine.size.toString())
                    RecipeListCard(recipes = uiState.mine, onRecipeClick = onRecipeClick, showLikes = false)
                }

                if (uiState.popular.isNotEmpty()) {
                    SectionHeading(s.popularWithThis)
                    RecipeListCard(recipes = uiState.popular, onRecipeClick = onRecipeClick, showLikes = true)
                }

                if (uiState.mine.isEmpty() && uiState.popular.isEmpty()) {
                    StickerCard(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(
                            text = s.nothingCookedWithThis,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = CookncoNavy.copy(alpha = 0.7f),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientCard(ingredient: IngredientSummary, s: Strings) {
    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowOffset = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val shape = RoundedCornerShape(22.dp)
            if (ingredient.type.isNotEmpty()) {
                AsyncImage(
                    model = "${ApiClient.IMAGE_URL}/ingredients/${ingredient.type}.webp",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(76.dp).clip(shape).background(CookncoGreenLight).border(3.dp, CookncoNavy, shape),
                )
            } else {
                Box(modifier = Modifier.size(76.dp).clip(shape).background(CookncoGreenLight).border(3.dp, CookncoNavy, shape))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ingredient.displayName(),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    lineHeight = 28.sp,
                )
                if (ingredient.type.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(CookncoWhite)
                            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = s.ingredientType(ingredient.type),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                        )
                    }
                }
            }
        }
    }
}

/** The name in the language the app is in, falling back to whatever the catalogue has. */
private fun IngredientSummary.displayName(): String =
    name[ApiClient.locale] ?: name["EN"] ?: name.values.firstOrNull() ?: ""

@Composable
private fun SectionHeading(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 9.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            letterSpacing = 0.7.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
        }
    }
}

@Composable
private fun UnitChip(label: String, isDefault: Boolean) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (isDefault) CookncoGold else CookncoGreenLight)
            .border(2.5.dp, CookncoNavy, RoundedCornerShape(percent = 50))
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (isDefault) FontWeight.Bold else FontWeight.SemiBold,
            color = CookncoNavy,
        )
    }
}

@Composable
private fun RecipeListCard(recipes: List<RecipeOverview>, onRecipeClick: (Long) -> Unit, showLikes: Boolean) {
    StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowOffset = 6.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            recipes.forEachIndexed { index, recipe ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecipeClick(recipe.id) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecipeImage(
                        recipeId = recipe.id,
                        version = recipe.version,
                        contentDescription = recipe.title,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, CookncoNavy, RoundedCornerShape(12.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recipe.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                            lineHeight = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = recipe.owner.username,
                            fontSize = 12.sp,
                            color = CookncoGreenDark,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (showLikes) {
                        LikeCount(count = recipe.likesCount, color = CookncoOrangeDark, fontSize = 12.sp)
                    }
                }
                if (index != recipes.lastIndex) {
                    HorizontalDivider(thickness = 2.dp, color = CookncoNavy.copy(alpha = 0.1f))
                }
            }
        }
    }
}
