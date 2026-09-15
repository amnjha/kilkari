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
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.StatCell
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.ToothSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/** Typical eruption month for each of the twenty primary teeth, left to right. */
private val UPPER_MONTHS = listOf(24, 18, 12, 9, 10, 10, 9, 12, 18, 24)
private val LOWER_MONTHS = listOf(22, 15, 12, 10, 7, 7, 10, 12, 15, 22)

/** Tap a tooth when it appears; the chart is the record. */
@Composable
fun TeethScreen(vm: KilkariViewModel, go: NavActions) {
    val teeth by vm.teeth.collectAsStateWithLifecycle()
    val dates by vm.toothDates.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()

    /** The tooth whose sheet is open, as code to typical eruption month. */
    var open by remember { mutableStateOf<Pair<String, Int>?>(null) }

    Column(Modifier.fillMaxSize()) {
        DetailBar("Teeth", go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 24.dp),
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
                    ToothRow(UPPER_MONTHS, prefix = "u", upper = true, erupted = teeth) { code, month ->
                        open = code to month
                    }
                    ToothRow(LOWER_MONTHS, prefix = "l", upper = false, erupted = teeth) { code, month ->
                        open = code to month
                    }
                    Text(
                        "LOWER", modifier = Modifier.fillMaxWidth(),
                        fontFamily = Sans, fontSize = 11.sp, color = KC.Muted,
                        letterSpacing = 0.7.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCell("Erupted", "${teeth.size} / 20", modifier = Modifier.weight(1f))
                StatCell("Usually first", "Lower central", "6–10 mo", KC.Muted, Modifier.weight(1f))
            }
        }

        val opened = open
        KSheet(opened != null, onDismiss = { open = null }) {
            if (opened != null) {
                val (code, month) = opened
                val jaw = if (code.startsWith("u")) "Upper" else "Lower"
                ToothSheet(
                    label = "$jaw tooth · usually ${month}m",
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
    months: List<Int>,
    prefix: String,
    upper: Boolean,
    erupted: Set<String>,
    onTap: (code: String, typicalMonth: Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        months.forEachIndexed { i, month ->
            val code = "$prefix$i"
            val on = code in erupted
            val shape = if (upper) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
            } else {
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            }
            Box(
                Modifier
                    .width(28.dp)
                    .height(40.dp)
                    .clip(shape)
                    .background(if (on) KC.SeaBg else KC.Surface)
                    .border(2.dp, if (on) KC.Sea else KC.BorderStrong, shape)
                    .clickable { onTap(code, month) }
                    .padding(bottom = if (upper) 3.dp else 0.dp, top = if (upper) 0.dp else 3.dp),
                contentAlignment = if (upper) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                Text(
                    "${month}m",
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                    color = if (on) KC.SeaDeep else KC.Faint,
                )
            }
        }
    }
}
