package com.kilkari.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.data.seed.MilestoneDef
import com.kilkari.data.seed.VaccineGroupDef
import com.kilkari.domain.Fmt
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

/**
 * The two catch-up lists, shared by onboarding and the Settings screen that runs the same
 * thing later. Almost nobody starts tracking on the day of birth, and the ones who do still
 * come back to a schedule they have fallen behind — the lists should not be two pieces of code
 * that can disagree about what counts as outstanding.
 *
 * Each list writes into a map the caller owns: group label or milestone key, to the date the
 * parent says it happened. Ticking a row fills in the expected date, and the row then offers a
 * picker to correct it.
 */
@Composable
fun VaccineCatchUpList(
    overdue: List<Pair<VaccineGroupDef, LocalDate>>,
    chosen: SnapshotStateMap<String, LocalDate>,
) {
    KCard {
        overdue.forEachIndexed { i, (group, due) ->
            val checked = chosen.containsKey(group.label)
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked) chosen.remove(group.label) else chosen[group.label] = due
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckRing(checked, rounded = true)
                    Column(Modifier.weight(1f)) {
                        Text(
                            group.label,
                            fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = KC.Ink,
                        )
                        Text(
                            "Due ${Fmt.date(due)} · ${group.vaccines.joinToString(", ") { it.name }}",
                            fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp, color = KC.Muted,
                        )
                    }
                }
                if (checked) {
                    KDateField(
                        "Given on", chosen[group.label], sheetStyle = false,
                        divider = false, selectableTo = LocalDate.now(),
                    ) { chosen[group.label] = it }
                }
                if (i != overdue.lastIndex) RowLine()
            }
        }
    }
}

@Composable
fun MilestoneCatchUpList(
    passed: List<MilestoneDef>,
    dob: LocalDate?,
    chosen: SnapshotStateMap<String, LocalDate>,
) {
    KCard {
        passed.forEachIndexed { i, def ->
            val checked = chosen.containsKey(def.key)
            val typical = dob?.plusDays((def.typicalMonths * 30.44).toLong()) ?: LocalDate.now()
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked) chosen.remove(def.key) else chosen[def.key] = typical
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckRing(checked)
                    Column(Modifier.weight(1f)) {
                        Text(
                            def.label,
                            fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = KC.Ink,
                        )
                        Text(
                            "Usually around ${milestoneAge(def.typicalMonths)}",
                            fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                        )
                    }
                    IconBadge(def.icon, KC.GoldDeep, KC.GoldBg, size = 32, corner = 10, iconSize = 18)
                }
                if (checked) {
                    KDateField(
                        "Happened on", chosen[def.key], sheetStyle = false,
                        divider = false, selectableTo = LocalDate.now(),
                    ) { chosen[def.key] = it }
                }
                if (i != passed.lastIndex) RowLine()
            }
        }
    }
}

@Composable
private fun RowLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
}

/** "6 weeks" / "4 months" — how a milestone's typical age reads in a sentence. */
fun milestoneAge(months: Double): String = when {
    months < 1.0 -> "${(months * 4.35).toInt()} weeks"
    months < 2.0 -> "6 weeks"
    else -> "${months.toInt()} months"
}
