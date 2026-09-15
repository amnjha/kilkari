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
import androidx.work.WorkerParameters
import com.kilkari.KilkariApp
import com.kilkari.MainActivity
import com.kilkari.R
import com.kilkari.data.repo.KilkariRepository
import com.kilkari.domain.Fmt
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Runs daily and posts whatever is due: medicine doses, vaccines at 7 and 1 day out,
 * appointments a day ahead, the fund top-up, and every weekly or custom reminder whose
 * cadence lands today. One worker covers all of them so the app schedules exactly one job.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KilkariApp ?: return Result.success()
        val repo: KilkariRepository = app.repository
        val baby = repo.baby.first() ?: return Result.success()
        val enabled = repo.reminders().first().filter { it.enabled }.map { it.key }.toSet()
        val today = LocalDate.now()
        val notes = mutableListOf<Pair<String, String>>()

        if ("meds" in enabled) {
            val meds = repo.medications().first().filter { it.active }
            meds.forEach { notes += "${it.name} · ${it.dose}" to "Due ${it.scheduleText}" }
        }

        if ("vac" in enabled) {
            repo.vaccineGroups(today).first()
                .filter { !it.allGiven && (it.inDays == 7 || it.inDays == 1 || it.inDays == 0) }
                .forEach {
                    val whenText = when (it.inDays) {
                        0 -> "due today"
                        1 -> "due tomorrow"
                        else -> "due in ${it.inDays} days"
                    }
                    notes += "${it.label} vaccines $whenText" to it.names
                }
        }

        if ("appt" in enabled) {
            repo.appointments().first()
                .filter { it.startAt.toLocalDate() == today.plusDays(1) }
                .forEach { notes += "Tomorrow: ${it.title}" to listOfNotNull(Fmt.time(it.startAt), it.place).joinToString(" · ") }
        }

        if ("fund" in enabled) {
            val settings = repo.settings.first()
            val due = settings.fundMonthlyInr > 0 && today.dayOfMonth == settings.fundDepositDay.coerceIn(1, 28)
            val alreadyPaid = repo.lastDepositDate()?.let {
                it.year == today.year && it.month == today.month
            } == true
            if (due && !alreadyPaid) {
                notes += "Top up ${settings.fundAccountName}" to
                    "${Fmt.money(settings.fundMonthlyInr, settings.currency)} due today"
            }
        }

        // Weekly prompts and anything custom, notified on the day their occurrence lands.
        repo.reminders().first()
            .filter { it.enabled && (!it.builtIn || it.key in SELF_STANDING) }
            .forEach { r ->
                if (dueToday(r, today)) {
                    notes += r.title to r.subtitle.ifBlank { "Due today" }
                }
            }

        notes.forEachIndexed { i, (title, body) -> notify(i, title, body) }
        return Result.success()
    }

    private fun notify(id: Int, title: String, body: String) {
        val ctx = applicationContext
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

    /** True when this reminder's cadence lands on [today]. */
    private fun dueToday(r: com.kilkari.data.db.ReminderEntity, today: LocalDate): Boolean =
        when (com.kilkari.domain.RepeatRule.of(r.repeatRule)) {
            com.kilkari.domain.RepeatRule.DAILY -> true
            com.kilkari.domain.RepeatRule.WEEKLY -> today.dayOfWeek.value == (r.weekday ?: 7)
            com.kilkari.domain.RepeatRule.MONTHLY -> today.dayOfMonth == (r.dayOfMonth ?: 1).coerceIn(1, 28)
            com.kilkari.domain.RepeatRule.NONE -> r.startDate == today
        }

    companion object {
        private const val NOTIFICATION_BASE = 4200

        /** Built-in reminders that stand on their own rather than gating derived tasks. */
        private val SELF_STANDING = setOf("album", "weigh")
    }
}

/** Kept separate so [KilkariApp] does not need to know WorkManager's API surface. */
object ReminderScheduler {

    const val WORK_NAME = "kilkari_daily_reminders"

    fun rescheduleAll(context: Context) {
        val now = LocalDateTime.now()
        val nextRun = now.toLocalDate().atTime(8, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }
        val delayMinutes = java.time.Duration.between(now, nextRun).toMinutes()

        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            androidx.work.PeriodicWorkRequestBuilder<ReminderWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .setInitialDelay(delayMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .build(),
        )
    }
}
