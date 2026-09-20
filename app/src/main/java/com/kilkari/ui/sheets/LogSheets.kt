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
import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.MedicationEntity
import com.kilkari.domain.BreastSide
import com.kilkari.domain.DiaperKind
import com.kilkari.domain.FeedType
import com.kilkari.domain.Fmt
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.rememberMeasurements
import com.kilkari.ui.components.MeasurementRows
import com.kilkari.ui.components.MeasurementHints
import com.kilkari.ui.components.MEASUREMENT_KEYBOARD
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

/** The destructive footer an editable sheet offers once it has opened an existing entry. */
@Composable
fun SheetDelete(label: String, onDelete: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDelete)
            .padding(vertical = 8.dp),
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        color = KC.Danger, textAlign = TextAlign.Center,
    )
}

/**
 * Log a feed — breast (minutes + side), bottle (ml) or solids (g). With [existing] the same
 * form reopens the entry it was saved from, so a wrong amount or time can be corrected.
 */
@Composable
fun ColumnScope.FeedSheet(
    existing: LogEntryEntity? = null,
    /** Where a new entry starts, when it is being added to a day other than today. */
    initialAt: LocalDateTime? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (FeedType, BreastSide?, Int, LocalDateTime) -> Unit,
) {
    val logged = FeedType.of(existing?.feedType)
    var typeIndex by remember(existing) { mutableIntStateOf(FeedType.entries.indexOf(logged)) }
    val moment = rememberMoment(existing?.startAt ?: initialAt ?: LocalDateTime.now(), key = existing)
    var side by remember(existing) {
        mutableStateOf(BreastSide.entries.firstOrNull { it.key == existing?.side } ?: BreastSide.LEFT)
    }
    // One column holds the amount for all three kinds, so it seeds only the kind the entry was
    // logged as — switching kind in the sheet still offers that kind's usual starting point.
    fun seed(of: FeedType, fallback: Int) =
        if (logged == of) (existing?.amount ?: fallback) else fallback

    var minutes by remember(existing) { mutableIntStateOf(seed(FeedType.BREAST, 15)) }
    var millilitres by remember(existing) { mutableIntStateOf(seed(FeedType.BOTTLE, 60)) }
    var grams by remember(existing) { mutableIntStateOf(seed(FeedType.SOLID, 15)) }

    val type = FeedType.entries[typeIndex]

    SheetTitle(if (existing == null) "Log a feed" else "Edit feed")
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
    PrimaryButton(if (existing == null) "Save feed" else "Save changes") {
        val amount = when (type) {
            FeedType.BREAST -> minutes
            FeedType.BOTTLE -> millilitres
            FeedType.SOLID -> grams
        }
        onSave(type, if (type == FeedType.BREAST) side else null, amount, moment.value)
    }
    if (onDelete != null) SheetDelete("Delete this feed", onDelete)
}

/**
 * Start or end a nap. [asleepSince] is non-null while a sleep is in progress.
 *
 * A nap that is already over can be entered in one go: pick when it started and switch to
 * "Already woke up" to give the end, rather than having to be at the cot for both taps. That
 * same form edits [existing], which is how a nap whose ends were guessed gets corrected — so
 * an entry being edited takes precedence over any nap currently running.
 */
@Composable
fun ColumnScope.SleepSheet(
    asleepSince: LocalDateTime?,
    existing: LogEntryEntity? = null,
    initialAt: LocalDateTime? = null,
    onDelete: (() -> Unit)? = null,
    onStart: (place: String?, from: LocalDateTime, to: LocalDateTime?) -> Unit,
    onEnd: (LocalDateTime) -> Unit,
) {
    var place by remember(existing) { mutableStateOf(existing?.place ?: "Bassinet") }

    if (existing != null || asleepSince == null) {
        val start = rememberMoment(existing?.startAt ?: initialAt ?: LocalDateTime.now(), key = existing)
        var endedIndex by remember(existing) { mutableIntStateOf(if (existing?.endAt == null) 0 else 1) }
        var endMinute by remember(existing) {
            mutableIntStateOf(existing?.endAt?.let { it.hour * 60 + it.minute } ?: start.minuteOfDay)
        }
        val ended = endedIndex == 1
        val endAt = endOfNap(start.value, endMinute)

        SheetTitle(if (existing == null) "Log a nap" else "Edit nap")
        SheetField("Where", place, "Bassinet") { place = it }
        MomentFields(start, timeLabel = "Fell asleep")
        KSegmented(listOf("Still asleep", "Already woke up"), endedIndex) { endedIndex = it }
        if (ended) {
            KTimeField("Woke up", endMinute) { endMinute = it }
            SheetStatic("Slept for", Fmt.elapsed(start.value, endAt))
        }
        PrimaryButton(
            when {
                existing != null -> "Save changes"
                ended -> "Save nap"
                else -> "Start sleep"
            }
        ) {
            onStart(place.ifBlank { null }, start.value, if (ended) endAt else null)
        }
        if (onDelete != null) SheetDelete("Delete this nap", onDelete)
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
fun ColumnScope.DiaperSheet(
    existing: LogEntryEntity? = null,
    initialAt: LocalDateTime? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (DiaperKind, LocalDateTime) -> Unit,
) {
    var kindIndex by remember(existing) {
        mutableIntStateOf(DiaperKind.entries.indexOf(DiaperKind.of(existing?.diaperKind)))
    }
    val moment = rememberMoment(existing?.startAt ?: initialAt ?: LocalDateTime.now(), key = existing)

    SheetTitle(if (existing == null) "Log a diaper" else "Edit diaper")
    KSegmented(DiaperKind.entries.map { it.label }, kindIndex) { kindIndex = it }
    MomentFields(moment)
    PrimaryButton(if (existing == null) "Save diaper" else "Save changes") {
        onSave(DiaperKind.entries[kindIndex], moment.value)
    }
    if (onDelete != null) SheetDelete("Delete this diaper", onDelete)
}

/** Tick off a scheduled medicine, or jump to Medications when none is set up yet. */
@Composable
fun ColumnScope.MedicineSheet(
    medications: List<MedicationEntity>,
    initialAt: LocalDateTime? = null,
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
    val moment = rememberMoment(initialAt ?: LocalDateTime.now())
    KSegmented(medications.map { it.name }, selected) { selected = it }
    val med = medications[selected.coerceIn(medications.indices)]
    SheetStatic("Dose", med.dose)
    SheetStatic("Schedule", med.scheduleText)
    MomentFields(moment, earliest = med.startDate)
    PrimaryButton("Log ${med.name}") { onLog(med, moment.value) }
}

/**
 * A dose already logged. Which medicine it was is left alone — what needs correcting after the
 * fact is when it was given — and the whole entry can be dropped if it was never given at all.
 */
@Composable
fun ColumnScope.MedicineDoseSheet(
    entry: LogEntryEntity,
    earliest: LocalDate? = null,
    onDelete: () -> Unit,
    onSave: (LocalDateTime) -> Unit,
) {
    val moment = rememberMoment(entry.startAt, key = entry)

    SheetTitle("Edit dose")
    SheetStatic("Medicine", entry.medicationName ?: "Medicine")
    entry.dose?.let { SheetStatic("Dose", it) }
    MomentFields(moment, earliest = earliest)
    PrimaryButton("Save changes") { onSave(moment.value) }
    SheetDelete("Delete this dose", onDelete)
}

@Composable
fun ColumnScope.GrowthSheet(
    hints: MeasurementHints,
    metric: Boolean,
    earliest: LocalDate? = null,
    existing: GrowthEntity? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (LocalDate, Double?, Double?, Double?) -> Unit,
) {
    val m = rememberMeasurements(
        existing?.weightKg, existing?.lengthCm, existing?.headCm, metric, key = existing,
    )
    var date by remember(existing) { mutableStateOf(existing?.date ?: LocalDate.now()) }

    SheetTitle(if (existing == null) "Add measurement" else "Edit measurement")
    MeasurementRows(m, hints) { label, value, hint, _, onChange ->
        SheetField(label, value, hint, MEASUREMENT_KEYBOARD, onChange = onChange)
    }
    KDateField(
        "Date", date, selectableFrom = earliest, selectableTo = LocalDate.now(),
        format = { Fmt.relativeDate(it) },
    ) { date = it }
    PrimaryButton(
        if (existing == null) "Save measurement" else "Save changes",
        enabled = m.anyEntered,
    ) {
        onSave(date, m.weightKg, m.lengthCm, m.headCm)
    }
    if (onDelete != null) SheetDelete("Delete this measurement", onDelete)
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
    if (recorded != null) SheetDelete("Remove this tooth", onRemove)
}
