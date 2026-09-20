package com.kilkari.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Weight-for-age from the WHO Child Growth Standards (2006), birth to two years.
 *
 * The standards are published as an LMS triple per age: a skew (L), the median (M) and a
 * coefficient of variation (S). Together they describe the whole distribution, not just its
 * middle, which is what lets the chart draw the percentile bands and say where one reading
 * sits. Values are taken from WHO's own expanded weight-for-age z-score tables, read at the
 * day each completed month falls on.
 *
 * A percentile is a position among healthy children, not a grade: the 15th and the 85th are
 * both ordinary. What matters clinically is the shape of a child's own line over time, and
 * only a clinician reading the full chart can say whether a reading needs attention.
 */
object GrowthStandards {

    /** One age's published parameters: skew, median in kg, and coefficient of variation. */
    data class Lms(val l: Double, val m: Double, val s: Double)

    /** WHO boys' weight-for-age, index = completed months. */
    private val BOYS = listOf(
        Lms(0.3487, 3.3464, 0.14602),  // 0 mo
        Lms(0.2303, 4.4525, 0.13413),  // 1 mo
        Lms(0.1969, 5.5714, 0.12382),  // 2 mo
        Lms(0.174, 6.369, 0.11732),  // 3 mo
        Lms(0.1551, 7.0069, 0.11313),  // 4 mo
        Lms(0.1396, 7.5077, 0.11081),  // 5 mo
        Lms(0.1256, 7.9389, 0.10957),  // 6 mo
        Lms(0.1134, 8.2963, 0.10902),  // 7 mo
        Lms(0.1019, 8.62, 0.10882),  // 8 mo
        Lms(0.0917, 8.9019, 0.10881),  // 9 mo
        Lms(0.0821, 9.1618, 0.1089),  // 10 mo
        Lms(0.0729, 9.4136, 0.10906),  // 11 mo
        Lms(0.0645, 9.646, 0.10925),  // 12 mo
        Lms(0.0563, 9.8772, 0.10949),  // 13 mo
        Lms(0.0487, 10.0944, 0.10976),  // 14 mo
        Lms(0.0412, 10.3139, 0.11008),  // 15 mo
        Lms(0.0343, 10.5228, 0.11041),  // 16 mo
        Lms(0.0276, 10.7289, 0.11078),  // 17 mo
        Lms(0.021, 10.9393, 0.1112),  // 18 mo
        Lms(0.0149, 11.1409, 0.11163),  // 19 mo
        Lms(0.0087, 11.3478, 0.11212),  // 20 mo
        Lms(0.0029, 11.5474, 0.11261),  // 21 mo
        Lms(-0.0029, 11.7528, 0.11315),  // 22 mo
        Lms(-0.0083, 11.951, 0.11369),  // 23 mo
        Lms(-0.0136, 12.1482, 0.11425),  // 24 mo
    )

    /** WHO girls' weight-for-age, index = completed months. */
    private val GIRLS = listOf(
        Lms(0.3809, 3.2322, 0.14171),  // 0 mo
        Lms(0.1727, 4.1716, 0.13738),  // 1 mo
        Lms(0.0959, 5.1315, 0.12998),  // 2 mo
        Lms(0.0407, 5.8393, 0.12622),  // 3 mo
        Lms(-0.0053, 6.428, 0.12401),  // 4 mo
        Lms(-0.0428, 6.8959, 0.12274),  // 5 mo
        Lms(-0.0759, 7.3016, 0.12204),  // 6 mo
        Lms(-0.1039, 7.6416, 0.12178),  // 7 mo
        Lms(-0.1292, 7.9534, 0.12181),  // 8 mo
        Lms(-0.1507, 8.2259, 0.12199),  // 9 mo
        Lms(-0.1698, 8.4769, 0.12222),  // 10 mo
        Lms(-0.1873, 8.7207, 0.12247),  // 11 mo
        Lms(-0.2022, 8.9462, 0.12267),  // 12 mo
        Lms(-0.216, 9.1722, 0.12283),  // 13 mo
        Lms(-0.2277, 9.3861, 0.12294),  // 14 mo
        Lms(-0.2385, 9.6038, 0.12299),  // 15 mo
        Lms(-0.2478, 9.8124, 0.12303),  // 16 mo
        Lms(-0.2561, 10.0196, 0.12305),  // 17 mo
        Lms(-0.2637, 10.2324, 0.12309),  // 18 mo
        Lms(-0.2702, 10.4372, 0.12315),  // 19 mo
        Lms(-0.2763, 10.6481, 0.12324),  // 20 mo
        Lms(-0.2814, 10.8521, 0.12335),  // 21 mo
        Lms(-0.2862, 11.0633, 0.12351),  // 22 mo
        Lms(-0.2903, 11.2684, 0.12369),  // 23 mo
        Lms(-0.294, 11.4741, 0.12389),  // 24 mo
    )

    /** The oldest age the tables above cover. */
    const val MAX_MONTHS: Int = 24

    /**
     * The bands the chart draws, as the WHO 0-2 years percentile chart does. The 50th is drawn
     * separately as the median line.
     */
    val BANDS: List<Int> = listOf(3, 15, 85, 97)

    /** Median expected weight at an age in months. Null beyond the end of the table. */
    fun medianWeightKg(months: Double, sex: Sex?): Double? = lms(months, sex)?.m

    /**
     * The weight at a percentile for a child of this age — the 3rd, 15th, 85th and 97th are
     * what the chart shades between.
     */
    fun weightAtPercentile(months: Double, sex: Sex?, percentile: Int): Double? {
        val p = lms(months, sex) ?: return null
        val z = zForPercentile(percentile)
        return if (abs(p.l) < 1e-7) p.m * exp(p.s * z) else p.m * (1 + p.l * p.s * z).pow(1 / p.l)
    }

    /**
     * Where a reading sits among healthy children of the same age and sex, 1 to 99.
     *
     * Clamped at the ends rather than reported precisely: past three standard deviations the
     * distribution is extrapolated, and "0.2nd" would read as precision the number does not
     * have. Needs the sex — a percentile averaged across boys and girls would be a number
     * about nobody.
     */
    fun percentileOf(kg: Double, months: Double, sex: Sex): Int? {
        val z = zScore(kg, months, sex) ?: return null
        return (percentileForZ(z) * 100).roundToInt().coerceIn(1, 99)
    }

    /** Standard deviations from the median, by the WHO's own LMS formula. */
    fun zScore(kg: Double, months: Double, sex: Sex?): Double? {
        val p = lms(months, sex) ?: return null
        if (kg <= 0) return null
        return if (abs(p.l) < 1e-7) ln(kg / p.m) / p.s
        else ((kg / p.m).pow(p.l) - 1) / (p.l * p.s)
    }

    /**
     * The published parameters at an age, straight-lined between the monthly rows so a chart
     * drawn at any age is smooth. Averaging the two sexes is a fallback for a child whose sex
     * is not recorded: it gives the curve roughly the right shape, and the screen says so.
     */
    private fun lms(months: Double, sex: Sex?): Lms? {
        if (months < 0.0 || months > MAX_MONTHS) return null
        val lower = months.toInt().coerceAtMost(MAX_MONTHS)
        val at = at(lower, sex)
        if (lower == MAX_MONTHS) return at
        val next = at(lower + 1, sex)
        val f = months - lower
        return Lms(
            at.l + (next.l - at.l) * f,
            at.m + (next.m - at.m) * f,
            at.s + (next.s - at.s) * f,
        )
    }

    private fun at(month: Int, sex: Sex?): Lms = when (sex) {
        Sex.BOY -> BOYS[month]
        Sex.GIRL -> GIRLS[month]
        null -> Lms(
            (BOYS[month].l + GIRLS[month].l) / 2,
            (BOYS[month].m + GIRLS[month].m) / 2,
            (BOYS[month].s + GIRLS[month].s) / 2,
        )
    }

    /** The z a percentile sits at: the 3rd is 1.88 standard deviations below the median. */
    internal fun zForPercentile(percentile: Int): Double {
        // Newton on the normal CDF — a handful of steps from a decent guess is exact enough
        // for a chart line, and keeps a table of constants out of the file.
        val target = percentile / 100.0
        var z = 0.0
        repeat(40) {
            val density = exp(-z * z / 2) / sqrt(2 * Math.PI)
            if (density < 1e-12) return z
            z += (target - percentileForZ(z)) / density
        }
        return z
    }

    /** The normal CDF, to about seven decimal places (Abramowitz & Stegun 7.1.26). */
    internal fun percentileForZ(z: Double): Double {
        val x = z / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * abs(x))
        val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) *
            t * exp(-x * x)
        val erf = sign(x) * y
        return 0.5 * (1 + erf)
    }
}
