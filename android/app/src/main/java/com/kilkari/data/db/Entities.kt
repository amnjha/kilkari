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
    /** "f" | "m", or null where it was never given. Only the growth chart reads it. */
    val sex: String? = null,
    /** The child's picture, copied into app-private storage. Null until one is taken. */
    val photoUri: String? = null,
    /** When that picture was taken, so the weekly check-in knows how stale it is. */
    val photoUpdatedOn: LocalDate? = null,
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
    /**
     * Which savings account this came out of, or null when it was paid from elsewhere. It
     * replaced a bare "from the fund" flag, which could not say which account once there was
     * more than one.
     */
    val fundAccountId: Long? = null,
) {
    /** Kept for every screen that only cares whether the fund paid, not which account did. */
    val paidFromFund: Boolean get() = fundAccountId != null
}

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
    /**
     * How it shows up on Today: either a name from the built-in icon set or an emoji the
     * parent chose. Null falls back to whatever suits the reminder's key.
     */
    val icon: String? = null,
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
 * Where one of the child's identity documents stands — birth certificate, Aadhaar, passport,
 * PAN. One row per document once it has been touched; a document with no row is simply
 * pending. [status] is pending | obtained | skipped, and [settledOn] is the day it was either
 * obtained or set aside, since the next document's clock starts from there either way.
 */
@Entity(tableName = "paperwork", primaryKeys = ["babyId", "key"])
data class PaperworkEntity(
    val babyId: Long,
    val key: String,
    val status: String = "pending",
    val settledOn: LocalDate? = null,
    /** A date of the parent's own, overriding the suggested one. */
    val targetDate: LocalDate? = null,
    val note: String = "",
    /** The filed scan of it, where there is one. */
    val documentId: Long? = null,
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
    /** Which account the money moved through. */
    val accountId: Long = 0,
    /**
     * Pairs the two halves of a transfer — a withdrawal from one account and a deposit into
     * another — so the ledger can show it as one movement and undo it as one.
     */
    val transferGroup: String? = null,
    /** The statement date this line was ticked off against, or null while it is unchecked. */
    val reconciledOn: LocalDate? = null,
)

/**
 * An account the child's money sits in.
 *
 * One is enough for most families and the app starts with exactly one, but a grandparent's
 * envelope or a gift account kept separately is common enough that forcing everything into a
 * single balance meant either lying or keeping it outside the app.
 */
@Entity(tableName = "fund_account", indices = [Index("babyId")])
data class FundAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val name: String,
    /** Bank, cash tin, whatever the parent calls it — free text, shown under the name. */
    val note: String? = null,
    /** Kept in the list but out of the way; its balance still counts towards the total. */
    val archived: Boolean = false,
    val sortOrder: Int = 0,
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
    /** Which savings account funded it, or null when the money came from elsewhere. */
    val fundAccountId: Long? = null,
) {
    val paidFromFund: Boolean get() = fundAccountId != null
}


/**
 * A doctor the family sees. Records keep the doctor's *name* rather than a reference, so a
 * later rename does not rewrite history; this table exists so the name can be picked instead
 * of retyped, and so a clinic can be filled in alongside it.
 */
@Entity(tableName = "doctor", indices = [Index("babyId")])
data class DoctorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val babyId: Long,
    val name: String,
    val speciality: String? = null,
    val clinic: String? = null,
    val phone: String? = null,
)
