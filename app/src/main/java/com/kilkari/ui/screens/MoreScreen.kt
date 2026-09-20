package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Fmt
import com.kilkari.domain.PaperworkStatus
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.Monogram
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.BabySheet
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle

/** Baby card plus the eight destinations that do not live in a tab. */
@Composable
fun MoreScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val paperwork by vm.paperwork.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val b = baby ?: return

    val nextDocument = paperwork.firstOrNull { it.status == PaperworkStatus.ACTIVE }
    val documentsObtained = paperwork.count { it.status == PaperworkStatus.OBTAINED }

    val birthdayDays = Fmt.daysUntil(Fmt.nextBirthday(b.dob))
    val remindersOn = reminders.count { it.enabled }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("More", style = ScreenTitle, color = KC.Ink)

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(KC.CoralWash, KC.GoldWash)))
                                .clickable { editing = true }
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(b.name.take(1).uppercase(), size = 48, fontSize = 20)
            Column(Modifier.weight(1f)) {
                Text(b.name, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = KC.Ink)
                Text(
                    "Born ${Fmt.dateFull(b.dob)} · ${Fmt.age(b.dob)}",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.MutedStrong,
                )
            }
            Icon(KIcons["edit"], null, tint = KC.CoralDeep, modifier = Modifier.size(22.dp))
        }

        // Three colours, not eight: the keepsakes, the records, and the app talking about
        // itself. A tile per hue turned the drawer into a paint chart and made the grid
        // harder to scan, not easier — grouping means the colour now tells you what kind of
        // thing you are about to open.
        val items = listOf(
            MoreItem(
                Routes.TIMELINE, "timeline", "Timeline",
                "Milestones and moments",
                KC.RoseDeep, KC.RoseWash,
            ),
            MoreItem(
                Routes.PHOTOS, "photo_library", "Photo albums",
                if (albums.isEmpty()) "Link a Google Photos album"
                else "${albums.size} ${Fmt.plural(albums.size.toLong(), "album")} linked",
                KC.RoseDeep, KC.RoseWash,
            ),
            MoreItem(
                Routes.EVENTS, "cake", "Birthdays & events",
                "First birthday in $birthdayDays days",
                KC.RoseDeep, KC.RoseWash,
            ),
            MoreItem(
                Routes.DOCUMENTS, "folder_open", "Documents",
                if (documents.isEmpty()) "Scan certificates and prescriptions"
                else "${documents.size} ${Fmt.plural(documents.size.toLong(), "scan")} filed",
                KC.SeaDeep, KC.SeaWash,
            ),
            MoreItem(
                Routes.PAPERWORK, "badge", "Paperwork",
                when {
                    nextDocument != null ->
                        "${nextDocument.kind.title} ${Fmt.dueText(nextDocument.inDays ?: 0)}"
                    documentsObtained == paperwork.size && paperwork.isNotEmpty() ->
                        "All four in hand"
                    else -> "$documentsObtained of ${paperwork.size} obtained"
                },
                KC.SeaDeep, KC.SeaWash,
            ),
            MoreItem(
                Routes.BACKUP, "backup", "Backup & export",
                "Everything stays on this phone",
                KC.SeaDeep, KC.SeaWash,
            ),
            MoreItem(
                Routes.REMINDERS, "notifications_active", "Reminders",
                "$remindersOn on",
                KC.LilacDeep, KC.LilacWash,
            ),
            MoreItem(
                Routes.SETTINGS, "settings", "Settings",
                "${settings.currency.symbol} ${settings.currency.code} · " +
                    "${VaccineSchedules.byId(settings.scheduleId).shortName} schedule",
                KC.LilacDeep, KC.LilacWash,
            ),
        )

        // A grid rather than a list. Eight rows of the same white card was the drawer of the
        // app looking like a settings screen; eight coloured tiles is somewhere to browse,
        // and the colour is what a parent aims at rather than the word.
        items.chunked(2).forEach { pair ->
            // Both tiles take the height of the taller one, so a two-line subtitle beside a
            // one-line subtitle does not leave a step in the grid.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { item ->
                    MoreTile(item, Modifier.weight(1f).fillMaxHeight()) { go.push(item.route) }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }

        KSheet(editing, onDismiss = { editing = false }) {
            BabySheet(b, settings.metricUnits) { name, dob, sex, place, weight, length, head ->
                vm.updateBabyDetails(name, dob, sex, place, weight, length, head)
                editing = false
            }
        }
    }
}

@Composable
private fun MoreTile(item: MoreItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    KCard(modifier, corner = 26, background = item.background, border = null, onClick = onClick) {
        Column(
            Modifier.fillMaxHeight().padding(16.dp).heightIn(min = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KIcons[item.icon], null, tint = item.tint, modifier = Modifier.size(23.dp))
            }
            Column {
                Text(
                    item.title,
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KC.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.subtitle,
                    fontFamily = Sans, fontSize = 12.sp, color = KC.MutedStrong,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class MoreItem(
    val route: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val tint: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color,
)
