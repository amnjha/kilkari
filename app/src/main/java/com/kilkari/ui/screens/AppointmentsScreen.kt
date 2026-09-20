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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.AppointmentEntity
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.DoctorPickerSheets
import com.kilkari.ui.components.rememberDoctorPickerState
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.AppointmentSheet
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDateTime

/** Upcoming and past clinic visits, with a date chip in the design's calendar-tile style. */
@Composable
fun AppointmentsScreen(vm: KilkariViewModel, go: NavActions) {
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val picker = rememberDoctorPickerState()
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppointmentEntity?>(null) }

    val now = LocalDateTime.now()
    val upcoming = appointments.filter { !it.startAt.isBefore(now) }
    val past = appointments.filter { it.startAt.isBefore(now) }.reversed()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Appointments", go::back) {
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
                SectionLabel("Upcoming")
                if (upcoming.isEmpty()) {
                    KCard {
                        Text(
                            "Nothing booked. Tap + to add a visit.",
                            modifier = Modifier.padding(14.dp),
                            fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                        )
                    }
                }
                upcoming.forEachIndexed { i, appt ->
                    AppointmentCard(
                        appt = appt,
                        emphasis = i == 0,
                        onEdit = { editing = appt },
                        onDelete = { vm.deleteAppointment(appt) },
                    )
                }

                if (past.isNotEmpty()) {
                    SectionLabel("Past")
                    past.forEach { appt ->
                        AppointmentCard(
                            appt = appt,
                            emphasis = false,
                            past = true,
                            onEdit = { editing = appt },
                            onDelete = { vm.deleteAppointment(appt) },
                        )
                    }
                }
            }
        }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            AppointmentSheet(onPickDoctor = picker::open) { title, date, minute, doctor, place ->
                vm.addAppointment(
                    title, date.atTime(minute / 60, minute % 60), doctor, place, reminderDaysBefore = 1,
                )
                sheetOpen = false
            }
        }

        val edit = editing
        KSheet(edit != null, onDismiss = { editing = null }) {
            if (edit != null) {
                AppointmentSheet(
                    onPickDoctor = picker::open,
                    existing = edit,
                    onDelete = { vm.deleteAppointment(edit); editing = null },
                ) { title, date, minute, doctor, place ->
                    vm.updateAppointment(edit, title, date.atTime(minute / 60, minute % 60), doctor, place)
                    editing = null
                }
            }
        }
        DoctorPickerSheets(
            state = picker,
            doctors = doctors,
        ) { name, speciality, clinic, phone ->
            vm.saveDoctor(null, name, speciality, clinic, phone)
        }

    }
}

@Composable
private fun AppointmentCard(
    appt: AppointmentEntity,
    emphasis: Boolean,
    past: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val tint = when {
        past -> KC.StoneMid
        emphasis -> KC.CoralDeep
        else -> KC.ClayDeep
    }
    val chipBg = when {
        past -> KC.StoneBg
        emphasis -> KC.CoralBg
        else -> KC.ClayBg
    }

    // The card opens for correction, the way every other saved thing in the app does. A
    // moved appointment is the normal case; deleting and re-adding it was the only way.
    KCard(Modifier.alpha(if (past) 0.75f else 1f), onClick = onEdit) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier
                        .width(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipBg)
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        Fmt.monthShort(appt.startAt.toLocalDate()),
                        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tint,
                    )
                    Text(
                        appt.startAt.dayOfMonth.toString(),
                        fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = tint,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        appt.title, fontFamily = Sans, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = KC.Ink,
                    )
                    Text(
                        listOfNotNull(
                            Fmt.dayAndDate(appt.startAt.toLocalDate()).substringBefore(" "),
                            Fmt.time(appt.startAt),
                            appt.doctor,
                        ).joinToString(" · "),
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    )
                    appt.place?.let {
                        Text(it, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
                    }
                }
            }

            if (!past) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pillbox(
                        text = appt.reminderDaysBefore?.let {
                            "Reminder · $it ${Fmt.plural(it.toLong(), "day")} before"
                        } ?: "No reminder",
                        modifier = Modifier.weight(1f),
                        background = KC.Surface,
                        border = KC.BorderStrong,
                        color = KC.CoralDeep,
                    )
                    Pillbox(
                        text = "Remove",
                        background = KC.CoralBg,
                        border = null,
                        color = KC.CoralDeep,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun Pillbox(
    text: String,
    modifier: Modifier = Modifier,
    background: Color,
    border: Color?,
    color: Color,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .background(background)
            .let { if (border != null) it.border(1.dp, border, shape) else it }
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, color = color, textAlign = TextAlign.Center,
        )
    }
}
