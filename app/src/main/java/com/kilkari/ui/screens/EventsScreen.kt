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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.EventEntity
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.Spot
import com.kilkari.ui.components.EmptyState
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.EventSheet
import com.kilkari.ui.theme.headerWash
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

/** Birthday countdown plus every other date worth remembering. */
@Composable
fun EventsScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EventEntity?>(null) }

    val today = LocalDate.now()
    val b = baby

    Box(Modifier.fillMaxSize().headerWash()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Birthdays & events", go::back) {
                IconButton44("add", KC.Coral, { sheetOpen = true }, iconSize = 26)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (b != null) {
                    val next = Fmt.nextBirthday(b.dob, today)
                    val days = Fmt.daysUntil(next, today)
                    val ordinal = next.year - b.dob.year
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(KC.Gold, KC.DangerLight)))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(KIcons["cake"], null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Text(
                            "$days ${Fmt.plural(days.toLong(), "day")}",
                            fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                            fontSize = 40.sp, color = Color.White, letterSpacing = (-0.8).sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            "to ${b.name}'s ${ordinalLabel(ordinal)} birthday · ${Fmt.dateFull(next)}",
                            fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = Color.White,
                        )
                    }
                }

                KCard {
                    val sorted = events.sortedBy { nextOccurrence(it.date, it.annual, today) }
                    if (sorted.isEmpty()) {
                        EmptyState(
                            Spot.EMPTY_EVENTS,
                            "No dates saved",
                            "Birthdays, naming days, the first Diwali — add one and it counts " +
                                "down here every year.",
                        )
                    }
                    sorted.forEachIndexed { i, event ->
                        val occurrence = nextOccurrence(event.date, event.annual, today)
                        val days = Fmt.daysUntil(occurrence, today)
                        val tint = eventTint(event.icon)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editing = event }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(event.icon, tint.first, tint.second, size = 36, corner = 10, iconSize = 20)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp, color = KC.Ink,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        Fmt.dayAndDate(occurrence),
                                        event.subtitle.ifBlank { null },
                                    ).joinToString(" · "),
                                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                Fmt.dueBadge(days),
                                fontFamily = Sans, fontWeight = FontWeight.Bold,
                                fontSize = 12.sp, color = tint.first,
                            )
                        }
                        if (i != sorted.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
                        }
                    }
                }
                Text(
                    "Tap an event to change or remove it.",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            EventSheet { title, note, date, annual ->
                vm.addEvent(title, note, date, iconFor(title), annual)
                sheetOpen = false
            }
        }

        val edit = editing
        KSheet(edit != null, onDismiss = { editing = null }) {
            if (edit != null) {
                EventSheet(
                    existing = edit,
                    onDelete = { vm.deleteEvent(edit); editing = null },
                ) { title, note, date, annual ->
                    vm.updateEvent(edit, title, note, date, annual)
                    editing = null
                }
            }
        }
    }
}

/** Annual events roll forward to their next occurrence; one-offs keep their date. */
private fun nextOccurrence(date: LocalDate, annual: Boolean, today: LocalDate): LocalDate =
    if (!annual) date else Fmt.nextBirthday(date, today)

private fun ordinalLabel(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

private fun eventTint(icon: String): Pair<Color, Color> = when (icon) {
    "cake" -> KC.Danger to KC.DangerBg
    "restaurant" -> KC.TealDeep to KC.TealBg
    "celebration" -> KC.GoldDeep to KC.GoldBg
    else -> KC.GoldDeep to KC.GoldBg
}

/** Picks a fitting glyph from the event title so rows are not all identical. */
private fun iconFor(title: String): String {
    val t = title.lowercase()
    return when {
        "birthday" in t -> "cake"
        "food" in t || "solid" in t || "annaprashan" in t -> "restaurant"
        "ceremony" in t || "naming" in t -> "celebration"
        else -> "event"
    }
}
