package com.kilkari.domain

import com.kilkari.data.db.ContributionEntity
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.data.db.InvestmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FundMathTest {

    private fun day(d: Int) = LocalDate.of(2026, 9, d)

    private fun deposit(id: Long, amount: Long, account: Long = 1, group: String? = null) =
        FundTxnEntity(id = id, babyId = 1, kind = "deposit", amountInr = amount, date = day(1), accountId = account, transferGroup = group)

    private fun withdrawal(id: Long, amount: Long, account: Long = 1, group: String? = null) =
        FundTxnEntity(id = id, babyId = 1, kind = "withdrawal", amountInr = amount, date = day(2), accountId = account, transferGroup = group)

    private fun expense(id: Long, amount: Long, account: Long? = 1) =
        ExpenseEntity(id = id, babyId = 1, title = "Nappies", category = "gen", amountInr = amount, date = day(3), fundAccountId = account)

    private fun contribution(id: Long, amount: Long, account: Long? = 1) =
        ContributionEntity(id = id, investmentId = 1, amountInr = amount, date = day(4), fundAccountId = account)

    @Test
    fun `the balance is what went in less everything that came out`() {
        val total = FundMath.total(
            listOf(deposit(1, 25_000), withdrawal(2, 2_000)),
            listOf(expense(1, 1_899)),
            listOf(contribution(1, 5_000)),
        )
        assertEquals(16_101, total)
    }

    @Test
    fun `money paid from elsewhere does not touch the fund`() {
        val total = FundMath.total(
            listOf(deposit(1, 10_000)),
            listOf(expense(1, 3_600, account = null)),
            listOf(contribution(1, 1_000, account = null)),
        )
        assertEquals(10_000, total)
    }

    @Test
    fun `a transfer moves money between accounts and leaves the total alone`() {
        val transactions = listOf(
            deposit(1, 25_000, account = 1),
            withdrawal(2, 2_000, account = 1, group = "tf-1"),
            deposit(3, 2_000, account = 2, group = "tf-1"),
        )
        assertEquals(25_000, FundMath.total(transactions, emptyList(), emptyList()))

        val balances = FundMath.balances(transactions, emptyList(), emptyList())
        assertEquals(23_000L, balances[1])
        assertEquals(2_000L, balances[2])
    }

    @Test
    fun `each account is charged only for what it paid for`() {
        val balances = FundMath.balances(
            listOf(deposit(1, 20_000, account = 1), deposit(2, 5_000, account = 2)),
            listOf(expense(1, 1_000, account = 2)),
            listOf(contribution(1, 4_000, account = 1)),
        )
        assertEquals(16_000L, balances[1])
        assertEquals(4_000L, balances[2])
    }

    @Test
    fun `the ledger gathers every kind of movement, newest first`() {
        val rows = FundMath.ledger(
            listOf(deposit(1, 25_000), withdrawal(2, 2_000, group = "tf-1")),
            listOf(expense(1, 1_899)),
            listOf(contribution(1, 5_000)),
            listOf(InvestmentEntity(id = 1, babyId = 1, name = "FD", kind = "fd", startDate = day(1), active = true)),
        )
        assertEquals(4, rows.size)
        assertEquals(listOf(day(4), day(3), day(2), day(1)), rows.map { it.date })
        assertEquals("FD", rows.first().title)
        assertTrue("a transfer half says so", rows.single { it.sourceId == 2L && it.origin == FundLedgerOrigin.WITHDRAWAL }.transfer)
        assertEquals("Transfer out", rows.single { it.transfer }.title)
    }

    @Test
    fun `an expense paid from elsewhere stays out of the fund's ledger`() {
        val rows = FundMath.ledger(
            emptyList(), listOf(expense(1, 3_600, account = null)), emptyList(), emptyList(),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `the monthly top-up is due once the day comes round, and only once`() {
        // Plan of 10,000 on the 1st, nothing deposited this month yet.
        assertTrue(FundMath.topUpDue(10_000, 1, lastDeposit = day(1).minusMonths(1), today = day(5)))
        // Already deposited this month.
        assertFalse(FundMath.topUpDue(10_000, 1, lastDeposit = day(2), today = day(5)))
        // The day has not come round yet.
        assertFalse(FundMath.topUpDue(10_000, 20, lastDeposit = null, today = day(5)))
        // No plan set.
        assertFalse(FundMath.topUpDue(0, 1, lastDeposit = null, today = day(5)))
    }
}
