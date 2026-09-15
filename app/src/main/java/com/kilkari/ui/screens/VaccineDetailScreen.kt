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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.CheckRing
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
 * One age group: per-dose checkboxes, the cost posted to Money if any, and the
 * "Mark all given" action that writes doses, expense and timeline in one go.
 */
@Composable
fun VaccineDetailScreen(vm: KilkariViewModel, go: NavActions) {
    val group by vm.selectedGroup.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    val g = group ?: return
    val skin = statusSkin(g.status)
    val lastClinic = appointments.firstOrNull { it.place != null }?.place.orEmpty()
    val lastDoctor = appointments.firstOrNull { it.doctor != null }?.doctor.orEmpty()

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
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggleDose(g.label, item.name, !item.given) }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckRing(item.given, rounded = true)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp, color = KC.Ink,
                                )
                                Text(item.desc, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
                            }
                        }
                        if (i != g.items.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
                        }
                    }
                }

                g.costMinor?.let { cost ->
                    KCard(corner = 14, background = KC.GreenBg, border = KC.GreenRing) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(KIcons["receipt_long"], null, tint = KC.Green, modifier = Modifier.size(20.dp))
                            Text(
                                "Posted to Money → Medical",
                                modifier = Modifier.weight(1f),
                                fontFamily = Sans, fontSize = 13.sp, color = KC.Ink,
                            )
                            Text(
                                Fmt.money(cost, currency), fontFamily = Sans,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Green,
                            )
                        }
                    }
                }

                Text(
                    "Marking as given adds a \"Vaccination\" entry to the timeline. " +
                        "Cost is optional and lands under Medical expenses.",
                    fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
                )
            }

            if (!g.allGiven) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
                    PrimaryButton("Mark all given") { sheetOpen = true }
                }
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            MarkVaccineSheet(
                group = g,
                currency = currency,
                defaultClinic = lastClinic,
                defaultDoctor = lastDoctor,
            ) { clinic, doctor, cost, addExpense ->
                vm.markGroupGiven(g, LocalDate.now(), clinic, doctor, cost, addExpense)
                sheetOpen = false
            }
        }
    }
}

@Composable
private fun SummaryCell(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = KC.Ink,
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
