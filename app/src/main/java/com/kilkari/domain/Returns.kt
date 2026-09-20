package com.kilkari.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * What a holding has actually earned, and what it should come to if the rate holds.
 *
 * Both are arithmetic on numbers the parent has already entered — contributions, the value they
 * last wrote down, the rate the bank quoted. Nothing is fetched: the app has no network and no
 * price feed, so a SIP's return is only as current as the last value entered by hand, and the
 * screen says so rather than implying a live number.
 */
object Returns {

    /** A year, for annualising. The average Gregorian year, so leap years do not skew a rate. */
    const val DAYS_IN_YEAR = 365.2425

    /** Below this, a return is arithmetic noise on a few weeks of holding. */
    const val MIN_DAYS_FOR_RATE = 30

    /** Money in on a day. Contributions are negative, what it is worth today is positive. */
    data class Flow(val date: LocalDate, val amount: Double)

    /**
     * The annualised return that makes these dated flows add up to nothing — the standard XIRR,
     * and the only fair way to compare a lump sum against money paid in monthly.
     *
     * Returns null when there is nothing to solve: no flows on both sides, too short a span, or
     * a series with no rate that balances it (a holding that lost everything, for instance).
     */
    fun xirr(flows: List<Flow>): Double? {
        if (flows.size < 2) return null
        if (flows.none { it.amount > 0 } || flows.none { it.amount < 0 }) return null
        val first = flows.minOf { it.date }
        val last = flows.maxOf { it.date }
        if (ChronoUnit.DAYS.between(first, last) < MIN_DAYS_FOR_RATE) return null

        fun npv(rate: Double): Double = flows.sumOf { flow ->
            val years = ChronoUnit.DAYS.between(first, flow.date) / DAYS_IN_YEAR
            flow.amount / (1 + rate).pow(years)
        }

        // Bisection rather than Newton: slower, but it cannot run away from a sane bracket,
        // and the bracket here is every rate worth showing a parent.
        var low = -0.9999
        var high = 10.0
        var lowValue = npv(low)
        if (lowValue.isNaN() || npv(high).isNaN()) return null
        if (lowValue * npv(high) > 0) return null

        repeat(200) {
            val mid = (low + high) / 2
            val value = npv(mid)
            if (abs(value) < 0.01) return mid
            if (lowValue * value <= 0) {
                high = mid
            } else {
                low = mid
                lowValue = value
            }
        }
        return (low + high) / 2
    }

    /**
     * XIRR for a holding: every contribution as money out, and what it is worth now as money
     * back on the day that value was written down.
     */
    fun holdingReturn(
        contributions: List<Pair<LocalDate, Long>>,
        valueInr: Long,
        valuedOn: LocalDate,
    ): Double? {
        if (contributions.isEmpty() || valueInr <= 0) return null
        val flows = contributions.map { (date, amount) -> Flow(date, -amount.toDouble()) } +
            Flow(valuedOn, valueInr.toDouble())
        return xirr(flows)
    }

    /**
     * What a fixed instrument comes to at maturity on the rate entered.
     *
     * Compounded quarterly, which is how Indian deposits are quoted, and stated as such on the
     * screen — a projection whose assumptions are hidden is just a number. Recurring plans
     * compound each instalment from the month it is paid.
     */
    fun projectedMaturityInr(
        kind: InvestmentKind,
        investedInr: Long,
        monthlyInr: Long?,
        ratePercent: Double?,
        start: LocalDate,
        maturity: LocalDate?,
    ): Long? {
        val rate = ratePercent?.takeIf { it > 0 } ?: return null
        val end = maturity ?: return null
        if (!end.isAfter(start)) return null
        val r = rate / 100.0

        if (!kind.recurring) {
            val years = ChronoUnit.DAYS.between(start, end) / DAYS_IN_YEAR
            return (investedInr * (1 + r / QUARTERS).pow(QUARTERS * years)).toLong()
        }

        val monthly = monthlyInr?.takeIf { it > 0 } ?: return null
        val months = ChronoUnit.MONTHS.between(
            start.withDayOfMonth(1),
            end.withDayOfMonth(1),
        ).toInt()
        if (months <= 0) return null
        // Each instalment earns from the month it goes in, so the first compounds longest.
        var total = 0.0
        for (month in 0 until months) {
            val years = (months - month) / 12.0
            total += monthly * (1 + r / QUARTERS).pow(QUARTERS * years)
        }
        return total.toLong()
    }

    /** Indian deposits are quoted on quarterly compounding. */
    private const val QUARTERS = 4.0
}
