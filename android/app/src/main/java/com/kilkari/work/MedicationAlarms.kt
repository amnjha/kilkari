package com.kilkari.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kilkari.KilkariApp
import com.kilkari.data.db.MedicationEntity
import com.kilkari.data.repo.KilkariRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Medicine doses, on exact alarms.
 *
 * Everything else in the app is fine arriving a few minutes late, and rides WorkManager, which
 * batches work and defers it in Doze. A dose does not have that slack: "give this at 8pm" means
 * 8pm, and a reminder that drifts half an hour on a phone with aggressive battery management is
 * worse than useless — it teaches the parent not to trust it.
 *
 * Exact alarms need the user's permission from Android 12, and are cleared by a reboot, so both
 * of those are handled here rather than assumed.
 */
object MedicationAlarms {

    /** Whether the system will currently let this app set an exact alarm. */
    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarms.canScheduleExactAlarms()
    }

    /**
     * Replaces every medicine alarm with one for each active medicine's next dose.
     *
     * Alarms are one-shot: each one re-arms the next when it fires, and this rebuilds the whole
     * set whenever the medicines change, so a stopped medicine stops waking the phone.
     */
    suspend fun rescheduleAll(context: Context, repo: KilkariRepository) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val medicines = repo.medications().first()
        val remindersOn = repo.reminders().first().any { it.key == "meds" && it.enabled }

        medicines.forEach { med -> alarms.cancel(pendingFor(context, med.id)) }
        if (!remindersOn || !available(context)) return

        medicines
            .filter { it.active && it.reminderMinute != null }
            .forEach { med -> schedule(context, alarms, med) }
    }

    /**
     * Drops one medicine's alarm.
     *
     * Needed because [rescheduleAll] cancels by walking the medicines that exist, so a deleted
     * one is already gone from that list and its alarm would outlive it — waking the phone
     * each day for a medicine that is no longer there.
     */
    fun cancel(context: Context, medicationId: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pendingFor(context, medicationId))
    }

    private fun schedule(context: Context, alarms: AlarmManager, med: MedicationEntity) {
        val minute = med.reminderMinute ?: return
        val now = LocalDateTime.now()
        val todayAt = now.toLocalDate().atTime(minute / 60, minute % 60)
        val next = if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        runCatching {
            // Fires on time even in Doze, which is the entire reason a dose is not on
            // WorkManager like everything else.
            alarms.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at,
                pendingFor(context, med.id),
            )
        }
    }

    private fun pendingFor(context: Context, medicationId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            medicationId.toInt(),
            Intent(context, MedicationAlarmReceiver::class.java)
                .putExtra(EXTRA_MEDICATION_ID, medicationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    internal const val EXTRA_MEDICATION_ID = "medication_id"

    /** Ids for dose notifications, kept clear of the reminder chain's range. */
    internal const val NOTIFICATION_OFFSET = 900
}

/** Posts one dose reminder and arms the next. */
class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? KilkariApp ?: return
        val id = intent.getLongExtra(MedicationAlarms.EXTRA_MEDICATION_ID, -1L)
        if (id < 0) return

        // The database read cannot happen on the receiver's thread, so the broadcast is held
        // open until it finishes.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val med = app.repository.medications().first().firstOrNull { it.id == id }
                if (med != null && med.active) {
                    postReminderNotification(
                        context,
                        MedicationAlarms.NOTIFICATION_OFFSET + id.toInt(),
                        "${med.name} · ${med.dose}",
                        "Due ${med.scheduleText}",
                    )
                }
                // Tomorrow's dose, set now that today's has gone off.
                MedicationAlarms.rescheduleAll(context, app.repository)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Re-arms medicine alarms after a restart.
 *
 * WorkManager restores its own jobs, but the alarm manager does not: every exact alarm is
 * dropped on reboot, so without this a phone restart would silently end dose reminders.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? KilkariApp ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MedicationAlarms.rescheduleAll(context, app.repository)
            } finally {
                pending.finish()
            }
        }
    }
}
