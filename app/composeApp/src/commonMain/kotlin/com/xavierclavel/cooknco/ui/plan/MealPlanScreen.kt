package com.xavierclavel.cooknco.ui.plan

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xavierclavel.cooknco.data.MealSlot
import com.xavierclavel.cooknco.data.PlannedDay
import com.xavierclavel.cooknco.data.daysFrom
import com.xavierclavel.cooknco.data.planWeek
import com.xavierclavel.cooknco.data.weekStartOf
import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import com.xavierclavel.cooknco.network.dto.MealPlanRecipe
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoBackground
import com.xavierclavel.cooknco.ui.theme.CookncoGold
import com.xavierclavel.cooknco.ui.theme.CookncoGoldDark
import com.xavierclavel.cooknco.ui.theme.CookncoGoldLight
import com.xavierclavel.cooknco.ui.theme.CookncoGreen
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoGreenLight
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoTheme
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerDashedButton
import com.xavierclavel.cooknco.ui.theme.dashedBorder
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.stickerShadow
import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * The meal plan tab: a week at a time — a strip of its seven days, then a card per day with
 * what is planned for each meal — and the ways to add to it.
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
        MealPlanLocked(today = remember { currentDay() }, modifier = modifier)
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
        onPickDate = viewModel::showWeekOf,
        onRetry = viewModel::load,
        onAdd = viewModel::openAdd,
        onPlanMeal = viewModel::openAddMeal,
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
            today = uiState.today,
            onQueryChange = viewModel::setQuery,
            onSlotChange = viewModel::setAddSlot,
            onToggleDate = viewModel::toggleAddDate,
            onMonthChange = viewModel::loadAddMonth,
            onClearDates = viewModel::clearAddDates,
            onAddTyped = viewModel::addTypedDish,
            onAddSuggestion = viewModel::addSuggestion,
            onAddRecipe = viewModel::addRecipe,
            onAddNotCooked = viewModel::addNotCooked,
            onDismissRequest = viewModel::closeAdd,
        )
    }

    uiState.editing?.let { editing ->
        DishDaysSheet(
            state = editing,
            today = uiState.today,
            onToggleDate = viewModel::toggleEntryDate,
            onMonthChange = viewModel::loadEntryMonth,
            onSlotChange = viewModel::setEntrySlot,
            onServingsChange = viewModel::setEntryServings,
            onTitleChange = viewModel::setEntryTitle,
            onSave = viewModel::saveEntry,
            onRemove = viewModel::removeEntry,
            onOpenRecipe = editing.entry.recipe?.let { recipe -> { viewModel.closeEntry(); onRecipeClick(recipe.id) } },
            onDismissRequest = viewModel::closeEntry,
        )
    }
}

@Composable
private fun MealPlanContent(
    uiState: MealPlanUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onAdd: (PlannedDay, MealSlot) -> Unit,
    onPlanMeal: () -> Unit,
    onDish: (MealPlanEntry) -> Unit,
    onDishMenu: (MealPlanEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    var pickingDate by remember { mutableStateOf(false) }
    // The day the calendar or the strip picked, until its week's list has scrolled to it
    var scrollTo by remember { mutableStateOf<LocalDate?>(null) }

    // One page per week, this week in the middle: far enough either way that nobody reaches an end
    val baseWeek = remember { weekStartOf(uiState.today) }
    fun weekOf(page: Int) = baseWeek.plus((page - CENTER_PAGE) * 7, DateTimeUnit.DAY)
    fun pageOf(week: LocalDate) = CENTER_PAGE + baseWeek.daysUntil(week) / 7
    val pagerState = rememberPagerState(initialPage = pageOf(uiState.weekStart)) { WEEK_PAGES }
    val onShowWeek by rememberUpdatedState(onPickDate)

    // A week chosen elsewhere — the arrows, "this week", the calendar — turns the pages there: one
    // week away slides as a swipe does, anything further jumps
    LaunchedEffect(uiState.weekStart) {
        val target = pageOf(uiState.weekStart)
        if (pagerState.currentPage == target || pagerState.isScrollInProgress) return@LaunchedEffect
        if (abs(target - pagerState.currentPage) == 1) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
    }
    // A page the cook swiped to is the week on screen once it has come to rest
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onShowWeek(weekOf(it)) }
    }

    // The header names the week mostly on screen, so it changes halfway through a swipe
    val shownWeek = weekOf(pagerState.currentPage)

    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MealPlanTitle(s.mealPlan, modifier = Modifier.weight(1f))
                // One dish on several days, weeks apart if need be — the day cards' "+" plan on one
                StickerIconButton(onClick = onPlanMeal, size = 40.dp, shape = RoundedCornerShape(13.dp), shadowOffset = 3.dp) {
                    Icon(Icons.Outlined.Add, contentDescription = s.planAMeal, tint = CookncoNavy, modifier = Modifier.size(22.dp))
                }
            }
            WeekNavigation(
                label = s.weekRange(shownWeek, shownWeek.plus(6, DateTimeUnit.DAY)),
                onPrevious = onPreviousWeek,
                onNext = onNextWeek,
                onPickDate = { pickingDate = true },
            )
            WeekStrip(
                week = shownWeek,
                today = uiState.today,
                entries = uiState.entriesOf(shownWeek),
                onDay = { day -> if (shownWeek == uiState.weekStart) scrollTo = day },
            )
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val week = weekOf(page)
            val isWeekOnScreen = week == uiState.weekStart
            WeekPage(
                week = week,
                today = uiState.today,
                entries = uiState.entriesOf(week),
                loadFailed = isWeekOnScreen && uiState.loadFailed,
                scrollTo = scrollTo.takeIf { isWeekOnScreen },
                onScrolled = { scrollTo = null },
                onRetry = onRetry,
                onAdd = onAdd,
                onDish = onDish,
                onDishMenu = onDishMenu,
            )
        }
    }

    if (pickingDate) {
        MealDatePickerDialog(
            selection = uiState.weekStart..uiState.weekStart.plus(6, DateTimeUnit.DAY),
            today = uiState.today,
            onPick = { day ->
                pickingDate = false
                onPickDate(day)
                scrollTo = day
            },
            onDismissRequest = { pickingDate = false },
            onThisWeek = {
                pickingDate = false
                onThisWeek()
                scrollTo = uiState.today
            },
        )
    }
}

/**
 * One week's days. [entries] is null while nothing is known of the week: the spinner, or the
 * apology when its load failed. A failed refresh of a week already known leaves it in place
 * rather than swapping a plan for an apology.
 */
@Composable
private fun WeekPage(
    week: LocalDate,
    today: LocalDate,
    entries: List<MealPlanEntry>?,
    loadFailed: Boolean,
    scrollTo: LocalDate?,
    onScrolled: () -> Unit,
    onRetry: () -> Unit,
    onAdd: (PlannedDay, MealSlot) -> Unit,
    onDish: (MealPlanEntry) -> Unit,
    onDishMenu: (MealPlanEntry) -> Unit,
) {
    val s = strings()
    // Each week starts at its Monday
    val listState = rememberSaveable(week, saver = LazyListState.Saver) { LazyListState() }
    val days = remember(week, entries) { entries?.let { planWeek(week, it) } }
    // Every card starts closed; a tap opens it on all its meals
    var expanded by remember(week) { mutableStateOf(emptySet<LocalDate>()) }
    LaunchedEffect(scrollTo, days != null) {
        val day = scrollTo ?: return@LaunchedEffect
        val index = days?.indexOfFirst { it.date == day } ?: return@LaunchedEffect
        // A day picked from the strip or the calendar is one the cook wants to see whole
        expanded = expanded + day
        if (index >= 0) listState.animateScrollToItem(index)
        onScrolled()
    }

    when {
        days != null -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(days, key = { it.date.toString() }) { day ->
                DayCard(
                    day = day,
                    isToday = day.date == today,
                    expanded = day.date in expanded,
                    onToggle = { expanded = if (day.date in expanded) expanded - day.date else expanded + day.date },
                    onAddTo = { slot -> onAdd(day, slot) },
                    onDish = onDish,
                    onDishMenu = onDishMenu,
                )
            }
        }

        loadFailed -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(s.mealPlanLoadFailed, color = CookncoNavy, fontWeight = FontWeight.SemiBold)
            StickerPill(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text(s.retry, fontWeight = FontWeight.Bold)
            }
        }

        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CookncoNavy, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun MealPlanTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
        color = CookncoNavy,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The arrows either side, and the week's dates between them, which open the calendar. */
@Composable
private fun WeekNavigation(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
) {
    val s = strings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StickerIconButton(onClick = onPrevious, size = 36.dp, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = s.previousWeek, tint = CookncoNavy)
        }
        // The week's dates are what opens the calendar: the arrows go one week at a time,
        // and this is the way to a dinner two months out without tapping eight times
        Row(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = s.pickADate, onClick = onPickDate),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CookncoNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(17.dp))
        }
        StickerIconButton(onClick = onNext, size = 36.dp, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = s.nextWeek, tint = CookncoNavy)
        }
    }
}

/**
 * The week at a glance: its seven days, today ringed, and under each two dots — lunch, then
 * dinner — filled when that meal has something, so a gap in the main meals shows from across
 * the room. A tap brings that day's card up, opened.
 *
 * @param entries null while nothing is known of the week, which draws no dots rather than empty ones.
 * @param onDay null draws the strip as a picture only, as the locked tab does.
 */
@Composable
private fun WeekStrip(
    week: LocalDate,
    today: LocalDate,
    entries: List<MealPlanEntry>?,
    onDay: ((LocalDate) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val days = remember(week, entries) { planWeek(week, entries.orEmpty()) }
    StickerCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadowOffset = 5.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            days.forEach { day ->
                val isToday = day.date == today
                val mains = listOf(MealSlot.LUNCH, MealSlot.DINNER).map { slot -> day.meals.any { it.slot == slot } }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (onDay != null) Modifier.clickable(onClickLabel = s.fullDate(day.date)) { onDay(day.date) } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = s.fullDate(day.date) }
                        .padding(top = 6.dp, bottom = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        s.weekday(day.date.dayOfWeek).take(3).uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = if (isToday) CookncoNavy else PlanMuted,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .then(if (isToday) Modifier.background(CookncoOrange).border(2.dp, CookncoNavy, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(day.date.day.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, modifier = Modifier.clearAndSetSemantics {})
                    }
                    if (onDay != null) {
                        Row(modifier = Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (entries != null) {
                                mains.forEach { planned ->
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (planned) PlanMuted else PlanMuted.copy(alpha = 0.22f)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One day: its date, and its meals. Closed, it shows only the meals with something in them, so
 * an unplanned day is one line rather than four empty rows. A tap opens it on all four, an
 * empty one as a dashed "+ Lunch" that plans a dish for it — the cookbooks' way of saying
 * "this is where a new one goes".
 */
@Composable
private fun DayCard(
    day: PlannedDay,
    isToday: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddTo: (MealSlot) -> Unit,
    onDish: (MealPlanEntry) -> Unit,
    onDishMenu: (MealPlanEntry) -> Unit,
) {
    val s = strings()
    StickerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        fillColor = if (isToday) CookncoGoldLight else CookncoBackground,
        shadowOffset = 5.dp,
        onClick = onToggle,
    ) {
        Column(modifier = Modifier.animateContentSize().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The strip above already says the month
                Text(
                    text = "${s.weekday(day.date.dayOfWeek)} ${day.date.day}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CookncoNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = s.fullDate(day.date) },
                )
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(CookncoOrange)
                            .border(2.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                    ) {
                        Text(s.today, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                    }
                }
                Box(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = CookncoNavy,
                    modifier = Modifier.padding(end = 6.dp).size(24.dp),
                )
            }
            if (day.meals.isEmpty() && !expanded) {
                Text(
                    text = s.nothingPlanned,
                    fontSize = 13.sp,
                    color = PlanMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            MealSlot.entries.forEach { slot ->
                val meal = day.meals.firstOrNull { it.slot == slot }
                when {
                    meal != null -> {
                        Text(
                            text = s.mealSlot(meal.slot).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = CookncoGreenDark,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        meal.dishes.forEach { dish ->
                            DishRow(dish = dish, onClick = { onDish(dish) }, onMenu = { onDishMenu(dish) })
                        }
                    }
                    expanded -> StickerDashedButton(
                        text = s.mealSlot(slot),
                        onClick = { onAddTo(slot) },
                        shape = RoundedCornerShape(14.dp),
                        height = 46.dp,
                        modifier = Modifier.padding(top = 10.dp, end = 4.dp),
                    )
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
        DishThumbnail(recipe = dish.recipe, size = 44.dp, cornerRadius = 12.dp, hasCooked = dish.hasCooked)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (dish.hasCooked) dish.title else s.notCooked,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CookncoNavy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp,
            )
            dish.servings?.let {
                Text(s.servingsCount(it), fontSize = 12.5.sp, lineHeight = 16.sp, color = PlanMuted)
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

/**
 * The tab without premium: this week's strip, faded, and a card saying what the tab does and
 * that premium opens it. It offers no way to subscribe, because there is none yet.
 */
@Composable
private fun MealPlanLocked(today: LocalDate, modifier: Modifier = Modifier) {
    val s = strings()
    Column(modifier = modifier.fillMaxSize().background(CookncoGreen)) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MealPlanTitle(s.mealPlan)
            WeekStrip(
                week = weekStartOf(today),
                today = today,
                entries = null,
                onDay = null,
                modifier = Modifier.alpha(0.55f).clearAndSetSemantics {},
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 28.dp, end = 28.dp, bottom = 80.dp),
            contentAlignment = Alignment.Center,
        ) {
            StickerCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), shadowOffset = 5.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StickerCard(
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        fillColor = CookncoGold,
                        shadowOffset = 4.dp,
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(28.dp).align(Alignment.Center))
                    }
                    Text(s.mealPlanLockedTitle, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, color = CookncoNavy, textAlign = TextAlign.Center)
                    Text(s.mealPlanLockedMessage, fontSize = 14.5.sp, lineHeight = 21.sp, color = PlanMuted, textAlign = TextAlign.Center)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).alpha(0.35f).clearAndSetSemantics {},
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(40.dp).dashedBorder(RoundedCornerShape(12.dp), CookncoNavy, strokeWidth = 2.dp))
                        Box(Modifier.fillMaxWidth(0.7f).height(40.dp).dashedBorder(RoundedCornerShape(12.dp), CookncoNavy, strokeWidth = 2.dp))
                    }
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(CookncoGoldLight)
                            .border(2.5.dp, CookncoNavy, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.StarBorder, contentDescription = null, tint = CookncoNavy, modifier = Modifier.size(18.dp))
                        Text(s.mealPlanPremiumBadge, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CookncoNavy)
                    }
                }
            }
        }
    }
}

/** Twenty years of weeks, ten either side of this one. */
private const val WEEK_PAGES = 1041
private const val CENTER_PAGE = WEEK_PAGES / 2

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
            onPreviousWeek = {}, onNextWeek = {}, onThisWeek = {}, onPickDate = {}, onRetry = {},
            onAdd = { _, _ -> }, onPlanMeal = {}, onDish = {}, onDishMenu = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MealPlanLockedPreview() {
    CookncoTheme { MealPlanLocked(today = previewMonday) }
}
