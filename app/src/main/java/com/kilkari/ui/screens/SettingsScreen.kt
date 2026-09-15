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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Currency
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KRow
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.OverlineLabel
import com.kilkari.ui.components.RadioRow
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

private val VARIANTS = listOf("A" to "Agenda", "B" to "Hero", "C" to "Checklist")

/** Currency, schedule, units and the Today layout — everything that reshapes other screens. */
@Composable
fun SettingsScreen(vm: KilkariViewModel, go: NavActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        DetailBar("Settings", go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OverlineLabel("Currency")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { c ->
                    KChip("${c.symbol} ${c.code}", settings.currency == c) { vm.setCurrency(c) }
                }
            }

            OverlineLabel("Vaccination schedule")
            KCard {
                VaccineSchedules.all.forEachIndexed { i, s ->
                    RadioRow(
                        title = s.name,
                        subtitle = s.desc,
                        selected = settings.scheduleId == s.id,
                        divider = i != VaccineSchedules.all.lastIndex,
                    ) { vm.setSchedule(s.id) }
                }
            }
            baby?.let {
                Text(
                    "Due dates regenerate from ${it.name}'s date of birth, ${Fmt.dateFull(it.dob)}. " +
                        "Doses already recorded stay marked.",
                    fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
                )
            }

            OverlineLabel("Units")
            KCard {
                KRow(
                    title = "Weight & length",
                    subtitle = if (settings.metricUnits) "Kilograms and centimetres" else "Pounds and inches",
                    divider = false,
                    onClick = { vm.setMetric(!settings.metricUnits) },
                ) {
                    Text(
                        if (settings.metricUnits) "kg · cm" else "lb · in",
                        fontFamily = Sans, fontSize = 13.sp, color = KC.Coral,
                    )
                }
            }

            OverlineLabel("Today screen")
            KSegmented(
                VARIANTS.map { it.second },
                VARIANTS.indexOfFirst { it.first == settings.todayVariant }.coerceAtLeast(0),
            ) { index -> vm.setTodayVariant(VARIANTS[index].first) }

            Text(
                "Kilkari works fully offline. Nothing leaves your phone unless you back up or share.",
                fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp,
                color = KC.Muted, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
