package com.kilkari.domain

/**
 * Weight-for-age medians from the WHO Child Growth Standards (2006), in kilograms, by completed
 * month from birth to two years.
 *
 * These are the 50th percentile — the weight half of healthy children are above at that age —
 * and the standards are published separately for boys and girls, which is why both are here.
 * Kilkari does not record a child's sex, so the chart plots the mean of the two; the difference
 * between them is about half a kilogram at two years, so the curve is a guide to the shape of
 * normal growth rather than a number to measure a particular child against. A child's own
 * healthy line can sit some way above or below it. Only a clinician reading the full percentile
 * chart can say whether a given reading matters.
 */
object GrowthStandards {

    /** WHO boys' weight-for-age median, kg, index = completed months. */
    private val BOYS = doubleArrayOf(
        3.3, 4.5, 5.6, 6.4, 7.0, 7.5, 7.9, 8.3, 8.6, 8.9, 9.2, 9.4, 9.6,
        9.9, 10.1, 10.3, 10.5, 10.7, 10.9, 11.1, 11.3, 11.5, 11.8, 12.0, 12.2,
    )

    /** WHO girls' weight-for-age median, kg, index = completed months. */
    private val GIRLS = doubleArrayOf(
        3.2, 4.2, 5.1, 5.8, 6.4, 6.9, 7.3, 7.6, 7.9, 8.2, 8.5, 8.7, 8.9,
        9.2, 9.4, 9.6, 9.8, 10.0, 10.2, 10.4, 10.6, 10.9, 11.1, 11.3, 11.5,
    )

    /** The oldest age the published table above covers. */
    const val MAX_MONTHS: Int = 24

    /**
     * Median expected weight at an age in months, straight-lined between the published monthly
     * figures so the curve is smooth. Null beyond the end of the table.
     */
    fun medianWeightKg(months: Double): Double? {
        if (months < 0.0 || months > MAX_MONTHS) return null
        val lower = months.toInt().coerceAtMost(MAX_MONTHS)
        val atLower = median(lower)
        if (lower == MAX_MONTHS) return atLower
        val fraction = months - lower
        return atLower + (median(lower + 1) - atLower) * fraction
    }

    private fun median(month: Int): Double = (BOYS[month] + GIRLS[month]) / 2.0
}
