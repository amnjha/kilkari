package com.kilkari.domain

import com.kilkari.data.db.ReminderEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * When a reminder is due, and which minute the notification chain should wake for next.
 *
 * The chain arms one run at a time: each run posts what is due at that minute and then asks
 * this for the next one. Getting it wrong is invisible — a notification that never arrives
 * looks like an app that does not send notifications — so the decision is kept here as
 * arithmetic over dates rather than inside the worker.
 */
object Recurrence {

    /** A week ahead: long enough to catch anything weekly without scanning a whole month. */
    const val SEARCH_DAYS = 7

    /** True when this reminder's cadence lands on [day]. */
    fun dueOn(reminder: ReminderEntity, day: LocalDate): Boolean = when (RepeatRule.of(reminder.repeatRule)) {
        RepeatRule.DAILY -> true
        RepeatRule.WEEKLY -> day.dayOfWeek.value == (reminder.weekday ?: 7)
        RepeatRule.MONTHLY -> day.dayOfMonth == (reminder.dayOfMonth ?: 1).coerceIn(1, 28)
        RepeatRule.NONE -> reminder.startDate == day
    }

    /**
     * The next moment anything is due, given what each day holds.
     *
     * [minutesOn] is asked for one day at a time so the caller can do the reading; today is
     * filtered to minutes strictly after the current one, or the run that has just fired would
     * arm itself again for the same minute and announce everything twice.
     */
    fun nextMoment(now: LocalDateTime, minutesOn: (LocalDate) -> List<Int>): Pair<LocalDateTime, Int>? {
        val today = now.toLocalDate()
        val minuteNow = now.hour * 60 + now.minute
        for (offset in 0..SEARCH_DAYS) {
            val day = today.plusDays(offset.toLong())
            val minutes = minutesOn(day)
            val next = if (offset == 0) minutes.filter { it > minuteNow } else minutes
            next.minOrNull()?.let { return day.atTime(it / 60, it % 60) to it }
        }
        return null
    }
}
