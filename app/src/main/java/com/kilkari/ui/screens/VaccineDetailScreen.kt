package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.Fmt
import com.kilkari.domain.VaccineItemState
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.MarkVaccineSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

/**
 * One age group: a CTA per dose, the cost posted to Money if any, and a "Mark all given"
 * action. Both entry points open the same sheet — a single dose is just a narrower selection.
 */
@Composable
fun VaccineDetailScreen(vm: KilkariViewModel, go: NavActions) {
    val group by vm.selectedGroup.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()

    /** Doses the open sheet is recording; empty means no sheet. */
    var sheetDoses by remember { mutableStateOf<List<VaccineItemState>>(emptyList()) }

    val g = group ?: return
    val skin = statusSkin(g.status)
    val lastClinic = appointments.firstOrNull { it.place != null }?.place.orEmpty()
    val lastDoctor = appointments.firstOrNull { it.doctor != null }?.doctor.orEmpty()
    val pending = g.items.filterNot { it.given }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("${g.label} vaccines", go::back)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KCard {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SummaryCell("Due", Fmt.date(g.dueDate), Modifier.weight(1f))
                        SummaryCell("Status", g.statusText, Modifier.weight(1f), skin.foreground)
                        SummaryCell("Doses", "${g.doneCount} / ${g.count}", Modifier.weight(1f))
                    }
                }

                KCard {
                    g.items.forEachIndexed { i, item ->
                        DoseRow(
                            item = item,
                            onMark = { sheetDoses = listOf(item) },
                            onUndo = { vm.toggleDose(g.label, item.name, false) },
                        )
                        if (i != g.items.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
                        }
                    }
                }

                g.costMinor?.let { cost ->
                    KCard(corner = 14, background = KC.TealBg, border = KC.TealRing) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(KIcons["receipt_long"], null, tint = KC.Teal, modifier = Modifier.size(20.dp))
                            Text(
                                "Posted to Money → Medical",
                                modifier = Modifier.weight(1f),
                                fontFamily = Sans, fontSize = 13.sp, color = KC.Ink,
                            )
                            Text(
                                Fmt.money(cost, currency), fontFamily = Sans,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Teal,
                            )
                        }
                    }
                }

                Text(
                    "Recording a dose adds a \"Vaccination\" entry to the timeline. Brand and cost " +
                        "are optional; cost lands under Medical expenses.",
                    fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
                )
            }

            if (pending.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp),
                ) {
                    PrimaryButton(
                        if (pending.size == g.count) "Mark all given" else "Mark remaining ${pending.size} given"
                    ) {
                        sheetDoses = pending
                    }
                }
            }
        }

        KSheet(sheetDoses.isNotEmpty(), onDismiss = { sheetDoses = emptyList() }) {
            // Guarded by the visibility check above, but read defensively for recomposition.
            val doses = sheetDoses
            if (doses.isNotEmpty()) {
                MarkVaccineSheet(
                    group = g,
                    doses = doses,
                    currency = currency,
                    defaultClinic = lastClinic,
                    defaultDoctor = lastDoctor,
                ) { clinic, doctor, brands, cost, addExpense ->
                    vm.markDosesGiven(g, doses, LocalDate.now(), clinic, doctor, brands, cost, addExpense)
                    sheetDoses = emptyList()
                }
            }
        }
    }
}

/** A dose: description and CTA when pending, brand and date once recorded. */
@Composable
private fun DoseRow(
    item: VaccineItemState,
    onMark: () -> Unit,
    onUndo: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.given) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KC.TealBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KIcons["check"], null, tint = KC.Teal, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = KC.Ink,
            )
            Text(
                if (item.given) {
                    listOfNotNull(item.brand, item.givenOn?.let { Fmt.date(it) })
                        .joinToString(" · ")
                        .ifBlank { "Recorded" }
                } else {
                    item.desc
                },
                fontFamily = Sans, fontSize = 12.sp,
                color = if (item.given) KC.Teal else KC.Muted,
            )
        }
        if (item.given) {
            Text(
                "Undo",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                fontFamily = Sans, fontSize = 12.sp, color = KC.Faint,
            )
        } else {
            Text(
                "Mark given",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(KC.CoralBg)
                    .clickable(onClick = onMark)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                fontFamily = Sans, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = KC.CoralDeep,
            )
        }
    }
}

@Composable
private fun SummaryCell(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = KC.Ink,
) {
    Column(modifier) {
        Text(
            caption.uppercase(), fontFamily = Sans, fontSize = 11.sp,
            color = KC.Muted, letterSpacing = 0.5.sp,
        )
        Text(
            value, fontFamily = Sans, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, color = valueColor,
        )
    }
}
