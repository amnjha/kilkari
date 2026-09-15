package com.kilkari.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.data.db.MedicationEntity
import com.kilkari.domain.BreastSide
import com.kilkari.domain.DiaperKind
import com.kilkari.domain.FeedType
import com.kilkari.domain.Fmt
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.components.SheetStatic
import com.kilkari.ui.components.Stepper
import com.kilkari.ui.theme.BarTitle
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
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
fun ColumnScope.FeedSheet(onSave: (FeedType, BreastSide?, Int) -> Unit) {
    var typeIndex by remember { mutableIntStateOf(0) }
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

    SheetStatic("Started", "Now · ${Fmt.time(LocalDateTime.now())}")
    PrimaryButton("Save feed") {
        val amount = when (type) {
            FeedType.BREAST -> minutes
            FeedType.BOTTLE -> millilitres
            FeedType.SOLID -> grams
        }
        onSave(type, if (type == FeedType.BREAST) side else null, amount)
    }
}

/** Start or end a nap. [asleepSince] is non-null while a sleep is in progress. */
@Composable
fun ColumnScope.SleepSheet(
    asleepSince: LocalDateTime?,
    onStart: (String?) -> Unit,
    onEnd: () -> Unit,
) {
    var place by remember { mutableStateOf("Bassinet") }

    if (asleepSince == null) {
        SheetTitle("Start a nap")
        SheetField("Where", place, "Bassinet") { place = it }
        SheetStatic("Fell asleep", "Now · ${Fmt.time(LocalDateTime.now())}")
        PrimaryButton("Start sleep") { onStart(place.ifBlank { null }) }
    } else {
        SheetTitle("End the nap")
        SheetStatic("Fell asleep", Fmt.time(asleepSince))
        SheetStatic("Slept for", Fmt.elapsed(asleepSince))
        PrimaryButton("Woke up now") { onEnd() }
    }
}

@Composable
fun ColumnScope.DiaperSheet(onSave: (DiaperKind) -> Unit) {
    var kindIndex by remember { mutableIntStateOf(0) }

    SheetTitle("Log a diaper")
    KSegmented(DiaperKind.entries.map { it.label }, kindIndex) { kindIndex = it }
    SheetStatic("Time", "Now · ${Fmt.time(LocalDateTime.now())}")
    PrimaryButton("Save diaper") { onSave(DiaperKind.entries[kindIndex]) }
}

/** Tick off a scheduled medicine, or jump to Medications when none is set up yet. */
@Composable
fun ColumnScope.MedicineSheet(
    medications: List<MedicationEntity>,
    onLog: (MedicationEntity) -> Unit,
    onManage: () -> Unit,
) {
    SheetTitle("Log medicine")
    if (medications.isEmpty()) {
        SheetHint("No active medicines yet.")
        PrimaryButton("Add one") { onManage() }
        return
    }
    var selected by remember { mutableIntStateOf(0) }
    KSegmented(medications.map { it.name }, selected) { selected = it }
    val med = medications[selected]
    SheetStatic("Dose", med.dose)
    SheetStatic("Schedule", med.scheduleText)
    SheetStatic("Time", "Now · ${Fmt.time(LocalDateTime.now())}")
    PrimaryButton("Log ${med.name}") { onLog(med) }
}

@Composable
fun ColumnScope.GrowthSheet(
    weightHint: String,
    lengthHint: String,
    headHint: String,
    onSave: (Double?, Double?, Double?) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    SheetTitle("Add measurement")
    SheetField("Weight (kg)", weight, weightHint, decimal) { weight = it }
    SheetField("Length (cm)", length, lengthHint, decimal) { length = it }
    SheetField("Head (cm)", head, headHint, decimal) { head = it }
    SheetStatic("Date", "Today")
    PrimaryButton(
        "Save measurement",
        enabled = listOf(weight, length, head).any { it.toDoubleOrNull() != null },
    ) {
        onSave(weight.toDoubleOrNull(), length.toDoubleOrNull(), head.toDoubleOrNull())
    }
}
