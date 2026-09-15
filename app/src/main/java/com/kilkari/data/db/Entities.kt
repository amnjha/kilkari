package com.kilkari.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "baby")
data class BabyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dob: LocalDate,
    val birthTime: LocalDateTime? = null,
    val birthWeightKg: Double? = null,
    val birthLengthCm: Double? = null,
    val birthHeadCm: Double? = null,
    val birthPlace: String? = null,
)

/**
 * One row per logged routine event. A single table keeps "Today's entries" and the
 * per-tile "last logged" queries simple; [kind] selects which of the optional columns apply.
 */
@Entity(tableName = "log_entry", indices = [Index("babyId", "startAt"), Index("kind")])
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val kind: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime? = null,
    /** breast | bottle | solid */
    val feedType: String? = null,
    /** L | R */
    val side: String? = null,
    /** minutes for breast, ml for bottle, g for solids */
    val amount: Int? = null,
    /** wet | dirty | both */
    val diaperKind: String? = null,
    val place: String? = null,
    val medicationId: Long? = null,
    val medicationName: String? = null,
    val dose: String? = null,
    val note: String? = null,
)

@Entity(tableName = "growth", indices = [Index("babyId", "date")])
data class GrowthEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val date: LocalDate,
    val weightKg: Double? = null,
    val lengthCm: Double? = null,
    val headCm: Double? = null,
)

/** Erupted teeth. [code] is "u0".."u9" / "l0".."l9", left-to-right as drawn on the Teeth screen. */
@Entity(tableName = "tooth", primaryKeys = ["babyId", "code"])
data class ToothEntity(
    val babyId: Long,
    val code: String,
    val eruptedOn: LocalDate,
)

/** A dose recorded as given. Absence of a row means "not yet given". */
@Entity(
    tableName = "vaccine_dose",
    primaryKeys = ["babyId", "scheduleId", "groupLabel", "vaccineName"],
    indices = [Index("babyId", "scheduleId")],
)
data class VaccineDoseEntity(
    val babyId: Long,
    val scheduleId: String,
    val groupLabel: String,
    val vaccineName: String,
    val givenOn: LocalDate,
    val clinic: String? = null,
    /** Product actually administered, e.g. "Pentavac" — optional, often on the vial label. */
    val brand: String? = null,
)

/** Cost recorded against a whole vaccine group, shown on the group detail screen. */
@Entity(tableName = "vaccine_cost", primaryKeys = ["babyId", "scheduleId", "groupLabel"])
data class VaccineCostEntity(
    val babyId: Long,
    val scheduleId: String,
    val groupLabel: String,
    val costInr: Long,
)

@Entity(tableName = "medication", indices = [Index("babyId")])
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val name: String,
    val dose: String,
    val scheduleText: String,
    val prescriber: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    /** Minutes past midnight for the daily reminder, null for "as needed". */
    val reminderMinute: Int? = null,
)

@Entity(tableName = "medication_dose", primaryKeys = ["medicationId", "date"])
data class MedicationDoseEntity(
    val medicationId: Long,
    val date: LocalDate,
    val takenAt: LocalDateTime,
)

@Entity(tableName = "appointment", indices = [Index("babyId", "startAt")])
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val title: String,
    val startAt: LocalDateTime,
    val doctor: String? = null,
    val place: String? = null,
    val notes: String? = null,
    val reminderDaysBefore: Int? = 1,
)

@Entity(tableName = "expense", indices = [Index("babyId", "date")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val title: String,
    val vendor: String? = null,
    /** med | gen */
    val category: String,
    val amountInr: Long,
    val date: LocalDate,
    val icon: String = "shopping_bag",
    /** Whether this came out of the savings account rather than from elsewhere. */
    val paidFromFund: Boolean = true,
)

@Entity(tableName = "timeline", indices = [Index("babyId", "date")])
data class TimelineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val date: LocalDate,
    val title: String,
    val subtitle: String = "",
    val icon: String = "auto_awesome",
    val albumUrl: String? = null,
)

@Entity(tableName = "document", indices = [Index("babyId", "filedOn")])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val title: String,
    val filedOn: LocalDate,
    val pageCount: Int = 1,
    val tags: String = "",
    /** Comma-separated content:// or file:// URIs, one per scanned page. */
    val pageUris: String = "",
)

@Entity(tableName = "album", indices = [Index("babyId")])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val title: String,
    val subtitle: String = "",
    val url: String,
)

@Entity(tableName = "event", indices = [Index("babyId", "date")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val title: String,
    val subtitle: String = "",
    val date: LocalDate,
    val icon: String = "event",
    val annual: Boolean = false,
)

/** Daily checklist item. Rows are created lazily the first time a day is opened. */
@Entity(tableName = "checklist", primaryKeys = ["babyId", "date", "key"])
data class ChecklistEntity(
    val babyId: Long,
    val date: LocalDate,
    val key: String,
    val title: String,
    val timeText: String,
    val done: Boolean = false,
)

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey val key: String,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    /** Built-ins are derived from app data and cannot be deleted, only switched off. */
    val builtIn: Boolean = true,
    /** Minutes past midnight, for reminders that name a time. */
    val minuteOfDay: Int? = null,
    /** none | daily | weekly | monthly */
    val repeatRule: String = "none",
    /** 1 (Monday) – 7 (Sunday), for weekly repeats. */
    val weekday: Int? = null,
    /** 1–28, for monthly repeats. */
    val dayOfMonth: Int? = null,
    /** The single occurrence for a non-repeating reminder. */
    val startDate: LocalDate? = null,
)

/**
 * Whether one occurrence of a task has been dealt with.
 *
 * Keyed by occurrence rather than by task, so a weekly prompt stays on the Today screen until
 * it is actually ticked or dismissed instead of vanishing when the day rolls over.
 */
@Entity(tableName = "task_state", primaryKeys = ["taskKey", "occurrenceDate"])
data class TaskStateEntity(
    val taskKey: String,
    val occurrenceDate: LocalDate,
    val done: Boolean = false,
    val dismissed: Boolean = false,
)

/**
 * A deposit into, or withdrawal from, the savings account the child's costs are paid from.
 * Expenses and investment contributions are *not* mirrored here — the balance subtracts them
 * directly, so deleting an expense restores the balance with no rows to keep in sync.
 */
@Entity(tableName = "fund_txn", indices = [Index("babyId", "date")])
data class FundTxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    /** deposit | withdrawal */
    val kind: String,
    val amountInr: Long,
    val date: LocalDate,
    val note: String? = null,
)

@Entity(tableName = "investment", indices = [Index("babyId")])
data class InvestmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val name: String,
    /** fd | rd | sip | ppf | ssy | gold | other */
    val kind: String,
    val institution: String? = null,
    /** Instalment for a recurring plan; null for a lump sum. */
    val monthlyInr: Long? = null,
    /** Percent per annum, where the instrument has a stated rate. */
    val interestRate: Double? = null,
    val startDate: LocalDate,
    val maturityDate: LocalDate? = null,
    /** Latest value the parent recorded — market-linked holdings drift. */
    val currentValueInr: Long? = null,
    val valueAsOf: LocalDate? = null,
    /** What the bank says it will be worth at maturity, for fixed instruments. */
    val maturityValueInr: Long? = null,
    val active: Boolean = true,
)

@Entity(
    tableName = "investment_contribution",
    indices = [Index("investmentId", "date")],
)
data class ContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val investmentId: Long,
    val amountInr: Long,
    val date: LocalDate,
    /** Whether the money came out of the savings account rather than from elsewhere. */
    val paidFromFund: Boolean = true,
)
