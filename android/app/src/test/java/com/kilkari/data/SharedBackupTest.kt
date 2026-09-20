package com.kilkari.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kilkari.data.db.AlbumEntity
import com.kilkari.data.db.AppointmentEntity
import com.kilkari.data.db.BabyEntity
import com.kilkari.data.db.DoctorEntity
import com.kilkari.data.db.EventEntity
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.KilkariDatabase
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.PaperworkEntity
import com.kilkari.data.db.VaccineDoseEntity
import com.kilkari.data.repo.SharedBackup
import com.kilkari.domain.Currency
import com.kilkari.domain.FundTxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The shared backup format, written and read back.
 *
 * The only honest test of an importer is whether a store put through it comes back the same,
 * so that is what this does: fill a database, write the JSON, wipe, read it back, and compare.
 * The iOS app checks itself the same way, and both are pinned to the shape documented in
 * `common/BACKUP.md` — which is the only reason a file written on one phone opens on the
 * other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SharedBackupTest {

    private lateinit var db: KilkariDatabase

    /** Records what a restore was told to set, so the settings half can be checked too. */
    private class RecordingSettings : SharedBackup.Settings {
        var currency: Currency? = null
        var metric: Boolean? = null
        var schedule: String? = null

        override suspend fun setCurrency(currency: Currency) { this.currency = currency }
        override suspend fun setMetric(metric: Boolean) { this.metric = metric }
        override suspend fun setSchedule(id: String) { this.schedule = id }
    }

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KilkariDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private suspend fun seed(): Long {
        val babyId = db.babyDao().insert(
            BabyEntity(name = "Ira", dob = LocalDate.of(2026, 9, 3), sex = "f")
        )
        db.logDao().insert(
            LogEntryEntity(
                babyId = babyId, kind = "feed",
                startAt = LocalDateTime.of(2026, 9, 18, 17, 22),
                feedType = "breast", side = "L", amount = 14,
            )
        )
        db.logDao().insert(
            LogEntryEntity(
                babyId = babyId, kind = "sleep",
                startAt = LocalDateTime.of(2026, 9, 18, 14, 0),
                endAt = LocalDateTime.of(2026, 9, 18, 16, 30),
            )
        )
        db.vaccineDao().upsertDose(
            VaccineDoseEntity(
                babyId = babyId, scheduleId = "iap", groupLabel = "Birth",
                vaccineName = "BCG", givenOn = LocalDate.of(2026, 9, 4),
                clinic = "Rainbow Clinic", brand = "Serum",
            )
        )
        db.growthDao().upsert(
            GrowthEntity(babyId = babyId, date = LocalDate.of(2026, 9, 18),
                         weightKg = 3.68, lengthCm = 50.4)
        )
        db.expenseDao().insert(
            ExpenseEntity(
                babyId = babyId, title = "Nappies, pack of 72", vendor = "Local pharmacy",
                category = "gen", amountInr = 1899, date = LocalDate.of(2026, 9, 9),
            )
        )
        db.fundDao().insert(
            FundTxnEntity(
                babyId = babyId, kind = FundTxnKind.DEPOSIT.key, amountInr = 15_000,
                date = LocalDate.of(2026, 9, 8), note = "Monthly transfer",
            )
        )
        db.appointmentDao().insert(
            AppointmentEntity(
                babyId = babyId, title = "Six-week check",
                startAt = LocalDateTime.of(2026, 10, 15, 19, 0), doctor = "Dr Nair",
            )
        )
        db.eventDao().insert(
            EventEntity(babyId = babyId, title = "Diwali", date = LocalDate.of(2026, 11, 8), annual = true)
        )
        db.albumDao().insert(
            AlbumEntity(babyId = babyId, title = "First month", subtitle = "184 photos",
                        url = "https://photos.app.goo.gl/example")
        )
        db.doctorDao().upsert(
            DoctorEntity(babyId = babyId, name = "Dr Meera Nair", speciality = "Paediatrician",
                         clinic = "Rainbow Clinic", phone = "+91 98450 11223")
        )
        db.paperworkDao().upsert(
            PaperworkEntity(babyId = babyId, key = "birth_cert", status = "obtained",
                            settledOn = LocalDate.of(2026, 10, 1))
        )
        return babyId
    }

    private suspend fun census(): Map<String, Int> {
        val id = db.babyDao().get()!!.id
        return mapOf(
            "logs" to db.logDao().allForExport(id).size,
            "doses" to db.vaccineDao().allForExport(id).size,
            "growth" to db.growthDao().allForExport(id).size,
            "expenses" to db.expenseDao().allForExport(id).size,
            "deposits" to db.fundDao().allForExport(id).size,
            "appointments" to db.appointmentDao().observeAll(id).first().size,
            "events" to db.eventDao().allForExport(id).size,
            "albums" to db.albumDao().observeAll(id).first().size,
            "doctors" to db.doctorDao().allForExport(id).size,
            "paperwork" to db.paperworkDao().observeAll(id).first().size,
        )
    }

    @Test
    fun `a store written out and read back is the same store`() = runTest {
        seed()
        val before = census()
        val json = SharedBackup.write(db, Currency.INR, metric = true, scheduleId = "iap")

        db.clearAllTables()
        assertEquals("wiped", null, db.babyDao().get())

        val settings = RecordingSettings()
        val (summary, root) = SharedBackup.summarise(json)
        assertEquals("written by", "android", summary.writtenBy)
        assertEquals("whose", "Ira", summary.babyName)
        SharedBackup.restore(root, db, settings)

        assertEquals("counts", before, census())
        assertEquals("currency", Currency.INR, settings.currency)
        assertEquals("units", true, settings.metric)
        assertEquals("schedule", "iap", settings.schedule)
    }

    /**
     * Counts alone would pass a restore that kept the right number of rows and lost what was
     * in them, so a few values are checked by hand.
     */
    @Test
    fun `what is in the rows survives the round trip`() = runTest {
        seed()
        val json = SharedBackup.write(db, Currency.INR, metric = false, scheduleId = "who")
        db.clearAllTables()
        SharedBackup.restore(SharedBackup.summarise(json).second, db, RecordingSettings())

        val baby = db.babyDao().get()!!
        assertEquals("name", "Ira", baby.name)
        assertEquals("date of birth", LocalDate.of(2026, 9, 3), baby.dob)
        assertEquals("sex survives the boy/girl spelling", "f", baby.sex)

        val feed = db.logDao().allForExport(baby.id).first { it.kind == "feed" }
        assertEquals("side", "L", feed.side)
        assertEquals("amount", 14, feed.amount)
        assertEquals("time of day", LocalDateTime.of(2026, 9, 18, 17, 22), feed.startAt)

        val nap = db.logDao().allForExport(baby.id).first { it.kind == "sleep" }
        assertEquals("a nap keeps both ends", LocalDateTime.of(2026, 9, 18, 16, 30), nap.endAt)

        val dose = db.vaccineDao().allForExport(baby.id).single()
        assertEquals("clinic", "Rainbow Clinic", dose.clinic)
        assertEquals("brand", "Serum", dose.brand)
        assertEquals("given on", LocalDate.of(2026, 9, 4), dose.givenOn)
        // The schedule is taken from the file's settings, not from whatever this app is on.
        assertEquals("schedule the dose belongs to", "who", dose.scheduleId)

        val expense = db.expenseDao().allForExport(baby.id).single()
        assertEquals("a comma in a title", "Nappies, pack of 72", expense.title)
        assertEquals("amount stays whole", 1899L, expense.amountInr)
        assertEquals("category survives the short/long spelling", "gen", expense.category)

        val growth = db.growthDao().allForExport(baby.id).single()
        assertEquals("weight", 3.68, growth.weightKg!!, 0.0001)
        assertEquals("head was never measured", null, growth.headCm)
    }

    @Test
    fun `a file from the other app is read the same way`() = runTest {
        // Written by hand in the documented shape rather than by this app, so the test fails
        // if Android drifts from what iOS writes.
        val json = """
            {
              "format": "kilkari-backup",
              "version": 1,
              "writtenBy": "ios",
              "writtenAt": "2026-09-20T14:24:04Z",
              "settings": { "currency": "USD", "metric": false, "scheduleId": "cdc" },
              "baby": { "name": "Ada", "dob": "2026-01-15T00:00:00Z", "sex": "girl" },
              "logEntries": [
                { "kind": "diaper", "startAt": "2026-09-18T09:00:00Z", "endAt": null,
                  "amount": null, "side": null, "feedType": null,
                  "diaperKind": "both", "note": null }
              ],
              "doses": [],
              "growth": [{ "date": "2026-03-01T00:00:00Z", "weightKg": 5.1,
                           "lengthCm": null, "headCm": null }],
              "expenses": [], "deposits": [], "investments": [], "appointments": [],
              "milestones": [{ "title": "First smile", "note": null,
                               "date": "2026-03-04T00:00:00Z" }],
              "events": [], "albums": [], "doctors": [], "reminders": [], "paperwork": []
            }
        """.trimIndent()

        val settings = RecordingSettings()
        val (summary, root) = SharedBackup.summarise(json)
        assertEquals("written by", "ios", summary.writtenBy)
        assertEquals("whose", "Ada", summary.babyName)
        SharedBackup.restore(root, db, settings)

        val baby = db.babyDao().get()!!
        assertEquals("name", "Ada", baby.name)
        assertEquals("the day survives the timezone", LocalDate.of(2026, 1, 15), baby.dob)
        assertEquals("sex", "f", baby.sex)
        assertEquals("currency", Currency.USD, settings.currency)
        assertEquals("units", false, settings.metric)
        assertEquals("schedule", "cdc", settings.schedule)

        val entry = db.logDao().allForExport(baby.id).single()
        assertEquals("kind", "diaper", entry.kind)
        assertEquals("nulls arrive as nulls", null, entry.amount)
        assertEquals("diaper kind", "both", entry.diaperKind)
        assertEquals("one moment", 1, db.timelineDao().allForExport(baby.id).size)
    }

    @Test
    fun `anything that is not a backup is refused rather than half-read`() {
        listOf("", "not json at all", """{"format":"something-else","version":1}""")
            .forEach { text ->
                val failed = runCatching { SharedBackup.summarise(text) }.isFailure
                assertTrue("refused: $text", failed)
            }
        // A file from a future version is refused too: reading half of it would be worse than
        // saying no.
        val newer = """{"format":"kilkari-backup","version":99}"""
        val error = runCatching { SharedBackup.summarise(newer) }.exceptionOrNull()
        assertNotNull("a newer version is refused", error)
        assertTrue("and says why", error!!.message!!.contains("99"))
    }
}
