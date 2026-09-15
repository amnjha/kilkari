package com.kilkari.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.data.db.MedicationEntity
import com.kilkari.domain.BreastSide
import com.kilkari.domain.DiaperKind
import com.kilkari.domain.FeedType
import com.kilkari.domain.Fmt
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.KTimeField
import com.kilkari.ui.components.MomentFields
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.components.SheetStatic
import com.kilkari.ui.components.Stepper
import com.kilkari.ui.components.rememberMoment
import com.kilkari.ui.theme.BarTitle
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun SheetTitle(text: String) {
    Text(text, style = BarTitle, color = KC.Ink)
}

@Composable
fun SheetHint(text: String) {
    Text(text, fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
}

/** Log a feed — breast (minutes + side), bottle (ml) or solids (g). */
@Composable
fun ColumnScope.FeedSheet(onSave: (FeedType, BreastSide?, Int, LocalDateTime) -> Unit) {
    var typeIndex by remember { mutableIntStateOf(0) }
    val moment = rememberMoment()
    var side by remember { mutableStateOf(BreastSide.LEFT) }
    var minutes by remember { mutableIntStateOf(15) }
    var millilitres by remember { mutableIntStateOf(60) }
    var grams by remember { mutableIntStateOf(15) }

    val type = FeedType.entries[typeIndex]

    SheetTitle("Log a feed")
    KSegmented(FeedType.entries.map { it.label }, typeIndex) { typeIndex = it }

    if (type == FeedType.BREAST) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BreastSide.entries.forEach { s ->
                KChip(s.label, side == s, Modifier.weight(1f)) { side = s }
            }
        }
    }

    when (type) {
        FeedType.BREAST -> Stepper("Duration", "$minutes min", { minutes = (minutes - 1).coerceAtLeast(1) }) { minutes++ }
        FeedType.BOTTLE -> Stepper("Amount", "$millilitres ml", { millilitres = (millilitres - 10).coerceAtLeast(10) }) { millilitres += 10 }
        FeedType.SOLID -> Stepper("Amount", "$grams g", { grams = (grams - 5).coerceAtLeast(5) }) { grams += 5 }
    }

    MomentFields(moment, timeLabel = "Started")
    PrimaryButton("Save feed") {
        val amount = when (type) {
            FeedType.BREAST -> minutes
            FeedType.BOTTLE -> millilitres
            FeedType.SOLID -> grams
        }
        onSave(type, if (type == FeedType.BREAST) side else null, amount, moment.value)
    }
}

/**
 * Start or end a nap. [asleepSince] is non-null while a sleep is in progress.
 *
 * A nap that is already over can be entered in one go: pick when it started and switch to
 * "Already woke up" to give the end, rather than having to be at the cot for both taps.
 */
@Composable
fun ColumnScope.SleepSheet(
    asleepSince: LocalDateTime?,
    onStart: (place: String?, from: LocalDateTime, to: LocalDateTime?) -> Unit,
    onEnd: (LocalDateTime) -> Unit,
) {
    var place by remember { mutableStateOf("Bassinet") }

    if (asleepSince == null) {
        val start = rememberMoment()
        var endedIndex by remember { mutableIntStateOf(0) }
        var endMinute by remember { mutableIntStateOf(start.minuteOfDay) }
        val ended = endedIndex == 1
        val endAt = endOfNap(start.value, endMinute)

        SheetTitle("Log a nap")
        SheetField("Where", place, "Bassinet") { place = it }
        MomentFields(start, timeLabel = "Fell asleep")
        KSegmented(listOf("Still asleep", "Already woke up"), endedIndex) { endedIndex = it }
        if (ended) {
            KTimeField("Woke up", endMinute) { endMinute = it }
            SheetStatic("Slept for", Fmt.elapsed(start.value, endAt))
        }
        PrimaryButton(if (ended) "Save nap" else "Start sleep") {
            onStart(place.ifBlank { null }, start.value, if (ended) endAt else null)
        }
    } else {
        val woke = rememberMoment()
        val tooEarly = woke.value.isBefore(asleepSince)

        SheetTitle("End the nap")
        SheetStatic("Fell asleep", Fmt.relativeDate(asleepSince.toLocalDate()) + " · " + Fmt.time(asleepSince))
        MomentFields(woke, timeLabel = "Woke up", earliest = asleepSince.toLocalDate())
        SheetStatic("Slept for", if (tooEarly) "—" else Fmt.elapsed(asleepSince, woke.value))
        if (tooEarly) SheetHint("The nap cannot end before it started.")
        PrimaryButton("Save nap", enabled = !tooEarly) { onEnd(woke.value) }
    }
}

/** A nap whose end reads earlier than its start ran past midnight, so it lands the next day. */
private fun endOfNap(start: LocalDateTime, endMinute: Int): LocalDateTime {
    val sameDay = start.toLocalDate().atTime(endMinute / 60, endMinute % 60)
    return if (sameDay.isBefore(start)) sameDay.plusDays(1) else sameDay
}

@Composable
fun ColumnScope.DiaperSheet(onSave: (DiaperKind, LocalDateTime) -> Unit) {
    var kindIndex by remember { mutableIntStateOf(0) }
    val moment = rememberMoment()

    SheetTitle("Log a diaper")
    KSegmented(DiaperKind.entries.map { it.label }, kindIndex) { kindIndex = it }
    MomentFields(moment)
    PrimaryButton("Save diaper") { onSave(DiaperKind.entries[kindIndex], moment.value) }
}

/** Tick off a scheduled medicine, or jump to Medications when none is set up yet. */
@Composable
fun ColumnScope.MedicineSheet(
    medications: List<MedicationEntity>,
    onLog: (MedicationEntity, LocalDateTime) -> Unit,
    onManage: () -> Unit,
) {
    SheetTitle("Log medicine")
    if (medications.isEmpty()) {
        SheetHint("No active medicines yet.")
        PrimaryButton("Add one") { onManage() }
        return
    }
    var selected by remember { mutableIntStateOf(0) }
    val moment = rememberMoment()
    KSegmented(medications.map { it.name }, selected) { selected = it }
    val med = medications[selected.coerceIn(medications.indices)]
    SheetStatic("Dose", med.dose)
    SheetStatic("Schedule", med.scheduleText)
    MomentFields(moment, earliest = med.startDate)
    PrimaryButton("Log ${med.name}") { onLog(med, moment.value) }
}

@Composable
fun ColumnScope.GrowthSheet(
    weightHint: String,
    lengthHint: String,
    headHint: String,
    earliest: LocalDate? = null,
    onSave: (LocalDate, Double?, Double?, Double?) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    SheetTitle("Add measurement")
    SheetField("Weight (kg)", weight, weightHint, decimal) { weight = it }
    SheetField("Length (cm)", length, lengthHint, decimal) { length = it }
    SheetField("Head (cm)", head, headHint, decimal) { head = it }
    KDateField(
        "Date", date, selectableFrom = earliest, selectableTo = LocalDate.now(),
        format = { Fmt.relativeDate(it) },
    ) { date = it }
    PrimaryButton(
        "Save measurement",
        enabled = listOf(weight, length, head).any { it.toDoubleOrNull() != null },
    ) {
        onSave(date, weight.toDoubleOrNull(), length.toDoubleOrNull(), head.toDoubleOrNull())
    }
}

/**
 * Records a tooth. The date is the point: a tooth is usually noticed days after it broke
 * through, so the chart would otherwise be a record of when someone looked.
 */
@Composable
fun ColumnScope.ToothSheet(
    label: String,
    recorded: LocalDate?,
    earliest: LocalDate? = null,
    onSave: (LocalDate) -> Unit,
    onRemove: () -> Unit,
) {
    var date by remember { mutableStateOf(recorded ?: LocalDate.now()) }

    SheetTitle(if (recorded == null) "Tooth appeared" else "Tooth recorded")
    SheetHint(label)
    KDateField(
        "Appeared on", date, selectableFrom = earliest, selectableTo = LocalDate.now(),
        format = { Fmt.relativeDate(it) },
    ) { date = it }
    PrimaryButton(if (recorded == null) "Save tooth" else "Save date") { onSave(date) }
    if (recorded != null) {
        Text(
            "Remove this tooth",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRemove)
                .padding(vertical = 8.dp),
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = KC.Danger, textAlign = TextAlign.Center,
        )
    }
}
