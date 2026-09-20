package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.domain.DaySummary
import com.kilkari.domain.Fmt
import com.kilkari.domain.LogKind
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KDatePickerDialog
import com.kilkari.ui.components.KFab
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.sheets.DiaperSheet
import com.kilkari.ui.sheets.FeedSheet
import com.kilkari.ui.sheets.MedicineSheet
import com.kilkari.ui.sheets.SheetHint
import com.kilkari.ui.sheets.SheetTitle
import com.kilkari.ui.sheets.SleepSheet
import com.kilkari.ui.theme.headerWash
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

private val WEEKDAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** What can be added to a day from here. Growth and teeth keep their own screens. */
private val ADDABLE = listOf(LogKind.FEED, LogKind.SLEEP, LogKind.DIAPER, LogKind.MEDICINE)

/**
 * Any day's entries, not just today's.
 *
 * The week strip covers the days people most often go back to; the month heading opens a date
 * picker for anything further. A day before the child was born, or one that has not happened
 * yet, cannot be chosen.
 */
@Composable
fun LogDayScreen(vm: KilkariViewModel, go: NavActions) {
    val day by vm.logDay.collectAsStateWithLifecycle()
    val entries by vm.logDayEntries.collectAsStateWithLifecycle()
    val summary by vm.logDaySummary.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()
    val medications by vm.medications.collectAsStateWithLifecycle()
    val growth by vm.growth.collectAsStateWithLifecycle()
    val openSleep by vm.openSleep.collectAsStateWithLifecycle()
    val metric by vm.metricUnits.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val earliest = baby?.dob
    var picking by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LogEntryEntity?>(null) }
    var adding by remember { mutableStateOf<LogKind?>(null) }

    fun select(target: LocalDate) {
        val bounded = earliest?.let { maxOf(it, target) } ?: target
        vm.showLogDay(minOf(bounded, today))
    }

    Box(Modifier.fillMaxSize().headerWash()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Daily log", go::back)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    // Clear of the add button, so the last entry can still be reached.
                    .padding(top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WeekStrip(
                    selected = day,
                    today = today,
                    earliest = earliest,
                    onSelect = ::select,
                    onPickDate = { picking = true },
                )

                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        relativeHeading(day, today),
                        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = KC.Ink,
                    )
                    if (day == today || day == today.minusDays(1)) {
                        Text(Fmt.dayAndDate(day, today), fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
                    }
                }

                val s = summary
                if (entries.isEmpty() || s == null) {
                    KCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Nothing logged on this day",
                                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = KC.Ink,
                            )
                            Text(
                                "If something was missed, tap Add to put it on this day.",
                                fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
                            )
                        }
                    }
                } else {
                    DayTotals(s)

                    SectionLabel("Entries", Modifier.padding(top = 4.dp))
                    KCard {
                        entries.forEachIndexed { i, entry ->
                            LogEntryRow(entry, Fmt.time(entry.startAt)) {
                                if (LogKind.of(entry.kind) == LogKind.TOOTH) go.push(Routes.TEETH) else editing = entry
                            }
                            if (i != entries.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }

        // Bottom right, where a thumb already is, rather than under however long the day ran.
        KFab("add", "Add") { choosing = true }

        if (picking) {
            KDatePickerDialog(
                initial = day,
                selectableFrom = earliest,
                selectableTo = today,
                onDismiss = { picking = false },
            ) { picked ->
                select(picked)
                picking = false
            }
        }

        KSheet(choosing, onDismiss = { choosing = false }) {
            SheetTitle(if (day == today) "Add to today" else "Add to ${Fmt.dayAndDate(day, today)}")
            SheetHint("The time can be changed on the next step.")
            ADDABLE.forEach { kind ->
                val spec = tileSpec(kind)
                AddRow(spec.icon, spec.title, spec.fg, spec.bg) {
                    choosing = false
                    adding = kind
                }
            }
        }

        val edit = editing
        KSheet(edit != null, onDismiss = { editing = null }) {
            if (edit != null) EditEntrySheet(vm, edit, baby?.dob, medications, growth, metric) { editing = null }
        }

        // New entries start on the chosen day at the current clock time. On today that is
        // simply now; on an earlier day it is a starting point the sheet lets the parent move.
        val startAt = if (day == today) LocalDateTime.now()
        else day.atTime(LocalTime.now().truncatedTo(ChronoUnit.MINUTES))

        KSheet(adding != null, onDismiss = { adding = null }) {
            when (adding) {
                LogKind.FEED -> FeedSheet(initialAt = startAt) { type, side, amount, at ->
                    vm.logFeed(type, side, amount, at); adding = null
                }
                LogKind.SLEEP -> SleepSheet(
                    // Only today can have a nap still running to end.
                    asleepSince = if (day == today) openSleep?.startAt else null,
                    initialAt = startAt,
                    onStart = { place, from, to -> vm.logSleepStart(place, from, to); adding = null },
                    onEnd = { at -> vm.logSleepEnd(at); adding = null },
                )
                LogKind.DIAPER -> DiaperSheet(initialAt = startAt) { kind, at ->
                    vm.logDiaper(kind, at); adding = null
                }
                LogKind.MEDICINE -> MedicineSheet(
                    medications = medications.filter { it.active },
                    initialAt = startAt,
                    onLog = { med, at -> vm.logMedicine(med, at); adding = null },
                    onManage = { adding = null; go.push(Routes.MEDS) },
                )
                else -> Unit
            }
        }
    }
}

/**
 * The seven days of the selected day's week, with a step either side and the month above.
 *
 * Each day is a full-height target rather than a small dot to aim at. Days that cannot be
 * chosen lose their tile but keep their place, so the week keeps its shape.
 */
@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    earliest: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
) {
    val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = (0..6).map { monday.plusDays(it.toLong()) }
    fun choosable(d: LocalDate) = !d.isAfter(today) && (earliest == null || !d.isBefore(earliest))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onPickDate)
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .semantics { contentDescription = "Choose a date" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    Fmt.monthYear(YearMonth.from(selected), today),
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = KC.Ink,
                )
                Icon(KIcons["expand_more"], null, tint = KC.Muted, modifier = Modifier.size(20.dp))
            }
            Row {
                StepButton("chevron_left", "Previous week", enabled = choosable(monday.minusDays(1))) {
                    onSelect(selected.minusWeeks(1))
                }
                StepButton("chevron_right", "Next week", enabled = !monday.plusWeeks(1).isAfter(today)) {
                    onSelect(selected.plusWeeks(1))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEachIndexed { i, d ->
                val on = d == selected
                val can = choosable(d)
                Column(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                on -> KC.Coral
                                can -> KC.Surface
                                else -> Color.Transparent
                            }
                        )
                        .let { if (can) it.clickable { onSelect(d) } else it }
                        .semantics {
                            contentDescription = Fmt.dayAndDate(d, today)
                            this.selected = on
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        WEEKDAY_LETTERS[i], fontFamily = Sans, fontSize = 11.sp,
                        color = when {
                            on -> Color.White.copy(alpha = 0.85f)
                            can -> KC.Muted
                            else -> KC.Faint
                        },
                    )
                    Text(
                        "${d.dayOfMonth}",
                        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        color = when {
                            on -> Color.White
                            can -> KC.Ink
                            else -> KC.Faint
                        },
                    )
                    // Marks today while another day is open, so it is easy to find the way back.
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (d == today && !on) KC.Coral else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(KIcons[icon], null, tint = if (enabled) KC.CoralDeep else KC.Faint, modifier = Modifier.size(24.dp))
    }
}

/** The day in three numbers, with what the feeds added up to beneath when there is anything. */
@Composable
private fun DayTotals(s: DaySummary) {
    KCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Total("Feeds", listOf("${s.feeds}" to null), Modifier.weight(1f))
                Total("Asleep", durationParts(s.sleepMinutes), Modifier.weight(1.3f))
                Total("Diapers", listOf("${s.diapers}" to null), Modifier.weight(1f))
            }
            val intake = listOfNotNull(
                s.bottleMl.takeIf { it > 0 }?.let { "$it ml from the bottle" },
                s.breastMinutes.takeIf { it > 0 }?.let { "${it} min breastfeeding" },
            )
            if (intake.isNotEmpty()) {
                Text(
                    intake.joinToString(" · "),
                    fontFamily = Sans, fontSize = 13.sp, color = KC.MutedStrong,
                )
            }
        }
    }
}

/** A caption over a figure whose units are set smaller than its numbers: "12 h 21 m". */
@Composable
private fun Total(caption: String, parts: List<Pair<String, String?>>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(caption, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            parts.forEach { (number, unit) ->
                Text(number, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = KC.Ink)
                if (unit != null) {
                    Text(
                        unit, modifier = Modifier.padding(bottom = 4.dp, end = 3.dp),
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    )
                }
            }
        }
    }
}

private fun durationParts(minutes: Double): List<Pair<String, String?>> {
    val total = minutes.roundToInt()
    val h = total / 60
    val m = total % 60
    return when {
        total == 0 -> listOf("0" to null)
        h == 0 -> listOf("$m" to "m")
        m == 0 -> listOf("$h" to "h")
        else -> listOf("$h" to "h", "$m" to "m")
    }
}

@Composable
private fun AddRow(icon: String, title: String, fg: Color, bg: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KC.Screen)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, fg, bg, size = 38, corner = 12, iconSize = 20)
        Text(
            title, modifier = Modifier.weight(1f),
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = KC.Ink,
        )
        Icon(KIcons["chevron_right"], null, tint = KC.Faint, modifier = Modifier.size(18.dp))
    }
}

/** "Today", "Yesterday", or the full day. */
private fun relativeHeading(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> Fmt.dayAndDate(day, today)
}
