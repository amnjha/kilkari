package com.kilkari.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class Currency(val code: String, val symbol: String, val perInr: Double) {
    INR("INR", "₹", 1.0),
    USD("USD", "$", 1.0 / 84),
    EUR("EUR", "€", 1.0 / 91),
    GBP("GBP", "£", 1.0 / 107);

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: INR
    }
}

enum class ExpenseCategory(val key: String, val label: String) {
    MEDICAL("med", "Medical"),
    GENERAL("gen", "General");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: GENERAL
    }
}

enum class FeedType(val key: String, val label: String) {
    BREAST("breast", "Breast"),
    BOTTLE("bottle", "Bottle"),
    SOLID("solid", "Solids");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: BREAST
    }
}

enum class BreastSide(val key: String, val label: String) { LEFT("L", "Left"), RIGHT("R", "Right") }

enum class DiaperKind(val key: String, val label: String) {
    WET("wet", "Wet"), DIRTY("dirty", "Dirty"), BOTH("both", "Both");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: WET
    }
}

/** The kinds of entry that show up on the Log tab and in "Today's entries". */
enum class LogKind(val key: String) {
    FEED("feed"), SLEEP("sleep"), DIAPER("diaper"), MEDICINE("medicine"),
    GROWTH("growth"), TOOTH("tooth");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: FEED
    }
}

/** Status of a vaccine group relative to today; drives colour and copy throughout Health. */
enum class VaccineStatus { GIVEN, OVERDUE, DUE_SOON, UPCOMING }

data class VaccineItemState(
    val name: String,
    val desc: String,
    val given: Boolean,
)

data class VaccineGroupState(
    val index: Int,
    val label: String,
    val dueDate: LocalDate,
    val inDays: Int,
    val items: List<VaccineItemState>,
    val costMinor: Long?,
) {
    val count get() = items.size
    val doneCount get() = items.count { it.given }
    val allGiven get() = items.isNotEmpty() && doneCount == count
    val names get() = items.joinToString(", ") { it.name }

    val status: VaccineStatus
        get() = when {
            allGiven -> VaccineStatus.GIVEN
            inDays < 0 -> VaccineStatus.OVERDUE
            inDays <= 21 -> VaccineStatus.DUE_SOON
            else -> VaccineStatus.UPCOMING
        }

    val statusText: String
        get() = when (status) {
            VaccineStatus.GIVEN -> "Given"
            VaccineStatus.OVERDUE -> "Overdue ${-inDays} d"
            VaccineStatus.DUE_SOON -> "Due in $inDays d"
            VaccineStatus.UPCOMING -> "Upcoming"
        }
}

data class TimelineEntry(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val icon: String,
    val albumUrl: String?,
)

data class MedicationDose(val date: LocalDate, val taken: Boolean)

data class TodayTask(
    val key: String,
    val title: String,
    val subtitle: String,
    val time: String,
    val done: Boolean,
)

data class GrowthPoint(val label: String, val date: LocalDate, val weightKg: Double?)

/** A Log tile on the Log tab / the three mini tiles on Today. */
data class LogTile(
    val kind: LogKind,
    val title: String,
    val ago: String,
    val detail: String,
)

data class Baby(
    val id: Long,
    val name: String,
    val dob: LocalDate,
    val birthTime: LocalDateTime?,
    val birthWeightKg: Double?,
    val birthLengthCm: Double?,
    val birthPlace: String?,
)
