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
    ],
    version = 2,
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

    companion object {
        @Volatile private var instance: KilkariDatabase? = null

        fun get(context: Context): KilkariDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KilkariDatabase::class.java,
                DB_NAME,
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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
    }
}
