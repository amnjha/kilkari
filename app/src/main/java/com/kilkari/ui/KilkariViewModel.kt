package com.kilkari.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kilkari.data.db.AlbumEntity
import com.kilkari.data.db.AppointmentEntity
import com.kilkari.data.db.BabyEntity
import com.kilkari.data.db.ChecklistEntity
import com.kilkari.data.db.DocumentEntity
import com.kilkari.data.db.EventEntity
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.MedicationDoseEntity
import com.kilkari.data.db.MedicationEntity
import com.kilkari.data.db.ReminderEntity
import com.kilkari.data.db.TimelineEntity
import com.kilkari.data.prefs.AppSettings
import com.kilkari.data.repo.KilkariRepository
import com.kilkari.domain.BreastSide
import com.kilkari.domain.Currency
import com.kilkari.domain.DiaperKind
import com.kilkari.domain.ExpenseCategory
import com.kilkari.domain.FeedType
import com.kilkari.domain.LogKind
import com.kilkari.domain.VaccineGroupState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One ViewModel for the whole app. Kilkari is a single-baby, single-user surface with heavy
 * cross-screen coupling (marking vaccines writes money and timeline rows), so a shared
 * state holder is simpler than a per-screen one and keeps those effects in one place.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
class KilkariViewModel(private val repo: KilkariRepository) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** Recomputed on collection; the app is short-lived enough not to need a midnight ticker. */
    private val today = MutableStateFlow(LocalDate.now())

    val settings: StateFlow<AppSettings> = repo.settings.state(AppSettings())
    val baby: StateFlow<BabyEntity?> = repo.baby.state(null)

    val currency: StateFlow<Currency> = settings.map { it.currency }.state(Currency.INR)

    // ── Today / Log ─────────────────────────────────────────────────────────

    val todayLogs: StateFlow<List<LogEntryEntity>> =
        today.flatMapLatest { repo.logsForDay(it) }.state(emptyList())

    val latestPerKind: StateFlow<Map<LogKind, LogEntryEntity>> =
        repo.latestPerKind().state(emptyMap())

    val openSleep: StateFlow<LogEntryEntity?> = repo.openSleep().state(null)

    val diapersToday: StateFlow<Int> =
        today.flatMapLatest { repo.diaperCountToday(it) }.state(0)

    val checklist: StateFlow<List<ChecklistEntity>> =
        today.flatMapLatest { repo.checklist(it) }.state(emptyList())

    // ── Health ──────────────────────────────────────────────────────────────

    val vaccineGroups: StateFlow<List<VaccineGroupState>> =
        today.flatMapLatest { repo.vaccineGroups(it) }.state(emptyList())

    /** Index of the group opened from the Vaccines list. */
    private val _selectedGroup = MutableStateFlow(0)
    val selectedGroup: StateFlow<VaccineGroupState?> =
        combine(vaccineGroups, _selectedGroup) { groups, i -> groups.getOrNull(i) }.state(null)

    /** First group not fully given — the "Next up" card and Health summary. */
    val nextVaccine: StateFlow<VaccineGroupState?> =
        vaccineGroups.map { groups -> groups.firstOrNull { !it.allGiven } ?: groups.lastOrNull() }
            .state(null)

    val growth: StateFlow<List<GrowthEntity>> = repo.growth().state(emptyList())
    val teeth: StateFlow<Set<String>> = repo.teeth().state(emptySet())
    val medications: StateFlow<List<MedicationEntity>> = repo.medications().state(emptyList())

    val medicationDoses: StateFlow<List<MedicationDoseEntity>> =
        medications.flatMapLatest { meds ->
            repo.medicationDoses(meds.map { it.id }, LocalDate.now().minusDays(30))
        }.state(emptyList())

    val appointments: StateFlow<List<AppointmentEntity>> = repo.appointments().state(emptyList())

    // ── Money ───────────────────────────────────────────────────────────────

    val expenses: StateFlow<List<ExpenseEntity>> = repo.expenses().state(emptyList())

    private val _moneyFilter = MutableStateFlow<ExpenseCategory?>(null)
    val moneyFilter: StateFlow<ExpenseCategory?> = _moneyFilter.asStateFlow()

    fun setMoneyFilter(c: ExpenseCategory?) { _moneyFilter.value = c }

    // ── More ────────────────────────────────────────────────────────────────

    val timeline: StateFlow<List<TimelineEntity>> = repo.timeline().state(emptyList())
    val documents: StateFlow<List<DocumentEntity>> = repo.documents().state(emptyList())
    val albums: StateFlow<List<AlbumEntity>> = repo.albums().state(emptyList())
    val events: StateFlow<List<EventEntity>> = repo.events().state(emptyList())
    val reminders: StateFlow<List<ReminderEntity>> = repo.reminders().state(emptyList())

    private val _selectedDocument = MutableStateFlow<Long?>(null)
    val selectedDocument: StateFlow<DocumentEntity?> =
        combine(documents, _selectedDocument) { docs, id -> docs.firstOrNull { it.id == id } }
            .state(null)

    // ── Transient UI ────────────────────────────────────────────────────────

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var toastJob: kotlinx.coroutines.Job? = null

    fun toast(message: String) {
        _toast.value = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2_200)
            _toast.value = null
        }
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    fun refreshToday() {
        today.value = LocalDate.now()
        viewModelScope.launch { repo.ensureChecklist(LocalDate.now()) }
    }

    fun selectVaccineGroup(index: Int) { _selectedGroup.value = index }
    fun selectDocument(id: Long) { _selectedDocument.value = id }

    fun createBaby(
        name: String, dob: LocalDate, birthTime: LocalDateTime?,
        weightKg: Double?, lengthCm: Double?, headCm: Double?, place: String?,
        scheduleId: String, currency: Currency,
    ) = viewModelScope.launch {
        repo.createBaby(name, dob, birthTime, weightKg, lengthCm, headCm, place)
        repo.setSchedule(scheduleId)
        repo.setCurrency(currency)
        repo.ensureChecklist(LocalDate.now())
        repo.setOnboarded(true)
    }

    fun updateBaby(row: BabyEntity) = viewModelScope.launch { repo.updateBaby(row) }

    fun logFeed(type: FeedType, side: BreastSide?, amount: Int) = viewModelScope.launch {
        repo.logFeed(type, side, amount)
        toast("Feed saved")
    }

    fun logSleepStart(place: String?) = viewModelScope.launch {
        repo.logSleep(LocalDateTime.now(), null, place)
        toast("Sleep started")
    }

    fun logSleepEnd() = viewModelScope.launch {
        repo.wakeUp()
        toast("Woke up")
    }

    fun logDiaper(kind: DiaperKind) = viewModelScope.launch {
        repo.logDiaper(kind)
        toast("Diaper logged")
    }

    fun logMedicine(med: MedicationEntity) = viewModelScope.launch {
        repo.logMedicine(med.id, med.name, med.dose)
        toast("${med.name} logged for today")
    }

    fun setDoseTaken(medId: Long, date: LocalDate, taken: Boolean) =
        viewModelScope.launch { repo.setDoseTaken(medId, date, taken) }

    fun addGrowth(date: LocalDate, weightKg: Double?, lengthCm: Double?, headCm: Double?) =
        viewModelScope.launch {
            repo.addGrowth(date, weightKg, lengthCm, headCm)
            toast("Measurement saved")
        }

    fun toggleTooth(code: String, erupted: Boolean) =
        viewModelScope.launch { repo.toggleTooth(code, erupted) }

    fun toggleDose(groupLabel: String, vaccineName: String, given: Boolean) =
        viewModelScope.launch { repo.toggleDose(groupLabel, vaccineName, given) }

    fun markGroupGiven(
        group: VaccineGroupState, on: LocalDate, clinic: String?, doctor: String?,
        costInr: Long?, addExpense: Boolean,
    ) = viewModelScope.launch {
        repo.markGroupGiven(group, on, clinic, doctor, costInr, addExpense)
        toast(
            if (addExpense && costInr != null && costInr > 0)
                "Saved · ${com.kilkari.domain.Fmt.money(costInr, currency.value)} added to Medical"
            else "Saved to timeline"
        )
    }

    fun addMedication(name: String, dose: String, scheduleText: String, prescriber: String?, reminderMinute: Int?) =
        viewModelScope.launch {
            repo.addMedication(name, dose, scheduleText, prescriber, LocalDate.now(), reminderMinute)
            toast("$name added")
        }

    fun setMedicationActive(med: MedicationEntity, active: Boolean) =
        viewModelScope.launch { repo.setMedicationActive(med, active) }

    fun addAppointment(title: String, at: LocalDateTime, doctor: String?, place: String?, reminderDaysBefore: Int?) =
        viewModelScope.launch {
            repo.addAppointment(title, at, doctor, place, reminderDaysBefore)
            toast("Appointment added")
        }

    fun deleteAppointment(row: AppointmentEntity) = viewModelScope.launch { repo.deleteAppointment(row) }

    fun addExpense(title: String, vendor: String?, category: ExpenseCategory, amountDisplay: Double) =
        viewModelScope.launch {
            val inr = com.kilkari.domain.Fmt.toInr(amountDisplay, currency.value)
            repo.addExpense(title, vendor, category, inr, LocalDate.now())
            toast("Expense saved")
        }

    fun deleteExpense(row: ExpenseEntity) = viewModelScope.launch { repo.deleteExpense(row) }

    fun addMilestone(title: String, subtitle: String, date: LocalDate, albumUrl: String?) =
        viewModelScope.launch {
            repo.addTimelineEntry(date, title, subtitle, "auto_awesome", albumUrl)
            toast("Added to timeline")
        }

    fun deleteTimelineEntry(row: TimelineEntity) = viewModelScope.launch { repo.deleteTimelineEntry(row) }

    fun addDocument(title: String, tags: String, pageUris: List<String>) = viewModelScope.launch {
        repo.addDocument(title, LocalDate.now(), tags, pageUris)
        toast("Scan filed under today")
    }

    fun deleteDocument(row: DocumentEntity) = viewModelScope.launch { repo.deleteDocument(row) }

    fun addAlbum(title: String, subtitle: String, url: String) = viewModelScope.launch {
        repo.addAlbum(title, subtitle, url)
        toast("Album linked")
    }

    fun deleteAlbum(row: AlbumEntity) = viewModelScope.launch { repo.deleteAlbum(row) }

    fun addEvent(title: String, subtitle: String, date: LocalDate, icon: String, annual: Boolean) =
        viewModelScope.launch {
            repo.addEvent(title, subtitle, date, icon, annual)
            toast("Event added")
        }

    fun deleteEvent(row: EventEntity) = viewModelScope.launch { repo.deleteEvent(row) }

    fun setChecklistDone(row: ChecklistEntity, done: Boolean) =
        viewModelScope.launch { repo.setChecklistDone(row, done) }

    fun setReminderEnabled(row: ReminderEntity, enabled: Boolean) =
        viewModelScope.launch { repo.setReminderEnabled(row, enabled) }

    fun setCurrency(c: Currency) = viewModelScope.launch { repo.setCurrency(c) }

    fun setSchedule(id: String) = viewModelScope.launch {
        repo.setSchedule(id)
        _selectedGroup.value = 0
        toast("Schedule set to ${com.kilkari.data.seed.VaccineSchedules.byId(id).name}")
    }

    fun setMetric(metric: Boolean) = viewModelScope.launch { repo.setMetric(metric) }
    fun setTodayVariant(v: String) = viewModelScope.launch { repo.setTodayVariant(v) }
    fun setAutoBackup(v: Boolean) = viewModelScope.launch { repo.setAutoBackup(v) }

    class Factory(private val repo: KilkariRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KilkariViewModel(repo) as T
    }
}
