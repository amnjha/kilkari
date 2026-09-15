package com.kilkari.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.data.db.DoctorEntity
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Currency
import com.kilkari.domain.Fmt
import com.kilkari.domain.VaccineGroupState
import com.kilkari.domain.VaccineItemState
import com.kilkari.ui.components.DoctorPickerField
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.KTimeField
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.RadioDot
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

/**
 * Records the given [doses] of a group. Used both by a single dose's CTA and by "Mark all
 * given", so the data entry is identical either way: date, clinic, doctor, an optional brand
 * per vaccine, and a cost that can post to Medical expenses.
 */
@Composable
fun ColumnScope.MarkVaccineSheet(
    group: VaccineGroupState,
    doses: List<VaccineItemState>,
    currency: Currency,
    dob: LocalDate?,
    defaultClinic: String,
    defaultDoctor: String,
    onPickDoctor: (current: String?, apply: (DoctorEntity?) -> Unit) -> Unit,
    onConfirm: (
        on: LocalDate,
        clinic: String?,
        doctor: String?,
        brands: Map<String, String?>,
        costInr: Long?,
        addExpense: Boolean,
    ) -> Unit,
) {
    var given by remember { mutableStateOf(LocalDate.now()) }
    var clinic by remember { mutableStateOf(defaultClinic) }
    var doctor by remember { mutableStateOf(defaultDoctor) }
    var cost by remember { mutableStateOf("") }
    var addExpense by remember { mutableStateOf(true) }
    val brands = remember(doses) {
        mutableStateMapOf<String, String>().apply {
            doses.forEach { put(it.name, it.brand.orEmpty()) }
        }
    }

    val wholeGroup = doses.size == group.count
    val single = doses.singleOrNull()

    SheetTitle(
        when {
            wholeGroup -> group.label + " vaccines given"
            single != null -> single.name + " given"
            else -> doses.size.toString() + " doses given"
        }
    )
    SheetHint(if (single != null) single.desc else doses.joinToString(", ") { it.name })

    KDateField(
        "Date", given, selectableFrom = dob, selectableTo = LocalDate.now(),
        format = { Fmt.relativeDate(it) },
    ) { given = it }
    SheetField("Clinic", clinic, "Where it was given") { clinic = it }
    DoctorPickerField("Doctor", doctor) {
        onPickDoctor(doctor) { picked ->
            doctor = picked?.name.orEmpty()
            picked?.clinic?.takeIf { it.isNotBlank() }?.let { clinic = it }
        }
    }

    if (single != null) {
        SheetField("Brand (optional)", brands[single.name].orEmpty(), "e.g. Pentavac") {
            brands[single.name] = it
        }
    } else {
        Text(
            "BRAND (OPTIONAL)",
            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            color = KC.Muted, letterSpacing = 0.6.sp,
        )
        doses.forEach { dose ->
            SheetField(dose.name, brands[dose.name].orEmpty(), "Brand") { brands[dose.name] = it }
        }
    }

    SheetField(
        "Cost (" + currency.symbol + ")", cost, "0", big = true,
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
    ) { cost = it.filter(Char::isDigit) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { addExpense = !addExpense }
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Add to Medical expenses", fontFamily = Sans,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink,
            )
            Text("Shows up under Money", fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
        }
        KSwitch(addExpense)
    }

    PrimaryButton("Save · add to timeline") {
        val amount = cost.toDoubleOrNull()?.let { Fmt.toInr(it, currency) }
        onConfirm(
            given,
            clinic.ifBlank { null },
            doctor.ifBlank { null },
            brands.toMap(),
            amount,
            addExpense,
        )
    }
}

/** Switch schedules. Doses already recorded under the old schedule are kept, not deleted. */
@Composable
fun ColumnScope.ScheduleSheet(currentId: String, onPick: (String) -> Unit) {
    SheetTitle("Vaccination schedule")
    SheetHint("Due dates regenerate from the date of birth. Doses already given stay marked.")
    VaccineSchedules.all.forEach { s ->
        val on = s.id == currentId
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (on) KC.CoralBg else KC.Surface)
                .border(1.dp, if (on) KC.Coral else KC.CoralPale, RoundedCornerShape(16.dp))
                .clickable { onPick(s.id) }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Ink)
                Text(s.desc, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
            }
            RadioDot(on)
        }
    }
}

@Composable
fun ColumnScope.MedicationSheet(
    dob: LocalDate? = null,
    onPickDoctor: (current: String?, apply: (DoctorEntity?) -> Unit) -> Unit,
    onSave: (String, String, String, String?, LocalDate, Int?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("Daily · 8:00 pm") }
    var prescriber by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var reminderMinute by remember { mutableStateOf<Int?>(20 * 60) }

    SheetTitle("Add a medicine")
    SheetField("Medicine", name, "e.g. Vitamin D3 drops") { name = it }
    SheetField("Dose", dose, "e.g. 1 drop (400 IU)") { dose = it }
    SheetField("Schedule", schedule, "Daily · 8:00 pm") { schedule = it }
    DoctorPickerField("Prescribed by", prescriber) {
        onPickDoctor(prescriber) { picked -> prescriber = picked?.name.orEmpty() }
    }
    KDateField(
        "Started", start, selectableFrom = dob, selectableTo = LocalDate.now(),
        format = { Fmt.relativeDate(it) },
    ) { start = it }
    KTimeField("Remind at", reminderMinute) { reminderMinute = it }

    PrimaryButton("Save medicine", enabled = name.isNotBlank() && dose.isNotBlank()) {
        onSave(
            name.trim(), dose.trim(), schedule.trim(),
            prescriber.trim().ifBlank { null }, start, reminderMinute,
        )
    }
}

@Composable
fun ColumnScope.AppointmentSheet(
    onPickDoctor: (current: String?, apply: (DoctorEntity?) -> Unit) -> Unit,
    onSave: (String, LocalDate, Int, String?, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var minute by remember { mutableStateOf<Int?>(10 * 60 + 30) }
    var doctor by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }

    SheetTitle("Add an appointment")
    SheetField("What", title, "e.g. 6-week check") { title = it }
    KDateField(
        "Date", date, selectableFrom = LocalDate.now().minusYears(2),
        format = { Fmt.relativeDate(it) },
    ) { date = it }
    KTimeField("Time", minute) { minute = it }
    DoctorPickerField("Doctor", doctor) {
        onPickDoctor(doctor) { picked ->
            doctor = picked?.name.orEmpty()
            picked?.clinic?.takeIf { it.isNotBlank() }?.let { place = it }
        }
    }
    SheetField("Where", place, "Clinic or hospital") { place = it }

    PrimaryButton("Save appointment", enabled = title.isNotBlank() && date != null && minute != null) {
        onSave(title.trim(), date!!, minute!!, doctor.ifBlank { null }, place.ifBlank { null })
    }
}

/** "HH:MM" → minutes past midnight, or null when unparseable. */
internal fun parseMinute(text: String): Int? {
    val parts = text.split(':', '.')
    if (parts.size != 2) return null
    val h = parts[0].trim().toIntOrNull() ?: return null
    val m = parts[1].trim().toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

internal fun parseDayMonthYear(text: String): LocalDate? {
    val parts = text.split('-', '/', '.').map { it.trim() }
    if (parts.size != 3) return null
    return runCatching { LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt()) }.getOrNull()
}
