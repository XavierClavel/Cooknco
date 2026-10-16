package com.xavierclavel.cooknco.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.xavierclavel.cooknco.ui.theme.CookncoGreenDark
import com.xavierclavel.cooknco.ui.theme.CookncoNavy
import com.xavierclavel.cooknco.ui.theme.CookncoOrange
import com.xavierclavel.cooknco.ui.theme.CookncoWhite
import com.xavierclavel.cooknco.ui.theme.StickerCard
import com.xavierclavel.cooknco.ui.theme.StickerIconButton
import com.xavierclavel.cooknco.ui.theme.StickerPill
import com.xavierclavel.cooknco.ui.theme.sheetScrim
import com.xavierclavel.cooknco.ui.theme.swallowTaps
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * A month at a time, to pick any day: the plan's way past the arrows that move it a week at a
 * time, and past the two weeks of chips in a dish's sheet. A tap picks and closes.
 *
 * The app's own rather than Material's `DatePicker`, which names the months and the weekdays
 * in the handset's language. The app speaks the account's (`AppLanguage`), so a French cook on
 * an English phone would get an English calendar over a French sheet.
 *
 * No bounds either way: what was cooked last month is as much the plan's as what will be.
 *
 * @param selection what is chosen now, drawn filled — a dish's day in its sheet, the week on
 *   screen in the tab. The calendar opens on the month of its first day.
 */
@Composable
fun MealDatePickerDialog(
    selection: ClosedRange<LocalDate>,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val s = strings()
    var month by remember { mutableStateOf(monthOf(selection.start)) }

    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CookncoNavy.copy(alpha = 0.6f))
                .sheetScrim(onDismissRequest)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            StickerCard(
                modifier = Modifier.fillMaxWidth().swallowTaps(),
                shape = RoundedCornerShape(22.dp),
                shadowOffset = 8.dp,
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = s.monthYear(month),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CookncoNavy,
                            modifier = Modifier.weight(1f),
                        )
                        StickerIconButton(
                            onClick = { month = month.minus(1, DateTimeUnit.MONTH) },
                            size = 36.dp,
                            shape = RoundedCornerShape(12.dp),
                        ) { Icon(Icons.Outlined.ChevronLeft, contentDescription = s.previousMonth, tint = CookncoNavy) }
                        StickerIconButton(
                            onClick = { month = month.plus(1, DateTimeUnit.MONTH) },
                            size = 36.dp,
                            shape = RoundedCornerShape(12.dp),
                        ) { Icon(Icons.Outlined.ChevronRight, contentDescription = s.nextMonth, tint = CookncoNavy) }
                    }

                    val grid = monthGrid(month)
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        grid.take(7).forEach { day ->
                            Text(
                                text = s.weekday(day.dayOfWeek).take(1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CookncoGreenDark,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).clearAndSetSemantics {},
                            )
                        }
                    }
                    grid.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                                    // A day of the month either side is a blank, not a dimmed
                                    // number: two "1"s in one grid is one too many to read.
                                    if (day.month == month.month) {
                                        CalendarDay(
                                            day = day,
                                            isSelected = day in selection,
                                            isToday = day == today,
                                            onClick = { onPick(day) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                        StickerPill(
                            onClick = onDismissRequest,
                            height = 38.dp,
                            shadowOffset = 3.dp,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) { Text(s.cancel, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/** One day of the grid: filled when chosen, ringed when it is today, plain otherwise. */
@Composable
private fun CalendarDay(day: LocalDate, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val s = strings()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (isSelected) CookncoOrange else Color.Transparent)
            .then(if (isSelected || isToday) Modifier.border(2.dp, CookncoNavy, shape) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = s.fullDate(day)
                selected = isSelected
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.day.toString(),
            fontSize = 15.sp,
            fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isSelected) CookncoWhite else CookncoNavy,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
