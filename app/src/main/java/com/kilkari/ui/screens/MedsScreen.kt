package com.kilkari.ui.screens

import androidx.compose.foundation.background
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.kilkari.work.MedicationAlarms
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.MedicationEntity
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.Spot
import com.kilkari.ui.components.EmptyState
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.DoctorPickerSheets
import com.kilkari.ui.components.rememberDoctorPickerState
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.MedicationSheet
import com.kilkari.ui.theme.headerWash
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

/** Active medicines with a seven-day adherence strip, plus the archive of past ones. */
@Composable
fun MedsScreen(vm: KilkariViewModel, go: NavActions) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val picker = rememberDoctorPickerState()
    val doses by vm.medicationDoses.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }
    /** The medicine being corrected, or null when adding a new one. */
    var editing by remember { mutableStateOf<MedicationEntity?>(null) }

    val context = LocalContext.current
    val active = meds.filter { it.active }
    val past = meds.filterNot { it.active }
    val today = LocalDate.now()
    val week = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }

    Box(Modifier.fillMaxSize().headerWash()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Medications", go::back) {
                IconButton44("add", KC.Coral, { editing = null; sheetOpen = true }, iconSize = 26)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Only shown when the system is actually withholding exact alarms, so a phone
                // that already allows them never sees a prompt about a setting it has met.
                if (active.isNotEmpty() && !MedicationAlarms.available(context)) {
                    ExactAlarmPrompt { openExactAlarmSettings(context) }
                }

                SectionLabel("Active")
                if (active.isEmpty()) {
                    EmptyState(
                        Spot.EMPTY_MEDICINES,
                        "Nothing being taken",
                        "Add a medicine and Kilkari keeps the doses, the days, and an alarm for " +
                            "each one.",
                    )
                }
                active.forEach { med ->
                    val taken = doses.filter { it.medicationId == med.id }.map { it.date }.toSet()
                    ActiveMedCard(
                        med = med,
                        week = week,
                        taken = taken,
                        onLogToday = { vm.logMedicine(med) },
                        onStop = { vm.setMedicationActive(med, false) },
                        onEdit = { editing = med; sheetOpen = true },
                        onToggleDay = { day -> vm.setDoseTaken(med.id, day, day !in taken) },
                    )
                }

                if (past.isNotEmpty()) {
                    SectionLabel("Past")
                    past.forEach { med ->
                        KCard {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(KIcons["pill"], null, tint = KC.Muted, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        med.name, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp, color = KC.Ink,
                                    )
                                    Text(
                                        "${Fmt.date(med.startDate)}–${med.endDate?.let { Fmt.date(it) } ?: "now"} · ${med.dose}",
                                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                                    )
                                }
                                Text(
                                    "Restart",
                                    modifier = Modifier.clickable { vm.setMedicationActive(med, true) },
                                    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, color = KC.Coral,
                                )
                            }
                        }
                    }
                }
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false; editing = null }) {
            val target = editing
            MedicationSheet(
                existing = target,
                dob = baby?.dob,
                onPickDoctor = picker::open,
                onDelete = target?.let {
                    { vm.deleteMedication(it); sheetOpen = false; editing = null }
                },
            ) { name, dose, schedule, prescriber, start, minute ->
                if (target == null) {
                    vm.addMedication(name, dose, schedule, prescriber, start, minute)
                } else {
                    vm.updateMedication(target, name, dose, schedule, prescriber, start, minute)
                }
                sheetOpen = false
                editing = null
            }
        }
        DoctorPickerSheets(
            state = picker,
            doctors = doctors,
        ) { name, speciality, clinic, phone ->
            vm.saveDoctor(null, name, speciality, clinic, phone)
        }

    }
}

@Composable
private fun ActiveMedCard(
    med: MedicationEntity,
    week: List<LocalDate>,
    taken: Set<LocalDate>,
    onLogToday: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onToggleDay: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val takenToday = today in taken

    KCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge("pill", KC.Danger, KC.DangerBg2)
                // The name and details open the medicine for correcting; Stop stays its own
                // target so tapping to fix a dose cannot end the course by accident.
                Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                    Text(
                        med.name, fontFamily = Sans, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = KC.Ink,
                    )
                    Text(
                        listOfNotNull(med.dose, med.scheduleText, med.prescriber).joinToString(" · "),
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    )
                }
                Text(
                    "Stop",
                    modifier = Modifier.clickable(onClick = onStop),
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Muted,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val on = day in taken
                    val future = day.isAfter(today)
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) KC.TealBg else KC.StoneBg)
                                .clickable(enabled = !future) { onToggleDay(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                KIcons[if (on) "check" else "schedule"], null,
                                tint = if (on) KC.Teal else KC.StoneLight,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            day.dayOfWeek.name.take(1),
                            fontFamily = Sans, fontSize = 10.sp, color = KC.Muted,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (takenToday) KC.TealBg else KC.CoralBg)
                    .clickable(enabled = !takenToday, onClick = onLogToday),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (takenToday) "Today's dose taken ✓" else "Mark today's dose taken",
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (takenToday) KC.Teal else KC.CoralDeep,
                )
            }
        }
    }
}

/** Says why a dose might arrive late, and offers the one setting that fixes it. */
@Composable
private fun ExactAlarmPrompt(onOpenSettings: () -> Unit) {
    KCard(corner = 16, background = KC.GoldBg, border = KC.GoldRing) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Dose reminders may run late",
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Ink,
            )
            Text(
                "Android is batching this app's reminders to save battery, which can delay a " +
                    "dose by several minutes. Allowing exact alarms makes them arrive on time.",
                fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.MutedStrong,
            )
            Text(
                "Allow exact alarms",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(vertical = 8.dp),
                fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = KC.GoldDeep,
            )
        }
    }
}

/** The system page for this one permission, falling back to the app's settings page. */
private fun openExactAlarmSettings(context: Context) {
    val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        null
    }
    val fallback = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(exact ?: fallback) }
        .onFailure { runCatching { context.startActivity(fallback) } }
}
