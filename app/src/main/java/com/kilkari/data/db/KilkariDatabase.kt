package com.kilkari.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BabyEntity::class,
        LogEntryEntity::class,
        GrowthEntity::class,
        ToothEntity::class,
        VaccineDoseEntity::class,
        VaccineCostEntity::class,
        MedicationEntity::class,
        MedicationDoseEntity::class,
        AppointmentEntity::class,
        ExpenseEntity::class,
        TimelineEntity::class,
        DocumentEntity::class,
        AlbumEntity::class,
        EventEntity::class,
        ChecklistEntity::class,
        ReminderEntity::class,
        FundTxnEntity::class,
        InvestmentEntity::class,
        ContributionEntity::class,
        TaskStateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KilkariDatabase : RoomDatabase() {
    abstract fun babyDao(): BabyDao
    abstract fun logDao(): LogDao
    abstract fun growthDao(): GrowthDao
    abstract fun toothDao(): ToothDao
    abstract fun vaccineDao(): VaccineDao
    abstract fun medicationDao(): MedicationDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun timelineDao(): TimelineDao
    abstract fun documentDao(): DocumentDao
    abstract fun albumDao(): AlbumDao
    abstract fun eventDao(): EventDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun reminderDao(): ReminderDao
    abstract fun fundDao(): FundDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun taskStateDao(): TaskStateDao

    companion object {
        @Volatile private var instance: KilkariDatabase? = null

        fun get(context: Context): KilkariDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KilkariDatabase::class.java,
                DB_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }

        /** Drops the cached handle so a restore can swap the file underneath us. */
        fun close() = synchronized(this) {
            instance?.close()
            instance = null
        }

        const val DB_NAME = "kilkari.db"

        /** Adds the optional vaccine brand; existing doses keep a null brand. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vaccine_dose ADD COLUMN brand TEXT")
            }
        }

        /** Adds the savings fund and investment tracking. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE expense ADD COLUMN paidFromFund INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fund_txn` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `babyId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `amountInr` INTEGER NOT NULL, `date` TEXT NOT NULL, `note` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fund_txn_babyId_date` ON `fund_txn` (`babyId`, `date`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `investment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `babyId` INTEGER NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `institution` TEXT, `monthlyInr` INTEGER, `interestRate` REAL, `startDate` TEXT NOT NULL, `maturityDate` TEXT, `currentValueInr` INTEGER, `valueAsOf` TEXT, `maturityValueInr` INTEGER, `active` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_investment_babyId` ON `investment` (`babyId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `investment_contribution` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `investmentId` INTEGER NOT NULL, `amountInr` INTEGER NOT NULL, `date` TEXT NOT NULL, `paidFromFund` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_investment_contribution_investmentId_date` ON `investment_contribution` (`investmentId`, `date`)"
                )
            }
        }

        /** Adds reminder scheduling and per-occurrence task state. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminder ADD COLUMN builtIn INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE reminder ADD COLUMN minuteOfDay INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE reminder ADD COLUMN repeatRule TEXT NOT NULL DEFAULT 'none'"
                )
                db.execSQL(
                    "ALTER TABLE reminder ADD COLUMN weekday INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE reminder ADD COLUMN dayOfMonth INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE reminder ADD COLUMN startDate TEXT"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_state` (`taskKey` TEXT NOT NULL, `occurrenceDate` TEXT NOT NULL, `done` INTEGER NOT NULL, `dismissed` INTEGER NOT NULL, PRIMARY KEY(`taskKey`, `occurrenceDate`))"
                )
                db.execSQL(
                    "UPDATE reminder SET repeatRule = 'daily' WHERE key = 'meds'"
                )
                db.execSQL(
                    "UPDATE reminder SET repeatRule = 'weekly', weekday = 7 WHERE key IN ('weigh', 'album')"
                )
                db.execSQL(
                    "UPDATE reminder SET repeatRule = 'monthly' WHERE key = 'fund'"
                )
                db.execSQL(
                    "UPDATE reminder SET title = 'Photo check-in', " +
                        "subtitle = 'Sundays — stays until you deal with it', " +
                        "minuteOfDay = 600 WHERE key = 'album'"
                )
                db.execSQL(
                    "UPDATE reminder SET subtitle = 'Sundays', minuteOfDay = 540 WHERE key = 'weigh'"
                )
                db.execSQL("DELETE FROM checklist WHERE key IN ('album', 'weigh')")
            }
        }
    }
}
