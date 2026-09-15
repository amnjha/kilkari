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
    onSave: (name: String, dob: LocalDate, place: String?, weight: Double?, length: Double?, head: Double?) -> Unit,
) {
    var name by remember(baby.id) { mutableStateOf(baby.name) }
    var dob by remember(baby.id) { mutableStateOf(baby.dob) }
    var place by remember(baby.id) { mutableStateOf(baby.birthPlace.orEmpty()) }
    var weight by remember(baby.id) { mutableStateOf(baby.birthWeightKg?.let(Fmt::trimNum).orEmpty()) }
    var length by remember(baby.id) { mutableStateOf(baby.birthLengthCm?.let(Fmt::trimNum).orEmpty()) }
    var head by remember(baby.id) { mutableStateOf(baby.birthHeadCm?.let(Fmt::trimNum).orEmpty()) }

    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    SheetTitle("${baby.name}'s details")
    SheetField("Name", name, "Name") { name = it }
    KDateField("Date of birth", dob, selectableTo = LocalDate.now()) { dob = it }
    SheetField("Born at", place, "Hospital or city") { place = it }

    SheetHint("Birth measurements — these anchor the growth chart.")
    SheetField("Weight (kg)", weight, "3.1", decimal) { weight = it }
    SheetField("Length (cm)", length, "50", decimal) { length = it }
    SheetField("Head (cm)", head, "35", decimal) { head = it }

    if (dob != baby.dob) {
        SheetHint("Vaccination due dates will move to match the new date of birth.")
    }

    PrimaryButton("Save details", enabled = name.isNotBlank()) {
        onSave(
            name.trim(),
            dob,
            place.trim().ifBlank { null },
            weight.toDoubleOrNull(),
            length.toDoubleOrNull(),
            head.toDoubleOrNull(),
        )
    }
}
