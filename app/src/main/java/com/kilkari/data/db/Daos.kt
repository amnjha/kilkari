package com.kilkari.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface BabyDao {
    @Query("SELECT * FROM baby ORDER BY id LIMIT 1")
    fun observe(): Flow<BabyEntity?>

    @Query("SELECT * FROM baby ORDER BY id LIMIT 1")
    suspend fun get(): BabyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(baby: BabyEntity): Long

    @Update
    suspend fun update(baby: BabyEntity)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entry WHERE babyId = :babyId AND startAt >= :from AND startAt < :to ORDER BY startAt DESC")
    fun observeBetween(babyId: Long, from: LocalDateTime, to: LocalDateTime): Flow<List<LogEntryEntity>>

    /** Latest entry per kind — backs the "last logged" badge on every tile. */
    @Query(
        """
        SELECT * FROM log_entry WHERE babyId = :babyId AND id IN (
            SELECT id FROM (
                SELECT id, MAX(startAt) AS newest FROM log_entry
                WHERE babyId = :babyId GROUP BY kind
            )
        )
        """
    )
    fun observeLatestPerKind(babyId: Long): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entry WHERE babyId = :babyId AND kind = :kind ORDER BY startAt DESC LIMIT 1")
    fun observeLatestOfKind(babyId: Long, kind: String): Flow<LogEntryEntity?>

    @Query("SELECT COUNT(*) FROM log_entry WHERE babyId = :babyId AND kind = :kind AND startAt >= :from AND startAt < :to")
    fun countOfKindBetween(babyId: Long, kind: String, from: LocalDateTime, to: LocalDateTime): Flow<Int>

    @Query("SELECT * FROM log_entry WHERE babyId = :babyId ORDER BY startAt ASC")
    suspend fun allForExport(babyId: Long): List<LogEntryEntity>

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Update
    suspend fun update(entry: LogEntryEntity)

    @Delete
    suspend fun delete(entry: LogEntryEntity)

    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun byId(id: Long): LogEntryEntity?

    /** The sleep that has started but not ended, if any — powers the "asleep 40 m" badge. */
    @Query("SELECT * FROM log_entry WHERE babyId = :babyId AND kind = 'sleep' AND endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    fun observeOpenSleep(babyId: Long): Flow<LogEntryEntity?>

    @Query("SELECT * FROM log_entry WHERE babyId = :babyId AND kind = 'sleep' AND endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    suspend fun openSleep(babyId: Long): LogEntryEntity?
}

@Dao
interface GrowthDao {
    @Query("SELECT * FROM growth WHERE babyId = :babyId ORDER BY date ASC")
    fun observeAll(babyId: Long): Flow<List<GrowthEntity>>

    @Query("SELECT * FROM growth WHERE babyId = :babyId ORDER BY date ASC")
    suspend fun allForExport(babyId: Long): List<GrowthEntity>

    @Upsert
    suspend fun upsert(row: GrowthEntity)

    @Update
    suspend fun update(row: GrowthEntity)

    @Delete
    suspend fun delete(row: GrowthEntity)
}

@Dao
interface ToothDao {
    @Query("SELECT * FROM tooth WHERE babyId = :babyId")
    fun observeAll(babyId: Long): Flow<List<ToothEntity>>

    @Upsert
    suspend fun upsert(row: ToothEntity)

    @Query("DELETE FROM tooth WHERE babyId = :babyId AND code = :code")
    suspend fun remove(babyId: Long, code: String)
}

@Dao
interface VaccineDao {
    @Query("SELECT * FROM vaccine_dose WHERE babyId = :babyId AND scheduleId = :scheduleId")
    fun observeDoses(babyId: Long, scheduleId: String): Flow<List<VaccineDoseEntity>>

    @Query("SELECT * FROM vaccine_cost WHERE babyId = :babyId AND scheduleId = :scheduleId")
    fun observeCosts(babyId: Long, scheduleId: String): Flow<List<VaccineCostEntity>>

    @Query("SELECT * FROM vaccine_dose WHERE babyId = :babyId ORDER BY givenOn ASC")
    suspend fun allForExport(babyId: Long): List<VaccineDoseEntity>

    @Upsert
    suspend fun upsertDose(dose: VaccineDoseEntity)

    @Upsert
    suspend fun upsertDoses(doses: List<VaccineDoseEntity>)

    @Query("DELETE FROM vaccine_dose WHERE babyId = :babyId AND scheduleId = :s AND groupLabel = :g AND vaccineName = :v")
    suspend fun removeDose(babyId: Long, s: String, g: String, v: String)

    @Query("SELECT COUNT(*) FROM vaccine_dose WHERE babyId = :babyId AND scheduleId = :s AND groupLabel = :g")
    suspend fun recordedDoseCount(babyId: Long, s: String, g: String): Int

    @Query("SELECT costInr FROM vaccine_cost WHERE babyId = :babyId AND scheduleId = :s AND groupLabel = :g")
    suspend fun costFor(babyId: Long, s: String, g: String): Long?

    @Query("DELETE FROM vaccine_cost WHERE babyId = :babyId AND scheduleId = :s AND groupLabel = :g")
    suspend fun removeCost(babyId: Long, s: String, g: String)

    @Upsert
    suspend fun upsertCost(cost: VaccineCostEntity)
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication WHERE babyId = :babyId ORDER BY active DESC, startDate DESC")
    fun observeAll(babyId: Long): Flow<List<MedicationEntity>>

    @Insert
    suspend fun insert(row: MedicationEntity): Long

    @Update
    suspend fun update(row: MedicationEntity)

    @Delete
    suspend fun delete(row: MedicationEntity)

    @Query("SELECT * FROM medication_dose WHERE medicationId IN (:medIds) AND date >= :from")
    fun observeDosesSince(medIds: List<Long>, from: LocalDate): Flow<List<MedicationDoseEntity>>

    @Upsert
    suspend fun upsertDose(row: MedicationDoseEntity)

    @Query("DELETE FROM medication_dose WHERE medicationId = :medId AND date = :date")
    suspend fun removeDose(medId: Long, date: LocalDate)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointment WHERE babyId = :babyId ORDER BY startAt ASC")
    fun observeAll(babyId: Long): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointment WHERE babyId = :babyId AND startAt >= :now ORDER BY startAt ASC")
    suspend fun upcoming(babyId: Long, now: LocalDateTime): List<AppointmentEntity>

    @Insert
    suspend fun insert(row: AppointmentEntity): Long

    @Update
    suspend fun update(row: AppointmentEntity)

    @Delete
    suspend fun delete(row: AppointmentEntity)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense WHERE babyId = :babyId ORDER BY date DESC, id DESC")
    fun observeAll(babyId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE babyId = :babyId AND date >= :from AND date <= :to ORDER BY date DESC, id DESC")
    fun observeBetween(babyId: Long, from: LocalDate, to: LocalDate): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE babyId = :babyId ORDER BY date ASC")
    suspend fun allForExport(babyId: Long): List<ExpenseEntity>

    @Insert
    suspend fun insert(row: ExpenseEntity): Long

    @Update
    suspend fun update(row: ExpenseEntity)

    @Delete
    suspend fun delete(row: ExpenseEntity)
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline WHERE babyId = :babyId ORDER BY date DESC, id DESC")
    fun observeAll(babyId: Long): Flow<List<TimelineEntity>>

    @Query("SELECT * FROM timeline WHERE babyId = :babyId ORDER BY date ASC")
    suspend fun allForExport(babyId: Long): List<TimelineEntity>

    @Insert
    suspend fun insert(row: TimelineEntity): Long

    @Update
    suspend fun update(row: TimelineEntity)

    @Delete
    suspend fun delete(row: TimelineEntity)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM document WHERE babyId = :babyId ORDER BY filedOn DESC, id DESC")
    fun observeAll(babyId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document WHERE id = :id")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Insert
    suspend fun insert(row: DocumentEntity): Long

    @Update
    suspend fun update(row: DocumentEntity)

    @Delete
    suspend fun delete(row: DocumentEntity)
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album WHERE babyId = :babyId ORDER BY id DESC")
    fun observeAll(babyId: Long): Flow<List<AlbumEntity>>

    @Insert
    suspend fun insert(row: AlbumEntity): Long

    @Delete
    suspend fun delete(row: AlbumEntity)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM event WHERE babyId = :babyId ORDER BY date ASC")
    fun observeAll(babyId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE babyId = :babyId ORDER BY date ASC")
    suspend fun allForExport(babyId: Long): List<EventEntity>

    @Insert
    suspend fun insert(row: EventEntity): Long

    @Update
    suspend fun update(row: EventEntity)

    @Delete
    suspend fun delete(row: EventEntity)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE enabled = 1")
    suspend fun enabled(): List<ReminderEntity>

    @Upsert
    suspend fun upsertAll(rows: List<ReminderEntity>)

    @Upsert
    suspend fun upsert(row: ReminderEntity)

    @Query("SELECT COUNT(*) FROM reminder")
    suspend fun count(): Int

    @Query("SELECT * FROM reminder WHERE key = :key")
    suspend fun byKey(key: String): ReminderEntity?

    @Query("DELETE FROM reminder WHERE key = :key AND builtIn = 0")
    suspend fun deleteCustom(key: String)
}

@Dao
interface FundDao {
    @Query("SELECT * FROM fund_txn WHERE babyId = :babyId ORDER BY date DESC, id DESC")
    fun observeAll(babyId: Long): Flow<List<FundTxnEntity>>

    @Query("SELECT * FROM fund_txn WHERE babyId = :babyId ORDER BY date ASC")
    suspend fun allForExport(babyId: Long): List<FundTxnEntity>

    @Insert
    suspend fun insert(row: FundTxnEntity): Long

    @Update
    suspend fun update(row: FundTxnEntity)

    @Delete
    suspend fun delete(row: FundTxnEntity)

    /** Most recent deposit, used to tell whether this month's top-up has happened. */
    @Query("SELECT * FROM fund_txn WHERE babyId = :babyId AND kind = 'deposit' ORDER BY date DESC LIMIT 1")
    suspend fun lastDeposit(babyId: Long): FundTxnEntity?
}

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investment WHERE babyId = :babyId ORDER BY active DESC, startDate DESC")
    fun observeAll(babyId: Long): Flow<List<InvestmentEntity>>

    @Query("SELECT * FROM investment WHERE babyId = :babyId ORDER BY startDate ASC")
    suspend fun allForExport(babyId: Long): List<InvestmentEntity>

    @Query("SELECT * FROM investment WHERE id = :id")
    suspend fun byId(id: Long): InvestmentEntity?

    @Insert
    suspend fun insert(row: InvestmentEntity): Long

    @Update
    suspend fun update(row: InvestmentEntity)

    @Delete
    suspend fun delete(row: InvestmentEntity)

    @Query(
        """
        SELECT * FROM investment_contribution
        WHERE investmentId IN (SELECT id FROM investment WHERE babyId = :babyId)
        ORDER BY date DESC, id DESC
        """
    )
    fun observeContributions(babyId: Long): Flow<List<ContributionEntity>>

    @Query(
        """
        SELECT * FROM investment_contribution
        WHERE investmentId IN (SELECT id FROM investment WHERE babyId = :babyId)
        ORDER BY date ASC
        """
    )
    suspend fun contributionsForExport(babyId: Long): List<ContributionEntity>

    @Insert
    suspend fun insertContribution(row: ContributionEntity): Long

    @Update
    suspend fun updateContribution(row: ContributionEntity)

    @Delete
    suspend fun deleteContribution(row: ContributionEntity)

    @Query("DELETE FROM investment_contribution WHERE investmentId = :investmentId")
    suspend fun deleteContributionsFor(investmentId: Long)
}

@Dao
interface TaskStateDao {
    @Query("SELECT * FROM task_state WHERE occurrenceDate >= :since")
    fun observeSince(since: LocalDate): Flow<List<TaskStateEntity>>

    @Upsert
    suspend fun upsert(row: TaskStateEntity)

    @Query("DELETE FROM task_state WHERE taskKey = :taskKey")
    suspend fun clearFor(taskKey: String)
}


@Dao
interface PaperworkDao {
    @Query("SELECT * FROM paperwork WHERE babyId = :babyId")
    fun observeAll(babyId: Long): Flow<List<PaperworkEntity>>

    @Query("SELECT * FROM paperwork WHERE babyId = :babyId AND `key` = :key")
    suspend fun byKey(babyId: Long, key: String): PaperworkEntity?

    @Upsert
    suspend fun upsert(row: PaperworkEntity)

    @Query("UPDATE paperwork SET documentId = NULL WHERE documentId = :documentId")
    suspend fun unlinkDocument(documentId: Long)
}

@Dao
interface DoctorDao {
    @Query("SELECT * FROM doctor WHERE babyId = :babyId ORDER BY name COLLATE NOCASE")
    fun observeAll(babyId: Long): Flow<List<DoctorEntity>>

    @Query("SELECT * FROM doctor WHERE babyId = :babyId ORDER BY name COLLATE NOCASE")
    suspend fun allForExport(babyId: Long): List<DoctorEntity>

    @Upsert
    suspend fun upsert(row: DoctorEntity): Long

    @Delete
    suspend fun delete(row: DoctorEntity)
}
