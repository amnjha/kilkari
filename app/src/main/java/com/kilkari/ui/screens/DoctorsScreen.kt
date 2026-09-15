package com.kilkari.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.kilkari.ui.components.KIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.DoctorEntity
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.IconButton44
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KRow
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.sheets.SheetHint
import com.kilkari.ui.sheets.SheetTitle
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * The family's doctors. Appointments, vaccinations and prescriptions pick from here rather
 * than asking for the name again each time.
 */
@Composable
fun DoctorsScreen(vm: KilkariViewModel, go: NavActions) {
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /** Non-null while the editor is open; the inner value is null for a new doctor. */
    var editing by remember { mutableStateOf<Pair<DoctorEntity?, Boolean>?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Doctors", go::back) {
                IconButton44("add", KC.Coral, { editing = null to true }, iconSize = 26)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KCard {
                    if (doctors.isEmpty()) {
                        Text(
                            "No one saved yet. Add a doctor here, or add one while booking an " +
                                "appointment or recording a vaccine.",
                            modifier = Modifier.padding(14.dp),
                            fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
                        )
                    }
                    doctors.forEachIndexed { i, doctor ->
                        KRow(
                            title = doctor.name,
                            subtitle = listOfNotNull(doctor.speciality, doctor.clinic, doctor.phone)
                                .joinToString(" · ")
                                .ifBlank { "Tap to add details" },
                            icon = "stethoscope",
                            iconTint = KC.Coral,
                            iconBg = KC.CoralBg,
                            divider = i != doctors.lastIndex,
                            onClick = { editing = doctor to true },
                        ) {
                            // A saved number is only useful if you can act on it from here.
                            doctor.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                                ContactAction("call", KC.Teal, KC.TealBg) {
                                    if (!dial(context, phone)) vm.toast("No dialler on this phone")
                                }
                                ContactAction("chat", KC.Clay, KC.ClayBg) {
                                    if (!whatsApp(context, phone)) vm.toast("Could not open WhatsApp")
                                }
                            }
                        }
                    }
                }
                Text(
                    "Records keep the doctor's name as it was at the time, so renaming someone " +
                        "here does not rewrite past appointments or vaccinations.",
                    fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
                )
            }
        }

        val open = editing
        KSheet(open != null, onDismiss = { editing = null }) {
            if (open != null) {
                DoctorSheet(
                    existing = open.first,
                    onSave = { id, name, speciality, clinic, phone ->
                        vm.saveDoctor(id, name, speciality, clinic, phone)
                        editing = null
                    },
                    onDelete = open.first?.let { existing ->
                        {
                            vm.deleteDoctor(existing)
                            editing = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DoctorSheet(
    existing: DoctorEntity?,
    onSave: (Long?, String, String?, String?, String?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var speciality by remember(existing) { mutableStateOf(existing?.speciality.orEmpty()) }
    var clinic by remember(existing) { mutableStateOf(existing?.clinic.orEmpty()) }
    var phone by remember(existing) { mutableStateOf(existing?.phone.orEmpty()) }

    SheetTitle(if (existing == null) "Add a doctor" else "Edit doctor")
    SheetHint("Only the name is required.")
    SheetField("Name", name, "Dr. …") { name = it }
    SheetField("Speciality", speciality, "e.g. Paediatrics") { speciality = it }
    SheetField("Clinic", clinic, "Where they practise") { clinic = it }
    SheetField(
        "Phone", phone, "Optional",
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
    ) { phone = it }

    PrimaryButton(if (existing == null) "Add doctor" else "Save", enabled = name.isNotBlank()) {
        onSave(
            existing?.id,
            name.trim(),
            speciality.trim().ifBlank { null },
            clinic.trim().ifBlank { null },
            phone.trim().ifBlank { null },
        )
    }

    if (onDelete != null) {
        Text(
            "Remove doctor",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDelete)
                .padding(vertical = 10.dp),
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = KC.Danger, textAlign = TextAlign.Center,
        )
    }
}


/** Small round action beside a doctor's name. */
@Composable
private fun ContactAction(
    icon: String,
    tint: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(KIcons[icon], contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Opens the dialler with the number filled in. Deliberately ACTION_DIAL rather than
 * ACTION_CALL: the call is never placed without the user pressing dial, and no
 * CALL_PHONE permission is needed.
 */
private fun dial(context: Context, phone: String): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)

/**
 * Opens a WhatsApp chat through wa.me, which hands off to the app when it is installed and
 * falls back to the browser when it is not.
 */
private fun whatsApp(context: Context, phone: String): Boolean = runCatching {
    val digits = phone.filter(Char::isDigit)
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)
