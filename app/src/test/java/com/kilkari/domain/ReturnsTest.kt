package com.kilkari.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReturnsTest {

    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Test
    fun `a lump sum that doubles in a year is about a hundred percent`() {
        val rate = Returns.holdingReturn(
            contributions = listOf(date(2025, 1, 1) to 100_000L),
            valueInr = 200_000,
            valuedOn = date(2026, 1, 1),
        )!!
        assertEquals(1.0, rate, 0.01)
    }

    @Test
    fun `a flat holding has no return`() {
        val rate = Returns.holdingReturn(
            contributions = listOf(date(2025, 1, 1) to 50_000L),
            valueInr = 50_000,
            valuedOn = date(2026, 1, 1),
        )!!
        assertEquals(0.0, rate, 0.001)
    }

    @Test
    fun `a loss reads as a negative rate`() {
        val rate = Returns.holdingReturn(
            contributions = listOf(date(2025, 1, 1) to 100_000L),
            valueInr = 90_000,
            valuedOn = date(2026, 1, 1),
        )!!
        assertTrue("expected a loss, got $rate", rate < 0)
        assertEquals(-0.10, rate, 0.01)
    }

    @Test
    fun `monthly payments are annualised over the time each one was in`() {
        // Twelve monthly 10k payments worth 130k at the end: the money was in for an average of
        // about half the year, so the annual rate is well above the 8% the total grew by.
        val contributions = (0..11).map { date(2025, 1, 1).plusMonths(it.toLong()) to 10_000L }
        val rate = Returns.holdingReturn(contributions, 130_000, date(2025, 12, 31))!!
        assertTrue("expected above 8%, got $rate", rate > 0.08)
        assertTrue("expected below 40%, got $rate", rate < 0.40)
    }

    @Test
    fun `nothing is claimed from too little to go on`() {
        assertNull(Returns.holdingReturn(emptyList(), 1000, date(2026, 1, 1)))
        assertNull(Returns.holdingReturn(listOf(date(2026, 1, 1) to 1000L), 0, date(2026, 6, 1)))
        // Three weeks is not a year's worth of anything.
        assertNull(
            Returns.holdingReturn(listOf(date(2026, 1, 1) to 1000L), 1100, date(2026, 1, 21))
        )
    }

    @Test
    fun `a fixed deposit compounds quarterly to the published figure`() {
        // 100,000 at 7% for 5 years, compounded quarterly: 100000 * (1.0175)^20 = 141,478.
        val value = Returns.projectedMaturityInr(
            kind = InvestmentKind.FD,
            investedInr = 100_000,
            monthlyInr = null,
            ratePercent = 7.0,
            start = date(2026, 1, 1),
            maturity = date(2031, 1, 1),
        )!!
        assertEquals(141_478.0, value.toDouble(), 200.0)
    }

    @Test
    fun `a recurring plan compounds each instalment from when it goes in`() {
        val value = Returns.projectedMaturityInr(
            kind = InvestmentKind.RD,
            investedInr = 0,
            monthlyInr = 5_000,
            ratePercent = 7.0,
            start = date(2026, 1, 1),
            maturity = date(2027, 1, 1),
        )!!
        // Twelve 5,000 instalments: more than the 60,000 paid in, and not by much.
        assertTrue("expected above 60,000, got $value", value > 60_000)
        assertTrue("expected below 64,000, got $value", value < 64_000)
    }

    @Test
    fun `no rate or no maturity date means no projection`() {
        assertNull(
            Returns.projectedMaturityInr(
                InvestmentKind.FD, 100_000, null, null, date(2026, 1, 1), date(2031, 1, 1),
            )
        )
        assertNull(
            Returns.projectedMaturityInr(
                InvestmentKind.FD, 100_000, null, 7.0, date(2026, 1, 1), null,
            )
        )
        assertNull(
            Returns.projectedMaturityInr(
                InvestmentKind.RD, 0, null, 7.0, date(2026, 1, 1), date(2027, 1, 1),
            )
        )
    }
}
