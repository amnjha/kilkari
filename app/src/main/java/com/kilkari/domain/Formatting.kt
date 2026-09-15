package com.kilkari.domain

import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

object Fmt {

    /** "20 Aug" within the current year, "20 Aug 2027" otherwise — matches the prototype's `fmt`. */
    fun date(d: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (d.year == today.year) "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
        else "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"

    fun dateFull(d: LocalDate): String =
        "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"

    fun dayAndDate(d: LocalDate, today: LocalDate = LocalDate.now()): String {
        val base = "${DAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
        return if (d.year == today.year) base else "$base ${d.year}"
    }

    fun monthShort(d: LocalDate): String = MONTHS[d.monthValue - 1].uppercase(Locale.US)

    /** 12-hour clock, lowercase meridiem: "9:30 am". */
    fun time(t: LocalDateTime): String {
        val h24 = t.hour
        val h = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        val m = t.minute.toString().padStart(2, '0')
        return "$h:$m ${if (h24 < 12) "am" else "pm"}"
    }

    /** "3 weeks 5 days" — the age string on Today and More. */
    fun age(dob: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(dob, today).coerceAtLeast(0)
        return when {
            days < 7 -> "$days ${plural(days, "day")}"
            days < 365 -> {
                val w = days / 7
                val d = days % 7
                if (d == 0L) "$w ${plural(w, "week")}" else "$w ${plural(w, "week")} $d ${plural(d, "day")}"
            }
            else -> {
                val months = ChronoUnit.MONTHS.between(dob, today)
                val y = months / 12
                val m = months % 12
                if (m == 0L) "$y ${plural(y, "year")}" else "$y ${plural(y, "year")} $m ${plural(m, "month")}"
            }
        }
    }

    /** Short age used on timeline rows: "day 0", "12 days". */
    fun ageShort(dob: LocalDate, on: LocalDate): String {
        val days = ChronoUnit.DAYS.between(dob, on)
        return if (days <= 0) "day 0" else "$days ${plural(days, "day")}"
    }

    /** "1 h 20 m ago", "just now", "3 d ago" — the freshness badge on every Log tile. */
    fun ago(from: LocalDateTime?, now: LocalDateTime = LocalDateTime.now()): String {
        if (from == null) return "—"
        val mins = Duration.between(from, now).toMinutes()
        return when {
            mins < 2 -> "just now"
            mins < 60 -> "$mins m ago"
            mins < 24 * 60 -> {
                val h = mins / 60
                val m = mins % 60
                if (m == 0L) "$h h ago" else "$h h $m m ago"
            }
            else -> {
                val d = mins / (24 * 60)
                "$d d ago"
            }
        }
    }

    /** Elapsed time without the "ago" suffix, for the "asleep 40 m" style badge. */
    fun elapsed(from: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
        val mins = Duration.between(from, now).toMinutes().coerceAtLeast(0)
        return if (mins < 60) "$mins m" else "${mins / 60} h ${mins % 60} m"
    }

    /**
     * Money is stored in INR minor-free whole rupees and converted for display, mirroring the
     * prototype: INR rounds and uses Indian digit grouping, other currencies show 2 dp under 100.
     */
    fun money(amountInr: Long, currency: Currency): String {
        val v = amountInr * currency.perInr
        // The sign belongs outside the symbol: "−₹2,400", not "₹-2,400".
        val sign = if (v < 0) "\u2212" else ""
        val magnitude = abs(v)
        val body = if (currency == Currency.INR) {
            NumberFormat.getIntegerInstance(Locale("en", "IN")).format(magnitude.roundToLong())
        } else {
            if (magnitude < 100) String.format(Locale.US, "%.2f", magnitude) else magnitude.roundToLong().toString()
        }
        return sign + currency.symbol + body
    }

    /** Display value → whole rupees, so entry in any currency stores consistently. */
    fun toInr(amount: Double, currency: Currency): Long = (amount / currency.perInr).roundToLong()

    /** Amount in the display currency with no symbol — for prefilling an input. */
    fun plain(amountInr: Long, currency: Currency): String {
        val v = amountInr * currency.perInr
        return if (currency == Currency.INR) v.roundToLong().toString() else trimNum(v)
    }

    fun weight(kg: Double?): String = kg?.let { trimNum(it) + " kg" } ?: "—"

    fun length(cm: Double?): String = cm?.let { trimNum(it) + " cm" } ?: "—"

    fun grams(delta: Double): String {
        val g = (delta * 1000).roundToLong()
        return (if (g >= 0) "+" else "") + "$g g"
    }

    fun trimNum(v: Double): String =
        if (v == v.roundToLong().toDouble()) v.roundToLong().toString()
        else String.format(Locale.US, "%.1f", v)

    fun plural(n: Long, word: String) = if (n == 1L) word else word + "s"

    fun daysUntil(target: LocalDate, from: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(from, target).toInt()

    /** Whole days elapsed since [from] — the "day N" counter on Today. */
    fun daysSince(from: LocalDate, today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(from, today).toInt()

    /** "in 16 days" / "26 days overdue" / "today", for anything with a due date. */
    fun dueText(inDays: Int): String = when {
        inDays == 0 -> "today"
        inDays > 0 -> "in $inDays ${plural(inDays.toLong(), "day")}"
        else -> "${-inDays} ${plural(-inDays.toLong(), "day")} overdue"
    }

    /** Compact counterpart of [dueText] for trailing badges: "16 d" / "26 d late". */
    fun dueBadge(inDays: Int): String = when {
        inDays == 0 -> "today"
        inDays > 0 -> "$inDays d"
        else -> "${-inDays} d late"
    }

    /** Next anniversary of [dob] on or after [from] — drives the birthday countdown. */
    fun nextBirthday(dob: LocalDate, from: LocalDate = LocalDate.now()): LocalDate {
        val thisYear = dob.withYear(from.year)
        return if (thisYear.isBefore(from)) dob.withYear(from.year + 1) else thisYear
    }
}
