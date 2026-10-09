package com.xavierclavel.cooknco.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xavierclavel.cooknco.data.MealSlot
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.ui.components.RecipeImage
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerSegmentedControl
import com.xavierclavel.cooknco.ui.theme.sheetScrim
import com.xavierclavel.cooknco.ui.theme.swallowTaps
import kotlinx.datetime.LocalDate

/**
 * Adds a dish to one day: one of the cook's recipes, something they planned before, or
 * whatever they type.
 *
 * One field for all three, because the cook does not decide up front whether tonight is "a
 * recipe" or "words" — they start typing what they will eat. The typed words come first in the
 * list, so the shortest path is always there; the recipes they have to hand follow. A recipe
 * that is none of theirs is planned from its own page instead.
 *
 * A tap adds and closes. How many it is for is the dish's own sheet's business: a recipe
 * already says how many it makes, and asking every time would be a step most dishes do not need.
 */
@Composable
fun AddDishSheet(
    state: AddDishState,
    onQueryChange: (String) -> Unit,
    onSlotChange: (MealSlot) -> Unit,
    onAddTyped: () -> Unit,
    onAddSuggestion: (String) -> Unit,
    onAddRecipe: (RecipeOverview) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val typed = state.query.trim()

    PlanSheetScaffold(
        footerLabel = s.cancel,
        onFooter = onDismissRequest,
        onDismissRequest = onDismissRequest,
    ) {
        SheetHeader(title = s.fullDate(state.date), subtitle = s.addADish)
        StickerSegmentedControl(
            options = MealSlot.entries,
            selected = state.slot,
            onSelect = onSlotChange,
            label = s::mealSlotShort,
            segmentHeight = 38.dp,
            fontSize = 12.5.sp,
            shadowOffset = 3.dp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        PlanTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = s.searchOrTypeADish,
            leadingIcon = Icons.Outlined.Search,
            imeAction = ImeAction.Done,
            onImeAction = onAddTyped,
            focusRequester = focusRequester,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
        SheetDivider()
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            if (typed.isNotEmpty()) {
                item(key = "typed") {
                    PickRow(
                        label = s.addTypedDish(typed),
                        enabled = !state.isSaving,
                        onClick = onAddTyped,
                        emphasised = true,
                    ) { RowIcon(Icons.Outlined.Add, filled = true) }
                }
            }
            if (state.suggestions.isNotEmpty()) {
                item(key = "suggestions") { SectionLabel(s.plannedBeforeCaps) }
                items(state.suggestions, key = { "s:$it" }) { suggestion ->
                    PickRow(label = suggestion, enabled = !state.isSaving, onClick = { onAddSuggestion(suggestion) }) {
                        RowIcon(Icons.Outlined.History)
                    }
                }
            }
            item(key = "recipes") { SectionLabel(s.yourRecipesCaps) }
            when {
                state.recipes.isEmpty() && state.isSearching -> item(key = "searching") {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                    }
                }
                state.recipes.isEmpty() -> item(key = "none") {
                    Text(
                        text = s.noRecipeMatches,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CookncoGreenDark,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
                else -> items(state.recipes, key = { "r:${it.id}" }) { recipe ->
                    PickRow(label = recipe.title, enabled = !state.isSaving, onClick = { onAddRecipe(recipe) }) {
                        RecipeImage(
                            recipeId = recipe.id,
                            version = recipe.version,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(2.dp, CookncoNavy, RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
        }
        if (state.saveFailed) SaveFailed()
    }
}

/**
 * When a dish is, which meal, and for how many — for one already planned (from the tab) or
 * about to be (from a recipe's page). The two differ only in where the days start, what the
 * button says, and what else the sheet offers, so they are one sheet.
 *
 * @param titleEditable for a dish shown as words, whose name the cook wrote and may change.
 * @param days the days on offer, scrolled to [initial]'s.
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
    onOpenRecipe: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val s = strings()
    var draft by remember(initial) { mutableStateOf(initial) }
    val canSave = !isSaving && (!titleEditable || draft.title.isNotBlank())

    PlanSheetScaffold(
        footerLabel = primaryLabel,
        footerHighlighted = true,
        footerBusy = isSaving,
        onFooter = { if (canSave) onSave(draft) },
        onDismissRequest = onDismissRequest,
    ) {
        SheetHeader(title = heading, subtitle = subtitle)
        if (titleEditable) {
            FieldLabel(s.dishName)
            PlanTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                placeholder = s.dishName,
                imeAction = ImeAction.Done,
                onImeAction = {},
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 6.dp),
            )
        }
        FieldLabel(s.dayLabel)
        DayChips(days = days, selected = draft.date, onSelect = { draft = draft.copy(date = it) })
        FieldLabel(s.mealLabel)
        StickerSegmentedControl(
            options = MealSlot.entries,
            selected = draft.slot,
            onSelect = { draft = draft.copy(slot = it) },
            label = s::mealSlotShort,
            segmentHeight = 38.dp,
            fontSize = 12.5.sp,
            shadowOffset = 3.dp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.servingsLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CookncoNavy, modifier = Modifier.weight(1f))
            ServingsStepper(servings = draft.servings, onChange = { draft = draft.copy(servings = it) })
        }
        if (onOpenRecipe != null || onRemove != null) SheetDivider()
        onOpenRecipe?.let { PickRow(label = s.openRecipe, onClick = it) { RowIcon(Icons.Outlined.MenuBook) } }
        onRemove?.let {
            PickRow(label = s.removeFromPlan, enabled = !isSaving, onClick = it, destructive = true) {
                RowIcon(Icons.Outlined.Delete, destructive = true)
            }
        }
        if (saveFailed) SaveFailed()
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

/**
 * The frame both sheets share, which is the one `AddToCookbookSheet` draws: a card at the
 * bottom over a dark scrim, and a separate button under it. [imePadding] lifts it over the
 * keyboard, which both sheets can raise.
 */
@Composable
private fun PlanSheetScaffold(
    footerLabel: String,
    onFooter: () -> Unit,
    onDismissRequest: () -> Unit,
    footerHighlighted: Boolean = false,
    footerBusy: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                .sheetScrim(onDismissRequest),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .swallowTaps()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 26.dp),
            ) {
                StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), content = content)
                }
                Spacer(Modifier.height(10.dp))
                StickerCard(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    fillColor = if (footerHighlighted) CookncoOrange else CookncoBackground,
                    shadowOffset = 4.dp,
                    onClick = onFooter,
                ) {
                    if (footerBusy) {
                        CircularProgressIndicator(
                            color = if (footerHighlighted) CookncoWhite else CookncoNavy,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp).align(Alignment.Center),
                        )
                    } else {
                        Text(
                            text = footerLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (footerHighlighted) CookncoWhite else CookncoNavy,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 12.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            color = CookncoNavy,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = CookncoGreenDark,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = CookncoNavy,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = CookncoGreenDark,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasised: Boolean = false,
    destructive: Boolean = false,
    leading: @Composable () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else CookncoNavy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) { leading() }
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (emphasised) FontWeight.ExtraBold else FontWeight.Bold,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowIcon(icon: ImageVector, filled: Boolean = false, destructive: Boolean = false) {
    val tint = when {
        filled -> CookncoWhite
        destructive -> MaterialTheme.colorScheme.error
        else -> CookncoNavy
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) CookncoOrange else CookncoBackground)
            .border(2.dp, if (destructive) tint else CookncoNavy, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** The search field's look (`RecipesScreen.SearchField`), with what a sheet field needs. */
@Composable
private fun PlanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CookncoBackground)
            .border(3.dp, CookncoNavy, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(18.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = CookncoNavy.copy(alpha = 0.42f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = CookncoNavy, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(CookncoOrange),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onImeAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
    }
}

@Composable
private fun DayChips(days: List<LocalDate>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val s = strings()
    // One chip of context to the left of the chosen day, so it is never the first thing cut off
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (days.indexOf(selected) - 1).coerceAtLeast(0))
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days, key = { it.toString() }) { day ->
            val isSelected = day == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) CookncoOrange else CookncoBackground)
                    .border(2.dp, CookncoNavy, RoundedCornerShape(12.dp))
                    .clickable { onSelect(day) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = s.shortDay(day),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) CookncoWhite else CookncoNavy,
                )
            }
        }
    }
}

/**
 * The recipe page's yield pill, for a count that may also be unsaid: below one it reads
 * "not set" rather than zero, because a dish nobody said the size of is not a dish for nobody.
 */
@Composable
private fun ServingsStepper(servings: Int?, onChange: (Int?) -> Unit) {
    val s = strings()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(CookncoBackground)
            .border(3.dp, CookncoNavy, RoundedCornerShape(percent = 50))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(enabled = servings != null) { onChange(servings?.let { if (it <= 1) null else it - 1 }) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Remove,
                contentDescription = s.decrease,
                tint = if (servings != null) CookncoNavy else CookncoNavy.copy(alpha = 0.3f),
            )
        }
        Text(
            text = servings?.toString() ?: s.servingsNotSet,
            fontWeight = FontWeight.Bold,
            fontSize = if (servings != null) 15.sp else 12.5.sp,
            color = CookncoNavy,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 4.dp),
        )
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CookncoOrange)
                .clickable { onChange(((servings ?: 0) + 1).coerceAtMost(MAX_SERVINGS)) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Add, contentDescription = s.increase, tint = CookncoWhite) }
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

@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(CookncoNavy.copy(alpha = 0.12f)),
    )
}

/** The backend's `MEAL_PLAN_MAX_SERVINGS`: past it the save is refused, so the stepper stops there. */
private const val MAX_SERVINGS = 999
