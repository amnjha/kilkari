package com.kilkari.domain

import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.MedicationDoseEntity
import com.kilkari.data.db.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class InsightsTest {

    private val today = LocalDate.of(2026, 9, 17)
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 9, day, hour, minute)

    private fun feed(start: LocalDateTime, type: FeedType = FeedType.BOTTLE, amount: Int = 100, side: String? = null) =
        LogEntryEntity(babyId = 1, kind = "feed", startAt = start, feedType = type.key, amount = amount, side = side)

    private fun sleep(start: LocalDateTime, end: LocalDateTime) =
        LogEntryEntity(babyId = 1, kind = "sleep", startAt = start, endAt = end)

    private fun diaper(start: LocalDateTime, kind: DiaperKind) =
        LogEntryEntity(babyId = 1, kind = "diaper", startAt = start, diaperKind = kind.key)

    private fun report(logs: List<LogEntryEntity>, days: Int = 7) =
        Insights.report(today, days, logs, emptyList(), emptyList(), emptyList())

    @Test
    fun `feeds are averaged over the days that were logged, not the whole week`() {
        // Six feeds across two logged days in a seven-day window: three a day, not six sevenths.
        val logs = listOf(
            feed(at(14, 8)), feed(at(14, 12)), feed(at(14, 16)),
            feed(at(15, 8)), feed(at(15, 12)), feed(at(15, 16)),
        )
        val feeding = report(logs).feeding
        assertEquals(2, feeding.loggedDays)
        assertEquals(3.0, feeding.feedsPerDay.current!!, 0.001)
    }

    @Test
    fun `today is left out of the window because it is still happening`() {
        val logs = listOf(feed(at(16, 8)), feed(at(17, 8)), feed(at(17, 9)))
        val feeding = report(logs).feeding
        assertEquals(1, feeding.loggedDays)
        assertEquals(1.0, feeding.feedsPerDay.current!!, 0.001)
    }

    @Test
    fun `bottle intake sums millilitres per logged feeding day`() {
        val logs = listOf(
            feed(at(15, 8), amount = 120), feed(at(15, 14), amount = 90),
            feed(at(16, 9), amount = 150),
        )
        assertEquals(180.0, report(logs).feeding.bottleMlPerDay.current!!, 0.001)
    }

    @Test
    fun `a day of only breastfeeds counts as zero bottle millilitres, not as missing`() {
        val logs = listOf(
            feed(at(15, 8), amount = 200),
            feed(at(16, 8), type = FeedType.BREAST, amount = 15),
        )
        assertEquals(100.0, report(logs).feeding.bottleMlPerDay.current!!, 0.001)
    }

    @Test
    fun `a sleep past midnight is split between the two days it touches`() {
        val byDay = Insights.sleepMinutesByDay(
            listOf(sleep(at(15, 23), at(16, 6))),
            from = LocalDate.of(2026, 9, 10),
            to = today,
        )
        assertEquals(60.0, byDay[LocalDate.of(2026, 9, 15)]!!, 0.001)
        assertEquals(360.0, byDay[LocalDate.of(2026, 9, 16)]!!, 0.001)
    }

    @Test
    fun `day and night are divided at seven in the morning and seven in the evening`() {
        val from = LocalDate.of(2026, 9, 10)
        val (day, night) = Insights.daySplit(
            listOf(
                sleep(at(15, 22), at(16, 6)),  // all night: 8 h
                sleep(at(15, 13), at(15, 15)), // all day: 2 h
                sleep(at(16, 6), at(16, 8)),   // one hour each side of seven
            ),
            from, today,
        )
        assertEquals(180.0, day, 0.001)
        assertEquals(540.0, night, 0.001)
    }

    @Test
    fun `a sleep still in progress has no length and is left out`() {
        val open = LogEntryEntity(babyId = 1, kind = "sleep", startAt = at(16, 13), endAt = null)
        assertNull(report(listOf(open)).sleep.totalMinutesPerDay.current)
    }

    @Test
    fun `typical gap is the median and ignores gaps too long to be real`() {
        val gaps = Insights.feedGaps(
            listOf(
                feed(at(15, 8)), feed(at(15, 11)), feed(at(15, 14)), feed(at(15, 18)),
                feed(at(16, 20)), // 26 hours later: an unlogged stretch, not a gap
            ),
        )
        assertEquals(listOf(180.0, 180.0, 240.0), gaps)
        assertEquals(180.0, Insights.median(gaps)!!, 0.001)
    }

    @Test
    fun `a change marked both counts as wet and as dirty`() {
        val diapers = report(
            listOf(
                diaper(at(16, 8), DiaperKind.WET),
                diaper(at(16, 11), DiaperKind.BOTH),
                diaper(at(16, 15), DiaperKind.DIRTY),
            ),
        ).diapers
        assertEquals(3.0, diapers.perDay.current!!, 0.001)
        assertEquals(2.0, diapers.wetPerDay!!, 0.001)
        assertEquals(2.0, diapers.dirtyPerDay!!, 0.001)
    }

    @Test
    fun `adherence only expects doses on days inside the course`() {
        val med = MedicationEntity(
            id = 7, babyId = 1, name = "Iron drops", dose = "0.6 ml", scheduleText = "Daily",
            startDate = LocalDate.of(2026, 9, 14),
        )
        val doses = listOf(14, 15, 16).map { MedicationDoseEntity(7, LocalDate.of(2026, 9, it), at(it, 9)) }
        val result = Insights.adherence(listOf(med), doses.dropLast(1), LocalDate.of(2026, 9, 10), today)
        // Started on the 14th, so the 14th, 15th and 16th are expected; two were recorded.
        assertEquals(listOf(MedicineAdherence("Iron drops", taken = 2, expected = 3)), result)
    }

    @Test
    fun `growth is measured from the last weigh-in before the window when there is one`() {
        val rate = Insights.growthRate(
            listOf(
                GrowthEntity(babyId = 1, date = LocalDate.of(2026, 9, 3), weightKg = 7.0),
                GrowthEntity(babyId = 1, date = LocalDate.of(2026, 9, 17), weightKg = 7.9), // today: excluded
                GrowthEntity(babyId = 1, date = LocalDate.of(2026, 9, 13), weightKg = 7.3),
            ),
            start = LocalDate.of(2026, 9, 10),
            end = today,
        )!!
        // 300 g over the 10 days from the 3rd to the 13th.
        assertEquals(210.0, rate.gramsPerWeek, 0.001)
        assertEquals(LocalDate.of(2026, 9, 3), rate.since)
    }

    @Test
    fun `two weigh-ins too close together give no growth rate`() {
        assertNull(
            Insights.growthRate(
                listOf(
                    GrowthEntity(babyId = 1, date = LocalDate.of(2026, 9, 14), weightKg = 7.0),
                    GrowthEntity(babyId = 1, date = LocalDate.of(2026, 9, 15), weightKg = 7.2),
                ),
                start = LocalDate.of(2026, 9, 10),
                end = today,
            ),
        )
    }

    @Test
    fun `change against the previous window needs both sides`() {
        assertNull(Metric(current = 4.0, previous = null).change)
        assertEquals(0.25, Metric(current = 5.0, previous = 4.0).change!!, 0.001)
    }

    @Test
    fun `left share counts only breastfeeds that recorded a side`() {
        val logs = listOf(
            feed(at(16, 8), FeedType.BREAST, 10, side = "L"),
            feed(at(16, 11), FeedType.BREAST, 10, side = "L"),
            feed(at(16, 14), FeedType.BREAST, 10, side = "R"),
            feed(at(16, 17), FeedType.BREAST, 10, side = null),
            feed(at(16, 20), FeedType.BOTTLE, 100),
        )
        assertEquals(2.0 / 3.0, report(logs).feeding.leftShare!!, 0.001)
    }

    @Test
    fun `a day's sleep includes the part of last night that fell after midnight`() {
        val logs = listOf(
            sleep(at(15, 22), at(16, 5)),  // 5 hours of it on the 16th
            sleep(at(16, 13), at(16, 14)), // a one-hour nap
            feed(at(16, 9), amount = 120),
            feed(at(16, 15), type = FeedType.BREAST, amount = 12),
            diaper(at(16, 10), DiaperKind.WET),
        )
        val summary = Insights.daySummary(logs, LocalDate.of(2026, 9, 16))
        assertEquals(360.0, summary.sleepMinutes, 0.001)
        assertEquals(2, summary.feeds)
        assertEquals(120, summary.bottleMl)
        assertEquals(12, summary.breastMinutes)
        assertEquals(1, summary.diapers)
    }

    @Test
    fun `a child younger than the window is measured from birth, with no comparison`() {
        // Born on the 3rd: a "last 30 days" can only cover the 14 days since.
        val born = LocalDate.of(2026, 9, 3)
        val logs = listOf(feed(at(3, 8)), feed(at(10, 8)), feed(at(16, 8)))
        val r = Insights.report(today, 30, logs, emptyList(), emptyList(), emptyList(), born = born)
        assertEquals(14, r.days)
        assertEquals(born, r.start)
        assertEquals(14, r.feeding.daily.size)
        assertNull(r.feeding.feedsPerDay.change)
    }

    @Test
    fun `a previous period that began before birth is not compared against`() {
        // A 7-day window from the 10th is whole, but the week before it starts on the 3rd,
        // two days before this child was born.
        val born = LocalDate.of(2026, 9, 5)
        val logs = listOf(feed(at(6, 8)), feed(at(12, 8)), feed(at(12, 12)))
        val r = Insights.report(today, 7, logs, emptyList(), emptyList(), emptyList(), born = born)
        assertEquals(7, r.days)
        assertNull(r.feeding.feedsPerDay.previous)

        // Born before that week began, the comparison is made.
        val older = Insights.report(today, 7, logs, emptyList(), emptyList(), emptyList(), born = LocalDate.of(2026, 9, 1))
        assertEquals(1.0, older.feeding.feedsPerDay.previous!!, 0.001)
    }

    @Test
    fun `sleeps a day and sleep a day are averaged over the same days`() {
        // One night from ten to six touches two days: four hours and half a sleep each.
        val r = report(listOf(sleep(at(14, 22), at(15, 6)))).sleep
        assertEquals(2, r.loggedDays)
        assertEquals(240.0, r.totalMinutesPerDay.current!!, 0.001)
        assertEquals(0.5, r.napsPerDay.current!!, 0.001)
    }
}
