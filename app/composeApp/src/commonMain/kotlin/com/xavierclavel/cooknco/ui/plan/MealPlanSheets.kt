package com.xavierclavel.cooknco.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NoMeals
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xavierclavel.cooknco.data.MealSlot
import com.xavierclavel.cooknco.data.monthOf
import com.xavierclavel.cooknco.network.dto.MealPlanRecipe
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGoldLight
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoOrangeDark
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.sheetScrim
import com.xavierclavel.cooknco.ui.theme.stickerShadow
import com.xavierclavel.cooknco.ui.theme.swallowTaps
import kotlinx.datetime.LocalDate

/**
 * Adds a dish: one of the cook's recipes, something they planned before, or whatever they type.
 *
 * One field for all three, because the cook does not decide up front whether tonight is "a
 * recipe" or "words" — they start typing what they will eat. The typed words come first in the
 * list, so the shortest path is always there; the recipes they have to hand follow. A recipe
 * that is none of theirs is planned from its own page instead.
 *
 * A tap adds and closes, for one (`DEFAULT_MEAL_SERVINGS`). Any other number is the dish's own
 * sheet's business: asking every time would be a step most dishes do not need.
 *
 * Opened from a day's "+", it plans on that day. Opened to plan a meal on several days
 * ([AddDishState.picksDays]), the days are picked on a calendar in the sheet itself, and the
 * dishes stay dimmed until there is at least one.
 */
@Composable
fun AddDishSheet(
    state: AddDishState,
    today: LocalDate,
    onQueryChange: (String) -> Unit,
    onSlotChange: (MealSlot) -> Unit,
    onToggleDate: (LocalDate) -> Unit,
    onMonthChange: (LocalDate) -> Unit,
    onClearDates: () -> Unit,
    onAddTyped: () -> Unit,
    onAddSuggestion: (String) -> Unit,
    onAddRecipe: (RecipeOverview) -> Unit,
    onAddNotCooked: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    val focusRequester = remember { FocusRequester() }
    // Straight to the keyboard for a day's "+"; not over the calendar the cook has to see first
    if (!state.picksDays) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val canAdd = !state.isSaving && state.dates.isNotEmpty()

    PlanBottomSheet(
        onDismissRequest = onDismissRequest,
        footer = { SheetFooterButton(label = s.cancel, onClick = onDismissRequest) },
    ) {
        if (state.picksDays) {
            SheetTitle(title = s.planAMeal, subtitle = s.planAMealSubtitle)
            // The meal first: the calendar marks the days that already have that meal
            PlanFieldLabel(s.mealLabel, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp))
            MealChips(selected = state.slot, onSelect = onSlotChange, modifier = Modifier.padding(horizontal = 18.dp))
            PickedDays(state = state, onToggleDate = onToggleDate, onClearDates = onClearDates)
            MonthCalendar(
                initialMonth = today,
                today = today,
                isSelected = { it in state.dates },
                onDayClick = { if (!state.isSaving) onToggleDate(it) },
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp),
                onMonthChange = onMonthChange,
                hasMeal = { state.slot in state.taken[it].orEmpty() },
            )
        } else {
            SheetTitle(title = s.addADish, subtitle = s.fullDate(state.dates.first()))
            MealChips(selected = state.slot, onSelect = onSlotChange, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp))
        }
        PlanSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = s.searchOrTypeADish,
            onDone = { if (canAdd) onAddTyped() },
            focusRequester = focusRequester,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp),
        )
        Column(modifier = Modifier.padding(top = 6.dp).alpha(if (canAdd || state.isSaving) 1f else 0.4f)) {
            DishResults(
                state = state,
                enabled = canAdd,
                onAddTyped = onAddTyped,
                onAddSuggestion = onAddSuggestion,
                onAddRecipe = onAddRecipe,
                onAddNotCooked = onAddNotCooked,
            )
        }
        if (state.saveFailed) SaveFailed()
    }
}

/** "DAYS · 3", the way to take them all back, and the days themselves as chips that each take one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickedDays(state: AddDishState, onToggleDate: (LocalDate) -> Unit, onClearDates: () -> Unit) {
    val s = strings()
    Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            val count = state.dates.size
            PlanFieldLabel(if (count > 0) "${s.daysLabel} · $count" else s.daysLabel, modifier = Modifier.weight(1f))
            if (count > 0) {
                Text(
                    text = s.clearDays,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !state.isSaving, onClick = onClearDates)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
        if (state.dates.isEmpty()) {
            Text(s.noDayPicked, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PlanMuted, modifier = Modifier.padding(top = 6.dp))
        } else {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.dates.sorted().forEach { day ->
                    val shape = RoundedCornerShape(percent = 50)
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(shape)
                            .background(CookncoGold)
                            .border(2.5.dp, CookncoNavy, shape)
                            .clickable(enabled = !state.isSaving, onClickLabel = s.removeDay(day)) { onToggleDate(day) }
                            .padding(start = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(s.shortDay(day), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** What was typed, what was planned before, and the cook's recipes that match. */
@Composable
private fun DishResults(
    state: AddDishState,
    enabled: Boolean,
    onAddTyped: () -> Unit,
    onAddSuggestion: (String) -> Unit,
    onAddRecipe: (RecipeOverview) -> Unit,
    onAddNotCooked: () -> Unit,
) {
    val s = strings()
    val typed = state.query.trim()
    if (typed.isNotEmpty()) {
        ResultRow(label = s.addTypedDish(typed), sub = s.asWordsNoRecipe, enabled = enabled, onClick = onAddTyped) {
            ResultIcon(Icons.Outlined.Add, background = CookncoOrange)
        }
    } else {
        // First, before any dish: a meal nobody cooked is still a meal accounted for
        ResultRow(label = s.notCooked, sub = s.notCookedHint, enabled = enabled, onClick = onAddNotCooked) {
            ResultIcon(Icons.Outlined.NoMeals, background = CookncoGoldLight)
        }
    }
    if (state.suggestions.isNotEmpty()) {
        SectionLabel(s.plannedBeforeCaps)
        state.suggestions.forEach { suggestion ->
            ResultRow(label = suggestion, enabled = enabled, onClick = { onAddSuggestion(suggestion) }) {
                ResultIcon(Icons.Outlined.History, background = CookncoGoldLight)
            }
        }
    }
    SectionLabel(s.yourRecipesCaps)
    when {
        state.recipes.isEmpty() && state.isSearching -> Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
        }
        state.recipes.isEmpty() -> Text(
            text = s.noRecipeMatches,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PlanMuted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        )
        else -> state.recipes.forEach { recipe ->
            ResultRow(label = recipe.title, enabled = enabled, onClick = { onAddRecipe(recipe) }) {
                val shape = RoundedCornerShape(12.dp)
                RecipeImage(
                    recipeId = recipe.id,
                    version = recipe.version,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(shape).border(2.dp, CookncoNavy, shape),
                )
            }
        }
    }
}

/**
 * When a dish is, which meal, and for how many — for one already planned (from the tab) or
 * about to be (from a recipe's page). The two differ only in where the days start, what the
 * button says, and what else the sheet offers, so they are one sheet.
 *
 * @param recipe the recipe the dish is, for its picture; null draws a dish planned as words.
 * @param titleEditable for a dish shown as words, whose name the cook wrote and may change.
 * @param days the days offered as chips, scrolled to [initial]'s. The calendar reaches any other.
 * @param servingsNote said under the servings — the recipe's yield, where it is known.
 */
@Composable
fun MealEntrySheet(
    heading: String,
    subtitle: String?,
    initial: MealEntryDraft,
    days: List<LocalDate>,
    titleEditable: Boolean,
    primaryLabel: String,
    isSaving: Boolean,
    saveFailed: Boolean,
    onSave: (MealEntryDraft) -> Unit,
    onDismissRequest: () -> Unit,
    recipe: MealPlanRecipe? = null,
    servingsNote: String? = null,
    onOpenRecipe: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val s = strings()
    var draft by remember(initial) { mutableStateOf(initial) }
    var pickingDate by remember { mutableStateOf(false) }
    val canSave = !isSaving && (!titleEditable || draft.title.isNotBlank())

    PlanBottomSheet(
        onDismissRequest = onDismissRequest,
        footer = {
            SheetFooterButton(label = primaryLabel, highlighted = true, busy = isSaving, onClick = { if (canSave) onSave(draft) })
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DishThumbnail(recipe = recipe, size = 56.dp, cornerRadius = 16.dp, borderWidth = 2.5.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(heading, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 13.5.sp, color = PlanMuted)
            }
            if (onOpenRecipe != null) {
                StickerPill(
                    onClick = onOpenRecipe,
                    height = 36.dp,
                    borderWidth = 2.5.dp,
                    shadowOffset = 3.dp,
                    contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                ) {
                    Text(s.openShort, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (titleEditable) {
            PlanFieldLabel(s.dishName, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp))
            DishNameField(value = draft.title, onValueChange = { draft = draft.copy(title = it) }, modifier = Modifier.padding(horizontal = 18.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanFieldLabel(s.dayLabel, modifier = Modifier.weight(1f))
            AnotherDayLink(date = draft.date.takeIf { it !in days }, onClick = { pickingDate = true })
        }
        DayChips(days = days, selected = draft.date, onSelect = { draft = draft.copy(date = it) })

        PlanFieldLabel(s.mealLabel, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp))
        MealChips(selected = draft.slot, onSelect = { draft = draft.copy(slot = it) }, modifier = Modifier.padding(horizontal = 18.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PlanFieldLabel(s.servingsLabel)
                if (servingsNote != null) Text(servingsNote, fontSize = 12.5.sp, color = PlanMuted)
            }
            ServingsStepper(servings = draft.servings, onChange = { draft = draft.copy(servings = it) })
        }

        if (onRemove != null) {
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, top = 22.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isSaving, onClick = onRemove)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = CookncoOrangeDark, modifier = Modifier.size(20.dp))
                Text(s.removeFromPlan, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CookncoOrangeDark)
            }
        }
        if (saveFailed) SaveFailed()
    }

    // Beside the sheet rather than inside it, so it is a second dialog over the first and not
    // one nested in the other's content.
    if (pickingDate) {
        MealDatePickerDialog(
            selection = draft.date..draft.date,
            today = remember { currentDay() },
            onPick = { draft = draft.copy(date = it); pickingDate = false },
            onDismissRequest = { pickingDate = false },
        )
    }
}

/**
 * A planned dish: every day it is served at its meal, on a calendar where a tap adds a day or
 * takes one off, and the meal, servings and — for one shown as words — the name, which a save
 * carries to all of them. The days arrive a month at a time, as the calendar pages to them.
 */
@Composable
fun DishDaysSheet(
    state: EditDishState,
    today: LocalDate,
    onToggleDate: (LocalDate) -> Unit,
    onMonthChange: (LocalDate) -> Unit,
    onSlotChange: (MealSlot) -> Unit,
    onServingsChange: (LocalDate, Int?) -> Unit,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onOpenRecipe: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    val titleEditable = state.entry.recipe == null && !state.entry.notCooked
    val initialMonth = remember(state.entry.id) { runCatching { LocalDate.parse(state.entry.date) }.getOrDefault(today) }
    // The month the calendar shows, whose days the portions below are for
    var shownMonth by remember(state.entry.id) { mutableStateOf(monthOf(initialMonth)) }

    PlanBottomSheet(
        onDismissRequest = onDismissRequest,
        footer = {
            SheetFooterButton(label = s.save, highlighted = true, busy = state.isSaving, onClick = onSave)
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DishThumbnail(recipe = state.entry.recipe, size = 56.dp, cornerRadius = 16.dp, borderWidth = 2.5.dp, notCooked = state.entry.notCooked)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (state.entry.notCooked) s.notCooked else state.entry.title, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s.mealSlot(state.slot), fontSize = 13.5.sp, color = PlanMuted)
            }
            if (onOpenRecipe != null) {
                StickerPill(
                    onClick = onOpenRecipe,
                    height = 36.dp,
                    borderWidth = 2.5.dp,
                    shadowOffset = 3.dp,
                    contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                ) {
                    Text(s.openShort, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (titleEditable) {
            PlanFieldLabel(s.dishName, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp))
            DishNameField(value = state.title, onValueChange = onTitleChange, modifier = Modifier.padding(horizontal = 18.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanFieldLabel("${s.daysLabel} · ${state.days.size}")
            if (state.isLoadingDays) {
                CircularProgressIndicator(color = CookncoGreenDark, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
            }
        }
        MonthCalendar(
            initialMonth = initialMonth,
            today = today,
            isSelected = { it in state.days },
            onDayClick = { if (!state.isSaving) onToggleDate(it) },
            onMonthChange = { month ->
                shownMonth = monthOf(month)
                onMonthChange(month)
            },
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp),
        )

        PlanFieldLabel(s.mealLabel, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp))
        MealChips(selected = state.slot, onSelect = onSlotChange, modifier = Modifier.padding(horizontal = 18.dp))

        // Each day its own count: a dinner for two on Monday can be for six on Saturday
        // Only the month on screen, so the list stays beside the calendar it belongs to
        val monthDays = state.days.filter { monthOf(it) == shownMonth }
        // Nothing was cooked, so there is nobody to count
        if (monthDays.isNotEmpty() && !state.entry.notCooked) {
            PlanFieldLabel(s.servingsLabel, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp))
            monthDays.sorted().forEach { date ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        s.fullDate(date),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CookncoNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ServingsStepper(
                        servings = state.servingsOn(date),
                        onChange = { onServingsChange(date, it) },
                        buttonSize = 36.dp,
                        fontSize = 18.sp,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(start = 12.dp, top = 22.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !state.isSaving, onClick = onRemove)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = CookncoOrangeDark, modifier = Modifier.size(20.dp))
            Text(s.removeFromPlan, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CookncoOrangeDark)
        }
        if (state.saveFailed) SaveFailed()
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

/**
 * The frame every meal plan sheet shares: a panel rising from the bottom edge over a dark
 * scrim, with its handle, its content — which scrolls when it outgrows the screen — and a
 * footer button set off by a rule. [imePadding] lifts it over the keyboard.
 */
@Composable
private fun PlanBottomSheet(
    onDismissRequest: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                .sheetScrim(onDismissRequest)
                .imePadding(),
        ) {
            val panelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            // The navy shows above the panel and nowhere else: a border on the top edge only
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .swallowTaps()
                    .clip(panelShape)
                    .background(CookncoNavy)
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(CookncoBackground),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(CookncoNavy))
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    content = content,
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(CookncoNavy))
                Box(modifier = Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 22.dp)) {
                    footer()
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, fontSize = 13.5.sp, color = PlanMuted)
    }
}

@Composable
private fun SheetFooterButton(label: String, onClick: () -> Unit, highlighted: Boolean = false, busy: Boolean = false) {
    StickerCard(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        fillColor = if (highlighted) CookncoOrange else CookncoBackground,
        shadowOffset = 4.dp,
        onClick = onClick,
    ) {
        if (busy) {
            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp, modifier = Modifier.size(24.dp).align(Alignment.Center))
        } else {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    PlanFieldLabel(text, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun ResultRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    sub: String? = null,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 12.5.sp, lineHeight = 16.sp, color = PlanMuted)
        }
    }
}

@Composable
private fun ResultIcon(icon: ImageVector, background: Color) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.size(40.dp).clip(shape).background(background).border(2.dp, CookncoNavy, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(20.dp))
    }
}

/** The name of a dish planned as words, set in the search field's white box. */
@Composable
private fun DishNameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .stickerShadow(shape, CookncoNavy, 4.dp, 4.dp)
            .clip(shape)
            .background(CookncoWhite)
            .border(3.dp, CookncoNavy, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = CookncoNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(CookncoOrange),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What opens the calendar. Once the calendar has picked a day past the chips, it is the only
 * thing showing that day — so it then names it, filled as a chip would be.
 */
@Composable
private fun AnotherDayLink(date: LocalDate?, onClick: () -> Unit) {
    val s = strings()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .then(if (date != null) Modifier.background(CookncoOrange).border(2.dp, CookncoNavy, shape) else Modifier)
            .clickable(onClickLabel = s.pickADate, onClick = onClick)
            .padding(horizontal = if (date != null) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val color = if (date != null) CookncoNavy else CookncoGreenDark
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(date?.let { s.fullDate(it) } ?: s.anotherDay, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** The days a dish can be moved to at a tap: weekday small, date large, the chosen one filled. */
@Composable
private fun DayChips(days: List<LocalDate>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val s = strings()
    // One chip of context to the left of the chosen day, so it is never the first thing cut off
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (days.indexOf(selected) - 1).coerceAtLeast(0))
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(days, key = { it.toString() }) { day ->
            val isSelected = day == selected
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .size(width = 46.dp, height = 56.dp)
                    .then(if (isSelected) Modifier.stickerShadow(shape, CookncoNavy, 3.dp, 3.dp) else Modifier)
                    .clip(shape)
                    .background(if (isSelected) CookncoOrange else CookncoBackground)
                    .border(2.5.dp, CookncoNavy, shape)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab, onClickLabel = s.fullDate(day)) { onSelect(day) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    s.weekday(day.dayOfWeek).take(3).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    color = if (isSelected) CookncoNavy else PlanMuted,
                )
                Text(day.day.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
            }
        }
    }
}

/**
 * Two sticker buttons either side of the count, which may also be unsaid: below one it reads
 * "not set" rather than zero, because a dish nobody said the size of is not a dish for nobody.
 */
@Composable
private fun ServingsStepper(
    servings: Int?,
    onChange: (Int?) -> Unit,
    buttonSize: Dp = 44.dp,
    fontSize: TextUnit = 22.sp,
) {
    val s = strings()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StickerIconButton(
            onClick = { if (servings != null) onChange(if (servings <= 1) null else servings - 1) },
            size = buttonSize,
            shape = RoundedCornerShape(13.dp),
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = s.decrease, tint = if (servings != null) CookncoNavy else CookncoNavy.copy(alpha = 0.3f))
        }
        Text(
            text = servings?.toString() ?: s.servingsNotSet,
            fontSize = if (servings != null) fontSize else 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 34.dp),
        )
        StickerIconButton(
            onClick = { onChange(((servings ?: 0) + 1).coerceAtMost(MAX_SERVINGS)) },
            size = buttonSize,
            shape = RoundedCornerShape(13.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = s.increase, tint = CookncoNavy)
        }
    }
}

@Composable
private fun SaveFailed() {
    Text(
        text = strings().mealPlanSaveFailed,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** The backend's `MEAL_PLAN_MAX_SERVINGS`: past it the save is refused, so the stepper stops there. */
private const val MAX_SERVINGS = 999
