package com.kilkari.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.kilkari.domain.Units

/** The keyboard every measurement field asks for. */
val MEASUREMENT_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/**
 * What the parent typed, and what that means in kilograms and centimetres.
 *
 * Imperial weight is two boxes rather than a decimal, because that is how a scale reads: a
 * parent who sees "7 lb 8 oz" should not have to work out 7.5. Whatever is typed, the values
 * handed back are metric — that is what the database and the WHO curves hold.
 */
@Stable
class MeasurementState(
    weightKg: Double?,
    lengthCm: Double?,
    headCm: Double?,
    val metric: Boolean,
) {
    /** Kilograms while metric; whole pounds otherwise. */
    var weight by mutableStateOf(
        when {
            weightKg == null -> ""
            metric -> Units.lengthField(weightKg, metric = true)
            else -> Units.lbOz(weightKg).first.toString()
        }
    )

    /** The ounces box, imperial only. */
    var ounces by mutableStateOf(
        if (weightKg == null || metric) "" else Units.lbOz(weightKg).second.toString()
    )

    var length by mutableStateOf(Units.lengthField(lengthCm, metric))
    var head by mutableStateOf(Units.lengthField(headCm, metric))

    val weightKg: Double?
        get() = if (metric) {
            weight.toDoubleOrNull()
        } else {
            val pounds = weight.toDoubleOrNull()
            val oz = ounces.toDoubleOrNull()
            if (pounds == null && oz == null) null else Units.fromLbOz(pounds ?: 0.0, oz ?: 0.0)
        }

    val lengthCm: Double? get() = length.toDoubleOrNull()?.let { Units.lengthFromField(it, metric) }
    val headCm: Double? get() = head.toDoubleOrNull()?.let { Units.lengthFromField(it, metric) }

    val anyEntered: Boolean get() = weightKg != null || lengthCm != null || headCm != null
}

@Composable
fun rememberMeasurements(
    weightKg: Double? = null,
    lengthCm: Double? = null,
    headCm: Double? = null,
    metric: Boolean,
    key: Any? = null,
): MeasurementState = remember(key, metric) { MeasurementState(weightKg, lengthCm, headCm, metric) }

/**
 * The three measurement rows, asked for in the unit the parent reads.
 *
 * [row] draws one line, so a bottom sheet and the onboarding card can use their own field
 * styles without the labels, hints or parsing being written out twice.
 */
@Composable
fun MeasurementRows(
    state: MeasurementState,
    hints: MeasurementHints = MeasurementHints(),
    row: @Composable (label: String, value: String, hint: String, last: Boolean, onChange: (String) -> Unit) -> Unit,
) {
    if (state.metric) {
        row("Weight (kg)", state.weight, hints.weightKg, false) { state.weight = it }
    } else {
        row("Weight (lb)", state.weight, hints.weightLb, false) { state.weight = it }
        row("and (oz)", state.ounces, hints.weightOz, false) { state.ounces = it }
    }
    val lengthLabel = if (state.metric) "Length (cm)" else "Length (in)"
    val headLabel = if (state.metric) "Head (cm)" else "Head (in)"
    row(lengthLabel, state.length, hints.length(state.metric), false) { state.length = it }
    row(headLabel, state.head, hints.head(state.metric), true) { state.head = it }
}

/**
 * Placeholder numbers. They default to a newborn's, and the growth sheet passes the last
 * measurement instead so the next one starts from somewhere familiar.
 */
data class MeasurementHints(
    val weight: Double = 3.1,
    val lengthCm: Double = 50.0,
    val headCm: Double = 35.0,
) {
    val weightKg: String get() = Units.lengthField(weight, metric = true)
    val weightLb: String get() = Units.lbOz(weight).first.toString()
    val weightOz: String get() = Units.lbOz(weight).second.toString()
    fun length(metric: Boolean): String = Units.lengthField(lengthCm, metric)
    fun head(metric: Boolean): String = Units.lengthField(headCm, metric)
}
