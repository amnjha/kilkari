package com.kilkari.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kilkari.data.db.KilkariDatabase
import com.kilkari.data.repo.SharedBackup
import com.kilkari.domain.Currency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A file the iOS app actually wrote, read by this one.
 *
 * Not a hand-typed approximation of the format: the JSON beside this test came out of the
 * iOS app running in a simulator, so it fails if either side drifts from what the other
 * produces. That is the whole promise of common/BACKUP.md, and it is worth a real file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CrossPlatformBackupTest {

    private class NoSettings : SharedBackup.Settings {
        var currency: Currency? = null
        override suspend fun setCurrency(currency: Currency) { this.currency = currency }
        override suspend fun setMetric(metric: Boolean) {}
        override suspend fun setSchedule(id: String) {}
    }

    @Test
    fun `a backup written by the iOS app opens here`() = runTest {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "common/samples/from-ios.json") }
            .firstOrNull { it.isFile }
            ?: throw AssertionError("common/samples/from-ios.json is missing")

        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KilkariDatabase::class.java,
        ).allowMainThreadQueries().build()

        val settings = NoSettings()
        val (summary, root) = SharedBackup.summarise(file.readText())
        assertEquals("written by the other app", "ios", summary.writtenBy)
        assertTrue("and holds something", summary.total > 0)

        SharedBackup.restore(root, db, settings)

        val baby = db.babyDao().get()!!
        assertEquals("name", "Ira", baby.name)
        assertEquals("sex, spelled out over there and lettered here", "f", baby.sex)
        assertEquals("every log entry", 107, db.logDao().allForExport(baby.id).size)
        assertEquals("every dose", 3, db.vaccineDao().allForExport(baby.id).size)
        assertEquals("every expense", 4, db.expenseDao().allForExport(baby.id).size)
        assertEquals("the currency it was kept in", Currency.INR, settings.currency)

        // The direction of a movement, which the format spells out because this app keeps it
        // as a kind and the other one as a sign. A withdrawal read as a deposit would leave
        // the row count right and the balance out by twice the amount.
        val fund = db.fundDao().allForExport(baby.id)
        assertEquals("every movement", 3, fund.size)
        assertEquals(
            "the money that went out went out",
            listOf(3_000L to "withdrawal"),
            fund.filter { it.kind == "withdrawal" }.map { it.amountInr to it.kind },
        )
        assertEquals("what the fund holds", 15_000L,
            fund.sumOf { if (it.kind == "withdrawal") -it.amountInr else it.amountInr })

        // A value, not just a count: the comma in this title is the one thing most likely to
        // survive a JSON round trip and not a CSV one.
        assertTrue(
            "the nappies expense came across",
            db.expenseDao().allForExport(baby.id).any { it.title == "Nappies, pack of 72" },
        )
        db.close()
    }
}
