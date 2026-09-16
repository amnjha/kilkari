package com.kilkari.ui.sheets

import com.kilkari.domain.ReminderDraft
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.kilkari.ui.components.DEFAULT_ICON
import com.kilkari.ui.components.IconPickerField
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.KTimeField
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
    onPickIcon: (current: String, apply: (String) -> Unit) -> Unit,
    onSave: (ReminderDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember(existing) { mutableStateOf(existing?.subtitle.orEmpty()) }
    var minute by remember(existing) { mutableStateOf(existing?.minuteOfDay ?: 9 * 60) }
    var repeat by remember(existing) { mutableStateOf(RepeatRule.of(existing?.repeatRule)) }
    var weekday by remember(existing) { mutableStateOf(existing?.weekday ?: 7) }
    var dayOfMonth by remember(existing) { mutableStateOf((existing?.dayOfMonth ?: 1).toString()) }
    var date by remember(existing) { mutableStateOf(existing?.startDate) }
    var icon by remember(existing) { mutableStateOf(existing?.icon ?: DEFAULT_ICON) }

    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    SheetTitle(if (existing == null) "New reminder" else "Edit reminder")
    SheetField("Remind me to", title, "e.g. Book the 9-month check") { title = it }
    SheetField("Note", note, "Optional detail") { note = it }

    IconPickerField("Icon", icon) { onPickIcon(icon) { icon = it } }

    KTimeField("Time", minute) { minute = it }

    Text(
        "HOW OFTEN",
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        color = KC.Muted, letterSpacing = 0.6.sp,
    )
    KSegmented(RepeatRule.entries.map { it.label }, RepeatRule.entries.indexOf(repeat)) {
        repeat = RepeatRule.entries[it]
    }

    when (repeat) {
        RepeatRule.NONE -> KDateField("On", date) { date = it }
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
            ReminderDraft(
                key = existing?.key,
                title = title.trim(),
                subtitle = note.trim(),
                icon = icon,
                minuteOfDay = minute,
                repeat = repeat,
                weekday = if (repeat == RepeatRule.WEEKLY) weekday else null,
                dayOfMonth = if (repeat == RepeatRule.MONTHLY) dayValue else null,
                startDate = if (repeat == RepeatRule.NONE) date else null,
            )
        )
    }

    if (onDelete != null) SheetDelete("Delete reminder", onDelete)
}
