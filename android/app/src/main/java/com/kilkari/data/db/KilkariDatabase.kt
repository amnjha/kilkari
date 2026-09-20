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
        ReminderEntity::class,
        FundTxnEntity::class,
        FundAccountEntity::class,
        InvestmentEntity::class,
        ContributionEntity::class,
        TaskStateEntity::class,
        DoctorEntity::class,
        PaperworkEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KilkariDatabase : RoomDatabase() {
    abstract fun babyDao(): BabyDao
    abstract fun fundAccountDao(): FundAccountDao
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
    abstract fun reminderDao(): ReminderDao
    abstract fun fundDao(): FundDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun taskStateDao(): TaskStateDao
    abstract fun doctorDao(): DoctorDao
    abstract fun paperworkDao(): PaperworkDao

    companion object {
        @Volatile private var instance: KilkariDatabase? = null

        fun get(context: Context): KilkariDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KilkariDatabase::class.java,
                DB_NAME,
            ).addMigrations(*ALL_MIGRATIONS).build().also { instance = it }
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

        /**
         * Retires the last two hardcoded checklist rows.
         *
         * Tummy time and the bath were written into every single day and could not be reworded,
         * switched off or removed. They become ordinary custom reminders, off by default like
         * the weigh-in, so the Today list starts as the parent's own rather than the app's.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT OR IGNORE INTO reminder " +
                        "(`key`, `title`, `subtitle`, `enabled`, `builtIn`, `repeatRule`) VALUES " +
                        "('tummy', 'Tummy time', 'A few minutes of floor time', 0, 0, 'daily'), " +
                        "('bath', 'Bath', 'Part of the evening routine', 0, 0, 'daily')"
                )
                db.execSQL("DELETE FROM checklist WHERE key IN ('bath', 'tummy')")
            }
        }

        /**
         * Lets a reminder carry its own icon — a name from the built-in set, or an emoji.
         *
         * The two seeded habits get theirs back, since they were being matched by key before
         * and a parent who renames one should not lose its bathtub.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminder ADD COLUMN icon TEXT")
                db.execSQL("UPDATE reminder SET icon = 'bathtub' WHERE `key` = 'bath'")
                db.execSQL("UPDATE reminder SET icon = 'child_care' WHERE `key` = 'tummy'")
            }
        }

        /**
         * Records the child's sex, so the growth chart can draw the WHO curve published for
         * them rather than the mean of both. Existing children keep a null and the averaged
         * curve until someone fills it in.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE baby ADD COLUMN sex TEXT")
            }
        }

        /** Holds the child's picture and the day it was taken. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE baby ADD COLUMN photoUri TEXT")
                db.execSQL("ALTER TABLE baby ADD COLUMN photoUpdatedOn TEXT")
            }
        }

        /**
         * Retires the checklist for good.
         *
         * Medicines were the last thing it held, and they were already a task in their own
         * right built straight from the medication table — so every dose showed up on Today
         * twice, once from each source, with two separate ticks that never agreed. The
         * medication row is the one that knows about doses, edits and the reminder switch,
         * so the copy goes and the table with it.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `checklist`")
            }
        }

        /**
         * Adds the paperwork chain — birth certificate, Aadhaar, passport, PAN — and the
         * built-in reminder that switches it. The reminder arrives on: a parent who already
         * has the four marks them obtained once, from the Paperwork screen.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `paperwork` (`babyId` INTEGER NOT NULL, `key` TEXT NOT NULL, `status` TEXT NOT NULL, `settledOn` TEXT, `targetDate` TEXT, `note` TEXT NOT NULL, `documentId` INTEGER, PRIMARY KEY(`babyId`, `key`))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO reminder " +
                        "(`key`, `title`, `subtitle`, `enabled`, `builtIn`, `repeatRule`) VALUES " +
                        "('docs', 'Paperwork', 'Birth certificate, Aadhaar, passport, PAN — one at a time', 1, 1, 'none')"
                )
            }
        }

        /**
         * Every migration, in one list so the database and its tests cannot disagree about
         * which ones exist — a migration left out of the builder only shows up as a crash on
         * someone's phone months later.
         */
        val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12,
        )

        /**
         * Gives the fund more than one account, and the ledger a way to be checked off.
         *
         * Everything recorded so far belonged to a single unnamed account, so one is created
         * and every existing movement, expense and contribution is pointed at it. The "paid
         * from the fund" flag becomes the account's id, which says the same thing and can also
         * say which account; SQLite on the oldest phones this app supports cannot drop a
         * column, so the two tables are rebuilt around it.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fund_account` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`babyId` INTEGER NOT NULL, `name` TEXT NOT NULL, `note` TEXT, " +
                        "`archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_account_babyId` ON `fund_account` (`babyId`)")

                db.execSQL("ALTER TABLE `fund_txn` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `fund_txn` ADD COLUMN `transferGroup` TEXT")
                db.execSQL("ALTER TABLE `fund_txn` ADD COLUMN `reconciledOn` TEXT")

                // One account per child already on file, carrying everything recorded so far.
                db.execSQL(
                    "INSERT INTO `fund_account` (`babyId`, `name`, `note`, `archived`, `sortOrder`) " +
                        "SELECT `id`, 'Baby fund', NULL, 0, 0 FROM `baby`"
                )
                db.execSQL(
                    "UPDATE `fund_txn` SET `accountId` = " +
                        "(SELECT `id` FROM `fund_account` WHERE `fund_account`.`babyId` = `fund_txn`.`babyId`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expense_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`babyId` INTEGER NOT NULL, `title` TEXT NOT NULL, `vendor` TEXT, `category` TEXT NOT NULL, " +
                        "`amountInr` INTEGER NOT NULL, `date` TEXT NOT NULL, `icon` TEXT NOT NULL, `fundAccountId` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO `expense_new` (`id`, `babyId`, `title`, `vendor`, `category`, `amountInr`, `date`, `icon`, `fundAccountId`) " +
                        "SELECT e.`id`, e.`babyId`, e.`title`, e.`vendor`, e.`category`, e.`amountInr`, e.`date`, e.`icon`, " +
                        "CASE WHEN e.`paidFromFund` = 1 THEN (SELECT a.`id` FROM `fund_account` a WHERE a.`babyId` = e.`babyId`) ELSE NULL END " +
                        "FROM `expense` e"
                )
                db.execSQL("DROP TABLE `expense`")
                db.execSQL("ALTER TABLE `expense_new` RENAME TO `expense`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_babyId_date` ON `expense` (`babyId`, `date`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `investment_contribution_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`investmentId` INTEGER NOT NULL, `amountInr` INTEGER NOT NULL, `date` TEXT NOT NULL, `fundAccountId` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO `investment_contribution_new` (`id`, `investmentId`, `amountInr`, `date`, `fundAccountId`) " +
                        "SELECT c.`id`, c.`investmentId`, c.`amountInr`, c.`date`, " +
                        "CASE WHEN c.`paidFromFund` = 1 THEN (SELECT a.`id` FROM `fund_account` a LIMIT 1) ELSE NULL END " +
                        "FROM `investment_contribution` c"
                )
                db.execSQL("DROP TABLE `investment_contribution`")
                db.execSQL("ALTER TABLE `investment_contribution_new` RENAME TO `investment_contribution`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_investment_contribution_investmentId_date` " +
                        "ON `investment_contribution` (`investmentId`, `date`)"
                )
            }
        }

        /** Adds the doctor directory. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `doctor` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `babyId` INTEGER NOT NULL, `name` TEXT NOT NULL, `speciality` TEXT, `clinic` TEXT, `phone` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_doctor_babyId` ON `doctor` (`babyId`)"
                )
            }
        }
    }
}
