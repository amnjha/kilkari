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
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Fmt
import com.kilkari.domain.VaccineGroupState
import com.kilkari.domain.VaccineStatus
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.sheets.ScheduleSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/** The whole generated schedule, one card per age group. */
@Composable
fun VaccinesScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val groups by vm.vaccineGroups.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    val doneCount = groups.sumOf { it.doneCount }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Vaccinations", go::back) {
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(KC.Surface)
                        .border(1.dp, KC.BorderStrong, RoundedCornerShape(999.dp))
                        .clickable { sheetOpen = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        VaccineSchedules.byId(settings.scheduleId).shortName,
                        fontFamily = Sans, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = KC.IndigoDeep,
                    )
                    Icon(KIcons["expand_more"], null, tint = KC.IndigoDeep, modifier = Modifier.size(18.dp))
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    baby?.let { "Generated from DOB ${Fmt.dateFull(it.dob)} · $doneCount given" }.orEmpty(),
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
                groups.forEach { group ->
                    VaccineGroupCard(group) {
                        vm.selectVaccineGroup(group.index)
                        go.push(Routes.VACCINE_DETAIL)
                    }
                }
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            ScheduleSheet(settings.scheduleId) { id ->
                vm.setSchedule(id)
                sheetOpen = false
            }
        }
    }
}

@Composable
private fun VaccineGroupCard(group: VaccineGroupState, onClick: () -> Unit) {
    val skin = statusSkin(group.status)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(KC.Surface)
            .border(1.dp, skin.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(skin.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KIcons[skin.icon], null, tint = skin.foreground, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.label, fontFamily = Sans, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = KC.Ink,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(skin.background)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        group.statusText, fontFamily = Sans, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = skin.foreground,
                    )
                }
            }
            Text(Fmt.date(group.dueDate), fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
            Text(
                group.names,
                fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp, color = KC.Ink,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

internal data class StatusSkin(
    val foreground: Color,
    val background: Color,
    val border: Color,
    val icon: String,
)

internal fun statusSkin(status: VaccineStatus): StatusSkin = when (status) {
    VaccineStatus.GIVEN -> StatusSkin(KC.Green, KC.GreenBg, KC.GreenRing, "check")
    VaccineStatus.OVERDUE -> StatusSkin(KC.Rose, KC.RoseBg, KC.RoseRing, "priority_high")
    VaccineStatus.DUE_SOON -> StatusSkin(KC.FuchsiaDeep, KC.FuchsiaBg, KC.FuchsiaRing, "event_upcoming")
    VaccineStatus.UPCOMING -> StatusSkin(KC.IndigoDeep, KC.IndigoBg, KC.Border, "schedule")
}
