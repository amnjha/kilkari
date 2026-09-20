package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Fmt
import com.kilkari.domain.PaperworkStatus
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KRow
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
                .background(Brush.linearGradient(listOf(KC.CoralBg, KC.GoldBg)))
                .border(1.dp, KC.BorderStrong, RoundedCornerShape(18.dp))
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
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
            Icon(KIcons["edit"], null, tint = KC.Muted, modifier = Modifier.size(22.dp))
        }

        KCard {
            val items = listOf(
                MoreItem(Routes.TIMELINE, "timeline", "Timeline", "Milestones, events, photo moments", KC.GoldDeep, KC.GoldBg),
                MoreItem(
                    Routes.DOCUMENTS, "folder_open", "Documents",
                    if (documents.isEmpty()) "Scan certificates and prescriptions"
                    else "${documents.size} ${Fmt.plural(documents.size.toLong(), "scan")} filed",
                    KC.GoldDeep, KC.GoldBg,
                ),
                MoreItem(
                    Routes.PAPERWORK, "badge", "Paperwork",
                    when {
                        nextDocument != null ->
                            "${nextDocument.kind.title} ${Fmt.dueText(nextDocument.inDays ?: 0)}"
                        documentsObtained == paperwork.size && paperwork.isNotEmpty() ->
                            "Birth certificate, Aadhaar, passport, PAN — all in hand"
                        else -> "$documentsObtained of ${paperwork.size} obtained"
                    },
                    KC.ClayDeep, KC.ClayBg,
                ),
                MoreItem(
                    Routes.PHOTOS, "photo_library", "Photo albums",
                    if (albums.isEmpty()) "Link a Google Photos album"
                    else "${albums.size} ${Fmt.plural(albums.size.toLong(), "album")} linked",
                    KC.SeaDeep, KC.SeaBg,
                ),
                MoreItem(
                    Routes.EVENTS, "cake", "Birthdays & events",
                    "First birthday in $birthdayDays days", KC.Danger, KC.DangerBg,
                ),
                MoreItem(Routes.REMINDERS, "notifications_active", "Reminders", "$remindersOn on", KC.CoralDeep, KC.CoralBg),
                MoreItem(Routes.BACKUP, "backup", "Backup & export", "Everything stays on this phone", KC.TealDeep, KC.TealBg),
                MoreItem(
                    Routes.SETTINGS, "settings", "Settings",
                    "${settings.currency.symbol} ${settings.currency.code} · " +
                        "${VaccineSchedules.byId(settings.scheduleId).shortName} schedule",
                    KC.Stone, KC.StoneBg,
                ),
            )
            items.forEachIndexed { i, item ->
                KRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    iconTint = item.tint,
                    iconBg = item.background,
                    divider = i != items.lastIndex,
                    onClick = { go.push(item.route) },
                ) {
                    Icon(KIcons["chevron_right"], null, tint = KC.CoralPaler, modifier = Modifier.size(20.dp))
                }
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

private data class MoreItem(
    val route: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val tint: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color,
)
