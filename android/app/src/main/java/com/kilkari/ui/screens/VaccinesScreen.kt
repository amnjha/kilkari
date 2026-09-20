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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Fmt
import com.kilkari.domain.VaccineGroupState
import com.kilkari.domain.VaccineItemState
import com.kilkari.domain.VaccineStatus
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.CheckRing
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.sheets.MarkVaccineSheet
import com.kilkari.ui.sheets.RecordedDoseSheet
import com.kilkari.ui.sheets.ScheduleSheet
import com.kilkari.ui.theme.KDepth
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.onCream
import com.kilkari.ui.theme.Sans

/** The whole generated schedule, one card per age group. */
@Composable
fun VaccinesScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()

    // Most doses are given where the last one was, so the sheet opens with that filled in.
    val lastClinic = appointments.firstOrNull { it.place != null }?.place.orEmpty()
    val lastDoctor = appointments.firstOrNull { it.doctor != null }?.doctor.orEmpty()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val groups by vm.vaccineGroups.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    /** The dose whose sheet is open, with the group it belongs to. */
    var recording by remember { mutableStateOf<Pair<VaccineGroupState, VaccineItemState>?>(null) }

    val doneCount = groups.sumOf { it.doneCount }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Vaccinations", go::back) {
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(KC.Surface)
                        .border(1.dp, KC.BorderStrong, RoundedCornerShape(999.dp))
                        .clickable { sheetOpen = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        VaccineSchedules.byId(settings.scheduleId).shortName,
                        fontFamily = Sans, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = KC.CoralDeep,
                    )
                    Icon(KIcons["expand_more"], null, tint = KC.CoralDeep, modifier = Modifier.size(18.dp))
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = KDepth.navClearance),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The whole schedule in one line, before any of the cards: how many doses are
                // done, and whether anything is late. A parent's first question on this
                // screen is "are we behind", and counting overdue badges is not an answer.
                val totalDoses = groups.sumOf { it.count }
                val overdue = groups.count { it.status == VaccineStatus.OVERDUE }
                KCard(background = KC.TealWash.onCream(0.5f), border = null) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "$doneCount of $totalDoses doses given",
                            fontFamily = Display, fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, color = KC.Ink,
                        )
                        Text(
                            if (overdue == 0) "Nothing overdue."
                            else "$overdue ${Fmt.plural(overdue.toLong(), "group")} overdue.",
                            fontFamily = Sans, fontSize = 13.sp,
                            color = if (overdue == 0) KC.MutedStrong else KC.Danger,
                        )
                        baby?.let {
                            Text(
                                "Generated from ${Fmt.dateFull(it.dob)}.",
                                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                            )
                        }
                    }
                }
                groups.forEach { group ->
                    VaccineGroupCard(
                        group = group,
                        // Never a bare tick. A dose is a thing that happened somewhere, on a
                        // day, and asking once beats a tick the parent has to go and annotate
                        // later — everything in the sheet is optional, so it is still one tap
                        // to confirm and done.
                        onToggle = { item -> recording = group to item },
                        onOpen = {
                            vm.selectVaccineGroup(group.index)
                            go.push(Routes.VACCINE_DETAIL)
                        },
                    )
                }
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            ScheduleSheet(settings.scheduleId) { id ->
                vm.setSchedule(id)
                sheetOpen = false
            }
        }

        val open = recording
        KSheet(open != null, onDismiss = { recording = null }) {
            if (open != null) {
                val (group, item) = open
                if (item.given) {
                    RecordedDoseSheet(
                        item = item,
                        dob = baby?.dob,
                        onPickDoctor = { _, _ -> },
                        onRemove = {
                            vm.toggleDose(group.label, item.name, false)
                            recording = null
                        },
                        onSave = { on, clinic, brand ->
                            vm.updateDose(group.label, item.name, on, clinic, brand)
                            recording = null
                        },
                    )
                } else {
                    MarkVaccineSheet(
                        group = group,
                        doses = listOf(item),
                        currency = currency,
                        dob = baby?.dob,
                        defaultClinic = lastClinic,
                        defaultDoctor = lastDoctor,
                        onPickDoctor = { _, _ -> },
                        onConfirm = { on, clinic, doctor, brands, cost, addExpense ->
                            vm.markDosesGiven(group, listOf(item), on, clinic, doctor, brands, cost, addExpense)
                            recording = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VaccineGroupCard(
    group: VaccineGroupState,
    onToggle: (VaccineItemState) -> Unit,
    onOpen: () -> Unit,
) {
    val skin = statusSkin(group.status)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KC.Surface)
            .border(1.dp, skin.border, RoundedCornerShape(18.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(skin.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KIcons[skin.icon], null, tint = skin.foreground, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    group.label, fontFamily = Sans, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = KC.Ink,
                )
                Text(
                    "${Fmt.date(group.dueDate)} · ${group.doneCount} of ${group.count} given",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(skin.background)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    group.statusText, fontFamily = Sans, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, color = skin.foreground,
                )
            }
        }

        // Every dose, tickable where it sits. The doses were behind a tap to a detail screen,
        // which is a long way to go to say "had it" — and the list of names it replaced said
        // how many there were without saying which of them were done.
        group.items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(item) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckRing(item.given, size = 24)
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        color = if (item.given) KC.Faint else KC.Ink,
                        textDecoration = if (item.given) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    // Once a dose is recorded, when and where it happened is more use than
                    // what it protects against, which the parent has just read.
                    val detail = if (item.given) {
                        listOfNotNull(item.givenOn?.let { Fmt.date(it) }, item.clinic, item.brand)
                            .joinToString(" · ")
                    } else {
                        item.desc
                    }
                    if (detail.isNotBlank()) {
                        Text(detail, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
                    }
                }
            }
        }

        // The full record — a date, a clinic, a brand, what it cost — still lives behind this.
        // Ticking is for the common case; this is for the one where the receipt matters.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (group.allGiven) "See what was recorded" else "Record with a date and clinic",
                fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = KC.CoralDeep,
            )
            Icon(KIcons["chevron_right"], null, tint = KC.CoralDeep, modifier = Modifier.size(16.dp))
        }
    }
}

internal data class StatusSkin(
    val foreground: Color,
    val background: Color,
    val border: Color,
    val icon: String,
)

internal fun statusSkin(status: VaccineStatus): StatusSkin = when (status) {
    VaccineStatus.GIVEN -> StatusSkin(KC.Teal, KC.TealBg, KC.TealRing, "check")
    VaccineStatus.OVERDUE -> StatusSkin(KC.Danger, KC.DangerBg, KC.DangerRing, "priority_high")
    VaccineStatus.DUE_SOON -> StatusSkin(KC.GoldDeep, KC.GoldBg, KC.GoldRing, "event_upcoming")
    VaccineStatus.UPCOMING -> StatusSkin(KC.CoralDeep, KC.CoralBg, KC.Border, "schedule")
}
