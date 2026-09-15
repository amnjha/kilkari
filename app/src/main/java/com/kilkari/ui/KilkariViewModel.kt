package com.kilkari.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kilkari.data.db.AlbumEntity
import com.kilkari.data.db.AppointmentEntity
import com.kilkari.data.db.BabyEntity
import com.kilkari.data.db.ChecklistEntity
import com.kilkari.data.db.ContributionEntity
import com.kilkari.data.db.DoctorEntity
import com.kilkari.data.db.DocumentEntity
import com.kilkari.data.db.EventEntity
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.InvestmentEntity
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
import com.kilkari.data.db.TaskStateEntity
import com.kilkari.domain.DueTask
import com.kilkari.domain.RepeatRule
import com.kilkari.domain.ExpenseCategory
import com.kilkari.domain.FeedType
import com.kilkari.domain.FundLedgerOrigin
import com.kilkari.domain.FundLedgerRow
import com.kilkari.domain.FundTxnKind
import com.kilkari.domain.InvestmentKind
import com.kilkari.domain.InvestmentSummary
import com.kilkari.domain.LogKind
import com.kilkari.domain.VaccineGroupState
import com.kilkari.domain.VaccineItemState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

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

    /** False until the database and settings have both answered, so launch shows the splash
     *  rather than flashing onboarding at someone who is already set up. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            repo.baby.first()
            repo.settings.first()
            _loaded.value = true
        }
    }

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

    // ── Fund & investments ──────────────────────────────────────────────────

    val fundTransactions: StateFlow<List<FundTxnEntity>> = repo.fundTransactions().state(emptyList())
    val investments: StateFlow<List<InvestmentEntity>> = repo.investments().state(emptyList())
    val contributions: StateFlow<List<ContributionEntity>> = repo.contributions().state(emptyList())

    /**
     * Deposits, less everything drawn from the account: manual withdrawals, expenses marked as
     * paid from the fund, and investment contributions funded from it. Nothing is mirrored, so
     * deleting an expense or a holding restores the balance on its own.
     */
    val fundBalance: StateFlow<Long> =
        combine(fundTransactions, expenses, contributions) { txns, expenses, contributions ->
            val deposits = txns.filter { it.kind == FundTxnKind.DEPOSIT.key }.sumOf { it.amountInr }
            val withdrawals = txns.filter { it.kind == FundTxnKind.WITHDRAWAL.key }.sumOf { it.amountInr }
            val spent = expenses.filter { it.paidFromFund }.sumOf { it.amountInr }
            val invested = contributions.filter { it.paidFromFund }.sumOf { it.amountInr }
            deposits - withdrawals - spent - invested
        }.state(0)

    /** Every movement through the account on one timeline, newest first. */
    val fundLedger: StateFlow<List<FundLedgerRow>> =
        combine(fundTransactions, expenses, contributions, investments) { txns, expenses, contributions, investments ->
            val byId = investments.associateBy { it.id }
            buildList {
                txns.forEach { t ->
                    val deposit = t.kind == FundTxnKind.DEPOSIT.key
                    add(
                        FundLedgerRow(
                            id = "t${t.id}",
                            date = t.date,
                            title = if (deposit) "Deposit" else "Withdrawal",
                            subtitle = t.note.orEmpty(),
                            amountInr = t.amountInr,
                            incoming = deposit,
                            icon = if (deposit) "payments" else "shopping_bag",
                            origin = if (deposit) FundLedgerOrigin.DEPOSIT else FundLedgerOrigin.WITHDRAWAL,
                        )
                    )
                }
                expenses.filter { it.paidFromFund }.forEach { e ->
                    add(
                        FundLedgerRow(
                            id = "e${e.id}",
                            date = e.date,
                            title = e.title,
                            subtitle = listOfNotNull(
                                ExpenseCategory.of(e.category).label,
                                e.vendor,
                            ).joinToString(" · "),
                            amountInr = e.amountInr,
                            incoming = false,
                            icon = e.icon,
                            origin = FundLedgerOrigin.EXPENSE,
                        )
                    )
                }
                contributions.filter { it.paidFromFund }.forEach { c ->
                    val investment = byId[c.investmentId]
                    add(
                        FundLedgerRow(
                            id = "c${c.id}",
                            date = c.date,
                            title = investment?.name ?: "Investment",
                            subtitle = investment?.let { InvestmentKind.of(it.kind).label }.orEmpty(),
                            amountInr = c.amountInr,
                            incoming = false,
                            icon = "savings",
                            origin = FundLedgerOrigin.INVESTMENT,
                        )
                    )
                }
            }.sortedWith(compareByDescending<FundLedgerRow> { it.date }.thenByDescending { it.id })
        }.state(emptyList())

    val investmentSummaries: StateFlow<List<InvestmentSummary>> =
        combine(investments, contributions) { investments, contributions ->
            val thisMonth = YearMonth.now()
            investments.map { row ->
                val own = contributions.filter { it.investmentId == row.id }
                InvestmentSummary(
                    id = row.id,
                    name = row.name,
                    kind = InvestmentKind.of(row.kind),
                    institution = row.institution,
                    investedInr = own.sumOf { it.amountInr },
                    currentValueInr = row.currentValueInr,
                    maturityValueInr = row.maturityValueInr,
                    interestRate = row.interestRate,
                    startDate = row.startDate,
                    maturityDate = row.maturityDate,
                    monthlyInr = row.monthlyInr,
                    active = row.active,
                    contributedThisMonth = own.any { YearMonth.from(it.date) == thisMonth },
                )
            }
        }.state(emptyList())

    // ── More ────────────────────────────────────────────────────────────────

    val timeline: StateFlow<List<TimelineEntity>> = repo.timeline().state(emptyList())
    val documents: StateFlow<List<DocumentEntity>> = repo.documents().state(emptyList())
    val albums: StateFlow<List<AlbumEntity>> = repo.albums().state(emptyList())
    val events: StateFlow<List<EventEntity>> = repo.events().state(emptyList())
    val reminders: StateFlow<List<ReminderEntity>> = repo.reminders().state(emptyList())
    val doctors: StateFlow<List<DoctorEntity>> = repo.doctors().state(emptyList())

    private val _selectedDocument = MutableStateFlow<Long?>(null)
    val selectedDocument: StateFlow<DocumentEntity?> =
        combine(documents, _selectedDocument) { docs, id -> docs.firstOrNull { it.id == id } }
            .state(null)

    // ── Due today ───────────────────────────────────────────────────────────

    private val taskStates: StateFlow<List<TaskStateEntity>> = repo.taskStates().state(emptyList())

    /**
     * Everything outstanding today, from every source. All three Today layouts render this list,
     * so they cannot drift apart the way the agenda and checklist views had.
     */
    val dueTasks: StateFlow<List<DueTask>> = combine(
        combine(medications, medicationDoses, appointments, vaccineGroups) { m, d, a, v ->
            listOf(m, d, a, v)
        },
        combine(reminders, checklist, taskStates, openSleep) { r, c, t, s ->
            listOf(r, c, t, s)
        },
        settings,
        today,
    ) { first, second, settings, today ->
        @Suppress("UNCHECKED_CAST")
        DueTaskBuilder.build(
            today = today,
            now = LocalDateTime.now(),
            medications = first[0] as List<MedicationEntity>,
            medicationDoses = first[1] as List<MedicationDoseEntity>,
            appointments = first[2] as List<AppointmentEntity>,
            vaccineGroups = first[3] as List<VaccineGroupState>,
            reminders = second[0] as List<ReminderEntity>,
            checklist = second[1] as List<ChecklistEntity>,
            taskStates = second[2] as List<TaskStateEntity>,
            openSleep = second[3] as LogEntryEntity?,
            fundDepositDue = if (settings.fundMonthlyInr > 0) {
                today.withDayOfMonth(settings.fundDepositDay.coerceIn(1, 28))
            } else {
                null
            },
        )
    }.state(emptyList())

    /**
     * Set when a task that needs data sends the user to another screen, so that screen can open
     * its entry sheet on arrival. Consumed once read.
     */
    private val _pendingEntry = MutableStateFlow<String?>(null)
    val pendingEntry: StateFlow<String?> = _pendingEntry.asStateFlow()

    fun requestEntry(key: String) { _pendingEntry.value = key }

    fun consumeEntry() { _pendingEntry.value = null }

    /**
     * Records that whatever a reminder was asking for has now been supplied, against the
     * occurrence it is currently sitting on.
     */
    private fun satisfyReminder(key: String) = viewModelScope.launch {
        val reminder = reminders.value.firstOrNull { it.key == key } ?: return@launch
        val occurrence = DueTaskBuilder.occurrenceOf(reminder, LocalDate.now()) ?: return@launch
        repo.setTaskDone(key, occurrence, true)
    }

    /** Ticking a task writes wherever that task actually lives. */
    fun setTaskDone(task: DueTask, done: Boolean) = viewModelScope.launch {
        val id = task.id
        when {
            id.startsWith("med:") -> id.removePrefix("med:").toLongOrNull()?.let {
                repo.setDoseTaken(it, LocalDate.now(), done)
            }
            id.startsWith("check:") -> {
                val key = id.removePrefix("check:")
                checklist.value.firstOrNull { it.key == key }
                    ?.let { repo.setChecklistDone(it, done) }
            }
            id.startsWith("rem:") -> repo.setTaskDone(id.removePrefix("rem:"), task.occurrence, done)
        }
    }

    fun dismissTask(task: DueTask) = viewModelScope.launch {
        if (task.id.startsWith("rem:")) {
            repo.dismissTask(task.id.removePrefix("rem:"), task.occurrence)
        }
    }

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

    fun onboard(
        name: String,
        dob: LocalDate,
        birthTime: LocalDateTime?,
        weightKg: Double?,
        lengthCm: Double?,
        headCm: Double?,
        place: String?,
        scheduleId: String,
        currency: Currency,
        givenGroups: Map<String, LocalDate>,
        milestones: Map<String, LocalDate>,
    ) = viewModelScope.launch {
        repo.onboard(
            name, dob, birthTime, weightKg, lengthCm, headCm, place,
            scheduleId, currency, givenGroups, milestones,
        )
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
            satisfyReminder("weigh")
            toast("Measurement saved")
        }

    fun toggleTooth(code: String, erupted: Boolean) =
        viewModelScope.launch { repo.toggleTooth(code, erupted) }

    fun toggleDose(groupLabel: String, vaccineName: String, given: Boolean) =
        viewModelScope.launch { repo.toggleDose(groupLabel, vaccineName, given) }

    fun markDosesGiven(
        group: VaccineGroupState,
        doses: List<VaccineItemState>,
        on: LocalDate,
        clinic: String?,
        doctor: String?,
        brands: Map<String, String?>,
        costInr: Long?,
        addExpense: Boolean,
    ) = viewModelScope.launch {
        repo.markDosesGiven(group, doses, on, clinic, doctor, brands, costInr, addExpense)
        toast(
            if (addExpense && costInr != null && costInr > 0)
                "Saved · ${com.kilkari.domain.Fmt.money(costInr, currency.value)} added to Medical"
            else if (doses.size == 1) "${doses.first().name} recorded"
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

    fun addExpense(
        title: String,
        vendor: String?,
        category: ExpenseCategory,
        amountDisplay: Double,
        paidFromFund: Boolean,
    ) = viewModelScope.launch {
        repo.addExpense(title, vendor, category, toInr(amountDisplay), LocalDate.now(), paidFromFund)
        toast("Expense saved")
    }

    fun deleteExpense(row: ExpenseEntity) = viewModelScope.launch { repo.deleteExpense(row) }

    fun addFundDeposit(amountDisplay: Double, date: LocalDate, note: String?) = viewModelScope.launch {
        repo.addFundTransaction(FundTxnKind.DEPOSIT, toInr(amountDisplay), date, note)
        satisfyReminder("fund")
        toast("Deposit recorded")
    }

    fun addFundWithdrawal(amountDisplay: Double, date: LocalDate, note: String?) = viewModelScope.launch {
        repo.addFundTransaction(FundTxnKind.WITHDRAWAL, toInr(amountDisplay), date, note)
        toast("Withdrawal recorded")
    }

    fun deleteFundTransaction(row: FundTxnEntity) = viewModelScope.launch {
        repo.deleteFundTransaction(row)
    }

    fun setFundPlan(monthlyDisplay: Double, day: Int, name: String) = viewModelScope.launch {
        repo.setFundPlan(toInr(monthlyDisplay), day, name)
        toast("Monthly plan saved")
    }

    fun addInvestment(
        name: String,
        kind: InvestmentKind,
        institution: String?,
        openingDisplay: Double?,
        monthlyDisplay: Double?,
        interestRate: Double?,
        startDate: LocalDate,
        maturityDate: LocalDate?,
        maturityValueDisplay: Double?,
        paidFromFund: Boolean,
    ) = viewModelScope.launch {
        repo.addInvestment(
            name = name,
            kind = kind,
            institution = institution,
            openingAmountInr = openingDisplay?.let(::toInr),
            monthlyInr = monthlyDisplay?.let(::toInr),
            interestRate = interestRate,
            startDate = startDate,
            maturityDate = maturityDate,
            maturityValueInr = maturityValueDisplay?.let(::toInr),
            paidFromFund = paidFromFund,
        )
        toast("$name added")
    }

    fun addContribution(investmentId: Long, amountDisplay: Double, date: LocalDate, paidFromFund: Boolean) =
        viewModelScope.launch {
            repo.addContribution(investmentId, toInr(amountDisplay), date, paidFromFund)
            toast("Contribution recorded")
        }

    fun updateInvestmentValue(investmentId: Long, valueDisplay: Double) = viewModelScope.launch {
        repo.updateInvestmentValue(investmentId, toInr(valueDisplay), LocalDate.now())
        toast("Value updated")
    }

    fun setInvestmentActive(investmentId: Long, active: Boolean) = viewModelScope.launch {
        repo.setInvestmentActive(investmentId, active)
    }

    fun deleteInvestment(investmentId: Long) = viewModelScope.launch {
        repo.deleteInvestment(investmentId)
        toast("Removed")
    }

    /** Display amounts are entered in the chosen currency; storage is always whole rupees. */
    private fun toInr(amount: Double): Long = com.kilkari.domain.Fmt.toInr(amount, currency.value)

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
        satisfyReminder("album")
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

    fun saveDoctor(id: Long?, name: String, speciality: String?, clinic: String?, phone: String?) =
        viewModelScope.launch {
            repo.saveDoctor(id, name.trim(), speciality, clinic, phone)
            toast(if (id == null) "$name added" else "Saved")
        }

    fun deleteDoctor(row: DoctorEntity) = viewModelScope.launch {
        repo.deleteDoctor(row)
        toast("Removed")
    }

    fun setReminderEnabled(row: ReminderEntity, enabled: Boolean) =
        viewModelScope.launch { repo.setReminderEnabled(row, enabled) }

    fun saveCustomReminder(
        key: String?,
        title: String,
        subtitle: String,
        minuteOfDay: Int?,
        repeat: RepeatRule,
        weekday: Int?,
        dayOfMonth: Int?,
        startDate: LocalDate?,
    ) = viewModelScope.launch {
        repo.saveCustomReminder(key, title, subtitle, minuteOfDay, repeat, weekday, dayOfMonth, startDate)
        toast(if (key == null) "Reminder added" else "Reminder updated")
    }

    fun deleteReminder(key: String) = viewModelScope.launch {
        repo.deleteReminder(key)
        toast("Reminder removed")
    }

    fun updateBabyDetails(
        name: String,
        dob: LocalDate,
        place: String?,
        weightKg: Double?,
        lengthCm: Double?,
        headCm: Double?,
    ) = viewModelScope.launch {
        val current = baby.value ?: return@launch
        repo.updateBaby(
            current.copy(
                name = name,
                dob = dob,
                birthPlace = place,
                birthWeightKg = weightKg,
                birthLengthCm = lengthCm,
                birthHeadCm = headCm,
            )
        )
        toast("Details saved")
    }

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
