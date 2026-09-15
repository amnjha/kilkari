package com.kilkari.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.data.db.DoctorEntity
import com.kilkari.ui.theme.BarTitle
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * Doctor selection, as a pair of secondary bottom sheets over whatever form asked for it.
 *
 * The form keeps its own doctor field: it hands the picker a callback to apply the choice, so
 * a sheet that also wants the clinic filled in can do that without hoisting its whole state.
 */

/** Read-only row inside a form. Tapping it asks the host to open the picker. */
@Composable
fun DoctorPickerField(label: String, selectedName: String?, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KC.Screen)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedName?.ifBlank { null } ?: "Choose",
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (selectedName.isNullOrBlank()) KC.Faint else KC.Coral,
            )
            Icon(KIcons["chevron_right"], null, tint = KC.Faint, modifier = Modifier.size(18.dp))
        }
    }
}

/** Tracks which of the two secondary sheets, if either, is showing. */
class DoctorPickerState {
    internal var apply by mutableStateOf<((DoctorEntity?) -> Unit)?>(null)
    internal var creating by mutableStateOf(false)

    /** What the calling form currently holds, so the list can show it as chosen. */
    internal var current by mutableStateOf<String?>(null)

    val isOpen: Boolean get() = apply != null || creating

    /** Called by a form's field: remembers the current value and where to send the choice. */
    fun open(current: String?, apply: (DoctorEntity?) -> Unit) {
        this.current = current?.ifBlank { null }
        this.apply = apply
        creating = false
    }

    internal fun close() {
        apply = null
        creating = false
        current = null
    }
}

@Composable
fun rememberDoctorPickerState(): DoctorPickerState = remember { DoctorPickerState() }

/**
 * Hosts both secondary sheets. Place inside the screen's root Box, alongside the primary sheet.
 */
@Composable
fun DoctorPickerSheets(
    state: DoctorPickerState,
    doctors: List<DoctorEntity>,
    onCreate: (name: String, speciality: String?, clinic: String?, phone: String?) -> Unit,
) {
    // The list. Choosing closes both; "Add" hands over to the second sheet.
    val selectedName = state.current

    KSheet(state.apply != null && !state.creating, onDismiss = { state.close() }) {
        Text("Choose a doctor", style = BarTitle, color = KC.Ink)

        if (doctors.isEmpty()) {
            Text(
                "No doctors saved yet.",
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )
        }

        KCard {
            doctors.forEachIndexed { i, doctor ->
                val chosen = doctor.name == selectedName
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.apply?.invoke(doctor)
                                state.close()
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioDot(chosen)
                        Column(Modifier.weight(1f)) {
                            Text(
                                doctor.name, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = KC.Ink,
                            )
                            val detail = listOfNotNull(doctor.speciality, doctor.clinic)
                                .joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(detail, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
                            }
                        }
                    }
                    if (i != doctors.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
                    }
                }
            }
        }

        SecondaryButton("Add a doctor", Modifier.fillMaxWidth(), icon = "add") {
            state.creating = true
        }

        if (!selectedName.isNullOrBlank()) {
            Text(
                "Clear selection",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        state.apply?.invoke(null)
                        state.close()
                    }
                    .padding(vertical = 10.dp),
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted, textAlign = TextAlign.Center,
            )
        }
    }

    // The form. Saving selects the new doctor straight away.
    KSheet(state.creating, onDismiss = { state.creating = false }) {
        var name by remember(state.creating) { mutableStateOf("") }
        var speciality by remember(state.creating) { mutableStateOf("") }
        var clinic by remember(state.creating) { mutableStateOf("") }
        var phone by remember(state.creating) { mutableStateOf("") }

        Text("Add a doctor", style = BarTitle, color = KC.Ink)
        Text(
            "Only the name is required. They will be saved for next time.",
            fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
        )
        SheetField("Name", name, "Dr. …") { name = it }
        SheetField("Speciality", speciality, "e.g. Paediatrics") { speciality = it }
        SheetField("Clinic", clinic, "Where they practise") { clinic = it }
        SheetField(
            "Phone", phone, "Optional",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
        ) { phone = it }

        PrimaryButton("Save and choose", enabled = name.isNotBlank()) {
            val trimmed = name.trim()
            val clinicValue = clinic.trim().ifBlank { null }
            onCreate(trimmed, speciality.trim().ifBlank { null }, clinicValue, phone.trim().ifBlank { null })
            // Apply immediately so the form does not wait for the database to come back.
            state.apply?.invoke(
                DoctorEntity(name = trimmed, babyId = 0, speciality = null, clinic = clinicValue),
            )
            state.close()
        }
    }
}
