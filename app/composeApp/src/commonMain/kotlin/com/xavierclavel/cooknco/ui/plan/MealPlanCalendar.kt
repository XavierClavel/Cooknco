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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xavierclavel.cooknco.data.monthGrid
import com.xavierclavel.cooknco.data.monthOf
import com.xavierclavel.cooknco.ui.i18n.strings
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.dashedBorder
import com.xavierclavel.cooknco.ui.theme.sheetScrim
import com.xavierclavel.cooknco.ui.theme.swallowTaps
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * A month on a white card: the "plan a meal" sheet's way to pick days, as many as wanted and
 * weeks apart, and what [MealDatePickerDialog] shows to pick one.
 *
 * The app's own rather than Material's `DatePicker`, which names the months and the weekdays
 * in the handset's language. The app speaks the account's (`AppLanguage`), so a French cook on
 * an English phone would get an English calendar over a French sheet.
 *
 * Six whole weeks every month, so the card keeps its height from one month to the next; the
 * days of the months either side are greyed, and as tappable as the others. No bounds either
 * way: what was cooked last month is as much the plan's as what will be.
 *
 * @param initialMonth any day of the month it opens on.
 */
@Composable
fun MonthCalendar(
    initialMonth: LocalDate,
    today: LocalDate,
    isSelected: (LocalDate) -> Boolean,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onMonthChange: (LocalDate) -> Unit = {},
    hasMeal: (LocalDate) -> Boolean = { false },
) {
    val s = strings()
    var month by remember { mutableStateOf(monthOf(initialMonth)) }
    // The label opens a year of months, so a month far off is a few taps rather than many arrows
    var pickingMonth by remember { mutableStateOf(false) }
    var pickerYear by remember { mutableStateOf(month.year) }
    val onMonth by rememberUpdatedState(onMonthChange)
    LaunchedEffect(month) { onMonth(month) }

    StickerCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        fillColor = CookncoWhite,
        shadowOffset = 4.dp,
    ) {
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthArrow(onClick = { if (pickingMonth) pickerYear-- else month = month.minus(1, DateTimeUnit.MONTH) }) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = if (pickingMonth) s.previousYear else s.previousMonth,
                        tint = CookncoNavy,
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = s.pickMonth) {
                                pickerYear = month.year
                                pickingMonth = !pickingMonth
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (pickingMonth) pickerYear.toString() else s.monthYear(month),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                        )
                        Icon(
                            if (pickingMonth) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                            tint = CookncoNavy,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                MonthArrow(onClick = { if (pickingMonth) pickerYear++ else month = month.plus(1, DateTimeUnit.MONTH) }) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = if (pickingMonth) s.nextYear else s.nextMonth,
                        tint = CookncoNavy,
                    )
                }
            }

            if (pickingMonth) {
                MonthPicker(
                    year = pickerYear,
                    shown = month,
                    today = today,
                    onPick = { picked ->
                        month = picked
                        pickingMonth = false
                    },
                )
                return@Column
            }

            val grid = monthGrid(month)
            Row(modifier = Modifier.fillMaxWidth()) {
                grid.take(7).forEach { day ->
                    Text(
                        text = s.weekday(day.dayOfWeek).take(1).uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = PlanMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(top = 2.dp, bottom = 4.dp).clearAndSetSemantics {},
                    )
                }
            }
            grid.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        CalendarDay(
                            day = day,
                            isInMonth = day.month == month.month,
                            isSelected = isSelected(day),
                            isToday = day == today,
                            hasMeal = hasMeal(day),
                            onClick = { onDayClick(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A year's twelve months, in place of the days: the one shown filled, the current one ringed.
 * Four rows of three, about as tall as the weekday letters and six weeks it stands in for, so
 * the card does not jump.
 */
@Composable
private fun MonthPicker(year: Int, shown: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val s = strings()
    Month.entries.chunked(3).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            row.forEach { m ->
                val isShown = shown.year == year && shown.month == m
                val isCurrent = today.year == year && today.month == m
                Box(modifier = Modifier.weight(1f).height(62.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                when {
                                    isShown -> Modifier.background(CookncoOrange).border(2.dp, CookncoNavy, RoundedCornerShape(14.dp))
                                    isCurrent -> Modifier.dashedBorder(RoundedCornerShape(14.dp), CookncoNavy, strokeWidth = 2.dp, dashLength = 4.dp, gapLength = 3.dp)
                                    else -> Modifier
                                },
                            )
                            .semantics { selected = isShown }
                            .clickable { onPick(LocalDate(year, m, 1)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = s.monthAlone(m),
                            fontSize = 14.sp,
                            fontWeight = if (isShown || isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            color = CookncoNavy,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** One day of the grid: filled when picked, a dashed ring when it is today, grey off the month. */
@Composable
private fun CalendarDay(
    day: LocalDate,
    isInMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    hasMeal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Box(modifier = modifier.height(38.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .then(
                    when {
                        isSelected -> Modifier.background(CookncoOrange).border(2.dp, CookncoNavy, CircleShape)
                        isToday -> Modifier.dashedBorder(CircleShape, CookncoNavy, strokeWidth = 2.dp, dashLength = 4.dp, gapLength = 3.dp)
                        else -> Modifier
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = s.fullDate(day)
                    selected = isSelected
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.day.toString(),
                fontSize = 14.sp,
                fontWeight = when {
                    isSelected || isToday -> FontWeight.Bold
                    isInMonth -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                },
                color = if (isInMonth || isSelected) CookncoNavy else PlanOutOfMonth,
                modifier = Modifier.clearAndSetSemantics {},
            )
            // That meal is already planned on this day
            if (hasMeal) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CookncoNavy else PlanMuted),
                )
            }
        }
    }
}

/**
 * The calendar on its own, to pick one day: the tab's way past the arrows that move it a week
 * at a time, and the dish sheet's past its chips. A tap picks and closes.
 *
 * @param selection what is chosen now, drawn filled — a dish's day in its sheet, the week on
 *   screen in the tab. The calendar opens on the month of its first day.
 * @param onThisWeek the tab's way back to the current week, offered beside "cancel" when set.
 */
@Composable
fun MealDatePickerDialog(
    selection: ClosedRange<LocalDate>,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
    onThisWeek: (() -> Unit)? = null,
) {
    val s = strings()
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.55f))
                .sheetScrim(onDismissRequest)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.fillMaxWidth().swallowTaps()) {
                MonthCalendar(
                    initialMonth = selection.start,
                    today = today,
                    isSelected = { it in selection },
                    onDayClick = onPick,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    if (onThisWeek != null) {
                        StickerPill(
                            onClick = onThisWeek,
                            height = 40.dp,
                            shadowOffset = 3.dp,
                            fillColor = CookncoOrange,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) { Text(s.thisWeek, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                    StickerPill(
                        onClick = onDismissRequest,
                        height = 40.dp,
                        shadowOffset = 3.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) { Text(s.cancel, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
