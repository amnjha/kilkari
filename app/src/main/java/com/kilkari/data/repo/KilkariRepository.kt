package com.kilkari.data.repo

import android.content.Context
import com.kilkari.data.db.AlbumEntity
import com.kilkari.data.db.AppointmentEntity
import com.kilkari.data.db.BabyEntity
import com.kilkari.data.db.ChecklistEntity
import com.kilkari.data.db.ContributionEntity
import com.kilkari.data.db.DocumentEntity
import com.kilkari.data.db.EventEntity
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.InvestmentEntity
import com.kilkari.data.db.KilkariDatabase
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.MedicationDoseEntity
import com.kilkari.data.db.MedicationEntity
import com.kilkari.data.db.ReminderEntity
import com.kilkari.data.db.TimelineEntity
import com.kilkari.data.db.ToothEntity
import com.kilkari.data.db.VaccineCostEntity
import com.kilkari.data.db.VaccineDoseEntity
import com.kilkari.data.prefs.AppSettings
import com.kilkari.data.prefs.SettingsStore
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.BreastSide
import com.kilkari.domain.Currency
import com.kilkari.domain.DiaperKind
import com.kilkari.domain.ExpenseCategory
import com.kilkari.domain.FeedType
import com.kilkari.domain.FundTxnKind
import com.kilkari.domain.InvestmentKind
import com.kilkari.domain.Fmt
import com.kilkari.domain.LogKind
import com.kilkari.domain.VaccineGroupState
import com.kilkari.domain.VaccineItemState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Single access point for app data. Screens observe flows keyed off the one baby row; every
 * mutation is a suspend function so callers stay off the main thread.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
class KilkariRepository(
    context: Context,
    private val settingsStore: SettingsStore,
) {
    private val db = KilkariDatabase.get(context)

    val settings: Flow<AppSettings> = settingsStore.settings
    val baby: Flow<BabyEntity?> = db.babyDao().observe()

    private fun <T> forBaby(block: (Long) -> Flow<List<T>>): Flow<List<T>> =
        baby.flatMapLatest { b -> if (b == null) flowOf(emptyList()) else block(b.id) }

    // ── Logs ────────────────────────────────────────────────────────────────

    fun logsForDay(date: LocalDate): Flow<List<LogEntryEntity>> = forBaby { id ->
        db.logDao().observeBetween(id, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
    }

    fun latestPerKind(): Flow<Map<LogKind, LogEntryEntity>> = forBaby { id ->
        db.logDao().observeLatestPerKind(id)
    }.map { rows -> rows.associateBy { LogKind.of(it.kind) } }

    fun diaperCountToday(date: LocalDate): Flow<Int> = baby.flatMapLatest { b ->
        if (b == null) flowOf(0)
        else db.logDao().countOfKindBetween(
            b.id, LogKind.DIAPER.key, date.atStartOfDay(), date.plusDays(1).atStartOfDay(),
        )
    }

    suspend fun logFeed(type: FeedType, side: BreastSide?, amount: Int, at: LocalDateTime = LocalDateTime.now()) {
        val id = babyId() ?: return
        db.logDao().insert(
            LogEntryEntity(
                babyId = id, kind = LogKind.FEED.key, startAt = at,
                feedType = type.key, side = side?.key, amount = amount,
            )
        )
    }

    suspend fun logSleep(from: LocalDateTime, to: LocalDateTime?, place: String?) {
        val id = babyId() ?: return
        db.logDao().insert(
            LogEntryEntity(babyId = id, kind = LogKind.SLEEP.key, startAt = from, endAt = to, place = place)
        )
    }

    /** The sleep currently in progress, if any. */
    fun openSleep(): Flow<LogEntryEntity?> = baby.flatMapLatest { b ->
        if (b == null) flowOf(null) else db.logDao().observeOpenSleep(b.id)
    }

    /** Closes the most recent open sleep, so the "asleep 40 m" badge can end. */
    suspend fun wakeUp(at: LocalDateTime = LocalDateTime.now()) {
        val id = babyId() ?: return
        val open = db.logDao().openSleep(id) ?: return
        db.logDao().update(open.copy(endAt = at))
    }

    suspend fun logDiaper(kind: DiaperKind, at: LocalDateTime = LocalDateTime.now()) {
        val id = babyId() ?: return
        db.logDao().insert(
            LogEntryEntity(babyId = id, kind = LogKind.DIAPER.key, startAt = at, diaperKind = kind.key)
        )
    }

    suspend fun logMedicine(medicationId: Long?, name: String, dose: String, at: LocalDateTime = LocalDateTime.now()) {
        val id = babyId() ?: return
        db.logDao().insert(
            LogEntryEntity(
                babyId = id, kind = LogKind.MEDICINE.key, startAt = at,
                medicationId = medicationId, medicationName = name, dose = dose,
            )
        )
        if (medicationId != null) {
            db.medicationDao().upsertDose(MedicationDoseEntity(medicationId, at.toLocalDate(), at))
        }
    }

    suspend fun deleteLog(entry: LogEntryEntity) = db.logDao().delete(entry)

    // ── Growth ──────────────────────────────────────────────────────────────

    fun growth(): Flow<List<GrowthEntity>> = forBaby { db.growthDao().observeAll(it) }

    suspend fun addGrowth(date: LocalDate, weightKg: Double?, lengthCm: Double?, headCm: Double?) {
        val id = babyId() ?: return
        db.growthDao().upsert(GrowthEntity(babyId = id, date = date, weightKg = weightKg, lengthCm = lengthCm, headCm = headCm))
        db.logDao().insert(LogEntryEntity(babyId = id, kind = LogKind.GROWTH.key, startAt = date.atTime(9, 0)))
    }

    // ── Teeth ───────────────────────────────────────────────────────────────

    fun teeth(): Flow<Set<String>> = forBaby { db.toothDao().observeAll(it) }.map { rows -> rows.map { it.code }.toSet() }

    suspend fun toggleTooth(code: String, erupted: Boolean, on: LocalDate = LocalDate.now()) {
        val id = babyId() ?: return
        if (erupted) db.toothDao().upsert(ToothEntity(id, code, on)) else db.toothDao().remove(id, code)
    }

    // ── Vaccines ────────────────────────────────────────────────────────────

    /**
     * Joins the static schedule with recorded doses to produce per-group state.
     * Re-emits whenever the baby, the chosen schedule, or any recorded dose changes.
     */
    fun vaccineGroups(today: LocalDate = LocalDate.now()): Flow<List<VaccineGroupState>> =
        combine(baby, settings) { b, s -> b to s }.flatMapLatest { (b, s) ->
            if (b == null) flowOf(emptyList()) else {
                combine(
                    db.vaccineDao().observeDoses(b.id, s.scheduleId),
                    db.vaccineDao().observeCosts(b.id, s.scheduleId),
                ) { doses, costs ->
                    val givenBy = doses.associateBy { "${it.groupLabel}|${it.vaccineName}" }
                    val costBy = costs.associate { it.groupLabel to it.costInr }
                    VaccineSchedules.byId(s.scheduleId).groups.mapIndexed { i, g ->
                        val due = b.dob.plusDays(g.days.toLong())
                        VaccineGroupState(
                            index = i,
                            label = g.label,
                            dueDate = due,
                            inDays = Fmt.daysUntil(due, today),
                            items = g.vaccines.map {
                                val dose = givenBy["${g.label}|${it.name}"]
                                VaccineItemState(
                                    name = it.name,
                                    desc = it.desc.ifBlank { "Routine dose" },
                                    given = dose != null,
                                    givenOn = dose?.givenOn,
                                    brand = dose?.brand,
                                )
                            },
                            costMinor = costBy[g.label],
                        )
                    }
                }
            }
        }

    /**
     * Records or withdraws a single dose. Withdrawing the last dose of a group also clears the
     * group's recorded cost, so the "posted to Money" annotation cannot outlive the doses it
     * describes. The Money entry itself is left alone — the spend happened either way, and
     * deleting someone's financial record is theirs to decide.
     */
    suspend fun toggleDose(
        groupLabel: String,
        vaccineName: String,
        given: Boolean,
        on: LocalDate = LocalDate.now(),
        clinic: String? = null,
    ) {
        val id = babyId() ?: return
        val scheduleId = currentScheduleId()
        if (given) {
            db.vaccineDao().upsertDose(VaccineDoseEntity(id, scheduleId, groupLabel, vaccineName, on, clinic))
        } else {
            db.vaccineDao().removeDose(id, scheduleId, groupLabel, vaccineName)
            if (db.vaccineDao().recordedDoseCount(id, scheduleId, groupLabel) == 0) {
                db.vaccineDao().removeCost(id, scheduleId, groupLabel)
            }
        }
    }

    /**
     * Records one or more doses of a group as given, optionally posting a Medical expense, and
     * always writing a timeline entry — the cross-screen effect the prototype demonstrates.
     *
     * [brands] maps vaccine name to the product administered; absent or blank entries store null.
     * Marking part of a group and returning later accumulates the recorded cost rather than
     * replacing it, so the group total stays correct.
     */
    suspend fun markDosesGiven(
        group: VaccineGroupState,
        doses: List<VaccineItemState>,
        on: LocalDate,
        clinic: String?,
        doctor: String?,
        brands: Map<String, String?>,
        costInr: Long?,
        addExpense: Boolean,
    ) {
        if (doses.isEmpty()) return
        val id = babyId() ?: return
        val scheduleId = currentScheduleId()

        fun brandOf(name: String) = brands[name]?.trim()?.ifBlank { null }

        db.vaccineDao().upsertDoses(
            doses.map {
                VaccineDoseEntity(
                    babyId = id,
                    scheduleId = scheduleId,
                    groupLabel = group.label,
                    vaccineName = it.name,
                    givenOn = on,
                    clinic = clinic,
                    brand = brandOf(it.name),
                )
            }
        )

        val wholeGroup = doses.size == group.count
        // A single dose reads best by name; anything larger by group, so a Money row or timeline
        // title does not become a comma-separated wall of vaccine names.
        val label = when {
            wholeGroup -> group.label + " vaccines"
            doses.size == 1 -> doses.first().name
            else -> group.label + " vaccines · " + doses.size + " doses"
        }

        if (costInr != null && costInr > 0) {
            val existing = db.vaccineDao().costFor(id, scheduleId, group.label) ?: 0L
            db.vaccineDao().upsertCost(VaccineCostEntity(id, scheduleId, group.label, existing + costInr))
            if (addExpense) {
                db.expenseDao().insert(
                    ExpenseEntity(
                        babyId = id,
                        title = label,
                        vendor = clinic,
                        category = ExpenseCategory.MEDICAL.key,
                        amountInr = costInr,
                        date = on,
                        icon = "vaccines",
                    )
                )
            }
        }

        val named = doses.joinToString(", ") { dose ->
            brandOf(dose.name)?.let { dose.name + " (" + it + ")" } ?: dose.name
        }
        db.timelineDao().insert(
            TimelineEntity(
                babyId = id,
                date = on,
                title = label + " given",
                subtitle = listOfNotNull(
                    named,
                    if (wholeGroup) null else group.label,
                    doctor,
                ).joinToString(" · "),
                icon = "vaccines",
            )
        )
    }

    // ── Medications ─────────────────────────────────────────────────────────

    fun medications(): Flow<List<MedicationEntity>> = forBaby { db.medicationDao().observeAll(it) }

    fun medicationDoses(medIds: List<Long>, since: LocalDate): Flow<List<MedicationDoseEntity>> =
        if (medIds.isEmpty()) flowOf(emptyList()) else db.medicationDao().observeDosesSince(medIds, since)

    suspend fun addMedication(
        name: String, dose: String, scheduleText: String, prescriber: String?,
        start: LocalDate, reminderMinute: Int?,
    ): Long {
        val id = babyId() ?: return 0
        return db.medicationDao().insert(
            MedicationEntity(
                babyId = id, name = name, dose = dose, scheduleText = scheduleText,
                prescriber = prescriber, startDate = start, reminderMinute = reminderMinute,
            )
        )
    }

    suspend fun setMedicationActive(med: MedicationEntity, active: Boolean) =
        db.medicationDao().update(med.copy(active = active, endDate = if (active) null else LocalDate.now()))

    suspend fun deleteMedication(med: MedicationEntity) = db.medicationDao().delete(med)

    suspend fun setDoseTaken(medId: Long, date: LocalDate, taken: Boolean) {
        if (taken) db.medicationDao().upsertDose(MedicationDoseEntity(medId, date, LocalDateTime.now()))
        else db.medicationDao().removeDose(medId, date)
    }

    // ── Appointments ────────────────────────────────────────────────────────

    fun appointments(): Flow<List<AppointmentEntity>> = forBaby { db.appointmentDao().observeAll(it) }

    suspend fun addAppointment(title: String, at: LocalDateTime, doctor: String?, place: String?, reminderDaysBefore: Int?): Long {
        val id = babyId() ?: return 0
        return db.appointmentDao().insert(
            AppointmentEntity(babyId = id, title = title, startAt = at, doctor = doctor, place = place, reminderDaysBefore = reminderDaysBefore)
        )
    }

    suspend fun updateAppointment(row: AppointmentEntity) = db.appointmentDao().update(row)
    suspend fun deleteAppointment(row: AppointmentEntity) = db.appointmentDao().delete(row)

    // ── Money ───────────────────────────────────────────────────────────────

    fun expenses(): Flow<List<ExpenseEntity>> = forBaby { db.expenseDao().observeAll(it) }

    suspend fun addExpense(
        title: String,
        vendor: String?,
        category: ExpenseCategory,
        amountInr: Long,
        date: LocalDate,
        paidFromFund: Boolean = true,
    ) {
        val id = babyId() ?: return
        db.expenseDao().insert(
            ExpenseEntity(
                babyId = id, title = title, vendor = vendor, category = category.key,
                amountInr = amountInr, date = date, paidFromFund = paidFromFund,
                icon = if (category == ExpenseCategory.MEDICAL) "medical_services" else "shopping_bag",
            )
        )
    }

    suspend fun deleteExpense(row: ExpenseEntity) = db.expenseDao().delete(row)

    // ── Fund ────────────────────────────────────────────────────────────────

    fun fundTransactions(): Flow<List<FundTxnEntity>> = forBaby { db.fundDao().observeAll(it) }

    suspend fun addFundTransaction(kind: FundTxnKind, amountInr: Long, date: LocalDate, note: String?) {
        val id = babyId() ?: return
        db.fundDao().insert(
            FundTxnEntity(babyId = id, kind = kind.key, amountInr = amountInr, date = date, note = note)
        )
    }

    suspend fun deleteFundTransaction(row: FundTxnEntity) = db.fundDao().delete(row)

    /** Date of the most recent deposit, so the UI can tell whether this month's is done. */
    suspend fun lastDepositDate(): LocalDate? = babyId()?.let { db.fundDao().lastDeposit(it)?.date }

    // ── Investments ─────────────────────────────────────────────────────────

    fun investments(): Flow<List<InvestmentEntity>> = forBaby { db.investmentDao().observeAll(it) }

    fun contributions(): Flow<List<ContributionEntity>> =
        forBaby { db.investmentDao().observeContributions(it) }

    /**
     * Opens a holding. A lump sum ([openingAmountInr]) is recorded as its first contribution so
     * invested totals come from one place — the contribution ledger — for every kind.
     */
    suspend fun addInvestment(
        name: String,
        kind: InvestmentKind,
        institution: String?,
        openingAmountInr: Long?,
        monthlyInr: Long?,
        interestRate: Double?,
        startDate: LocalDate,
        maturityDate: LocalDate?,
        maturityValueInr: Long?,
        paidFromFund: Boolean,
    ): Long {
        val babyId = babyId() ?: return 0
        val id = db.investmentDao().insert(
            InvestmentEntity(
                babyId = babyId,
                name = name,
                kind = kind.key,
                institution = institution,
                monthlyInr = monthlyInr,
                interestRate = interestRate,
                startDate = startDate,
                maturityDate = maturityDate,
                maturityValueInr = maturityValueInr,
            )
        )
        val opening = openingAmountInr ?: monthlyInr
        if (opening != null && opening > 0) {
            db.investmentDao().insertContribution(
                ContributionEntity(
                    investmentId = id,
                    amountInr = opening,
                    date = startDate,
                    paidFromFund = paidFromFund,
                )
            )
        }
        return id
    }

    suspend fun addContribution(investmentId: Long, amountInr: Long, date: LocalDate, paidFromFund: Boolean) {
        db.investmentDao().insertContribution(
            ContributionEntity(
                investmentId = investmentId,
                amountInr = amountInr,
                date = date,
                paidFromFund = paidFromFund,
            )
        )
    }

    suspend fun updateInvestmentValue(investmentId: Long, valueInr: Long, asOf: LocalDate) {
        val row = db.investmentDao().byId(investmentId) ?: return
        db.investmentDao().update(row.copy(currentValueInr = valueInr, valueAsOf = asOf))
    }

    suspend fun setInvestmentActive(investmentId: Long, active: Boolean) {
        val row = db.investmentDao().byId(investmentId) ?: return
        db.investmentDao().update(row.copy(active = active))
    }

    /** Removes the holding and its contributions, so the fund balance drops them too. */
    suspend fun deleteInvestment(investmentId: Long) {
        val row = db.investmentDao().byId(investmentId) ?: return
        db.investmentDao().deleteContributionsFor(investmentId)
        db.investmentDao().delete(row)
    }

    // ── Timeline, documents, albums, events ─────────────────────────────────

    fun timeline(): Flow<List<TimelineEntity>> = forBaby { db.timelineDao().observeAll(it) }

    suspend fun addTimelineEntry(date: LocalDate, title: String, subtitle: String, icon: String, albumUrl: String?) {
        val id = babyId() ?: return
        db.timelineDao().insert(TimelineEntity(babyId = id, date = date, title = title, subtitle = subtitle, icon = icon, albumUrl = albumUrl))
    }

    suspend fun deleteTimelineEntry(row: TimelineEntity) = db.timelineDao().delete(row)

    fun documents(): Flow<List<DocumentEntity>> = forBaby { db.documentDao().observeAll(it) }
    fun document(id: Long): Flow<DocumentEntity?> = db.documentDao().observeById(id)

    suspend fun addDocument(title: String, filedOn: LocalDate, tags: String, pageUris: List<String>): Long {
        val id = babyId() ?: return 0
        return db.documentDao().insert(
            DocumentEntity(
                babyId = id, title = title, filedOn = filedOn, tags = tags,
                pageCount = pageUris.size.coerceAtLeast(1), pageUris = pageUris.joinToString(","),
            )
        )
    }

    suspend fun deleteDocument(row: DocumentEntity) = db.documentDao().delete(row)

    fun albums(): Flow<List<AlbumEntity>> = forBaby { db.albumDao().observeAll(it) }

    suspend fun addAlbum(title: String, subtitle: String, url: String) {
        val id = babyId() ?: return
        db.albumDao().insert(AlbumEntity(babyId = id, title = title, subtitle = subtitle, url = url))
    }

    suspend fun deleteAlbum(row: AlbumEntity) = db.albumDao().delete(row)

    fun events(): Flow<List<EventEntity>> = forBaby { db.eventDao().observeAll(it) }

    suspend fun addEvent(title: String, subtitle: String, date: LocalDate, icon: String, annual: Boolean) {
        val id = babyId() ?: return
        db.eventDao().insert(EventEntity(babyId = id, title = title, subtitle = subtitle, date = date, icon = icon, annual = annual))
    }

    suspend fun deleteEvent(row: EventEntity) = db.eventDao().delete(row)

    // ── Checklist ───────────────────────────────────────────────────────────

    fun checklist(date: LocalDate): Flow<List<ChecklistEntity>> = baby.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else db.checklistDao().observeForDay(b.id, date)
    }

    /** Creates the day's rows from the template the first time a date is opened. */
    suspend fun ensureChecklist(date: LocalDate) {
        val id = babyId() ?: return
        if (db.checklistDao().countForDay(id, date) > 0) return
        val meds = db.medicationDao().activeNow(id)
        val rows = buildList {
            meds.forEach { m ->
                add(ChecklistEntity(id, date, "med_${m.id}", "${m.name} · ${m.dose}", m.scheduleText, false))
            }
            add(ChecklistEntity(id, date, "tummy", "Tummy time · 5 min", "anytime", false))
            add(ChecklistEntity(id, date, "bath", "Bath", "anytime", false))
            if (date.dayOfWeek.value == 7) {
                add(ChecklistEntity(id, date, "weigh", "Weekly weigh-in", "anytime", false))
            }
            add(ChecklistEntity(id, date, "album", "Add this week's photos", "anytime", false))
        }
        db.checklistDao().upsertAll(rows)
    }

    suspend fun setChecklistDone(row: ChecklistEntity, done: Boolean) =
        db.checklistDao().upsert(row.copy(done = done))

    // ── Reminders ───────────────────────────────────────────────────────────

    fun reminders(): Flow<List<ReminderEntity>> = db.reminderDao().observeAll()

    suspend fun setReminderEnabled(row: ReminderEntity, enabled: Boolean) =
        db.reminderDao().upsert(row.copy(enabled = enabled))

    // ── Baby & settings ─────────────────────────────────────────────────────

    suspend fun createBaby(
        name: String, dob: LocalDate, birthTime: LocalDateTime?,
        weightKg: Double?, lengthCm: Double?, headCm: Double?, place: String?,
    ): Long {
        val id = db.babyDao().insert(
            BabyEntity(
                name = name, dob = dob, birthTime = birthTime,
                birthWeightKg = weightKg, birthLengthCm = lengthCm,
                birthHeadCm = headCm, birthPlace = place,
            )
        )
        if (weightKg != null || lengthCm != null || headCm != null) {
            db.growthDao().upsert(GrowthEntity(babyId = id, date = dob, weightKg = weightKg, lengthCm = lengthCm, headCm = headCm))
        }
        db.timelineDao().insert(
            TimelineEntity(
                babyId = id, date = dob, title = "$name arrived",
                subtitle = listOfNotNull(
                    birthTime?.let { Fmt.time(it) },
                    weightKg?.let { Fmt.weight(it) },
                    lengthCm?.let { Fmt.length(it) },
                    place,
                ).joinToString(" · "),
                icon = "favorite",
            )
        )
        db.eventDao().insert(
            EventEntity(
                babyId = id, title = "$name's birthday", subtitle = Fmt.dateFull(dob.plusYears(1)),
                date = dob.plusYears(1), icon = "cake", annual = true,
            )
        )
        seedReminders()
        return id
    }

    suspend fun updateBaby(row: BabyEntity) = db.babyDao().update(row)

    suspend fun setCurrency(c: Currency) = settingsStore.setCurrency(c)
    suspend fun setSchedule(id: String) = settingsStore.setSchedule(id)
    suspend fun setMetric(metric: Boolean) = settingsStore.setMetric(metric)
    suspend fun setOnboarded(v: Boolean) = settingsStore.setOnboarded(v)
    suspend fun setTodayVariant(v: String) = settingsStore.setTodayVariant(v)
    suspend fun setAutoBackup(v: Boolean) = settingsStore.setAutoBackup(v)

    suspend fun setFundPlan(monthlyInr: Long, day: Int, name: String) =
        settingsStore.setFundPlan(monthlyInr, day, name)

    suspend fun seedReminders() {
        if (db.reminderDao().count() > 0) return
        db.reminderDao().upsertAll(
            listOf(
                ReminderEntity("meds", "Medicines", "At each dose time", true),
                ReminderEntity("vac", "Vaccines due", "7 days and 1 day before", true),
                ReminderEntity("appt", "Appointments", "1 day before, 1 hour before", true),
                ReminderEntity("weigh", "Weekly weigh-in", "Sundays 9:00 am", false),
                ReminderEntity("album", "Photo nudge", "Weekly: add photos to album", true),
                ReminderEntity("fund", "Monthly fund top-up", "On the day the deposit is due", true),
            )
        )
    }

    private suspend fun babyId(): Long? = db.babyDao().get()?.id

    private suspend fun currentScheduleId(): String = settingsStore.settings.first().scheduleId
}
