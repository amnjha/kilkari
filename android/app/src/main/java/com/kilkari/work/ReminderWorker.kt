package com.kilkari.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kilkari.KilkariApp
import com.kilkari.MainActivity
import com.kilkari.R
import com.kilkari.data.db.ReminderEntity
import com.kilkari.data.repo.KilkariRepository
import com.kilkari.domain.Fmt
import com.kilkari.domain.PaperworkStatus
import com.kilkari.domain.Recurrence
import com.kilkari.domain.RepeatRule
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Posts reminders at the time they are set for.
 *
 * There is no single daily sweep: a reminder carries a time, and a sweep at a fixed hour either
 * announced it hours early or, for anything set later in the day, looked like the app simply
 * did not send notifications. Instead each run is scheduled for one particular minute, posts
 * what is due at that minute, and then arms the next one — a chain that always points at the
 * next thing rather than at the same hour every morning.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KilkariApp ?: return Result.success()
        val repo = app.repository
        if (repo.baby.first() == null) return Result.success()

        // A run with no minute is the daily safety net: it exists only to rebuild the chain if
        // it was ever broken, and must not post anything of its own.
        val minute = inputData.getInt(KEY_MINUTE, NO_MINUTE)
        if (minute != NO_MINUTE) {
            notesFor(applicationContext, repo, LocalDate.now())
                .filter { it.minuteOfDay == minute }
                .forEachIndexed { i, note -> notify(minute * 100 + i, note.title, note.body) }
        }

        ReminderScheduler.arm(applicationContext, repo)
        return Result.success()
    }

    private fun notify(id: Int, title: String, body: String) =
        postReminderNotification(applicationContext, id, title, body)

    companion object {
        const val KEY_MINUTE = "minute_of_day"
        const val NO_MINUTE = -1
    }
}

/** Where reminder notification ids start, so they never collide with anything else. */
internal const val NOTIFICATION_BASE = 4200

/** One reminder notification, posted the same way whoever noticed it was due. */
internal fun postReminderNotification(ctx: Context, id: Int, title: String, body: String) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) return

    val intent = PendingIntent.getActivity(
        ctx, id,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val notification = NotificationCompat.Builder(ctx, KilkariApp.CHANNEL_REMINDERS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(intent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(ctx).notify(NOTIFICATION_BASE + id, notification)
}

/** Something to be said, and the minute of the day it should be said at. */
internal data class Note(val minuteOfDay: Int, val title: String, val body: String)

/**
 * The hour things with a date but no time are announced at — a vaccine is due on a day, not at
 * half past two.
 */
internal const val DIGEST_MINUTE = 8 * 60

/** Built-in reminders that stand on their own rather than gating derived tasks. */
private val SELF_STANDING = setOf("album", "weigh")

/** Everything wanting to be said on [day], each carrying the minute it is due at. */
internal suspend fun notesFor(
    context: Context,
    repo: KilkariRepository,
    day: LocalDate,
): List<Note> {
    val reminders = repo.reminders().first()
    val enabled = reminders.filter { it.enabled }.map { it.key }.toSet()
    val notes = mutableListOf<Note>()

    // Medicines are on exact alarms wherever the system allows them, and only fall back to
    // this chain when it cannot. Without the check both would fire and every dose would be
    // announced twice.
    if ("meds" in enabled && !MedicationAlarms.available(context)) {
        repo.medications().first().filter { it.active }.forEach { med ->
            notes += Note(
                med.reminderMinute ?: DIGEST_MINUTE,
                "${med.name} · ${med.dose}",
                "Due ${med.scheduleText}",
            )
        }
    }

    if ("vac" in enabled) {
        repo.vaccineGroups(day).first()
            .filter { !it.allGiven && (it.inDays == 7 || it.inDays == 1 || it.inDays == 0) }
            .forEach {
                val whenText = when (it.inDays) {
                    0 -> "due today"
                    1 -> "due tomorrow"
                    else -> "due in ${it.inDays} days"
                }
                notes += Note(DIGEST_MINUTE, "${it.label} vaccines $whenText", it.names)
            }
    }

    // The identity document whose turn it is: a week out, the day before, the day itself,
    // and then once a week for as long as it stays outstanding — an office visit slips.
    if ("docs" in enabled) {
        repo.paperwork(day).first()
            .firstOrNull { it.status == PaperworkStatus.ACTIVE }
            ?.let { step ->
                val days = step.inDays ?: return@let
                val announce = days == 7 || days == 1 || days == 0 || (days < 0 && days % 7 == 0)
                if (!announce) return@let
                val whenText = when {
                    days == 0 -> "due today"
                    days == 1 -> "due tomorrow"
                    days > 0 -> "due in $days days"
                    else -> "${-days} days overdue"
                }
                notes += Note(
                    DIGEST_MINUTE,
                    "${step.kind.title} $whenText",
                    "${step.kind.leadText} · ${step.kind.needs.first()}",
                )
            }
    }

    if ("appt" in enabled) {
        repo.appointments().first()
            .filter { it.startAt.toLocalDate() == day.plusDays(1) }
            .forEach {
                notes += Note(
                    DIGEST_MINUTE,
                    "Tomorrow: ${it.title}",
                    listOfNotNull(Fmt.time(it.startAt), it.place).joinToString(" · "),
                )
            }
    }

    if ("fund" in enabled) {
        val settings = repo.settings.first()
        val due = settings.fundMonthlyInr > 0 &&
            day.dayOfMonth == settings.fundDepositDay.coerceIn(1, 28)
        val alreadyPaid = repo.lastDepositDate()?.let {
            it.year == day.year && it.month == day.month
        } == true
        if (due && !alreadyPaid) {
            notes += Note(
                DIGEST_MINUTE,
                "Top up ${settings.fundAccountName}",
                "${Fmt.money(settings.fundMonthlyInr, settings.currency)} due today",
            )
        }
    }

    // Weekly prompts and anything the parent added, each at its own time.
    reminders
        .filter { it.enabled && (!it.builtIn || it.key in SELF_STANDING) }
        .filter { Recurrence.dueOn(it, day) }
        .forEach { r ->
            notes += Note(
                r.minuteOfDay ?: DIGEST_MINUTE,
                r.title,
                r.subtitle.ifBlank { "Due today" },
            )
        }

    return notes
}

/** Kept separate so [KilkariApp] does not need to know WorkManager's API surface. */
object ReminderScheduler {

    private const val TICK_WORK = "kilkari_reminder_tick"
    private const val SAFETY_WORK = "kilkari_reminder_safety"

    /** The single daily sweep this replaced. Cancelled so upgrades do not keep running it. */
    private const val LEGACY_DAILY_WORK = "kilkari_daily_reminders"

    /**
     * Points the chain at the next minute anything is due.
     *
     * Called on launch, after each run, and whenever a reminder or medicine changes — without
     * that last one, a reminder set for five minutes from now would wait for the next run of
     * whatever was already scheduled.
     */
    suspend fun arm(context: Context, repo: KilkariRepository) {
        val now = LocalDateTime.now()
        val next = nextMoment(context, repo, now) ?: return

        WorkManager.getInstance(context).enqueueUniqueWork(
            TICK_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(
                    Duration.between(now, next.first).toMillis().coerceAtLeast(0),
                    TimeUnit.MILLISECONDS,
                )
                .setInputData(Data.Builder().putInt(ReminderWorker.KEY_MINUTE, next.second).build())
                .build(),
        )
    }

    /**
     * A daily run that only re-arms. The chain is self-sustaining while it runs, but a force
     * stop or a missed run would otherwise end it until the app was next opened.
     */
    fun ensureSafetyNet(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_DAILY_WORK)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SAFETY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    /**
     * The next moment something is due.
     *
     * Searches a week rather than just today and tomorrow: a Sunday photo check-in looked at
     * on a Wednesday is four days out, and stopping at tomorrow armed nothing at all, leaving
     * the whole chain resting on whenever the daily safety net happened to run. A monthly
     * reminder further out than this is picked up by that safety net as the day approaches.
     */
    private suspend fun nextMoment(
        context: Context,
        repo: KilkariRepository,
        now: LocalDateTime,
    ): Pair<LocalDateTime, Int>? {
        // Read a week of days up front: the decision itself is arithmetic, and lives in
        // Recurrence where it can be tested without a worker, a database or a clock.
        val minutesByDay = (0..Recurrence.SEARCH_DAYS).associate { offset ->
            val day = now.toLocalDate().plusDays(offset.toLong())
            day to notesFor(context, repo, day).map { it.minuteOfDay }
        }
        return Recurrence.nextMoment(now) { day -> minutesByDay[day].orEmpty() }
    }
}
