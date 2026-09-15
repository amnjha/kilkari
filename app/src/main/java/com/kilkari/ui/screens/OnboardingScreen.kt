package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Currency
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.Hint
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.OverlineLabel
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.RadioRow
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.components.ValueField
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.ScreenTitle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

/**
 * First run: name, date of birth and the two settings that shape every other screen —
 * which vaccination schedule to generate and which currency to spend in.
 */
@Composable
fun OnboardingScreen(vm: KilkariViewModel) {
    var name by remember { mutableStateOf("") }
    var dobText by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var scheduleId by remember { mutableStateOf("iap") }
    var currency by remember { mutableStateOf(Currency.INR) }

    val dob = remember(dobText) { parseDate(dobText) }
    val valid = name.isNotBlank() && dob != null && !dob.isAfter(LocalDate.now())

    Box(
        Modifier
            .fillMaxSize()
            .background(KC.Screen),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(KC.IndigoDeep, KC.Violet, KC.Fuchsia)))
                    .padding(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Kilkari",
                        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp, color = Color.White, letterSpacing = (-0.7).sp,
                    )
                    Text(
                        "Everything for your baby, on your phone.",
                        fontFamily = Sans, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            Text("Tell us about your baby", style = ScreenTitle, color = KC.Ink)
            Hint("Nothing leaves this phone. You can change any of it later.")

            KCard {
                ValueField("Name", name, "e.g. Avika") { name = it }
                ValueField(
                    "Date of birth", dobText, "DD-MM-YYYY",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                ) { dobText = it }
                ValueField("Born at", place, "Hospital or city", divider = false) { place = it }
            }

            if (dob != null) {
                Hint("${name.ifBlank { "Baby" }} is ${Fmt.age(dob)} today.")
            } else if (dobText.isNotBlank()) {
                Text(
                    "Use DD-MM-YYYY, e.g. 20-08-2026",
                    fontFamily = Sans, fontSize = 13.sp, color = KC.Rose,
                )
            }

            SectionLabel("Birth measurements (optional)")
            KCard {
                ValueField(
                    "Weight (kg)", weight, "3.1",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                ) { weight = it }
                ValueField(
                    "Length (cm)", length, "50",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                ) { length = it }
                ValueField(
                    "Head (cm)", head, "35", divider = false,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                ) { head = it }
            }

            OverlineLabel("Vaccination schedule")
            Hint("Due dates are generated from the date of birth.")
            KCard {
                VaccineSchedules.all.forEachIndexed { i, s ->
                    RadioRow(
                        title = s.name,
                        subtitle = s.desc,
                        selected = scheduleId == s.id,
                        divider = i != VaccineSchedules.all.lastIndex,
                    ) { scheduleId = s.id }
                }
            }

            OverlineLabel("Currency")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Currency.entries.forEach { c ->
                    KChip("${c.symbol} ${c.code}", currency == c) { currency = c }
                }
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton("Start tracking", enabled = valid) {
                vm.createBaby(
                    name = name.trim(),
                    dob = dob!!,
                    birthTime = null,
                    weightKg = weight.toDoubleOrNull(),
                    lengthCm = length.toDoubleOrNull(),
                    headCm = head.toDoubleOrNull(),
                    place = place.trim().ifBlank { null },
                    scheduleId = scheduleId,
                    currency = currency,
                )
            }
        }
    }
}

/** Accepts DD-MM-YYYY with `-`, `/` or `.` separators. */
internal fun parseDate(text: String): LocalDate? {
    val parts = text.split('-', '/', '.').map { it.trim() }
    if (parts.size != 3) return null
    val (d, m, y) = parts
    return runCatching {
        LocalDate.of(y.toInt(), m.toInt(), d.toInt())
    }.getOrNull()
}
