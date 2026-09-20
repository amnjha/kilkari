package com.kilkari.ui.sheets

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.kilkari.data.db.BabyEntity
import com.kilkari.domain.Fmt
import com.kilkari.domain.Sex
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.rememberMeasurements
import com.kilkari.ui.components.MeasurementRows
import com.kilkari.ui.components.MEASUREMENT_KEYBOARD
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import java.time.LocalDate

/**
 * Edit the child's details. Changing the date of birth re-dates the whole vaccination
 * schedule, since due dates are derived from it rather than stored.
 */
@Composable
fun ColumnScope.BabySheet(
    baby: BabyEntity,
    metric: Boolean,
    onSave: (
        name: String,
        dob: LocalDate,
        sex: Sex?,
        place: String?,
        weight: Double?,
        length: Double?,
        head: Double?,
    ) -> Unit,
) {
    var name by remember(baby.id) { mutableStateOf(baby.name) }
    var dob by remember(baby.id) { mutableStateOf(baby.dob) }
    var sex by remember(baby.id) { mutableStateOf(Sex.of(baby.sex)) }
    var place by remember(baby.id) { mutableStateOf(baby.birthPlace.orEmpty()) }
    val m = rememberMeasurements(
        baby.birthWeightKg, baby.birthLengthCm, baby.birthHeadCm, metric, key = baby.id,
    )

    SheetTitle("${baby.name}'s details")
    SheetField("Name", name, "Name") { name = it }
    KDateField("Date of birth", dob, selectableTo = LocalDate.now()) { dob = it }

    // Only the growth chart reads this: the WHO curve is published per sex.
    SheetHint("Sex — picks the growth curve the chart compares against.")
    KSegmented(Sex.entries.map { it.label }, Sex.entries.indexOf(sex)) { sex = Sex.entries[it] }

    SheetField("Born at", place, "Hospital or city") { place = it }

    SheetHint("Birth measurements — these anchor the growth chart.")
    MeasurementRows(m) { label, value, hint, _, onChange ->
        SheetField(label, value, hint, MEASUREMENT_KEYBOARD, onChange = onChange)
    }

    if (dob != baby.dob) {
        SheetHint("Vaccination due dates will move to match the new date of birth.")
    }

    PrimaryButton("Save details", enabled = name.isNotBlank()) {
        onSave(
            name.trim(),
            dob,
            sex,
            place.trim().ifBlank { null },
            m.weightKg,
            m.lengthCm,
            m.headCm,
        )
    }
}
