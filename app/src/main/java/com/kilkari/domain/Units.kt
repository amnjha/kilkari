package com.kilkari.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Weight and length in whichever units the parent reads them in.
 *
 * Everything is stored metric — kilograms, centimetres, grams — because that is what the WHO
 * tables speak and what the growth chart compares against. Conversion happens at the edges:
 * on the way onto a screen, and on the way back out of an input. Nothing in the database
 * changes when the setting is switched.
 *
 * Pounds are shown with ounces rather than as a decimal, because that is how a scale reads and
 * how a paediatrician says it: 3.4 kg is "7 lb 8 oz", not "7.5 lb".
 */
object Units {

    const val LB_PER_KG = 2.20462262185
    const val CM_PER_IN = 2.54
    const val G_PER_OZ = 28.349523125
    private const val OZ_PER_LB = 16

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG
    fun lbToKg(lb: Double): Double = lb / LB_PER_KG
    fun cmToIn(cm: Double): Double = cm / CM_PER_IN
    fun inToCm(inches: Double): Double = inches * CM_PER_IN

    /**
     * Whole pounds and the ounces left over, rounded to the nearest ounce.
     *
     * Rounding happens on the total, so 0.9999 lb reads "1 lb 0 oz" rather than "0 lb 16 oz".
     */
    fun lbOz(kg: Double): Pair<Int, Int> {
        val totalOz = (kgToLb(kg) * OZ_PER_LB).roundToInt()
        return totalOz / OZ_PER_LB to totalOz % OZ_PER_LB
    }

    fun fromLbOz(lb: Double, oz: Double): Double = lbToKg(lb + oz / OZ_PER_LB)

    /** "7 lb 8 oz" — ounces are kept even at zero, so the reading is unambiguous. */
    fun weightLabel(kg: Double): String {
        val (pounds, ounces) = lbOz(kg)
        return "$pounds lb $ounces oz"
    }

    /** "21.5 in" */
    fun lengthLabel(cm: Double): String = trim(cmToIn(cm)) + " in"

    /**
     * A change in weight: grams while metric, ounces otherwise. Gains between weigh-ins are
     * small enough that pounds would round most of them away.
     */
    fun deltaLabel(kgDelta: Double, metric: Boolean): String {
        val sign = if (kgDelta >= 0) "+" else "−"
        return if (metric) {
            sign + abs(kgDelta * 1000).roundToLong() + " g"
        } else {
            sign + trim(abs(kgDelta * 1000) / G_PER_OZ) + " oz"
        }
    }

    /** A change in length: "+1.5 cm" or "+0.6 in". */
    fun lengthDelta(cmDelta: Double, metric: Boolean): String {
        val sign = if (cmDelta >= 0) "+" else "−"
        val value = abs(if (metric) cmDelta else cmToIn(cmDelta))
        return sign + trim(value) + if (metric) " cm" else " in"
    }

    /** What an input asks for, so its label and its parsing cannot disagree. */
    fun lengthField(cm: Double?, metric: Boolean): String =
        cm?.let { trim(if (metric) it else cmToIn(it)) }.orEmpty()

    fun lengthFromField(value: Double, metric: Boolean): Double =
        if (metric) value else inToCm(value)

    private fun trim(v: Double): String =
        if (v == v.roundToLong().toDouble()) v.roundToLong().toString()
        else String.format(Locale.US, "%.1f", v)
}
