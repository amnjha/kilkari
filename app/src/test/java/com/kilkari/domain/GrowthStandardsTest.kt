package com.kilkari.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checked against the WHO weight-for-age percentile tables the LMS values come from, so a
 * mistyped parameter or a wrong formula shows up as a wrong kilogram.
 */
class GrowthStandardsTest {

    @Test
    fun `medians match the published table`() {
        assertEquals(3.3464, GrowthStandards.medianWeightKg(0.0, Sex.BOY)!!, 0.0001)
        assertEquals(3.2322, GrowthStandards.medianWeightKg(0.0, Sex.GIRL)!!, 0.0001)
        assertEquals(9.646, GrowthStandards.medianWeightKg(12.0, Sex.BOY)!!, 0.0001)
        assertEquals(11.4741, GrowthStandards.medianWeightKg(24.0, Sex.GIRL)!!, 0.0001)
    }

    /**
     * Against WHO's own published percentile tables — not its z-score lines, which are a
     * different thing: −2 SD is the 2.3rd percentile, not the 3rd.
     */
    @Test
    fun `percentile curves match the WHO percentile tables`() {
        // Boys at birth.
        assertEquals(2.507, GrowthStandards.weightAtPercentile(0.0, Sex.BOY, 3)!!, 0.01)
        assertEquals(2.865, GrowthStandards.weightAtPercentile(0.0, Sex.BOY, 15)!!, 0.01)
        assertEquals(3.878, GrowthStandards.weightAtPercentile(0.0, Sex.BOY, 85)!!, 0.01)
        assertEquals(4.350, GrowthStandards.weightAtPercentile(0.0, Sex.BOY, 97)!!, 0.01)
        // Boys at one year, and at the end of the table.
        assertEquals(7.844, GrowthStandards.weightAtPercentile(12.0, Sex.BOY, 3)!!, 0.01)
        assertEquals(11.830, GrowthStandards.weightAtPercentile(12.0, Sex.BOY, 97)!!, 0.01)
        assertEquals(9.802, GrowthStandards.weightAtPercentile(24.0, Sex.BOY, 3)!!, 0.01)
        assertEquals(15.065, GrowthStandards.weightAtPercentile(24.0, Sex.BOY, 97)!!, 0.01)
        // Girls at birth and at one year.
        assertEquals(2.440, GrowthStandards.weightAtPercentile(0.0, Sex.GIRL, 3)!!, 0.01)
        assertEquals(4.166, GrowthStandards.weightAtPercentile(0.0, Sex.GIRL, 97)!!, 0.01)
        assertEquals(7.140, GrowthStandards.weightAtPercentile(12.0, Sex.GIRL, 3)!!, 0.01)
        assertEquals(7.891, GrowthStandards.weightAtPercentile(12.0, Sex.GIRL, 15)!!, 0.01)
        assertEquals(10.176, GrowthStandards.weightAtPercentile(12.0, Sex.GIRL, 85)!!, 0.01)
        assertEquals(11.331, GrowthStandards.weightAtPercentile(12.0, Sex.GIRL, 97)!!, 0.01)
    }

    @Test
    fun `the median is the fiftieth percentile`() {
        val median = GrowthStandards.medianWeightKg(6.0, Sex.GIRL)!!
        assertEquals(median, GrowthStandards.weightAtPercentile(6.0, Sex.GIRL, 50)!!, 0.001)
        assertEquals(50, GrowthStandards.percentileOf(median, 6.0, Sex.GIRL))
    }

    @Test
    fun `a weight on a band reads back as that percentile`() {
        listOf(3, 15, 85, 97).forEach { p ->
            val kg = GrowthStandards.weightAtPercentile(9.0, Sex.BOY, p)!!
            assertEquals(p, GrowthStandards.percentileOf(kg, 9.0, Sex.BOY))
        }
    }

    @Test
    fun `percentiles are clamped rather than reported past the table's reach`() {
        assertEquals(1, GrowthStandards.percentileOf(1.5, 6.0, Sex.BOY))
        assertEquals(99, GrowthStandards.percentileOf(14.0, 6.0, Sex.BOY))
    }

    @Test
    fun `nothing is claimed beyond two years or before birth`() {
        assertNull(GrowthStandards.medianWeightKg(24.5, Sex.BOY))
        assertNull(GrowthStandards.medianWeightKg(-1.0, Sex.GIRL))
        assertNull(GrowthStandards.percentileOf(9.0, 30.0, Sex.BOY))
    }

    @Test
    fun `a curve between two months sits between the two published rows`() {
        val at6 = GrowthStandards.medianWeightKg(6.0, Sex.BOY)!!
        val at7 = GrowthStandards.medianWeightKg(7.0, Sex.BOY)!!
        val half = GrowthStandards.medianWeightKg(6.5, Sex.BOY)!!
        assertTrue(half > at6 && half < at7)
    }

    @Test
    fun `an unrecorded sex sits between the boys' and girls' curves`() {
        val boy = GrowthStandards.medianWeightKg(12.0, Sex.BOY)!!
        val girl = GrowthStandards.medianWeightKg(12.0, Sex.GIRL)!!
        val either = GrowthStandards.medianWeightKg(12.0, null)!!
        assertTrue(either in girl..boy)
    }
}
