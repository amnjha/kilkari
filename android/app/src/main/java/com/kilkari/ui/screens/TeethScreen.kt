package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.kilkari.domain.ToothChart
import com.kilkari.domain.ToothSpec
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.StatCell
import com.kilkari.ui.components.StatRow
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.ToothSheet
import com.kilkari.ui.theme.KDepth
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/** Tap a tooth when it appears; the chart is the record. */
@Composable
fun TeethScreen(vm: KilkariViewModel, go: NavActions) {
    val teeth by vm.teeth.collectAsStateWithLifecycle()
    val dates by vm.toothDates.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()

    /** The code of the tooth whose sheet is open. */
    var open by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        DetailBar("Teeth", go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = KDepth.navClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Tap a tooth when it appears. The date is yours to set, so one noticed late " +
                    "still lands on the day it came through.",
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )

            KCard {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "UPPER", modifier = Modifier.fillMaxWidth(),
                        fontFamily = Sans, fontSize = 11.sp, color = KC.Muted,
                        letterSpacing = 0.7.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    ToothRow(ToothChart.UPPER, prefix = "u", upper = true, erupted = teeth) { code ->
                        open = code
                    }
                    // A gap between the arches. At the old 6dp the two rows read as one block
                    // of twenty boxes, with the UPPER and LOWER labels floating unattached.
                    Spacer(Modifier.height(6.dp))
                    ToothRow(ToothChart.LOWER, prefix = "l", upper = false, erupted = teeth) { code ->
                        open = code
                    }
                    Text(
                        "LOWER", modifier = Modifier.fillMaxWidth(),
                        fontFamily = Sans, fontSize = 11.sp, color = KC.Muted,
                        letterSpacing = 0.7.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            val next = ToothChart.nextExpected(teeth)
            StatRow {
                StatCell("Erupted", "${teeth.size} / ${ToothChart.TOTAL}", modifier = Modifier.weight(1f))
                StatCell(
                    caption = "Next expected",
                    value = next?.let { ToothChart.labelFor(it.first) } ?: "All through",
                    note = next?.let { "${it.second.fromMonth}–${it.second.toMonth} mo" },
                    noteColor = KC.Muted,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val opened = open
        KSheet(opened != null, onDismiss = { open = null }) {
            if (opened != null) {
                val code = opened
                val spec = ToothChart.specFor(code)
                ToothSheet(
                    label = "${ToothChart.labelFor(code)} · usually ${spec.fromMonth}–${spec.toMonth} mo",
                    recorded = dates[code],
                    earliest = baby?.dob,
                    onSave = { on ->
                        vm.toggleTooth(code, true, on)
                        open = null
                    },
                    onRemove = {
                        vm.toggleTooth(code, false)
                        open = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ToothRow(
    specs: List<ToothSpec>,
    prefix: String,
    upper: Boolean,
    erupted: Set<String>,
    onTap: (code: String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        specs.forEachIndexed { i, spec ->
            val code = "$prefix$i"
            val on = code in erupted
            val shape = if (upper) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
            } else {
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            }
            Box(
                Modifier
                    // Ten teeth cannot each be 48dp wide on a phone, so the row takes all the
                    // width there is and splits it evenly, and the height carries the target.
                    .weight(1f)
                    .height(52.dp)
                    .clip(shape)
                    .background(if (on) KC.SeaBg else KC.Surface)
                    .border(2.dp, if (on) KC.Sea else KC.BorderStrong, shape)
                    .clickable { onTap(code) }
                    .padding(bottom = if (upper) 5.dp else 0.dp, top = if (upper) 0.dp else 5.dp),
                contentAlignment = if (upper) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                Text(
                    // The month the window opens: when to start looking, not an average.
                    "${spec.fromMonth}m",
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = if (on) KC.SeaDeep else KC.Faint,
                )
            }
        }
    }
}
