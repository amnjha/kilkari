package com.kilkari.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kilkari.domain.Fmt
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Date and time entry throughout the app goes through the platform pickers rather than typed
 * text: no format to get wrong, no impossible dates, and the calendar gives useful context
 * (which day of the week a vaccine fell on, how long ago a milestone was).
 */

/** A field that opens the Material date picker. */
@Composable
fun KDateField(
    label: String,
    value: LocalDate?,
    placeholder: String = "Pick a date",
    sheetStyle: Boolean = true,
    divider: Boolean = true,
    selectableFrom: LocalDate? = null,
    selectableTo: LocalDate? = null,
    format: (LocalDate) -> String = Fmt::dateFull,
    onPick: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    PickerRow(
        label = label,
        text = value?.let(format) ?: placeholder,
        placeholder = value == null,
        sheetStyle = sheetStyle,
        divider = divider,
    ) { open = true }

    if (open) {
        KDatePickerDialog(
            initial = value,
            selectableFrom = selectableFrom,
            selectableTo = selectableTo,
            onDismiss = { open = false },
        ) {
            onPick(it)
            open = false
        }
    }
}

/** A field that opens the Material time picker. [value] is minutes past midnight. */
@Composable
fun KTimeField(
    label: String,
    value: Int?,
    placeholder: String = "Pick a time",
    sheetStyle: Boolean = true,
    divider: Boolean = true,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    PickerRow(
        label = label,
        text = value?.let(::clockLabel) ?: placeholder,
        placeholder = value == null,
        sheetStyle = sheetStyle,
        divider = divider,
    ) { open = true }

    if (open) {
        KTimePickerDialog(value ?: 9 * 60, onDismiss = { open = false }) {
            onPick(it)
            open = false
        }
    }
}

/**
 * The moment an entry is filed against. Every action defaults to now — still the common case —
 * but the date and time are both open, so something remembered at bedtime can be recorded
 * against the afternoon it actually happened.
 */
@Stable
class MomentState(initial: LocalDateTime) {
    var date: LocalDate by mutableStateOf(initial.toLocalDate())
    var minuteOfDay: Int by mutableIntStateOf(initial.hour * 60 + initial.minute)

    val value: LocalDateTime get() = date.atTime(minuteOfDay / 60, minuteOfDay % 60)
}

/**
 * [key] re-seeds the state — pass the entry a sheet is editing so reopening it on a different
 * row starts from that row's date and time rather than the previous one's.
 */
@Composable
fun rememberMoment(initial: LocalDateTime = LocalDateTime.now(), key: Any? = null): MomentState =
    remember(key) { MomentState(initial) }

/**
 * The date/time pair every logging sheet carries. Dates read back as "Today, 20 Aug" so a
 * back-dated entry is unmistakable, and future dates are closed off by default.
 */
@Composable
fun MomentFields(
    state: MomentState,
    dateLabel: String = "Date",
    timeLabel: String = "Time",
    earliest: LocalDate? = null,
    latest: LocalDate? = LocalDate.now(),
) {
    KDateField(
        label = dateLabel,
        value = state.date,
        selectableFrom = earliest,
        selectableTo = latest,
        format = { Fmt.relativeDate(it) },
    ) { state.date = it }
    KTimeField(timeLabel, state.minuteOfDay) { state.minuteOfDay = it }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KDatePickerDialog(
    initial: LocalDate?,
    selectableFrom: LocalDate? = null,
    selectableTo: LocalDate? = null,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).toEpochMillis(),
        selectableDates = remember(selectableFrom, selectableTo) {
            object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    if (selectableFrom != null && d.isBefore(selectableFrom)) return false
                    if (selectableTo != null && d.isAfter(selectableTo)) return false
                    return true
                }
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = KC.Surface),
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text("Select", color = KC.Coral, fontFamily = Sans, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = KC.Muted, fontFamily = Sans)
            }
        },
    ) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = KC.Surface))
    }
}

/** Material 3 ships a time picker but no dialog for it, so this wraps one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KTimePickerDialog(
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = false,
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(KC.Surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Pick a time",
                modifier = Modifier.fillMaxWidth(),
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KC.Ink,
            )
            TimePicker(state = state, colors = TimePickerDefaults.colors(containerColor = KC.Surface))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = KC.Muted, fontFamily = Sans) }
                TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                    Text("Set", color = KC.Coral, fontFamily = Sans, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Shared presentation for both fields, matching whichever container they sit in. */
@Composable
private fun PickerRow(
    label: String,
    text: String,
    placeholder: Boolean,
    sheetStyle: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val row: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .let {
                    if (sheetStyle) it.clip(RoundedCornerShape(14.dp)).background(KC.Screen) else it
                }
                .clickable(onClick = onClick)
                // In a form the row sits beside ValueField and must match it; in a sheet it
                // keeps the tighter rhythm the surrounding controls already use.
                .let { if (sheetStyle) it else it.heightIn(min = FORM_ROW_HEIGHT) }
                .padding(
                    horizontal = if (sheetStyle) 14.dp else 16.dp,
                    vertical = if (sheetStyle) 13.dp else 12.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontFamily = Sans, fontSize = if (sheetStyle) 13.sp else 14.sp, color = KC.Muted,
            )
            Text(
                text,
                fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = if (sheetStyle) 14.sp else 16.sp,
                color = if (placeholder) KC.Faint else KC.Coral,
            )
        }
    }
    if (sheetStyle) {
        row()
    } else {
        Column {
            row()
            if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
        }
    }
}

internal fun clockLabel(minuteOfDay: Int): String {
    val h24 = minuteOfDay / 60
    val m = (minuteOfDay % 60).toString().padStart(2, '0')
    val h = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return "$h:$m ${if (h24 < 12) "am" else "pm"}"
}

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
