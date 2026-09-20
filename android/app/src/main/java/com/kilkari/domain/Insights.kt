package com.kilkari.domain

import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.MedicationDoseEntity
import com.kilkari.data.db.MedicationEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Averages and patterns drawn from what has been logged.
 *
 * Every figure is an average over the days that were actually logged, not over the calendar.
 * A parent who logged sleep on four days out of seven did not have a baby who slept half as
 * much on the other three, and dividing by seven would say so. Each section carries how many
 * days it rests on, so the screen can say how much to trust it.
 *
 * Windows end at the start of today. Today is still happening: including it would drag every
 * daily average down every morning.
 */
object Insights {

    /** Night is taken as seven in the evening to seven in the morning. */
    val NIGHT_STARTS: LocalTime = LocalTime.of(19, 0)
    val NIGHT_ENDS: LocalTime = LocalTime.of(7, 0)

    /**
     * Gaps longer than this are not counted as time between feeds: they are almost always a
     * stretch nobody logged, and would turn a three-hour rhythm into a nine-hour one.
     */
    const val MAX_FEED_GAP_MINUTES = 12 * 60

    /** Two weigh-ins closer than this say more about the scale than about growth. */
    const val MIN_GROWTH_SPAN_DAYS = 3

    fun report(
        today: LocalDate,
        days: Int,
        logs: List<LogEntryEntity>,
        medications: List<MedicationEntity>,
        doses: List<MedicationDoseEntity>,
        growth: List<GrowthEntity>,
        born: LocalDate? = null,
    ): InsightReport {
        val end = today
        val fullStart = end.minusDays(days.toLong())
        // A child a fortnight old has no "last 30 days". The window starts at birth instead, so
        // the day count and the empty chart slots do not include days before there was anyone
        // to log.
        val start = if (born != null && born.isAfter(fullStart)) minOf(born, end) else fullStart
        // Compared only against a whole previous period. Half a period of a newborn's first days
        // set against a full one would read as a trend when it is only the calendar.
        val previousStart = start.minusDays(days.toLong())
            .takeUnless { born != null && born.isAfter(it) } ?: start

        return InsightReport(
            days = ChronoUnit.DAYS.between(start, end).toInt(),
            start = start,
            end = end,
            feeding = feeding(logs, start, end, previousStart),
            sleep = sleep(logs, start, end, previousStart),
            diapers = diapers(logs, start, end, previousStart),
            medicines = adherence(medications, doses, start, end),
            growth = growthRate(growth, start, end),
        )
    }

    // ── Feeding ─────────────────────────────────────────────────────────────

    private fun feeding(
        logs: List<LogEntryEntity>,
        start: LocalDate,
        end: LocalDate,
        previousStart: LocalDate,
    ): FeedingInsights {
        val feeds = logs.filter { LogKind.of(it.kind) == LogKind.FEED }
        val current = feeds.inRange(start, end)
        val previous = feeds.inRange(previousStart, start)

        fun perDay(entries: List<LogEntryEntity>, value: (LogEntryEntity) -> Double): Double? {
            val logged = entries.map { it.startAt.toLocalDate() }.toSet()
            if (logged.isEmpty()) return null
            return entries.sumOf(value) / logged.size
        }

        fun amountOf(type: FeedType): (LogEntryEntity) -> Double = {
            if (FeedType.of(it.feedType) == type) (it.amount ?: 0).toDouble() else 0.0
        }

        val sided = current.filter { FeedType.of(it.feedType) == FeedType.BREAST && it.side != null }
        val leftShare = if (sided.isEmpty()) null else {
            sided.count { it.side == BreastSide.LEFT.key }.toDouble() / sided.size
        }

        return FeedingInsights(
            loggedDays = current.map { it.startAt.toLocalDate() }.toSet().size,
            feedsPerDay = Metric(perDay(current) { 1.0 }, perDay(previous) { 1.0 }),
            bottleMlPerDay = Metric(
                perDay(current, amountOf(FeedType.BOTTLE)).takeIf { current.any { it.isType(FeedType.BOTTLE) } },
                perDay(previous, amountOf(FeedType.BOTTLE)).takeIf { previous.any { it.isType(FeedType.BOTTLE) } },
            ),
            breastMinutesPerDay = Metric(
                perDay(current, amountOf(FeedType.BREAST)).takeIf { current.any { it.isType(FeedType.BREAST) } },
                perDay(previous, amountOf(FeedType.BREAST)).takeIf { previous.any { it.isType(FeedType.BREAST) } },
            ),
            solidsGramsPerDay = Metric(
                perDay(current, amountOf(FeedType.SOLID)).takeIf { current.any { it.isType(FeedType.SOLID) } },
                perDay(previous, amountOf(FeedType.SOLID)).takeIf { previous.any { it.isType(FeedType.SOLID) } },
            ),
            typicalGapMinutes = median(feedGaps(current)),
            leftShare = leftShare,
            mix = FeedType.entries.associateWith { type -> current.count { it.isType(type) } },
            daily = dailyCounts(current, start, end),
        )
    }

    /** Minutes between each feed and the next, leaving out gaps too long to be real. */
    internal fun feedGaps(feeds: List<LogEntryEntity>): List<Double> =
        feeds.map { it.startAt }.sorted().zipWithNext { a, b ->
            Duration.between(a, b).toMinutes().toDouble()
        }.filter { it in 1.0..MAX_FEED_GAP_MINUTES.toDouble() }

    // ── Sleep ───────────────────────────────────────────────────────────────

    private fun sleep(
        logs: List<LogEntryEntity>,
        start: LocalDate,
        end: LocalDate,
        previousStart: LocalDate,
    ): SleepInsights {
        // Only finished sleeps count. One still running has no length yet.
        val sleeps = logs.filter { LogKind.of(it.kind) == LogKind.SLEEP && it.endAt != null }

        val minutesByDay = sleepMinutesByDay(sleeps, start, end)
        val previousByDay = sleepMinutesByDay(sleeps, previousStart, start)

        val current = sleeps.filter { it.startAt.toLocalDate() in start..end.minusDays(1) }
        val previous = sleeps.filter { it.startAt.toLocalDate() in previousStart..start.minusDays(1) }

        val lengths = current.map { Duration.between(it.startAt, it.endAt).toMinutes().toDouble() }
        val split = daySplit(sleeps, start, end)
        val loggedDays = minutesByDay.size

        return SleepInsights(
            loggedDays = loggedDays,
            totalMinutesPerDay = Metric(
                minutesByDay.values.averageOrNull(),
                previousByDay.values.averageOrNull(),
            ),
            // Over the same days as the total above it. Counting only days a sleep began on
            // gave one overnight sleep "4 h a day" beside "1 sleep a day", which cannot both
            // be true of the same two days.
            napsPerDay = Metric(
                if (loggedDays == 0) null else current.size.toDouble() / loggedDays,
                if (previousByDay.isEmpty()) null else previous.size.toDouble() / previousByDay.size,
            ),
            averageSleepMinutes = lengths.averageOrNull(),
            longestMinutes = lengths.maxOrNull(),
            dayMinutesPerDay = if (loggedDays == 0) null else split.first / loggedDays,
            nightMinutesPerDay = if (loggedDays == 0) null else split.second / loggedDays,
            daily = dayRange(start, end).map { day -> DayValue(day, minutesByDay[day]?.div(60.0)) },
        )
    }

    /**
     * Minutes asleep on each calendar day in [from, to).
     *
     * A sleep that runs past midnight is split between the two days it touches rather than
     * credited to the day it began, so a night that started at eleven does not give one day
     * nine hours and the next day none.
     */
    internal fun sleepMinutesByDay(
        sleeps: List<LogEntryEntity>,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Double> {
        val totals = sortedMapOf<LocalDate, Double>()
        for (entry in sleeps) {
            val ended = entry.endAt ?: continue
            if (!ended.isAfter(entry.startAt)) continue
            var day = entry.startAt.toLocalDate()
            while (!day.isAfter(ended.toLocalDate())) {
                if (day >= from && day < to) {
                    val overlap = overlapMinutes(entry.startAt, ended, day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                    if (overlap > 0) totals[day] = (totals[day] ?: 0.0) + overlap
                }
                day = day.plusDays(1)
            }
        }
        return totals
    }

    /** Total daytime and night-time minutes asleep in [from, to), as (day, night). */
    internal fun daySplit(sleeps: List<LogEntryEntity>, from: LocalDate, to: LocalDate): Pair<Double, Double> {
        var dayMinutes = 0.0
        var nightMinutes = 0.0
        for (entry in sleeps) {
            val ended = entry.endAt ?: continue
            var day = entry.startAt.toLocalDate()
            while (!day.isAfter(ended.toLocalDate())) {
                if (day >= from && day < to) {
                    val daytime = overlapMinutes(
                        entry.startAt, ended, day.atTime(NIGHT_ENDS), day.atTime(NIGHT_STARTS),
                    )
                    val wholeDay = overlapMinutes(
                        entry.startAt, ended, day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                    )
                    dayMinutes += daytime
                    nightMinutes += wholeDay - daytime
                }
                day = day.plusDays(1)
            }
        }
        return dayMinutes to nightMinutes
    }

    // ── Diapers ─────────────────────────────────────────────────────────────

    private fun diapers(
        logs: List<LogEntryEntity>,
        start: LocalDate,
        end: LocalDate,
        previousStart: LocalDate,
    ): DiaperInsights {
        val all = logs.filter { LogKind.of(it.kind) == LogKind.DIAPER }
        val current = all.inRange(start, end)
        val previous = all.inRange(previousStart, start)
        val days = current.map { it.startAt.toLocalDate() }.toSet().size
        val previousDays = previous.map { it.startAt.toLocalDate() }.toSet().size

        // A change marked "both" was wet and dirty, so it counts towards each.
        val wet = current.count { DiaperKind.of(it.diaperKind) != DiaperKind.DIRTY }
        val dirty = current.count { DiaperKind.of(it.diaperKind) != DiaperKind.WET }

        return DiaperInsights(
            loggedDays = days,
            perDay = Metric(
                if (days == 0) null else current.size.toDouble() / days,
                if (previousDays == 0) null else previous.size.toDouble() / previousDays,
            ),
            wetPerDay = if (days == 0) null else wet.toDouble() / days,
            dirtyPerDay = if (days == 0) null else dirty.toDouble() / days,
            daily = dailyCounts(current, start, end),
        )
    }

    // ── Medicine ────────────────────────────────────────────────────────────

    /**
     * For each medicine, the days a dose was recorded against the days it was being taken.
     *
     * Only days inside both the window and the course count as expected: a medicine started
     * three days ago cannot have been missed on the four days before that.
     */
    internal fun adherence(
        medications: List<MedicationEntity>,
        doses: List<MedicationDoseEntity>,
        start: LocalDate,
        end: LocalDate,
    ): List<MedicineAdherence> = medications.mapNotNull { med ->
        val from = maxOf(start, med.startDate)
        val until = minOf(end, med.endDate?.plusDays(1) ?: end)
        val expected = ChronoUnit.DAYS.between(from, until).toInt()
        if (expected <= 0) return@mapNotNull null
        val taken = doses.count {
            it.medicationId == med.id && it.date >= from && it.date < until
        }
        MedicineAdherence(med.name, taken = min(taken, expected), expected = expected)
    }

    // ── Growth ──────────────────────────────────────────────────────────────

    /**
     * Weight change per week across the window.
     *
     * Measured from the last weigh-in before the window opened, when there is one, so a
     * single weigh-in inside the window still has something to be compared with.
     */
    internal fun growthRate(growth: List<GrowthEntity>, start: LocalDate, end: LocalDate): GrowthRate? {
        val weights = growth.filter { it.weightKg != null && it.date < end }.sortedBy { it.date }
        val latest = weights.lastOrNull { it.date >= start } ?: return null
        val baseline = weights.lastOrNull { it.date < start } ?: weights.firstOrNull { it.date >= start } ?: return null
        val span = ChronoUnit.DAYS.between(baseline.date, latest.date)
        if (span < MIN_GROWTH_SPAN_DAYS) return null
        val grams = (latest.weightKg!! - baseline.weightKg!!) * 1000.0
        return GrowthRate(gramsPerWeek = grams / span * 7.0, since = baseline.date)
    }

    // ── A single day ────────────────────────────────────────────────────────

    /**
     * What one day came to, for the daily log.
     *
     * [logs] should reach back into the evening before, so a night that began then contributes
     * the part of it that fell on this day.
     */
    fun daySummary(logs: List<LogEntryEntity>, day: LocalDate): DaySummary {
        val onDay = logs.filter { it.startAt.toLocalDate() == day }
        val feeds = onDay.filter { LogKind.of(it.kind) == LogKind.FEED }
        val sleeps = logs.filter { LogKind.of(it.kind) == LogKind.SLEEP && it.endAt != null }
        return DaySummary(
            feeds = feeds.size,
            bottleMl = feeds.filter { it.isType(FeedType.BOTTLE) }.sumOf { it.amount ?: 0 },
            breastMinutes = feeds.filter { it.isType(FeedType.BREAST) }.sumOf { it.amount ?: 0 },
            sleepMinutes = sleepMinutesByDay(sleeps, day, day.plusDays(1))[day] ?: 0.0,
            diapers = onDay.count { LogKind.of(it.kind) == LogKind.DIAPER },
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun LogEntryEntity.isType(type: FeedType) = FeedType.of(feedType) == type

    private fun List<LogEntryEntity>.inRange(from: LocalDate, to: LocalDate) =
        filter { it.startAt.toLocalDate() >= from && it.startAt.toLocalDate() < to }

    private fun dailyCounts(entries: List<LogEntryEntity>, from: LocalDate, to: LocalDate): List<DayValue> {
        val counts = entries.groupingBy { it.startAt.toLocalDate() }.eachCount()
        return dayRange(from, to).map { day -> DayValue(day, counts[day]?.toDouble()) }
    }

    private fun dayRange(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it < to }.toList()

    private fun overlapMinutes(
        start: LocalDateTime,
        end: LocalDateTime,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime,
    ): Double {
        val from = maxOf(start, windowStart)
        val to = minOf(end, windowEnd)
        return max(0L, Duration.between(from, to).toMinutes()).toDouble()
    }

    private fun Collection<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    internal fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}

/** One figure for the window, and the same figure for the window before it. */
data class Metric(val current: Double?, val previous: Double?) {
    /**
     * Change against the previous window as a fraction, or null when either side is missing
     * or the previous value is too small to divide by honestly.
     */
    val change: Double?
        get() {
            val now = current ?: return null
            val before = previous ?: return null
            if (before < 0.5) return null
            return (now - before) / before
        }
}

/** A day's figure, or null when nothing of that kind was logged that day. */
data class DayValue(val date: LocalDate, val value: Double?)

data class FeedingInsights(
    val loggedDays: Int,
    val feedsPerDay: Metric,
    val bottleMlPerDay: Metric,
    val breastMinutesPerDay: Metric,
    val solidsGramsPerDay: Metric,
    val typicalGapMinutes: Double?,
    /** Share of breastfeeds that started on the left, when any recorded a side. */
    val leftShare: Double?,
    val mix: Map<FeedType, Int>,
    val daily: List<DayValue>,
)

data class SleepInsights(
    val loggedDays: Int,
    val totalMinutesPerDay: Metric,
    val napsPerDay: Metric,
    val averageSleepMinutes: Double?,
    val longestMinutes: Double?,
    val dayMinutesPerDay: Double?,
    val nightMinutesPerDay: Double?,
    /** Hours asleep per day. */
    val daily: List<DayValue>,
)

data class DiaperInsights(
    val loggedDays: Int,
    val perDay: Metric,
    val wetPerDay: Double?,
    val dirtyPerDay: Double?,
    val daily: List<DayValue>,
)

data class MedicineAdherence(val name: String, val taken: Int, val expected: Int)

data class DaySummary(
    val feeds: Int,
    val bottleMl: Int,
    val breastMinutes: Int,
    val sleepMinutes: Double,
    val diapers: Int,
)

data class GrowthRate(val gramsPerWeek: Double, val since: LocalDate)

data class InsightReport(
    /** Days the window actually covers: the length asked for, or fewer for a newborn. */
    val days: Int,
    val start: LocalDate,
    /** Exclusive: the window stops at the start of this day. */
    val end: LocalDate,
    val feeding: FeedingInsights,
    val sleep: SleepInsights,
    val diapers: DiaperInsights,
    val medicines: List<MedicineAdherence>,
    val growth: GrowthRate?,
)
