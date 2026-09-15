package com.kilkari.ui

import com.kilkari.data.db.AppointmentEntity
import com.kilkari.data.db.ChecklistEntity
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.MedicationDoseEntity
import com.kilkari.data.db.MedicationEntity
import com.kilkari.data.db.ReminderEntity
import com.kilkari.data.db.TaskStateEntity
import com.kilkari.domain.DueTask
import com.kilkari.domain.DueTaskKind
import com.kilkari.domain.Fmt
import com.kilkari.domain.RepeatRule
import com.kilkari.domain.VaccineGroupState
import com.kilkari.ui.nav.Routes
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Turns everything the app knows into the single list of things outstanding today.
 *
 * Every Today layout renders this, so the agenda, hero and checklist views cannot disagree
 * about what is due. Reminders act as switches: the built-in ones gate the tasks derived from
 * medicines, vaccines, appointments and the fund, while weekly prompts and anything custom
 * produce a task of their own.
 */
object DueTaskBuilder {

    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun build(
        today: LocalDate,
        now: LocalDateTime,
        medications: List<MedicationEntity>,
        medicationDoses: List<MedicationDoseEntity>,
        appointments: List<AppointmentEntity>,
        vaccineGroups: List<VaccineGroupState>,
        reminders: List<ReminderEntity>,
        checklist: List<ChecklistEntity>,
        taskStates: List<TaskStateEntity>,
        openSleep: LogEntryEntity?,
        fundDepositDue: LocalDate?,
    ): List<DueTask> {
        val on = reminders.filter { it.enabled }.associateBy { it.key }
        val state = taskStates.associateBy { it.taskKey to it.occurrenceDate }
        val tasks = mutableListOf<DueTask>()

        // Medicines still to give today.
        if (on.containsKey("meds")) {
            val takenToday = medicationDoses.filter { it.date == today }.map { it.medicationId }.toSet()
            medications.filter { it.active }.forEach { med ->
                tasks += DueTask(
                    id = "med:${med.id}",
                    kind = DueTaskKind.MEDICATION,
                    title = "${med.name} · ${med.dose}",
                    subtitle = med.scheduleText,
                    trailing = med.reminderMinute?.let(::clock) ?: "today",
                    icon = "pill",
                    done = med.id in takenToday,
                    completable = true,
                    dismissible = false,
                    occurrence = today,
                    overdue = med.reminderMinute?.let { it < now.hour * 60 + now.minute } == true &&
                        med.id !in takenToday,
                    route = Routes.MEDS,
                )
            }
        }

        // A nap in progress is worth surfacing so it can be ended from here.
        openSleep?.let { sleep ->
            tasks += DueTask(
                id = "sleep",
                kind = DueTaskKind.SLEEP,
                title = "Napping now",
                subtitle = "Since ${Fmt.time(sleep.startAt)}${sleep.place?.let { " · $it" }.orEmpty()}",
                trailing = Fmt.elapsed(sleep.startAt, now),
                icon = "bedtime",
                done = false,
                completable = false,
                dismissible = false,
                occurrence = today,
                overdue = false,
                route = Routes.LOG,
            )
        }

        // Today's appointments.
        if (on.containsKey("appt")) {
            appointments.filter { it.startAt.toLocalDate() == today }.forEach { appt ->
                tasks += DueTask(
                    id = "appt:${appt.id}",
                    kind = DueTaskKind.APPOINTMENT,
                    title = appt.title,
                    subtitle = listOfNotNull(appt.doctor, appt.place).joinToString(" · "),
                    trailing = Fmt.time(appt.startAt),
                    icon = "stethoscope",
                    done = false,
                    completable = false,
                    dismissible = false,
                    occurrence = today,
                    overdue = appt.startAt.isBefore(now),
                    route = Routes.APPOINTMENTS,
                )
            }
        }

        // Vaccine groups whose date has arrived and that are not fully recorded.
        if (on.containsKey("vac")) {
            vaccineGroups.filter { !it.allGiven && it.inDays <= 0 }.forEach { group ->
                tasks += DueTask(
                    id = "vac:${group.label}",
                    kind = DueTaskKind.VACCINE,
                    title = "${group.label} vaccines",
                    subtitle = group.names,
                    trailing = Fmt.dueBadge(group.inDays),
                    icon = "vaccines",
                    done = false,
                    completable = false,
                    dismissible = false,
                    occurrence = group.dueDate,
                    overdue = group.inDays < 0,
                    route = Routes.VACCINES,
                )
            }
        }

        // The savings account top-up, once its day has come round.
        if (on.containsKey("fund") && fundDepositDue != null && !fundDepositDue.isAfter(today)) {
            val acted = state["fund" to fundDepositDue]
            if (acted?.done != true && acted?.dismissed != true) {
                tasks += DueTask(
                    id = "rem:fund",
                    kind = DueTaskKind.REMINDER,
                    title = on.getValue("fund").title,
                    subtitle = on.getValue("fund").subtitle,
                    trailing = Fmt.dueBadge(Fmt.daysUntil(fundDepositDue, today)),
                    icon = "savings",
                    done = false,
                    completable = true,
                    dismissible = true,
                    occurrence = fundDepositDue,
                    overdue = fundDepositDue.isBefore(today),
                    route = Routes.MONEY,
                )
            }
        }

        // The daily checklist.
        checklist.forEach { row ->
            tasks += DueTask(
                id = "check:${row.key}",
                kind = DueTaskKind.CHECKLIST,
                title = row.title,
                subtitle = "",
                trailing = row.timeText,
                icon = "check_circle",
                done = row.done,
                completable = true,
                dismissible = false,
                occurrence = row.date,
                overdue = false,
                route = null,
            )
        }

        // Weekly prompts and anything the parent added themselves. These persist across days:
        // a Sunday photo check-in stays put until it is ticked or dismissed.
        reminders.filter { it.enabled && (!it.builtIn || it.key in SELF_STANDING) }.forEach { r ->
            val occurrence = occurrenceOf(r, today) ?: return@forEach
            val acted = state[r.key to occurrence]
            if (acted?.dismissed == true) return@forEach
            val days = Fmt.daysUntil(occurrence, today)
            tasks += DueTask(
                id = "rem:${r.key}",
                kind = DueTaskKind.REMINDER,
                title = r.title,
                subtitle = listOfNotNull(
                    r.subtitle.ifBlank { null },
                    r.minuteOfDay?.let(::clock),
                ).joinToString(" · "),
                trailing = if (days == 0) "today" else Fmt.dueBadge(days),
                icon = iconFor(r.key),
                done = acted?.done == true,
                completable = true,
                dismissible = true,
                occurrence = occurrence,
                overdue = occurrence.isBefore(today),
                route = routeFor(r.key),
            )
        }

        return tasks.sortedWith(
            compareBy<DueTask> { it.done }
                .thenByDescending { it.overdue }
                .thenBy { it.kind.ordinal },
        )
    }

    /** Built-in reminders that are a task in their own right rather than a switch. */
    private val SELF_STANDING = setOf("weigh", "album")

    /**
     * The occurrence a reminder is currently sitting on.
     *
     * Daily reminders only ever mean today, so they clear themselves overnight. Anything less
     * frequent points at its most recent occurrence and stays there until acted on.
     */
    private fun occurrenceOf(r: ReminderEntity, today: LocalDate): LocalDate? =
        when (RepeatRule.of(r.repeatRule)) {
            RepeatRule.DAILY -> today
            RepeatRule.WEEKLY -> {
                val weekday = (r.weekday ?: 7).coerceIn(1, 7)
                today.minusDays(((today.dayOfWeek.value - weekday + 7) % 7).toLong())
            }
            RepeatRule.MONTHLY -> {
                val day = (r.dayOfMonth ?: 1).coerceIn(1, 28)
                val thisMonth = today.withDayOfMonth(day)
                if (thisMonth.isAfter(today)) thisMonth.minusMonths(1) else thisMonth
            }
            RepeatRule.NONE -> r.startDate?.takeIf { !it.isAfter(today) }
        }

    private fun iconFor(key: String) = when (key) {
        "album" -> "photo_camera"
        "weigh" -> "monitor_weight"
        "fund" -> "savings"
        else -> "notifications_active"
    }

    private fun routeFor(key: String) = when (key) {
        "album" -> Routes.PHOTOS
        "weigh" -> Routes.GROWTH
        "fund" -> Routes.MONEY
        else -> null
    }

    private fun clock(minuteOfDay: Int): String {
        val h24 = minuteOfDay / 60
        val m = (minuteOfDay % 60).toString().padStart(2, '0')
        val h = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return "$h:$m ${if (h24 < 12) "am" else "pm"}"
    }
}
