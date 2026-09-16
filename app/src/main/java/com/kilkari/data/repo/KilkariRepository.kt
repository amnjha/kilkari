package com.kilkari.data.repo

import android.content.Context
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
import com.kilkari.data.db.KilkariDatabase
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.MedicationDoseEntity
import com.kilkari.data.db.MedicationEntity
import com.kilkari.data.db.ReminderEntity
import com.kilkari.data.db.TaskStateEntity
import com.kilkari.data.db.TimelineEntity
import com.kilkari.data.db.ToothEntity
import com.kilkari.data.db.VaccineCostEntity
import com.kilkari.data.db.VaccineDoseEntity
import com.kilkari.data.prefs.AppSettings
import com.kilkari.data.prefs.SettingsStore
import com.kilkari.data.seed.Milestones
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
import com.kilkari.domain.ReminderDraft
import com.kilkari.domain.RepeatRule
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
 * Icons on the two rows the app writes for itself at the start. They are also how those rows
 * are found again when the child's details are edited, so both ends read the same constant.
 */
private const val BIRTH_ICON = "favorite"
private const val BIRTHDAY_ICON = "cake"

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

    /**
     * Everything logged, newest first — the Log tab reads back beyond today through this so a
     * back-dated entry does not become uncorrectable once the day rolls over.
     */
    fun recentLogs(limit: Int = 200): Flow<List<LogEntryEntity>> = forBaby { id ->
        db.logDao().observeRecent(id, limit)
    }

    /**
     * Saves a corrected entry. A medicine dose is mirrored in `medication_dose` so the Today
     * tick knows about it, so moving one to another day has to move that tick with it.
     */
    suspend fun updateLog(entry: LogEntryEntity) {
        val before = db.logDao().byId(entry.id) ?: return
        db.logDao().update(entry)
        clearMirroredDose(before)
        if (entry.kind == LogKind.MEDICINE.key) {
            entry.medicationId?.let {
                db.medicationDao().upsertDose(
                    MedicationDoseEntity(it, entry.startAt.toLocalDate(), entry.startAt)
                )
            }
        }
    }

    suspend fun deleteLog(entry: LogEntryEntity) {
        db.logDao().delete(entry)
        clearMirroredDose(entry)
    }

    /** Drops the "taken" tick mirroring a medicine entry that has moved day or gone. */
    private suspend fun clearMirroredDose(entry: LogEntryEntity) {
        if (entry.kind != LogKind.MEDICINE.key) return
        val medId = entry.medicationId ?: return
        db.medicationDao().removeDose(medId, entry.startAt.toLocalDate())
    }

    // ── Growth ──────────────────────────────────────────────────────────────

    fun growth(): Flow<List<GrowthEntity>> = forBaby { db.growthDao().observeAll(it) }

    suspend fun addGrowth(date: LocalDate, weightKg: Double?, lengthCm: Double?, headCm: Double?) {
        val id = babyId() ?: return
        db.growthDao().upsert(GrowthEntity(babyId = id, date = date, weightKg = weightKg, lengthCm = lengthCm, headCm = headCm))
        db.logDao().insert(LogEntryEntity(babyId = id, kind = LogKind.GROWTH.key, startAt = date.atTime(9, 0)))
    }

    /**
     * A measurement and the journal row announcing it are one entry as far as the parent is
     * concerned, so they are corrected and dropped together. Callers pair them up by date.
     */
    suspend fun updateGrowth(
        marker: LogEntryEntity?,
        measurement: GrowthEntity?,
        date: LocalDate,
        weightKg: Double?,
        lengthCm: Double?,
        headCm: Double?,
    ) {
        val id = babyId() ?: return
        if (measurement == null) {
            db.growthDao().upsert(
                GrowthEntity(
                    babyId = id, date = date,
                    weightKg = weightKg, lengthCm = lengthCm, headCm = headCm,
                )
            )
        } else {
            db.growthDao().update(
                measurement.copy(
                    date = date, weightKg = weightKg, lengthCm = lengthCm, headCm = headCm,
                )
            )
        }
        if (marker != null) {
            db.logDao().update(marker.copy(startAt = date.atTime(marker.startAt.toLocalTime())))
        }
    }

    suspend fun deleteGrowth(marker: LogEntryEntity?, measurement: GrowthEntity?) {
        if (measurement != null) db.growthDao().delete(measurement)
        if (marker != null) db.logDao().delete(marker)
    }

    // ── Teeth ───────────────────────────────────────────────────────────────

    /** Rows rather than codes: the eruption date is editable, so the UI needs to read it back. */
    fun toothRows(): Flow<List<ToothEntity>> = forBaby { db.toothDao().observeAll(it) }

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

    /** The icon follows the category, so it only moves when the category does. */
    suspend fun updateExpense(
        row: ExpenseEntity,
        title: String,
        vendor: String?,
        category: ExpenseCategory,
        amountInr: Long,
        date: LocalDate,
        paidFromFund: Boolean,
    ) = db.expenseDao().update(
        row.copy(
            title = title,
            vendor = vendor,
            category = category.key,
            amountInr = amountInr,
            date = date,
            paidFromFund = paidFromFund,
            icon = if (category.key == row.category) {
                row.icon
            } else if (category == ExpenseCategory.MEDICAL) {
                "medical_services"
            } else {
                "shopping_bag"
            },
        )
    )

    suspend fun deleteExpense(row: ExpenseEntity) = db.expenseDao().delete(row)

    // ── Doctors ─────────────────────────────────────────────────────────────

    fun doctors(): Flow<List<DoctorEntity>> = forBaby { db.doctorDao().observeAll(it) }

    /** Saves a new doctor or updates an existing one, returning the row id. */
    suspend fun saveDoctor(
        id: Long?,
        name: String,
        speciality: String?,
        clinic: String?,
        phone: String?,
    ): Long {
        val babyId = babyId() ?: return 0
        return db.doctorDao().upsert(
            DoctorEntity(
                id = id ?: 0,
                babyId = babyId,
                name = name,
                speciality = speciality,
                clinic = clinic,
                phone = phone,
            )
        )
    }

    suspend fun deleteDoctor(row: DoctorEntity) = db.doctorDao().delete(row)

    // ── Fund ────────────────────────────────────────────────────────────────

    fun fundTransactions(): Flow<List<FundTxnEntity>> = forBaby { db.fundDao().observeAll(it) }

    suspend fun addFundTransaction(kind: FundTxnKind, amountInr: Long, date: LocalDate, note: String?) {
        val id = babyId() ?: return
        db.fundDao().insert(
            FundTxnEntity(babyId = id, kind = kind.key, amountInr = amountInr, date = date, note = note)
        )
    }

    suspend fun updateFundTransaction(
        row: FundTxnEntity,
        kind: FundTxnKind,
        amountInr: Long,
        date: LocalDate,
        note: String?,
    ) = db.fundDao().update(
        row.copy(kind = kind.key, amountInr = amountInr, date = date, note = note)
    )

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

    /** Contributions are the invested total, so correcting one moves the fund balance with it. */
    suspend fun updateContribution(
        row: ContributionEntity,
        amountInr: Long,
        date: LocalDate,
        paidFromFund: Boolean,
    ) = db.investmentDao().updateContribution(
        row.copy(amountInr = amountInr, date = date, paidFromFund = paidFromFund)
    )

    suspend fun deleteContribution(row: ContributionEntity) =
        db.investmentDao().deleteContribution(row)

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

    /** The icon is left alone: a birth or a vaccination keeps its own even when reworded. */
    suspend fun updateTimelineEntry(
        row: TimelineEntity,
        date: LocalDate,
        title: String,
        subtitle: String,
        albumUrl: String?,
    ) = db.timelineDao().update(
        row.copy(date = date, title = title, subtitle = subtitle, albumUrl = albumUrl)
    )

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
            // Everything else that used to be hardcoded here — the weigh-in, the photo
            // check-in, tummy time and the bath — is a reminder now. That makes each one
            // switchable and removable, and lets the less-than-daily ones survive past
            // their day instead of disappearing at midnight.
        }
        db.checklistDao().upsertAll(rows)
    }

    suspend fun setChecklistDone(row: ChecklistEntity, done: Boolean) =
        db.checklistDao().upsert(row.copy(done = done))

    // ── Reminders ───────────────────────────────────────────────────────────

    fun reminders(): Flow<List<ReminderEntity>> = db.reminderDao().observeAll()

    suspend fun setReminderEnabled(row: ReminderEntity, enabled: Boolean) =
        db.reminderDao().upsert(row.copy(enabled = enabled))

    suspend fun saveCustomReminder(draft: ReminderDraft) {
        // An existing reminder keeps whether it is switched on; a new one arrives on.
        val enabled = draft.key?.let { db.reminderDao().byKey(it)?.enabled } ?: true
        db.reminderDao().upsert(
            ReminderEntity(
                key = draft.key ?: "custom:" + System.currentTimeMillis(),
                title = draft.title,
                subtitle = draft.subtitle,
                enabled = enabled,
                builtIn = false,
                minuteOfDay = draft.minuteOfDay,
                repeatRule = draft.repeat.key,
                weekday = draft.weekday,
                dayOfMonth = draft.dayOfMonth,
                startDate = draft.startDate,
                icon = draft.icon,
            )
        )
    }

    /** Built-ins are switched off rather than deleted; only custom reminders go away. */
    suspend fun deleteReminder(key: String) {
        db.reminderDao().deleteCustom(key)
        db.taskStateDao().clearFor(key)
    }

    // ── Task state ──────────────────────────────────────────────────────────

    /** Only recent occurrences matter; anything older has been superseded. */
    fun taskStates(): Flow<List<TaskStateEntity>> =
        db.taskStateDao().observeSince(LocalDate.now().minusDays(120))

    suspend fun setTaskDone(taskKey: String, occurrence: LocalDate, done: Boolean) =
        db.taskStateDao().upsert(TaskStateEntity(taskKey, occurrence, done = done))

    suspend fun dismissTask(taskKey: String, occurrence: LocalDate) =
        db.taskStateDao().upsert(TaskStateEntity(taskKey, occurrence, dismissed = true))

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
                subtitle = birthSubtitle(
                    BabyEntity(
                        id = id, name = name, dob = dob, birthTime = birthTime,
                        birthWeightKg = weightKg, birthLengthCm = lengthCm,
                        birthHeadCm = headCm, birthPlace = place,
                    )
                ),
                icon = BIRTH_ICON,
            )
        )
        db.eventDao().insert(
            EventEntity(
                babyId = id, title = "$name's birthday", subtitle = Fmt.dateFull(dob.plusYears(1)),
                date = dob.plusYears(1), icon = BIRTHDAY_ICON, annual = true,
            )
        )
        seedReminders()
        return id
    }

    /**
     * Completes onboarding in one go: creates the baby, applies the chosen schedule and
     * currency, then backfills whatever the parent said had already happened.
     *
     * Catch-up matters because people rarely start tracking on day one — [givenGroups] maps a
     * vaccine group label to the date it was given, and [milestones] a milestone key to its date.
     */
    suspend fun onboard(
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
    ) {
        settingsStore.setSchedule(scheduleId)
        settingsStore.setCurrency(currency)
        val babyId = createBaby(name, dob, birthTime, weightKg, lengthCm, headCm, place)

        val schedule = VaccineSchedules.byId(scheduleId)
        givenGroups.forEach { (label, on) ->
            val group = schedule.groups.firstOrNull { it.label == label } ?: return@forEach
            db.vaccineDao().upsertDoses(
                group.vaccines.map {
                    VaccineDoseEntity(babyId, scheduleId, label, it.name, on, place)
                }
            )
            db.timelineDao().insert(
                TimelineEntity(
                    babyId = babyId,
                    date = on,
                    title = "$label vaccines given",
                    subtitle = group.vaccines.joinToString(", ") { it.name },
                    icon = "vaccines",
                )
            )
        }

        milestones.forEach { (key, on) ->
            val def = Milestones.all.firstOrNull { it.key == key } ?: return@forEach
            db.timelineDao().insert(
                TimelineEntity(
                    babyId = babyId,
                    date = on,
                    title = def.label,
                    subtitle = "Recorded while setting up",
                    icon = def.icon,
                )
            )
        }

        ensureChecklist(LocalDate.now())
        settingsStore.setOnboarded(true)
    }

    /**
     * Saves edited child details, moving the rows that were derived from them.
     *
     * [createBaby] fans the name and date of birth out into an arrival entry on the timeline,
     * a first birthday and the growth chart's first point. Rewriting only the baby row left
     * those behind, so a corrected date of birth kept the old birthday — and because the events
     * screen rolls an annual date forward to its next occurrence, announced it as due today.
     *
     * Each row is looked for where [createBaby] put it, against the details being replaced.
     * Anything that no longer matches is something the parent has changed by hand, and is left
     * alone rather than dragged along or overwritten.
     */
    suspend fun updateBabyDetails(updated: BabyEntity) {
        val previous = db.babyDao().get()
        db.babyDao().update(updated)
        if (previous == null || previous == updated) return

        db.timelineDao().allForExport(updated.id)
            .firstOrNull { it.icon == BIRTH_ICON && it.date == previous.dob }
            ?.let { row ->
                db.timelineDao().update(
                    row.copy(
                        date = updated.dob,
                        title = if (row.title == arrivalTitle(previous)) arrivalTitle(updated) else row.title,
                        subtitle = if (row.subtitle == birthSubtitle(previous)) {
                            birthSubtitle(updated)
                        } else {
                            row.subtitle
                        },
                    )
                )
            }

        db.eventDao().allForExport(updated.id)
            .firstOrNull { it.annual && it.icon == BIRTHDAY_ICON && it.date == previous.dob.plusYears(1) }
            ?.let { row ->
                val birthday = updated.dob.plusYears(1)
                db.eventDao().update(
                    row.copy(
                        title = if (row.title == birthdayTitle(previous)) birthdayTitle(updated) else row.title,
                        subtitle = Fmt.dateFull(birthday),
                        date = birthday,
                    )
                )
            }

        syncBirthMeasurement(previous, updated)
    }

    private fun arrivalTitle(baby: BabyEntity) = "${baby.name} arrived"

    private fun birthdayTitle(baby: BabyEntity) = "${baby.name}'s birthday"

    /** The line under the arrival entry: whatever of the birth details was given. */
    private fun birthSubtitle(baby: BabyEntity) = listOfNotNull(
        baby.birthTime?.let { Fmt.time(it) },
        baby.birthWeightKg?.let { Fmt.weight(it) },
        baby.birthLengthCm?.let { Fmt.length(it) },
        baby.birthPlace,
    ).joinToString(" · ")

    /**
     * Keeps the growth chart's first point in step with the birth measurements, which the edit
     * sheet offers and which otherwise reached nothing. Clearing them all removes the point
     * instead of leaving an empty one behind.
     *
     * Only the point this code wrote is touched — same date and same numbers as the details
     * being replaced — so a measurement the parent recorded on the birthday itself survives.
     */
    private suspend fun syncBirthMeasurement(previous: BabyEntity, updated: BabyEntity) {
        val hasAny = updated.birthWeightKg != null ||
            updated.birthLengthCm != null ||
            updated.birthHeadCm != null
        val ours = db.growthDao().allForExport(updated.id).firstOrNull {
            it.date == previous.dob &&
                it.weightKg == previous.birthWeightKg &&
                it.lengthCm == previous.birthLengthCm &&
                it.headCm == previous.birthHeadCm
        }
        when {
            ours != null && !hasAny -> db.growthDao().delete(ours)
            ours != null -> db.growthDao().update(
                ours.copy(
                    date = updated.dob,
                    weightKg = updated.birthWeightKg,
                    lengthCm = updated.birthLengthCm,
                    headCm = updated.birthHeadCm,
                )
            )
            hasAny -> db.growthDao().upsert(
                GrowthEntity(
                    babyId = updated.id,
                    date = updated.dob,
                    weightKg = updated.birthWeightKg,
                    lengthCm = updated.birthLengthCm,
                    headCm = updated.birthHeadCm,
                )
            )
        }
    }

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
                ReminderEntity("meds", "Medicines", "At each dose time", true, repeatRule = "daily"),
                ReminderEntity("vac", "Vaccines due", "7 days and 1 day before", true),
                ReminderEntity("appt", "Appointments", "1 day before, 1 hour before", true),
                ReminderEntity(
                    "weigh", "Weekly weigh-in", "Sundays", false,
                    repeatRule = "weekly", weekday = 7, minuteOfDay = 9 * 60,
                ),
                ReminderEntity(
                    "album", "Photo check-in", "Sundays — stays until you deal with it", true,
                    repeatRule = "weekly", weekday = 7, minuteOfDay = 10 * 60,
                ),
                ReminderEntity(
                    "fund", "Monthly fund top-up", "On the day the deposit is due", true,
                    repeatRule = "monthly",
                ),
                // Two daily habits the design showed on the Today list. They are the parent's
                // to keep, reword or delete rather than the app's to insist on, so they arrive
                // as ordinary custom reminders — and switched off, since nobody asked for them.
                ReminderEntity(
                    "tummy", "Tummy time", "A few minutes of floor time", false,
                    builtIn = false, repeatRule = "daily", icon = "child_care",
                ),
                ReminderEntity(
                    "bath", "Bath", "Part of the evening routine", false,
                    builtIn = false, repeatRule = "daily", icon = "bathtub",
                ),
            )
        )
    }

    private suspend fun babyId(): Long? = db.babyDao().get()?.id

    private suspend fun currentScheduleId(): String = settingsStore.settings.first().scheduleId
}
