package com.kilkari.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.GrowthEntity
import com.kilkari.domain.Fmt
import com.kilkari.domain.GrowthStandards
import com.kilkari.domain.Sex
import com.kilkari.domain.Units
import com.kilkari.ui.components.MeasurementHints
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.WeightChart
import com.kilkari.ui.components.WeightChartLegend
import com.kilkari.ui.components.WeightPoint
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.StatCell
import com.kilkari.ui.components.StatRow
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.GrowthSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Latest measurements, a weekly weight chart, and the add-measurement sheet. */
@Composable
fun GrowthScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val growth by vm.growth.collectAsStateWithLifecycle()
    val metric by vm.metricUnits.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    // Arriving from the Today prompt should land straight on the entry sheet.
    val pendingEntry by vm.pendingEntry.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEntry) {
        if (pendingEntry == "weigh") {
            sheetOpen = true
            vm.consumeEntry()
        }
    }

    val latest = growth.lastOrNull()
    val previous = growth.dropLast(1).lastOrNull { it.weightKg != null }
    val weightDelta = latest?.weightKg?.let { w -> previous?.weightKg?.let { w - it } }
    val lengthDelta = latest?.lengthCm?.let { l ->
        growth.dropLast(1).lastOrNull { it.lengthCm != null }?.lengthCm?.let { l - it }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Growth", go::back) {
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
                StatRow {
                    StatCell(
                        "Weight", Fmt.weight(latest?.weightKg, metric),
                        weightDelta?.let { Fmt.grams(it, metric) },
                        if ((weightDelta ?: 0.0) >= 0) KC.Teal else KC.Danger,
                        Modifier.weight(1f),
                    )
                    StatCell(
                        "Length", Fmt.length(latest?.lengthCm, metric),
                        lengthDelta?.let { Units.lengthDelta(it, metric) },
                        KC.Teal, Modifier.weight(1f),
                    )
                    StatCell(
                        "Head", Fmt.length(latest?.headCm, metric),
                        null, KC.Muted, Modifier.weight(1f),
                    )
                }

                val series = weightSeries(
                    growth.mapNotNull { g -> g.weightKg?.let { it to g.date } },
                    baby?.dob,
                )
                KCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Weight over time", fontFamily = Sans,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Ink,
                            )
                            Text(
                                Fmt.weightUnit(metric),
                                fontFamily = Sans, fontSize = 11.sp, color = KC.Muted,
                            )
                        }
                        if (series.isEmpty()) {
                            Text(
                                "Add a weight to start the chart.",
                                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                            )
                        } else {
                            val sex = Sex.of(baby?.sex)
                            WeightChart(series, sex, metric)
                            WeightChartLegend(baby?.name ?: "Weight")

                            // Where the latest reading sits. Only with a sex on file: a
                            // percentile averaged across boys and girls is a number about
                            // nobody, and it reads as precise.
                            val standing = sex?.let { s ->
                                val last = series.lastOrNull()
                                last?.let { GrowthStandards.percentileOf(it.kg, it.ageMonths, s) }
                            }
                            if (standing != null) {
                                Text(
                                    "The last weigh-in sits on the ${ordinal(standing)} percentile: " +
                                        "of 100 ${if (sex == Sex.GIRL) "girls" else "boys"} that age, " +
                                        "about $standing weigh less.",
                                    fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp, lineHeight = 17.sp, color = KC.MutedStrong,
                                )
                            }

                            Text(
                                when (sex) {
                                    null ->
                                        "The bands are where the middle of the population sits, " +
                                            "averaged across boys and girls — add the child's sex " +
                                            "in their details for the exact curves. A healthy " +
                                            "line can run anywhere in them, or just outside; only " +
                                            "a clinician reading the full chart can say whether a " +
                                            "reading matters."
                                    else ->
                                        "The bands hold the middle 70% and 94% of " +
                                            "${if (sex == Sex.GIRL) "girls" else "boys"} this age, " +
                                            "and the dotted line is the WHO median. A healthy line " +
                                            "can run anywhere in them, or just outside; what counts " +
                                            "is that it keeps its own shape. Only a clinician " +
                                            "reading the full chart can say whether a reading matters."
                                },
                                fontFamily = Sans, fontSize = 11.sp, lineHeight = 16.sp,
                                color = KC.Muted,
                            )
                        }
                    }
                }

                if (growth.size >= 2) {
                    Text(
                        "${growth.size} measurements recorded since birth.",
                        fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp,
                        color = KC.Muted, modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            GrowthSheet(
                hints = growthHints(latest),
                metric = metric,
                earliest = baby?.dob,
            ) { date, w, l, h ->
                vm.addGrowth(date, w, l, h)
                sheetOpen = false
            }
        }
    }
}

/** Recorded weights placed by the child's age, which is the axis the WHO curve is drawn on. */
private fun weightSeries(points: List<Pair<Double, LocalDate>>, dob: LocalDate?): List<WeightPoint> {
    if (dob == null) return emptyList()
    return points
        .map { (kg, date) ->
            WeightPoint(ChronoUnit.DAYS.between(dob, date).coerceAtLeast(0).toDouble() / DAYS_PER_MONTH, kg)
        }
        .sortedBy { it.ageMonths }
}

/** Average length of a month, so days convert to the months the WHO table is indexed by. */
private const val DAYS_PER_MONTH = 30.4375

/**
 * Placeholders for the next measurement: the last one recorded, or a rough newborn where
 * there is nothing yet. They are converted along with everything else.
 */
internal fun growthHints(latest: GrowthEntity?) = MeasurementHints(
    weight = latest?.weightKg ?: 3.9,
    lengthCm = latest?.lengthCm ?: 52.0,
    headCm = latest?.headCm ?: 36.0,
)

/** "1st", "2nd", "3rd", "11th", "62nd" — for a percentile written into a sentence. */
internal fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
