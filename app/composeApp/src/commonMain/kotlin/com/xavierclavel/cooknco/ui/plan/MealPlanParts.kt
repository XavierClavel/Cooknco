package com.xavierclavel.cooknco.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Restaurant
import com.xavierclavel.cooknco.ui.theme.CookncoGoldLight
import androidx.compose.material.icons.outlined.NoMeals
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.xavierclavel.cooknco.data.MealSlot
import com.xavierclavel.cooknco.data.plannableMealSlots
import com.xavierclavel.cooknco.network.dto.MealPlanRecipe
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.stickerShadow

/** The meal plan's secondary text: dates under a heading, a dish's servings. */
internal val PlanMuted = Color(0xFF3F4953)

/** A day of the month either side of the one a calendar shows. */
internal val PlanOutOfMonth = Color(0xFF8A939B)

/** A field's name, set small above it: "DAY", "MEAL", "SERVINGS". */
@Composable
internal fun PlanFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = CookncoGreenDark,
        modifier = modifier,
    )
}

/** The four meals of a day as chips, the chosen one filled and lifted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealChips(selected: MealSlot, onSelect: (MealSlot) -> Unit, modifier: Modifier = Modifier) {
    val s = strings()
    // A dish already at breakfast keeps its chip when breakfast is not offered, or it could
    // not be shown where it is
    val offered = plannableMealSlots()
    val slots = MealSlot.entries.filter { it in offered || it == selected }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEach { slot ->
            val isSelected = slot == selected
            val shape = RoundedCornerShape(percent = 50)
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .then(if (isSelected) Modifier.stickerShadow(shape, CookncoNavy, 3.dp, 3.dp) else Modifier)
                    .clip(shape)
                    .background(if (isSelected) CookncoOrange else CookncoBackground)
                    .border(2.5.dp, CookncoNavy, shape)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(slot) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.mealSlotShort(slot), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, maxLines = 1)
            }
        }
    }
}

/** The white search field the sheets share, with a way to empty it once something is typed. */
@Composable
internal fun PlanSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val s = strings()
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .stickerShadow(shape, CookncoNavy, 4.dp, 4.dp)
            .clip(shape)
            .background(CookncoWhite)
            .border(3.dp, CookncoNavy, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = PlanMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = CookncoNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(CookncoOrange),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CookncoNavy)
                    .clickable(onClickLabel = s.clear) { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = s.clear, tint = CookncoWhite, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * A dish's picture: its recipe's, or — for one planned as words, or whose recipe is out of
 * reach — a fork and knife on green.
 */
@Composable
internal fun DishThumbnail(recipe: MealPlanRecipe?, size: Dp, cornerRadius: Dp, borderWidth: Dp = 2.dp, hasCooked: Boolean = true) {
    val shape = RoundedCornerShape(cornerRadius)
    val frame = Modifier.size(size).clip(shape).border(borderWidth, CookncoNavy, shape)
    if (!hasCooked) {
        Box(modifier = frame.background(CookncoGoldLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.NoMeals, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(size * 0.42f))
        }
    } else if (recipe != null) {
        RecipeImage(recipeId = recipe.id, version = recipe.version, contentDescription = null, modifier = frame)
    } else {
        Box(modifier = frame.background(CookncoGreenLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(size * 0.42f))
        }
    }
}
