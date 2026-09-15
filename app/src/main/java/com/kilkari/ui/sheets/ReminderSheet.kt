package com.kilkari.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.data.db.ReminderEntity
import com.kilkari.domain.RepeatRule
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Create or edit a reminder of the parent's own: what to be reminded about, when, and
 * optionally how often it comes back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ReminderSheet(
    existing: ReminderEntity?,
    onSave: (
        key: String?,
        title: String,
        subtitle: String,
        minuteOfDay: Int?,
        repeat: RepeatRule,
        weekday: Int?,
        dayOfMonth: Int?,
        startDate: LocalDate?,
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember(existing) { mutableStateOf(existing?.subtitle.orEmpty()) }
    var time by remember(existing) {
        mutableStateOf(existing?.minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "09:00")
    }
    var repeat by remember(existing) { mutableStateOf(RepeatRule.of(existing?.repeatRule)) }
    var weekday by remember(existing) { mutableStateOf(existing?.weekday ?: 7) }
    var dayOfMonth by remember(existing) { mutableStateOf((existing?.dayOfMonth ?: 1).toString()) }
    var dateText by remember(existing) {
        mutableStateOf(existing?.startDate?.let { "%02d-%02d-%04d".format(it.dayOfMonth, it.monthValue, it.year) }.orEmpty())
    }

    val minute = remember(time) { parseMinute(time) }
    val date = remember(dateText) { parseDayMonthYear(dateText) }
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    SheetTitle(if (existing == null) "New reminder" else "Edit reminder")
    SheetField("Remind me to", title, "e.g. Book the 9-month check") { title = it }
    SheetField("Note", note, "Optional detail") { note = it }
    SheetField("Time", time, "09:00", number) { time = it }

    Text(
        "HOW OFTEN",
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        color = KC.Muted, letterSpacing = 0.6.sp,
    )
    KSegmented(RepeatRule.entries.map { it.label }, RepeatRule.entries.indexOf(repeat)) {
        repeat = RepeatRule.entries[it]
    }

    when (repeat) {
        RepeatRule.NONE -> SheetField("On", dateText, "DD-MM-YYYY", number) { dateText = it }
        RepeatRule.WEEKLY -> FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WEEKDAYS.forEachIndexed { i, label ->
                KChip(label, weekday == i + 1) { weekday = i + 1 }
            }
        }
        RepeatRule.MONTHLY -> SheetField("Day of month", dayOfMonth, "1", number) {
            dayOfMonth = it.filter(Char::isDigit).take(2)
        }
        RepeatRule.DAILY -> SheetHint("Shows every day and clears itself overnight.")
    }

    if (repeat != RepeatRule.DAILY) {
        SheetHint("Stays on Today until you tick it off or dismiss it.")
    }

    val dayValue = dayOfMonth.toIntOrNull()
    val valid = title.isNotBlank() && when (repeat) {
        RepeatRule.NONE -> date != null
        RepeatRule.MONTHLY -> dayValue != null && dayValue in 1..28
        else -> true
    }

    PrimaryButton(if (existing == null) "Add reminder" else "Save reminder", enabled = valid) {
        onSave(
            existing?.key,
            title.trim(),
            note.trim(),
            minute,
            repeat,
            if (repeat == RepeatRule.WEEKLY) weekday else null,
            if (repeat == RepeatRule.MONTHLY) dayValue else null,
            if (repeat == RepeatRule.NONE) date else null,
        )
    }

    if (onDelete != null) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onDelete).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "Delete reminder",
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Rose,
            )
        }
    }
}
