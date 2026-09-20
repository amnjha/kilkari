package com.kilkari.domain

import com.kilkari.data.db.ContributionEntity
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.data.db.InvestmentEntity
import java.time.LocalDate

/**
 * What is in the fund, worked out rather than stored.
 *
 * A balance kept as a number drifts: delete an expense and it is wrong until something
 * remembers to put it back. Here every figure is derived from the rows themselves, so a
 * correction anywhere moves the balance with it and there is nothing to keep in step.
 *
 * Kept out of the ViewModel so it can be tested as arithmetic, which is what it is.
 */
object FundMath {

    /** Deposits, less withdrawals, expenses and contributions paid from the fund. */
    fun total(
        transactions: List<FundTxnEntity>,
        expenses: List<ExpenseEntity>,
        contributions: List<ContributionEntity>,
    ): Long {
        val deposits = transactions.filter { it.kind == FundTxnKind.DEPOSIT.key }.sumOf { it.amountInr }
        val withdrawals = transactions.filter { it.kind == FundTxnKind.WITHDRAWAL.key }.sumOf { it.amountInr }
        val spent = expenses.filter { it.paidFromFund }.sumOf { it.amountInr }
        val invested = contributions.filter { it.paidFromFund }.sumOf { it.amountInr }
        return deposits - withdrawals - spent - invested
    }

    /**
     * The same sum per account.
     *
     * A transfer is two ordinary movements, so it falls out of this without a special case:
     * one account is down and the other up by the same amount, and [total] is unchanged.
     */
    fun balances(
        transactions: List<FundTxnEntity>,
        expenses: List<ExpenseEntity>,
        contributions: List<ContributionEntity>,
    ): Map<Long, Long> = buildMap {
        transactions.forEach { t ->
            val signed = if (t.kind == FundTxnKind.DEPOSIT.key) t.amountInr else -t.amountInr
            merge(t.accountId, signed, Long::plus)
        }
        expenses.forEach { e -> e.fundAccountId?.let { merge(it, -e.amountInr, Long::plus) } }
        contributions.forEach { c -> c.fundAccountId?.let { merge(it, -c.amountInr, Long::plus) } }
    }

    /**
     * Every movement through the fund on one timeline, newest first, whatever produced it —
     * so a line can be opened back into the sheet that wrote it.
     */
    fun ledger(
        transactions: List<FundTxnEntity>,
        expenses: List<ExpenseEntity>,
        contributions: List<ContributionEntity>,
        investments: List<InvestmentEntity>,
    ): List<FundLedgerRow> {
        val byId = investments.associateBy { it.id }
        return buildList {
            transactions.forEach { t ->
                val deposit = t.kind == FundTxnKind.DEPOSIT.key
                val transfer = t.transferGroup != null
                add(
                    FundLedgerRow(
                        id = "t${t.id}",
                        sourceId = t.id,
                        date = t.date,
                        title = when {
                            transfer && deposit -> "Transfer in"
                            transfer -> "Transfer out"
                            deposit -> "Deposit"
                            else -> "Withdrawal"
                        },
                        subtitle = t.note.orEmpty(),
                        amountInr = t.amountInr,
                        incoming = deposit,
                        icon = when {
                            transfer -> "swap_horiz"
                            deposit -> "payments"
                            else -> "shopping_bag"
                        },
                        origin = if (deposit) FundLedgerOrigin.DEPOSIT else FundLedgerOrigin.WITHDRAWAL,
                        accountId = t.accountId,
                        reconciled = t.reconciledOn != null,
                        transfer = transfer,
                    )
                )
            }
            expenses.filter { it.paidFromFund }.forEach { e ->
                add(
                    FundLedgerRow(
                        id = "e${e.id}",
                        sourceId = e.id,
                        date = e.date,
                        title = e.title,
                        subtitle = listOfNotNull(ExpenseCategory.of(e.category).label, e.vendor)
                            .joinToString(" · "),
                        amountInr = e.amountInr,
                        incoming = false,
                        icon = e.icon,
                        origin = FundLedgerOrigin.EXPENSE,
                        accountId = e.fundAccountId,
                    )
                )
            }
            contributions.filter { it.paidFromFund }.forEach { c ->
                val investment = byId[c.investmentId]
                add(
                    FundLedgerRow(
                        id = "c${c.id}",
                        sourceId = c.id,
                        date = c.date,
                        title = investment?.name ?: "Investment",
                        subtitle = investment?.let { InvestmentKind.of(it.kind).label }.orEmpty(),
                        amountInr = c.amountInr,
                        incoming = false,
                        icon = "savings",
                        origin = FundLedgerOrigin.INVESTMENT,
                        accountId = c.fundAccountId,
                    )
                )
            }
        }.sortedWith(compareByDescending<FundLedgerRow> { it.date }.thenByDescending { it.id })
    }

    /**
     * Whether this month's standing top-up still needs making: a plan is set, the day has come
     * round, and no deposit has been recorded this month.
     */
    fun topUpDue(monthlyInr: Long, depositDay: Int, lastDeposit: LocalDate?, today: LocalDate): Boolean {
        if (monthlyInr <= 0) return false
        if (today.dayOfMonth < depositDay.coerceIn(1, 28)) return false
        return lastDeposit == null || lastDeposit.year != today.year || lastDeposit.month != today.month
    }
}
