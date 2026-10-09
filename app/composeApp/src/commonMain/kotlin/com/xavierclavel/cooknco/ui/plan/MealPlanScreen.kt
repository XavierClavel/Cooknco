package com.xavierclavel.cooknco.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.data.MealSlot
import com.xavierclavel.cooknco.data.PlannedDay
import com.xavierclavel.cooknco.data.daysFrom
import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import com.xavierclavel.cooknco.network.dto.MealPlanRecipe
import com.xavierclavel.cooknco.ui.components.CookingEmptyScreen
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGoldLight
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The meal plan tab: this week's days, what is planned for each meal, and the way to add to it.
 *
 * Premium. Without a grant the tab is still there and says what it would do — a paid feature is
 * shown locked rather than hidden, as the export is (`premiumSheetAction`) — and it asks the
 * server nothing, since every answer would be a 403.
 *
 * One list for planning and for remembering: the past days of the week are as editable as the
 * coming ones, so what was actually cooked can be put down after the fact.
 */
@Composable
fun MealPlanScreen(
    viewModel: MealPlanViewModel,
    isPremium: Boolean,
    onRecipeClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    if (!isPremium) {
        Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
            MealPlanHeader(title = s.mealPlan)
            CookingEmptyScreen(title = s.mealPlanLockedTitle, message = s.mealPlanLockedMessage)
        }
        return
    }

    val uiState by viewModel.uiState.collectAsState()
    // Every time the tab comes back on screen: a dish may have been planned from a recipe's page.
    LaunchedEffect(Unit) { viewModel.load() }

    MealPlanContent(
        uiState = uiState,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onThisWeek = viewModel::thisWeek,
        onRetry = viewModel::load,
        onAdd = viewModel::openAdd,
        onDish = { entry ->
            // A recipe opens: that is what a planned recipe is tapped for, on the evening it is
            // cooked. Everything else about the dish is behind its "···".
            val recipe = entry.recipe
            if (recipe != null) onRecipeClick(recipe.id) else viewModel.openEntry(entry)
        },
        onDishMenu = viewModel::openEntry,
        modifier = modifier,
    )

    uiState.adding?.let { adding ->
        AddDishSheet(
            state = adding,
            onQueryChange = viewModel::setQuery,
            onSlotChange = viewModel::setAddSlot,
            onAddTyped = viewModel::addTypedDish,
            onAddSuggestion = viewModel::addSuggestion,
            onAddRecipe = viewModel::addRecipe,
            onDismissRequest = viewModel::closeAdd,
        )
    }

    uiState.editing?.let { editing ->
        val entry = editing.entry
        val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@let
        val slot = MealSlot.of(entry.slot) ?: return@let
        MealEntrySheet(
            heading = entry.title,
            subtitle = s.mealOn(date, slot),
            initial = MealEntryDraft(date = date, slot = slot, servings = entry.servings, title = entry.title),
            // The week on screen and the next, so a dish can be pushed back a few days
            days = daysFrom(uiState.weekStart, 14),
            titleEditable = entry.recipe == null,
            primaryLabel = s.save,
            isSaving = editing.isSaving,
            saveFailed = editing.saveFailed,
            onSave = viewModel::saveEntry,
            onDismissRequest = viewModel::closeEntry,
            onOpenRecipe = entry.recipe?.let { recipe -> { viewModel.closeEntry(); onRecipeClick(recipe.id) } },
            onRemove = viewModel::removeEntry,
        )
    }
}

@Composable
private fun MealPlanContent(
    uiState: MealPlanUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onRetry: () -> Unit,
    onAdd: (PlannedDay) -> Unit,
    onDish: (MealPlanEntry) -> Unit,
    onDishMenu: (MealPlanEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        MealPlanHeader(title = s.mealPlan) {
            WeekNavigation(
                label = s.weekRange(uiState.weekStart, uiState.weekStart.plus(6, DateTimeUnit.DAY)),
                isCurrentWeek = uiState.isCurrentWeek,
                onPrevious = onPreviousWeek,
                onNext = onNextWeek,
                onThisWeek = onThisWeek,
            )
        }

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
            }

            // Only before the week's first answer: after it, a failed refresh leaves what was
            // shown in place rather than swapping a plan for an apology.
            uiState.loadFailed && !uiState.hasLoaded -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.mealPlanLoadFailed, color = CookncoNavy, fontWeight = FontWeight.SemiBold)
                StickerPill(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                    Text(s.retry, fontWeight = FontWeight.Bold)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(uiState.days, key = { it.date.toString() }) { day ->
                    DayCard(
                        day = day,
                        isToday = day.date == uiState.today,
                        onAdd = { onAdd(day) },
                        onDish = onDish,
                        onDishMenu = onDishMenu,
                    )
                }
            }
        }
    }
}

@Composable
private fun MealPlanHeader(title: String, below: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
    ) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, lineHeight = 33.sp)
        below()
    }
}

@Composable
private fun WeekNavigation(
    label: String,
    isCurrentWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    val s = strings()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CookncoNavy, modifier = Modifier.weight(1f))
        if (!isCurrentWeek) {
            StickerPill(onClick = onThisWeek, height = 36.dp, shadowOffset = 3.dp, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(s.thisWeek, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        StickerIconButton(onClick = onPrevious, size = 36.dp, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = s.previousWeek, tint = CookncoNavy)
        }
        StickerIconButton(onClick = onNext, size = 36.dp, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = s.nextWeek, tint = CookncoNavy)
        }
    }
}

/**
 * One day: its date, a "+" to add to it, and its meals — only the ones with something in them,
 * so an unplanned day is one line rather than four empty rows.
 */
@Composable
private fun DayCard(
    day: PlannedDay,
    isToday: Boolean,
    onAdd: () -> Unit,
    onDish: (MealPlanEntry) -> Unit,
    onDishMenu: (MealPlanEntry) -> Unit,
) {
    val s = strings()
    StickerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        fillColor = if (isToday) CookncoGoldLight else CookncoBackground,
        shadowOffset = 5.dp,
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = s.fullDate(day.date),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(CookncoOrange)
                            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    ) {
                        Text(s.today, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CookncoWhite)
                    }
                }
                Box(Modifier.weight(1f))
                StickerIconButton(onClick = onAdd, size = 34.dp, shape = RoundedCornerShape(11.dp), shadowOffset = 2.dp) {
                    Icon(Icons.Outlined.Add, contentDescription = s.addADish, tint = CookncoNavy, modifier = Modifier.size(20.dp))
                }
            }
            if (day.meals.isEmpty()) {
                Text(
                    text = s.nothingPlanned,
                    fontSize = 13.sp,
                    color = CookncoNavy.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            day.meals.forEach { meal ->
                Text(
                    text = s.mealSlot(meal.slot).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = CookncoGreenDark,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                meal.dishes.forEach { dish ->
                    DishRow(dish = dish, onClick = { onDish(dish) }, onMenu = { onDishMenu(dish) })
                }
            }
        }
    }
}

@Composable
private fun DishRow(dish: MealPlanEntry, onClick: () -> Unit, onMenu: () -> Unit) {
    val s = strings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val thumbnail = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .border(2.dp, CookncoNavy, RoundedCornerShape(11.dp))
        val recipe = dish.recipe
        if (recipe != null) {
            RecipeImage(recipeId = recipe.id, version = recipe.version, contentDescription = null, modifier = thumbnail)
        } else {
            Box(modifier = thumbnail.background(CookncoGreenLight), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dish.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp,
            )
            dish.servings?.let {
                Text(s.servingsCount(it), fontSize = 12.5.sp, color = CookncoNavy.copy(alpha = 0.62f))
            }
        }
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = s.edit, tint = CookncoNavy)
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewMonday = LocalDate(2026, 10, 12)
private val previewEntries = listOf(
    MealPlanEntry(id = 1, date = "2026-10-12", slot = "LUNCH", title = "Leftovers"),
    MealPlanEntry(id = 2, date = "2026-10-12", slot = "DINNER", title = "Harcha", servings = 4, recipe = MealPlanRecipe(1, 1)),
    MealPlanEntry(id = 3, date = "2026-10-12", slot = "DINNER", title = "Green salad", servings = 2),
    MealPlanEntry(id = 4, date = "2026-10-14", slot = "BREAKFAST", title = "Porridge", servings = 2),
)

@Preview(showBackground = true)
@Composable
fun MealPlanScreenPreview() {
    CookncoTheme {
        MealPlanContent(
            uiState = MealPlanUiState(
                today = previewMonday,
                weekStart = previewMonday,
                entries = previewEntries,
                hasLoaded = true,
            ),
            onPreviousWeek = {}, onNextWeek = {}, onThisWeek = {}, onRetry = {},
            onAdd = {}, onDish = {}, onDishMenu = {},
        )
    }
}
