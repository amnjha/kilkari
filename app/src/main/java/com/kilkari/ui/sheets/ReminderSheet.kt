package com.kilkari.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.kilkari.domain.ReminderDraft
import com.kilkari.ui.components.KIcons
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
 * Emoji offered alongside the drawn icons. The drawn set is deliberately plain and on-palette;
 * these cover the things a parent reaches for that no icon set has, and anything not here can
 * be typed straight into the field.
 */
private val EMOJI = listOf(
    "\uD83C\uDF7C", "\uD83D\uDECC", "\uD83D\uDEBC", "\uD83E\uDDF8", "\uD83D\uDC63",
    "\uD83E\uDDF4", "\uD83D\uDC55", "\uD83E\uDD5B", "\uD83C\uDF4E", "\uD83D\uDC8A",
    "\uD83E\uDD7C", "\uD83E\uDDB7", "\uD83D\uDCCF", "\uD83C\uDF1E", "\uD83C\uDF19",
    "\uD83C\uDF1F", "\uD83C\uDF88", "\uD83D\uDCDE", "\uD83D\uDCCB", "\u2764\uFE0F",
)

/**
 * Create or edit a reminder of the parent's own: what to be reminded about, when, and
 * optionally how often it comes back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ReminderSheet(
    existing: ReminderEntity?,
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

    IconPicker(icon) { icon = it }

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

/** The icon a reminder starts with before the parent picks anything. */
const val DEFAULT_ICON = "notifications_active"

/**
 * Picks how the reminder shows up on Today: one of the drawn icons, one of the offered emoji,
 * or any emoji at all typed into the field.
 *
 * Both rows feed the same value, so choosing from one clears the selection in the other, and
 * a typed emoji is selected the moment it is entered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.IconPicker(selected: String, onPick: (String) -> Unit) {
    Text(
        "ICON",
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        color = KC.Muted, letterSpacing = 0.6.sp,
    )

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KIcons.CHOICES.forEach { name ->
            IconChoice(name == selected, { onPick(name) }) {
                Icon(
                    KIcons[name],
                    contentDescription = null,
                    tint = if (name == selected) KC.Coral else KC.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        EMOJI.forEach { glyph ->
            IconChoice(glyph == selected, { onPick(glyph) }) {
                Text(glyph, fontSize = 18.sp, lineHeight = 18.sp)
            }
        }
    }

    // Anything the offered emoji miss. The system keyboard's emoji key puts it here.
    val typed = if (KIcons.isDrawn(selected) || selected in EMOJI) "" else selected
    SheetField("Or type an emoji", typed, "🥚") { entered ->
        // Plain letters and digits are dropped, so a stray keypress cannot turn the icon into
        // a word. Everything else — any emoji, with its modifiers — is kept.
        val glyph = entered.filterNot { it.isAsciiWord() }.trim().take(EMOJI_MAX_CHARS)
        onPick(glyph.ifEmpty { DEFAULT_ICON })
    }
}

/** ASCII letters, digits and spaces: the things an emoji field should quietly refuse. */
private fun Char.isAsciiWord(): Boolean = code < 128 && (isLetterOrDigit() || isWhitespace())

/** One square in the icon grid, outlined when it is the chosen one. */
@Composable
private fun IconChoice(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) KC.CoralBg else KC.Surface)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) KC.Coral else KC.Border,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Long enough for a flag or a skin-toned emoji, short enough not to be a word. */
private const val EMOJI_MAX_CHARS = 8
