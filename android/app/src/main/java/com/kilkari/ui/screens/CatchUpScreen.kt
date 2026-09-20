package com.kilkari.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.seed.MilestoneDef
import com.kilkari.data.seed.Milestones
import com.kilkari.data.seed.VaccineGroupDef
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.MilestoneCatchUpList
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.components.VaccineCatchUpList
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Catch-up, run again after first launch.
 *
 * Onboarding asks once, when there is least to say: someone who set the app up in five minutes
 * between feeds, or who switched schedule later, is left with doses and moments that happened
 * but were never recorded, and no way to enter them except one sheet at a time.
 *
 * Only what is genuinely outstanding is offered — a vaccine group with any dose already
 * recorded is left alone rather than overwritten, and a milestone already on the timeline is
 * not offered twice.
 */
@Composable
fun CatchUpScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val groups by vm.vaccineGroups.collectAsStateWithLifecycle()

    val chosenGroups = remember { mutableStateMapOf<String, LocalDate>() }
    val chosenMilestones = remember { mutableStateMapOf<String, LocalDate>() }

    // Read once on arrival: a milestone recorded from this very screen should not vanish from
    // under the parent's finger while they are still ticking things.
    var alreadyRecorded by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { alreadyRecorded = vm.recordedMilestoneKeys() }

    val dob = baby?.dob
    val schedule = VaccineSchedules.byId(settings.scheduleId)

    /** Groups due before today that nothing has been recorded against yet. */
    val outstanding: List<Pair<VaccineGroupDef, LocalDate>> = remember(groups, dob, schedule) {
        val born = dob ?: return@remember emptyList()
        val touched = groups.filter { it.doneCount > 0 }.map { it.label }.toSet()
        schedule.groups
            .map { it to born.plusDays(it.days.toLong()) }
            .filter { !it.second.isAfter(LocalDate.now()) && it.first.label !in touched }
    }

    val passed: List<MilestoneDef> = remember(dob, alreadyRecorded) {
        val born = dob ?: return@remember emptyList()
        val months = ChronoUnit.DAYS.between(born, LocalDate.now()) / 30.44
        Milestones.passedBy(months).filter { it.key !in alreadyRecorded }
    }

    val nothingLeft = outstanding.isEmpty() && passed.isEmpty()

    Column(Modifier.fillMaxSize()) {
        DetailBar("Catch up", go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Anything that happened before you started, or before you switched schedule. " +
                    "Tick what you remember and correct the dates — nothing already recorded is " +
                    "touched.",
                fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
            )

            if (nothingLeft) {
                KCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Nothing outstanding",
                            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                            color = KC.Ink,
                        )
                        Text(
                            "Every dose due so far is recorded, and every milestone for this age " +
                                "is on the timeline.",
                            fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
                        )
                    }
                }
                return@Column
            }

            if (outstanding.isNotEmpty()) {
                SectionLabel("Vaccines due before today")
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KChip("Mark all given", false) {
                        outstanding.forEach { (g, due) -> chosenGroups[g.label] = due }
                    }
                    KChip("Clear", false) { chosenGroups.clear() }
                }
                VaccineCatchUpList(outstanding, chosenGroups)
            }

            if (passed.isNotEmpty()) {
                SectionLabel("Moments for this age", Modifier.padding(top = 4.dp))
                MilestoneCatchUpList(passed, dob, chosenMilestones)
            }

            PrimaryButton(
                saveLabel(chosenGroups, chosenMilestones),
                enabled = chosenGroups.isNotEmpty() || chosenMilestones.isNotEmpty(),
            ) {
                vm.recordCatchUp(chosenGroups.toMap(), chosenMilestones.toMap())
                go.back()
            }
        }
    }
}

/** Says what the button is about to write, rather than just "Save". */
private fun saveLabel(
    groups: SnapshotStateMap<String, LocalDate>,
    milestones: SnapshotStateMap<String, LocalDate>,
): String {
    val parts = buildList {
        if (groups.isNotEmpty()) add("${groups.size} vaccine ${plural(groups.size, "group")}")
        if (milestones.isNotEmpty()) add("${milestones.size} ${plural(milestones.size, "moment")}")
    }
    return if (parts.isEmpty()) "Nothing ticked yet" else "Record " + parts.joinToString(" and ")
}

private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"
