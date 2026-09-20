package com.kilkari.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kilkari.data.db.FundAccountEntity
import com.kilkari.data.db.KilkariDatabase
import com.kilkari.data.prefs.SettingsStore
import com.kilkari.data.repo.KilkariRepository
import com.kilkari.domain.FundTxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The repository against a real database.
 *
 * These are the writes that touch more than one table — a transfer, a deleted account, a
 * corrected date of birth — where getting it half right leaves rows pointing at things that no
 * longer exist, and nothing on screen says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class RepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repo: KilkariRepository

    @Before
    fun setUp() {
        repo = KilkariRepository(context, SettingsStore(context))
        runBlocking {
            repo.onboard(
                name = "Meera",
                dob = LocalDate.of(2026, 8, 20),
                sex = null,
                birthTime = null,
                weightKg = 3.2,
                lengthCm = 50.0,
                headCm = 35.0,
                place = "Bengaluru",
                scheduleId = "iap",
                currency = com.kilkari.domain.Currency.INR,
                givenGroups = emptyMap(),
                milestones = emptyMap(),
            )
        }
    }

    @After
    fun tearDown() {
        KilkariDatabase.close()
        context.getDatabasePath("kilkari.db").delete()
    }

    @Test
    fun `the first account is made when money is first recorded`() = runBlocking {
        assertTrue(repo.fundAccounts().first().isEmpty())
        repo.addFundTransaction(FundTxnKind.DEPOSIT, 25_000, LocalDate.of(2026, 9, 1), "Opening")

        val accounts = repo.fundAccounts().first()
        assertEquals(1, accounts.size)
        assertEquals(accounts.single().id, repo.fundTransactions().first().single().accountId)
    }

    @Test
    fun `a transfer writes both halves and deleting one takes the other`() = runBlocking {
        val from = repo.addFundAccount("Baby fund", null)
        val to = repo.addFundAccount("Gift envelope", null)
        repo.transferBetweenAccounts(from, to, 2_000, LocalDate.of(2026, 9, 20), "For the cot")

        val halves = repo.fundTransactions().first()
        assertEquals(2, halves.size)
        assertEquals(1, halves.map { it.transferGroup }.toSet().size)
        assertEquals(setOf(from, to), halves.map { it.accountId }.toSet())

        repo.deleteFundTransaction(halves.first())
        assertTrue("both halves go together", repo.fundTransactions().first().isEmpty())
    }

    @Test
    fun `a transfer to the same account is not recorded at all`() = runBlocking {
        val only = repo.addFundAccount("Baby fund", null)
        repo.transferBetweenAccounts(only, only, 2_000, LocalDate.of(2026, 9, 20), null)
        assertTrue(repo.fundTransactions().first().isEmpty())
    }

    @Test
    fun `an account with movements behind it is not deleted`() = runBlocking {
        val id = repo.addFundAccount("Baby fund", null)
        repo.addFundTransaction(FundTxnKind.DEPOSIT, 5_000, LocalDate.of(2026, 9, 1), null, id)
        val account = repo.fundAccounts().first().single()

        assertFalse("refuses while money points at it", repo.deleteFundAccount(account))
        assertEquals(1, repo.fundAccounts().first().size)

        repo.deleteFundTransaction(repo.fundTransactions().first().single())
        assertTrue("and goes once nothing does", repo.deleteFundAccount(account))
        assertTrue(repo.fundAccounts().first().isEmpty())
    }

    @Test
    fun `reconciling one half of a transfer leaves the other to its own statement`() = runBlocking {
        val from = repo.addFundAccount("Baby fund", null)
        val to = repo.addFundAccount("Gift envelope", null)
        repo.transferBetweenAccounts(from, to, 2_000, LocalDate.of(2026, 9, 20), null)

        val outgoing = repo.fundTransactions().first().single { it.accountId == from }
        repo.setReconciled(outgoing, LocalDate.of(2026, 9, 30))

        val after = repo.fundTransactions().first()
        assertEquals(LocalDate.of(2026, 9, 30), after.single { it.accountId == from }.reconciledOn)
        assertNull(after.single { it.accountId == to }.reconciledOn)
    }

    @Test
    fun `correcting the date of birth moves what was derived from it`() = runBlocking {
        val baby = repo.baby.first()!!
        val corrected = LocalDate.of(2026, 8, 22)
        repo.updateBabyDetails(baby.copy(dob = corrected))

        val timeline = repo.timeline().first()
        val arrival = timeline.single { it.title == "Meera arrived" }
        assertEquals("the arrival moves with it", corrected, arrival.date)

        val birthday = repo.events().first().single { it.annual }
        assertEquals(corrected.plusYears(1), birthday.date)

        val firstWeighIn = repo.growth().first().first()
        assertEquals("and so does the growth chart's first point", corrected, firstWeighIn.date)
    }

    @Test
    fun `withdrawing the last dose of a group clears the cost recorded against it`() = runBlocking {
        val groups = repo.vaccineGroups(LocalDate.of(2026, 9, 20)).first()
        val birth = groups.first { it.label == "Birth" }
        repo.markDosesGiven(
            group = birth,
            doses = birth.items,
            on = LocalDate.of(2026, 8, 22),
            clinic = "Rainbow",
            doctor = null,
            brands = emptyMap(),
            costInr = 1_200,
            addExpense = true,
        )
        assertEquals(1_200L, repo.vaccineGroups(LocalDate.of(2026, 9, 20)).first()
            .first { it.label == "Birth" }.costMinor)

        birth.items.forEach { repo.toggleDose(birth.label, it.name, false) }

        val after = repo.vaccineGroups(LocalDate.of(2026, 9, 20)).first().first { it.label == "Birth" }
        assertNull("the cost cannot outlive the doses it describes", after.costMinor)
        // The expense itself stays: the money was spent either way.
        assertEquals(1, repo.expenses().first().count { it.amountInr == 1_200L })
    }
}
