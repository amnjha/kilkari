package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.StatCell
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell(
                        "Weight", Fmt.weight(latest?.weightKg),
                        weightDelta?.let { Fmt.grams(it) },
                        if ((weightDelta ?: 0.0) >= 0) KC.Teal else KC.Danger,
                        Modifier.weight(1f),
                    )
                    StatCell(
                        "Length", Fmt.length(latest?.lengthCm),
                        lengthDelta?.let { "${if (it >= 0) "+" else ""}${Fmt.trimNum(it)} cm" },
                        KC.Teal, Modifier.weight(1f),
                    )
                    StatCell(
                        "Head", latest?.headCm?.let { "${Fmt.trimNum(it)} cm" } ?: "—",
                        null, KC.Muted, Modifier.weight(1f),
                    )
                }

                val bars = weightBars(growth.mapNotNull { g -> g.weightKg?.let { it to g.date } }, baby?.dob)
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
                            Text("kg", fontFamily = Sans, fontSize = 11.sp, color = KC.Muted)
                        }
                        if (bars.isEmpty()) {
                            Text(
                                "Add a weight to start the chart.",
                                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                            )
                        } else {
                            Row(
                                Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                val max = bars.maxOf { it.value }
                                bars.forEach { bar ->
                                    Column(
                                        Modifier.weight(1f).fillMaxHeight(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                    ) {
                                        Text(
                                            Fmt.trimNum(bar.value), fontFamily = Sans,
                                            fontWeight = FontWeight.Bold, fontSize = 11.sp, color = KC.Coral,
                                        )
                                        Box(
                                            Modifier
                                                .padding(vertical = 6.dp)
                                                .fillMaxWidth()
                                                .fillMaxHeight((bar.value / max).toFloat().coerceIn(0.08f, 1f))
                                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                                .background(Brush.verticalGradient(listOf(KC.CoralLight, KC.Coral))),
                                        )
                                        Text(bar.label, fontFamily = Sans, fontSize = 11.sp, color = KC.Muted)
                                    }
                                }
                            }
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
                weightHint = latest?.weightKg?.let(Fmt::trimNum) ?: "3.9",
                lengthHint = latest?.lengthCm?.let(Fmt::trimNum) ?: "52",
                headHint = latest?.headCm?.let(Fmt::trimNum) ?: "36",
            ) { w, l, h ->
                vm.addGrowth(LocalDate.now(), w, l, h)
                sheetOpen = false
            }
        }
    }
}

internal data class Bar(val label: String, val value: Double)

/** Last eight weight readings, labelled "Birth" then by week since birth. */
private fun weightBars(points: List<Pair<Double, LocalDate>>, dob: LocalDate?): List<Bar> =
    points.takeLast(8).map { (kg, date) ->
        val label = when {
            dob == null -> Fmt.date(date)
            date == dob -> "Birth"
            else -> "Wk ${ChronoUnit.WEEKS.between(dob, date).coerceAtLeast(1)}"
        }
        Bar(label, kg)
    }
