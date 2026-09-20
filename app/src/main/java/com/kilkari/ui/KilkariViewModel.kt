package com.kilkari.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kilkari.data.db.AlbumEntity
import com.kilkari.data.db.AppointmentEntity
import com.kilkari.data.db.BabyEntity
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
import com.kilkari.data.db.ToothEntity
import com.kilkari.data.prefs.AppSettings
import com.kilkari.data.repo.KilkariRepository
import com.kilkari.domain.BreastSide
import com.kilkari.domain.Currency
import com.kilkari.domain.DiaperKind
import com.kilkari.data.db.TaskStateEntity
import com.kilkari.domain.DueTask
import com.kilkari.domain.ReminderDraft
import com.kilkari.domain.RepeatRule
import com.kilkari.domain.Sex
import com.kilkari.domain.ExpenseCategory
import com.kilkari.domain.FeedType
import com.kilkari.domain.Fmt
import com.kilkari.domain.Insights
import com.kilkari.domain.InsightReport
import com.kilkari.domain.DaySummary
import com.kilkari.domain.FundLedgerOrigin
import com.kilkari.domain.FundLedgerRow
import com.kilkari.domain.FundTxnKind
import com.kilkari.domain.InvestmentKind
import com.kilkari.domain.InvestmentSummary
import com.kilkari.domain.LogKind
import com.kilkari.domain.PaperworkStatus
import com.kilkari.domain.PaperworkStep
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

    /** Kilograms and centimetres, or pounds and inches. Storage stays metric either way. */
    val metricUnits: StateFlow<Boolean> = settings.map { it.metricUnits }.state(true)

    // ── Today / Log ─────────────────────────────────────────────────────────

    val todayLogs: StateFlow<List<LogEntryEntity>> =
        today.flatMapLatest { repo.logsForDay(it) }.state(emptyList())

    val latestPerKind: StateFlow<Map<LogKind, LogEntryEntity>> =
        repo.latestPerKind().state(emptyMap())

    val openSleep: StateFlow<LogEntryEntity?> = repo.openSleep().state(null)

    val diapersToday: StateFlow<Int> =
        today.flatMapLatest { repo.diaperCountToday(it) }.state(0)

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

    // ── Daily log ───────────────────────────────────────────────────────────

    private val _logDay = MutableStateFlow(LocalDate.now())

    /** The day the daily log is showing. */
    val logDay: StateFlow<LocalDate> = _logDay.asStateFlow()

    fun showLogDay(day: LocalDate) {
        _logDay.value = minOf(day, today.value)
    }

    /**
     * The chosen day's entries, plus the evening before it, so a night that started then can
     * be counted towards this day's sleep. The list itself shows only this day's.
     */
    private val logDayWindow: StateFlow<List<LogEntryEntity>> =
        _logDay.flatMapLatest { day -> repo.logsBetween(day.minusDays(1), day.plusDays(1)) }
            .state(emptyList())

    val logDayEntries: StateFlow<List<LogEntryEntity>> =
        combine(_logDay, logDayWindow) { day, logs ->
            logs.filter { it.startAt.toLocalDate() == day }.sortedBy { it.startAt }
        }.state(emptyList())

    val logDaySummary: StateFlow<DaySummary?> =
        combine(_logDay, logDayWindow) { day, logs -> Insights.daySummary(logs, day) }.state(null)


    private val toothRows: StateFlow<List<ToothEntity>> = repo.toothRows().state(emptyList())
    val teeth: StateFlow<Set<String>> =
        toothRows.map { rows -> rows.map { it.code }.toSet() }.state(emptySet())

    /** When each recorded tooth came through, so the Teeth chart can show and edit the date. */
    val toothDates: StateFlow<Map<String, LocalDate>> =
        toothRows.map { rows -> rows.associate { it.code to it.eruptedOn } }.state(emptyMap())

    val medications: StateFlow<List<MedicationEntity>> = repo.medications().state(emptyList())

    val medicationDoses: StateFlow<List<MedicationDoseEntity>> =
        medications.flatMapLatest { meds ->
            repo.medicationDoses(meds.map { it.id }, LocalDate.now().minusDays(30))
        }.state(emptyList())

    // ── Insights ────────────────────────────────────────────────────────────

    private val _insightDays = MutableStateFlow(7)

    /** How far back the insights look: 7 or 30 days. */
    val insightDays: StateFlow<Int> = _insightDays.asStateFlow()

    fun setInsightDays(days: Int) { _insightDays.value = days }

    /**
     * Two of the longest windows, so the previous period can be compared against. Follows the
     * day the app thinks it is, so a screen left open past midnight moves its window along.
     */
    private val insightLogs: StateFlow<List<LogEntryEntity>> =
        today.flatMapLatest { day -> repo.logsBetween(day.minusDays(60), day.plusDays(1)) }
            .state(emptyList())

    private val insightWindow = combine(today, _insightDays, insightLogs, baby) { day, days, logs, child ->
        InsightWindow(day, days, logs, child?.dob)
    }

    val insights: StateFlow<InsightReport?> =
        combine(insightWindow, medications, medicationDoses, growth) { w, meds, doses, weights ->
            Insights.report(w.today, w.days, w.logs, meds, doses, weights, born = w.born)
        }.state(null)

    val appointments: StateFlow<List<AppointmentEntity>> = repo.appointments().state(emptyList())

    // ── Money ───────────────────────────────────────────────────────────────

    val expenses: StateFlow<List<ExpenseEntity>> = repo.expenses().state(emptyList())

    private val _moneyFilter = MutableStateFlow<ExpenseCategory?>(null)
    val moneyFilter: StateFlow<ExpenseCategory?> = _moneyFilter.asStateFlow()

    fun setMoneyFilter(c: ExpenseCategory?) { _moneyFilter.value = c }

    /**
     * Whether the Spending headline totals every expense ever recorded rather than this month.
     * Per-month figures live on the list's own month headings, so this is the one extra span
     * the headline needs to offer.
     */
    private val _moneyAllTime = MutableStateFlow(false)
    val moneyAllTime: StateFlow<Boolean> = _moneyAllTime.asStateFlow()

    fun setMoneyAllTime(on: Boolean) { _moneyAllTime.value = on }

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
                            sourceId = t.id,
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
                            sourceId = e.id,
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
                            sourceId = c.id,
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

    /**
     * The identity documents in the order they are applied for, with the one whose turn it
     * is carrying a due date. Re-keyed on the date so a step's lateness moves overnight.
     */
    val paperwork: StateFlow<List<PaperworkStep>> =
        today.flatMapLatest { repo.paperwork(it) }.state(emptyList())

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
        combine(reminders, taskStates, openSleep, paperwork) { r, t, s, p ->
            listOf(r, t, s, p)
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
            taskStates = second[1] as List<TaskStateEntity>,
            openSleep = second[2] as LogEntryEntity?,
            fundDepositDue = if (settings.fundMonthlyInr > 0) {
                today.withDayOfMonth(settings.fundDepositDay.coerceIn(1, 28))
            } else {
                null
            },
            paperwork = second[3] as List<PaperworkStep>,
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
    private fun satisfyReminder(key: String, on: LocalDate = LocalDate.now()) = viewModelScope.launch {
        val reminder = reminders.value.firstOrNull { it.key == key } ?: return@launch
        val occurrence = DueTaskBuilder.occurrenceOf(reminder, LocalDate.now()) ?: return@launch
        // A back-dated entry belongs to an earlier occurrence, so it must not tick off this one.
        if (on.isBefore(occurrence)) return@launch
        repo.setTaskDone(key, occurrence, true)
    }

    /** Ticking a task writes wherever that task actually lives. */
    fun setTaskDone(task: DueTask, done: Boolean) = viewModelScope.launch {
        val id = task.id
        when {
            id.startsWith("med:") -> id.removePrefix("med:").toLongOrNull()?.let {
                repo.setDoseTaken(it, LocalDate.now(), done)
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
    }

    fun selectVaccineGroup(index: Int) { _selectedGroup.value = index }
    fun selectDocument(id: Long) { _selectedDocument.value = id }

    fun onboard(
        name: String,
        dob: LocalDate,
        sex: Sex?,
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
            name, dob, sex, birthTime, weightKg, lengthCm, headCm, place,
            scheduleId, currency, givenGroups, milestones,
        )
    }


    fun logFeed(type: FeedType, side: BreastSide?, amount: Int, at: LocalDateTime = LocalDateTime.now()) =
        viewModelScope.launch {
            repo.logFeed(type, side, amount, at)
            toast("Feed saved" + forDay(at.toLocalDate()))
        }

    /** [to] is null while the baby is still asleep, set when the whole nap is entered at once. */
    fun logSleepStart(place: String?, from: LocalDateTime = LocalDateTime.now(), to: LocalDateTime? = null) =
        viewModelScope.launch {
            repo.logSleep(from, to, place)
            toast(if (to == null) "Sleep started" else ("Nap saved" + forDay(from.toLocalDate())))
        }

    fun logSleepEnd(at: LocalDateTime = LocalDateTime.now()) = viewModelScope.launch {
        repo.wakeUp(at)
        toast("Woke up")
    }

    fun logDiaper(kind: DiaperKind, at: LocalDateTime = LocalDateTime.now()) = viewModelScope.launch {
        repo.logDiaper(kind, at)
        toast("Diaper logged" + forDay(at.toLocalDate()))
    }

    fun logMedicine(med: MedicationEntity, at: LocalDateTime = LocalDateTime.now()) = viewModelScope.launch {
        repo.logMedicine(med.id, med.name, med.dose, at)
        val day = at.toLocalDate()
        toast("${med.name} logged for " + (if (day == LocalDate.now()) "today" else Fmt.date(day)))
    }

    fun setDoseTaken(medId: Long, date: LocalDate, taken: Boolean) =
        viewModelScope.launch { repo.setDoseTaken(medId, date, taken) }

    /** A correction to something already logged — the row carries its own id and babyId. */
    fun updateLog(entry: LogEntryEntity) = viewModelScope.launch {
        repo.updateLog(entry)
        toast("Entry updated" + forDay(entry.startAt.toLocalDate()))
    }

    fun deleteLog(entry: LogEntryEntity) = viewModelScope.launch {
        repo.deleteLog(entry)
        toast("Entry removed")
    }

    fun addGrowth(date: LocalDate, weightKg: Double?, lengthCm: Double?, headCm: Double?) =
        viewModelScope.launch {
            repo.addGrowth(date, weightKg, lengthCm, headCm)
            satisfyReminder("weigh", date)
            toast("Measurement saved" + forDay(date))
        }

    /** [marker] is the journal row that announced the measurement; the two move together. */
    fun updateGrowth(
        marker: LogEntryEntity?,
        measurement: GrowthEntity?,
        date: LocalDate,
        weightKg: Double?,
        lengthCm: Double?,
        headCm: Double?,
    ) = viewModelScope.launch {
        repo.updateGrowth(marker, measurement, date, weightKg, lengthCm, headCm)
        toast("Measurement updated" + forDay(date))
    }

    fun deleteGrowth(marker: LogEntryEntity?, measurement: GrowthEntity?) = viewModelScope.launch {
        repo.deleteGrowth(marker, measurement)
        toast("Measurement removed")
    }

    fun toggleTooth(code: String, erupted: Boolean, on: LocalDate = LocalDate.now()) =
        viewModelScope.launch { repo.toggleTooth(code, erupted, on) }

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
                "Saved · ${Fmt.money(costInr, currency.value)} added to Medical"
            else if (doses.size == 1) "${doses.first().name} recorded" + forDay(on)
            else "Saved to timeline" + forDay(on)
        )
    }

    fun addMedication(
        name: String,
        dose: String,
        scheduleText: String,
        prescriber: String?,
        start: LocalDate,
        reminderMinute: Int?,
    ) = viewModelScope.launch {
        repo.addMedication(name, dose, scheduleText, prescriber, start, reminderMinute)
        repo.rescheduleNotifications()
        toast("$name added")
    }

    fun updateMedication(
        med: MedicationEntity,
        name: String,
        dose: String,
        scheduleText: String,
        prescriber: String?,
        start: LocalDate,
        reminderMinute: Int?,
    ) = viewModelScope.launch {
        repo.updateMedication(med, name, dose, scheduleText, prescriber, start, reminderMinute)
        repo.rescheduleNotifications()
        toast("$name updated")
    }

    fun deleteMedication(med: MedicationEntity) = viewModelScope.launch {
        repo.deleteMedication(med)
        repo.rescheduleNotifications()
        toast("${med.name} removed")
    }

    fun setMedicationActive(med: MedicationEntity, active: Boolean) = viewModelScope.launch {
        repo.setMedicationActive(med, active)
        repo.rescheduleNotifications()
    }

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
        date: LocalDate,
        paidFromFund: Boolean,
    ) = viewModelScope.launch {
        repo.addExpense(title, vendor, category, toInr(amountDisplay), date, paidFromFund)
        toast("Expense saved" + forDay(date))
    }

    fun updateExpense(
        row: ExpenseEntity,
        title: String,
        vendor: String?,
        category: ExpenseCategory,
        amountDisplay: Double,
        date: LocalDate,
        paidFromFund: Boolean,
    ) = viewModelScope.launch {
        repo.updateExpense(row, title, vendor, category, toInr(amountDisplay), date, paidFromFund)
        toast("Expense updated" + forDay(date))
    }

    fun deleteExpense(row: ExpenseEntity) = viewModelScope.launch {
        repo.deleteExpense(row)
        toast("Expense removed")
    }

    fun addFundDeposit(amountDisplay: Double, date: LocalDate, note: String?) = viewModelScope.launch {
        repo.addFundTransaction(FundTxnKind.DEPOSIT, toInr(amountDisplay), date, note)
        satisfyReminder("fund", date)
        toast("Deposit recorded" + forDay(date))
    }

    fun addFundWithdrawal(amountDisplay: Double, date: LocalDate, note: String?) = viewModelScope.launch {
        repo.addFundTransaction(FundTxnKind.WITHDRAWAL, toInr(amountDisplay), date, note)
        toast("Withdrawal recorded" + forDay(date))
    }

    fun updateFundTransaction(
        row: FundTxnEntity,
        deposit: Boolean,
        amountDisplay: Double,
        date: LocalDate,
        note: String?,
    ) = viewModelScope.launch {
        val kind = if (deposit) FundTxnKind.DEPOSIT else FundTxnKind.WITHDRAWAL
        repo.updateFundTransaction(row, kind, toInr(amountDisplay), date, note)
        toast("${kind.label} updated" + forDay(date))
    }

    fun deleteFundTransaction(row: FundTxnEntity) = viewModelScope.launch {
        repo.deleteFundTransaction(row)
        toast("${FundTxnKind.of(row.kind).label} removed")
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
            toast("Contribution recorded" + forDay(date))
        }

    fun updateContribution(
        row: ContributionEntity,
        amountDisplay: Double,
        date: LocalDate,
        paidFromFund: Boolean,
    ) = viewModelScope.launch {
        repo.updateContribution(row, toInr(amountDisplay), date, paidFromFund)
        toast("Contribution updated" + forDay(date))
    }

    fun deleteContribution(row: ContributionEntity) = viewModelScope.launch {
        repo.deleteContribution(row)
        toast("Contribution removed")
    }

    fun updateInvestmentValue(investmentId: Long, valueDisplay: Double, asOf: LocalDate = LocalDate.now()) =
        viewModelScope.launch {
            repo.updateInvestmentValue(investmentId, toInr(valueDisplay), asOf)
            toast("Value updated" + forDay(asOf))
        }

    fun setInvestmentActive(investmentId: Long, active: Boolean) = viewModelScope.launch {
        repo.setInvestmentActive(investmentId, active)
    }

    fun deleteInvestment(investmentId: Long) = viewModelScope.launch {
        repo.deleteInvestment(investmentId)
        toast("Removed")
    }

    /** Display amounts are entered in the chosen currency; storage is always whole rupees. */
    private fun toInr(amount: Double): Long = Fmt.toInr(amount, currency.value)

    /**
     * " · 12 Aug" for anything filed against another day, and nothing at all for today — so a
     * back-dated entry says so, and the ordinary case stays quiet.
     */
    private fun forDay(date: LocalDate): String =
        if (date == LocalDate.now()) "" else " · " + Fmt.date(date)

    fun addMilestone(title: String, subtitle: String, date: LocalDate, albumUrl: String?) =
        viewModelScope.launch {
            repo.addTimelineEntry(date, title, subtitle, "auto_awesome", albumUrl)
            toast("Added to timeline" + forDay(date))
        }

    fun updateMilestone(
        row: TimelineEntity,
        title: String,
        subtitle: String,
        date: LocalDate,
        albumUrl: String?,
    ) = viewModelScope.launch {
        repo.updateTimelineEntry(row, date, title, subtitle, albumUrl)
        toast("Moment updated" + forDay(date))
    }

    fun deleteTimelineEntry(row: TimelineEntity) = viewModelScope.launch {
        repo.deleteTimelineEntry(row)
        toast("Moment removed")
    }

    fun addDocument(title: String, tags: String, pageUris: List<String>, filedOn: LocalDate = LocalDate.now()) =
        viewModelScope.launch {
            repo.addDocument(title, filedOn, tags, pageUris)
            val day = if (filedOn == LocalDate.now()) "today" else Fmt.date(filedOn)
            toast("Document filed under $day")
        }

    fun deleteDocument(row: DocumentEntity) = viewModelScope.launch { repo.deleteDocument(row) }

    /**
     * Records where an identity document stands. Obtaining one moves the chain on, so the
     * confirmation names what comes next rather than just what was done.
     */
    fun savePaperwork(
        step: PaperworkStep,
        status: PaperworkStatus,
        settledOn: LocalDate?,
        targetDate: LocalDate?,
        note: String,
    ) = viewModelScope.launch {
        repo.savePaperwork(step.kind.key, status, settledOn, targetDate, note)
        repo.rescheduleNotifications()
        val next = paperwork.value.drop(step.index + 1)
            .firstOrNull { it.status == PaperworkStatus.WAITING || it.status == PaperworkStatus.ACTIVE }
        toast(
            when (status) {
                PaperworkStatus.OBTAINED ->
                    if (next != null) "${step.kind.title} done · ${next.kind.title} is next"
                    else "${step.kind.title} done · that's all four"
                PaperworkStatus.SKIPPED ->
                    if (next != null) "${step.kind.title} set aside · ${next.kind.title} is next"
                    else "${step.kind.title} set aside"
                else -> "${step.kind.title} updated"
            }
        )
    }

    /** The one-tap path from the hero card: obtained today, nothing else changed. */
    fun markPaperworkObtained(step: PaperworkStep) =
        savePaperwork(step, PaperworkStatus.OBTAINED, LocalDate.now(), step.dueDate?.takeIf { step.customDate }, step.note)

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

    fun saveDoctor(id: Long?, name: String, speciality: String?, clinic: String?, phone: String?) =
        viewModelScope.launch {
            repo.saveDoctor(id, name.trim(), speciality, clinic, phone)
            toast(if (id == null) "$name added" else "Saved")
        }

    fun deleteDoctor(row: DoctorEntity) = viewModelScope.launch {
        repo.deleteDoctor(row)
        toast("Removed")
    }

    fun setReminderEnabled(row: ReminderEntity, enabled: Boolean) = viewModelScope.launch {
        repo.setReminderEnabled(row, enabled)
        repo.rescheduleNotifications()
    }

    fun saveCustomReminder(draft: ReminderDraft) = viewModelScope.launch {
        repo.saveCustomReminder(draft)
        repo.rescheduleNotifications()
        toast(if (draft.key == null) "Reminder added" else "Reminder updated")
    }

    fun deleteReminder(key: String) = viewModelScope.launch {
        repo.deleteReminder(key)
        repo.rescheduleNotifications()
        toast("Reminder removed")
    }

    fun updateBabyDetails(
        name: String,
        dob: LocalDate,
        sex: Sex?,
        place: String?,
        weightKg: Double?,
        lengthCm: Double?,
        headCm: Double?,
    ) = viewModelScope.launch {
        val current = baby.value ?: return@launch
        repo.updateBabyDetails(
            current.copy(
                name = name,
                dob = dob,
                sex = sex?.key,
                birthPlace = place,
                birthWeightKg = weightKg,
                birthLengthCm = lengthCm,
                birthHeadCm = headCm,
            )
        )
        toast("Details saved")
    }

    fun setChildPhoto(uri: String?) = viewModelScope.launch {
        repo.setChildPhoto(uri, if (uri == null) null else LocalDate.now())
        // A new picture is what the weekly check-in is asking for, whether it was taken from
        // the prompt or from the avatar on a whim.
        if (uri != null) satisfyReminder("album")
        toast(if (uri == null) "Photo removed" else "Photo saved")
    }

    /**
     * The weekly check-in is dealt with once its steps have been walked through, skips
     * included — choosing to add nothing this week is still an answer, and the prompt should
     * not keep asking after the parent has been through it.
     */
    fun completePhotoCheckIn() = satisfyReminder("album")

    fun setCurrency(c: Currency) = viewModelScope.launch { repo.setCurrency(c) }

    fun setSchedule(id: String) = viewModelScope.launch {
        repo.setSchedule(id)
        _selectedGroup.value = 0
        toast("Schedule set to ${com.kilkari.data.seed.VaccineSchedules.byId(id).name}")
    }

    /** Records a later catch-up: vaccine groups by label, milestones by key, each with a date. */
    fun recordCatchUp(groups: Map<String, LocalDate>, milestones: Map<String, LocalDate>) =
        viewModelScope.launch {
            repo.recordCatchUp(groups, milestones)
            val counted = groups.size + milestones.size
            toast("Recorded $counted ${if (counted == 1) "entry" else "entries"}")
        }

    /** Which milestones are already on the timeline, so catch-up does not offer them again. */
    suspend fun recordedMilestoneKeys(): Set<String> = repo.recordedMilestoneKeys()

    fun setMetric(metric: Boolean) = viewModelScope.launch { repo.setMetric(metric) }
    fun setTodayVariant(v: String) = viewModelScope.launch { repo.setTodayVariant(v) }
    fun setAutoBackup(v: Boolean) = viewModelScope.launch { repo.setAutoBackup(v) }

    class Factory(private val repo: KilkariRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KilkariViewModel(repo) as T
    }
}

private data class InsightWindow(
    val today: LocalDate,
    val days: Int,
    val logs: List<LogEntryEntity>,
    val born: LocalDate?,
)
