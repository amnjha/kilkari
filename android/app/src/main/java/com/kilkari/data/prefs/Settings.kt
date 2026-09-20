package com.kilkari.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kilkari.domain.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kilkari_settings")

data class AppSettings(
    val currency: Currency = Currency.INR,
    val scheduleId: String = "iap",
    val metricUnits: Boolean = true,
    val onboarded: Boolean = false,
    val todayVariant: String = "A",
    val autoBackup: Boolean = true,
    /** Standing monthly top-up of the savings account; 0 means no plan set. */
    val fundMonthlyInr: Long = 0,
    /** Day of month the top-up is made, 1–28. */
    val fundDepositDay: Int = 1,
    val fundAccountName: String = "Baby fund",
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val CURRENCY = stringPreferencesKey("currency")
        val SCHEDULE = stringPreferencesKey("schedule")
        val METRIC = booleanPreferencesKey("metric")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val VARIANT = stringPreferencesKey("today_variant")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val FUND_MONTHLY = longPreferencesKey("fund_monthly")
        val FUND_DAY = intPreferencesKey("fund_day")
        val FUND_NAME = stringPreferencesKey("fund_name")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            currency = Currency.of(p[Keys.CURRENCY]),
            scheduleId = p[Keys.SCHEDULE] ?: "iap",
            metricUnits = p[Keys.METRIC] ?: true,
            onboarded = p[Keys.ONBOARDED] ?: false,
            todayVariant = p[Keys.VARIANT] ?: "A",
            autoBackup = p[Keys.AUTO_BACKUP] ?: true,
            fundMonthlyInr = p[Keys.FUND_MONTHLY] ?: 0,
            fundDepositDay = p[Keys.FUND_DAY] ?: 1,
            fundAccountName = p[Keys.FUND_NAME] ?: "Baby fund",
        )
    }

    suspend fun setCurrency(c: Currency) = context.dataStore.edit { it[Keys.CURRENCY] = c.code }
    suspend fun setSchedule(id: String) = context.dataStore.edit { it[Keys.SCHEDULE] = id }
    suspend fun setMetric(metric: Boolean) = context.dataStore.edit { it[Keys.METRIC] = metric }
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDED] = v }
    suspend fun setTodayVariant(v: String) = context.dataStore.edit { it[Keys.VARIANT] = v }
    suspend fun setAutoBackup(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_BACKUP] = v }

    suspend fun setFundPlan(monthlyInr: Long, day: Int, name: String) = context.dataStore.edit {
        it[Keys.FUND_MONTHLY] = monthlyInr
        it[Keys.FUND_DAY] = day.coerceIn(1, 28)
        it[Keys.FUND_NAME] = name.ifBlank { "Baby fund" }
    }
}
