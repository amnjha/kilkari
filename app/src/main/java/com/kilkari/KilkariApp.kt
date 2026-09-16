package com.kilkari

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.kilkari.data.prefs.SettingsStore
import com.kilkari.data.repo.KilkariRepository
import com.kilkari.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KilkariApp : Application() {

    lateinit var repository: KilkariRepository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repository = KilkariRepository(this, SettingsStore(this))
        createNotificationChannel()
        ReminderScheduler.ensureSafetyNet(this)
        scope.launch {
            repository.seedReminders()
            ReminderScheduler.arm(this@KilkariApp, repository)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.reminder_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_REMINDERS = "kilkari_reminders"
    }
}
