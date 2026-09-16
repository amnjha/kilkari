package com.kilkari.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.ui.theme.BarTitle
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * Icon selection, as a secondary bottom sheet over whatever form asked for it.
 *
 * The grid is forty-odd squares. Inline it was taller than everything else in the reminder
 * sheet put together, so the name and the cadence — the parts you actually came to fill in —
 * were pushed off the screen by a decoration.
 */

/** The icon a reminder starts with before anything is picked. */
const val DEFAULT_ICON = "notifications_active"

/**
 * Emoji offered alongside the drawn icons: the things a parent reaches for that no icon set
 * has. Anything missing can be typed into the field instead.
 */
private val EMOJI = listOf(
    "🍼", "🛌", "🚼", "🧸", "👣",
    "🧴", "👕", "🥛", "🍎", "💊",
    "🥼", "🦷", "📏", "🌞", "🌙",
    "🌟", "🎈", "📞", "📋", "❤️",
)

/** Long enough for a flag or a skin-toned emoji, short enough not to be a word. */
private const val EMOJI_MAX_CHARS = 8

/** Read-only row inside a form, showing the current icon. Tapping asks the host to open it. */
@Composable
fun IconPickerField(label: String, selected: String, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KC.Screen)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(selected, KC.Coral, KC.CoralBg, size = 32, corner = 10, iconSize = 18)
            Text(
                "Change",
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = KC.Coral,
            )
            Icon(KIcons["chevron_right"], null, tint = KC.Faint, modifier = Modifier.size(18.dp))
        }
    }
}

/** Tracks whether the secondary sheet is showing, and where to send the choice. */
class IconPickerState {
    internal var apply by mutableStateOf<((String) -> Unit)?>(null)

    /** What the calling form currently holds, so the grid can show it as chosen. */
    internal var current by mutableStateOf(DEFAULT_ICON)

    val isOpen: Boolean get() = apply != null

    /** Called by a form's field: remembers the current value and where to send the choice. */
    fun open(current: String, apply: (String) -> Unit) {
        this.current = current
        this.apply = apply
    }

    internal fun close() {
        apply = null
    }
}

@Composable
fun rememberIconPickerState(): IconPickerState = remember { IconPickerState() }

/** Hosts the sheet. Place inside the screen's root Box, alongside the primary sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerSheet(state: IconPickerState) {
    // Held here rather than in the form, so the grid tracks taps before the sheet is dismissed.
    var chosen by remember(state.apply) { mutableStateOf(state.current) }

    fun choose(value: String) {
        chosen = value
        state.apply?.invoke(value)
    }

    KSheet(state.isOpen, onDismiss = { state.close() }) {
        Text("Choose an icon", style = BarTitle, color = KC.Ink)

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KIcons.CHOICES.forEach { name ->
                IconChoice(name == chosen, { choose(name) }) {
                    Icon(
                        KIcons[name],
                        contentDescription = null,
                        tint = if (name == chosen) KC.Coral else KC.Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            EMOJI.forEach { glyph ->
                IconChoice(glyph == chosen, { choose(glyph) }) {
                    Text(glyph, fontSize = 18.sp, lineHeight = 18.sp)
                }
            }
        }

        // Anything the offered emoji miss. The keyboard's emoji key puts it here.
        val typed = if (KIcons.isDrawn(chosen) || chosen in EMOJI) "" else chosen
        SheetField("Or type an emoji", typed, "🥚") { entered ->
            // Plain letters and digits are dropped, so a stray keypress cannot turn the icon
            // into a word. Everything else — any emoji, with its modifiers — is kept.
            val glyph = entered.filterNot { it.isAsciiWord() }.trim().take(EMOJI_MAX_CHARS)
            choose(glyph.ifEmpty { DEFAULT_ICON })
        }

        PrimaryButton("Done") { state.close() }
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
