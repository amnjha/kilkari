package com.kilkari.domain

import com.kilkari.data.db.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceTest {

    private fun reminder(
        rule: RepeatRule,
        weekday: Int? = null,
        dayOfMonth: Int? = null,
        startDate: LocalDate? = null,
    ) = ReminderEntity(
        key = "r", title = "Reminder", subtitle = "", enabled = true, builtIn = false,
        repeatRule = rule.key, weekday = weekday, dayOfMonth = dayOfMonth, startDate = startDate,
    )

    private val monday = LocalDate.of(2026, 9, 21)
    private val sunday = LocalDate.of(2026, 9, 20)

    @Test
    fun `a daily reminder is due every day`() {
        assertTrue(Recurrence.dueOn(reminder(RepeatRule.DAILY), monday))
        assertTrue(Recurrence.dueOn(reminder(RepeatRule.DAILY), sunday))
    }

    @Test
    fun `a weekly reminder is due on its own weekday`() {
        val mondays = reminder(RepeatRule.WEEKLY, weekday = 1)
        assertTrue(Recurrence.dueOn(mondays, monday))
        assertFalse(Recurrence.dueOn(mondays, sunday))
        // No weekday recorded means Sunday, which is what the photo check-in relies on.
        assertTrue(Recurrence.dueOn(reminder(RepeatRule.WEEKLY), sunday))
    }

    @Test
    fun `a monthly reminder is due on its date, and never past the 28th`() {
        assertTrue(Recurrence.dueOn(reminder(RepeatRule.MONTHLY, dayOfMonth = 21), monday))
        assertFalse(Recurrence.dueOn(reminder(RepeatRule.MONTHLY, dayOfMonth = 22), monday))
        // A 31st would never come round in February; it is pulled back to the 28th.
        val late = reminder(RepeatRule.MONTHLY, dayOfMonth = 31)
        assertTrue(Recurrence.dueOn(late, LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `a one-off is due on its day alone`() {
        val once = reminder(RepeatRule.NONE, startDate = monday)
        assertTrue(Recurrence.dueOn(once, monday))
        assertFalse(Recurrence.dueOn(once, monday.plusDays(1)))
        assertFalse(Recurrence.dueOn(reminder(RepeatRule.NONE), monday))
    }

    @Test
    fun `the chain arms the next minute still to come today`() {
        val now = LocalDateTime.of(2026, 9, 20, 9, 30)
        val next = Recurrence.nextMoment(now) { day ->
            if (day == sunday) listOf(8 * 60, 10 * 60, 20 * 60) else emptyList()
        }
        assertEquals(LocalDateTime.of(2026, 9, 20, 10, 0), next?.first)
        assertEquals(10 * 60, next?.second)
    }

    @Test
    fun `a minute that has just fired does not arm itself again`() {
        val now = LocalDateTime.of(2026, 9, 20, 10, 0)
        val next = Recurrence.nextMoment(now) { day ->
            if (day == sunday) listOf(10 * 60) else listOf(9 * 60)
        }
        // Not 10:00 again today: tomorrow's 9:00.
        assertEquals(LocalDateTime.of(2026, 9, 21, 9, 0), next?.first)
    }

    @Test
    fun `something days away is still found, so nothing rests on the daily safety net`() {
        val now = LocalDateTime.of(2026, 9, 16, 12, 0)
        val next = Recurrence.nextMoment(now) { day ->
            if (day == sunday) listOf(10 * 60) else emptyList()
        }
        assertEquals(LocalDateTime.of(2026, 9, 20, 10, 0), next?.first)
    }

    @Test
    fun `nothing within the week arms nothing`() {
        val now = LocalDateTime.of(2026, 9, 1, 12, 0)
        assertNull(Recurrence.nextMoment(now) { emptyList() })
        // A month out is past the horizon; the daily safety net picks it up nearer the time.
        assertNull(
            Recurrence.nextMoment(now) { day ->
                if (day == LocalDate.of(2026, 10, 1)) listOf(8 * 60) else emptyList()
            }
        )
    }
}
