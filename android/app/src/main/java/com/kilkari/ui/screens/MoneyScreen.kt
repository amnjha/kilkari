package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.ContributionEntity
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.FundAccountEntity
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.domain.Currency
import com.kilkari.domain.ExpenseCategory
import com.kilkari.domain.Fmt
import com.kilkari.domain.FundLedgerOrigin
import com.kilkari.domain.FundLedgerRow
import com.kilkari.domain.InvestmentKind
import com.kilkari.domain.InvestmentSummary
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.GradientCard
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KFab
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.SecondaryButton
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.sheets.ContributionSheet
import com.kilkari.ui.sheets.ExpenseSheet
import com.kilkari.ui.sheets.FundPlanSheet
import com.kilkari.ui.sheets.FundAccountSheet
import com.kilkari.ui.sheets.TransferSheet
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.sheets.FundSources
import com.kilkari.ui.sheets.FundTxnSheet
import com.kilkari.ui.sheets.InvestmentDetailSheet
import com.kilkari.ui.sheets.InvestmentSheet
import com.kilkari.ui.theme.KDepth
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private enum class MoneySheet { EXPENSE, FUND_TXN, FUND_PLAN, INVESTMENT, ACCOUNT, TRANSFER }

/** How much of the account's history the Fund view lists. */
private const val LEDGER_SHOWN = 60

/**
 * Three views over the same money: what has been **spent**, the savings account it is
 * **funded** from, and what is being **invested** for later.
 */
@Composable
fun MoneyScreen(vm: KilkariViewModel, go: NavActions) {
    val currency by vm.currency.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<MoneySheet?>(null) }
    var openInvestment by remember { mutableStateOf<InvestmentSummary?>(null) }

    // Each is non-null while the entry it holds is open for correction. The three are collected
    // here rather than in the views so a fund ledger line can be traced back to whichever of
    // them it came from.
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var editingTxn by remember { mutableStateOf<FundTxnEntity?>(null) }
    var editingContribution by remember { mutableStateOf<ContributionEntity?>(null) }

    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val fundTransactions by vm.fundTransactions.collectAsStateWithLifecycle()
    val contributions by vm.contributions.collectAsStateWithLifecycle()
    val investments by vm.investmentSummaries.collectAsStateWithLifecycle()

    val pendingEntry by vm.pendingEntry.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEntry) {
        if (pendingEntry == "fund") {
            tab = 1
            sheet = MoneySheet.FUND_TXN
            vm.consumeEntry()
        }
    }

    val fundName = settings.fundAccountName
    val accounts by vm.fundAccounts.collectAsStateWithLifecycle()
    val balances by vm.fundBalances.collectAsStateWithLifecycle()
    val sources = FundSources(accounts, balances, fundName)
    var editingAccount by remember { mutableStateOf<FundAccountEntity?>(null) }

    fun openLedgerRow(row: FundLedgerRow) {
        when (row.origin) {
            FundLedgerOrigin.DEPOSIT, FundLedgerOrigin.WITHDRAWAL ->
                editingTxn = fundTransactions.firstOrNull { it.id == row.sourceId }
            FundLedgerOrigin.EXPENSE ->
                editingExpense = expenses.firstOrNull { it.id == row.sourceId }
            FundLedgerOrigin.INVESTMENT ->
                editingContribution = contributions.firstOrNull { it.id == row.sourceId }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Money", style = ScreenTitle, color = KC.Ink)
                    Text(
                        YearMonth.now().month.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    )
                }
                KSegmented(listOf("Spending", "Fund", "Invest"), tab) { tab = it }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = KDepth.navClearance),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    1 -> FundView(
                        vm, currency, fundName,
                        accounts = accounts,
                        balances = balances,
                        unreconciled = fundTransactions.count { it.reconciledOn == null },
                        onEditPlan = { sheet = MoneySheet.FUND_PLAN },
                        onAddAccount = { editingAccount = null; sheet = MoneySheet.ACCOUNT },
                        onEditAccount = { editingAccount = it; sheet = MoneySheet.ACCOUNT },
                        onTransfer = { sheet = MoneySheet.TRANSFER },
                        onReconcile = { go.push(Routes.RECONCILE) },
                        onOpenRow = { openLedgerRow(it) },
                    )
                    2 -> InvestView(vm, currency) { openInvestment = it }
                    else -> SpendingView(vm, currency, fundName) { editingExpense = it }
                }
            }
        }

        KFab(if (tab == 2) "savings" else "add") {
            sheet = when (tab) {
                1 -> MoneySheet.FUND_TXN
                2 -> MoneySheet.INVESTMENT
                else -> MoneySheet.EXPENSE
            }
        }

        KSheet(sheet != null, onDismiss = { sheet = null }) {
            when (sheet) {
                MoneySheet.EXPENSE -> ExpenseSheet(currency, fundName, sources) {
                    title, vendor, category, amount, date, fromFund, accountId ->
                    vm.addExpense(title, vendor, category, amount, date, fromFund, accountId)
                    sheet = null
                }
                MoneySheet.FUND_TXN -> FundTxnSheet(
                    currency = currency,
                    fundName = fundName,
                    suggestedDeposit = settings.fundMonthlyInr,
                    sources = sources,
                ) { deposit, amount, date, note, accountId ->
                    if (deposit) vm.addFundDeposit(amount, date, note, accountId)
                    else vm.addFundWithdrawal(amount, date, note, accountId)
                    sheet = null
                }
                MoneySheet.FUND_PLAN -> FundPlanSheet(
                    currency = currency,
                    currentMonthly = settings.fundMonthlyInr,
                    currentDay = settings.fundDepositDay,
                    currentName = fundName,
                ) { monthly, day, name ->
                    vm.setFundPlan(monthly, day, name)
                    sheet = null
                }
                MoneySheet.INVESTMENT -> InvestmentSheet(currency, fundName, sources) {
                    name, kind, institution, opening, monthly, rate, start, maturity, maturityValue,
                    fromFund, accountId ->
                    vm.addInvestment(
                        name, kind, institution, opening, monthly, rate,
                        start, maturity, maturityValue, fromFund, accountId,
                    )
                    sheet = null
                }
                MoneySheet.ACCOUNT -> {
                    val account = editingAccount
                    FundAccountSheet(
                        existing = account,
                        onDelete = account?.let {
                            { vm.deleteFundAccount(it); editingAccount = null; sheet = null }
                        },
                    ) { name, note, archived ->
                        if (account == null) vm.addFundAccount(name, note)
                        else vm.updateFundAccount(account, name, note, archived)
                        editingAccount = null
                        sheet = null
                    }
                }
                MoneySheet.TRANSFER -> TransferSheet(
                    accounts = accounts.filterNot { it.archived },
                    currency = currency,
                    balances = balances,
                ) { fromId, toId, amount, date, note ->
                    vm.transferBetweenAccounts(fromId, toId, amount, date, note)
                    sheet = null
                }
                null -> Unit
            }
        }

        val open = openInvestment
        KSheet(open != null, onDismiss = { openInvestment = null }) {
            if (open != null) {
                InvestmentDetailSheet(
                    investment = open,
                    currency = currency,
                    fundName = fundName,
                    contributions = contributions.filter { it.investmentId == open.id },
                    sources = sources,
                    onContribute = { amount, date, fromFund, accountId ->
                        vm.addContribution(open.id, amount, date, fromFund, accountId)
                        openInvestment = null
                    },
                    onUpdateValue = { value, asOf ->
                        vm.updateInvestmentValue(open.id, value, asOf)
                        openInvestment = null
                    },
                    // Sheets do not stack, so the holding closes as the contribution opens.
                    onEditContribution = { row ->
                        openInvestment = null
                        editingContribution = row
                    },
                    onSetActive = { active ->
                        vm.setInvestmentActive(open.id, active)
                        openInvestment = null
                    },
                    onDelete = {
                        vm.deleteInvestment(open.id)
                        openInvestment = null
                    },
                )
            }
        }

        val expense = editingExpense
        KSheet(expense != null, onDismiss = { editingExpense = null }) {
            if (expense != null) {
                ExpenseSheet(
                    currency = currency,
                    fundName = fundName,
                    sources = sources,
                    existing = expense,
                    onDelete = { vm.deleteExpense(expense); editingExpense = null },
                ) { title, vendor, category, amount, date, fromFund, accountId ->
                    vm.updateExpense(expense, title, vendor, category, amount, date, fromFund, accountId)
                    editingExpense = null
                }
            }
        }

        val txn = editingTxn
        KSheet(txn != null, onDismiss = { editingTxn = null }) {
            if (txn != null) {
                FundTxnSheet(
                    currency = currency,
                    fundName = fundName,
                    suggestedDeposit = 0,
                    sources = sources,
                    existing = txn,
                    onDelete = { vm.deleteFundTransaction(txn); editingTxn = null },
                ) { deposit, amount, date, note, accountId ->
                    vm.updateFundTransaction(txn, deposit, amount, date, note, accountId)
                    editingTxn = null
                }
            }
        }

        val contribution = editingContribution
        KSheet(contribution != null, onDismiss = { editingContribution = null }) {
            if (contribution != null) {
                val holding = investments.firstOrNull { it.id == contribution.investmentId }
                ContributionSheet(
                    contribution = contribution,
                    investmentName = holding?.name ?: "Investment",
                    currency = currency,
                    fundName = fundName,
                    earliest = holding?.startDate,
                    sources = sources,
                    onDelete = { vm.deleteContribution(contribution); editingContribution = null },
                ) { amount, date, fromFund, accountId ->
                    vm.updateContribution(contribution, amount, date, fromFund, accountId)
                    editingContribution = null
                }
            }
        }
    }
}

// ── Spending ────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.SpendingView(
    vm: KilkariViewModel,
    currency: Currency,
    fundName: String,
    onOpen: (ExpenseEntity) -> Unit,
) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val filter by vm.moneyFilter.collectAsStateWithLifecycle()
    val allTime by vm.moneyAllTime.collectAsStateWithLifecycle()

    val thisMonth = YearMonth.now()
    val earliest = expenses.minOfOrNull { YearMonth.from(it.date) } ?: thisMonth

    val counted = if (allTime) expenses else expenses.filter { YearMonth.from(it.date) == thisMonth }
    val medical = counted.filter { it.category == ExpenseCategory.MEDICAL.key }.sumOf { it.amountInr }
    val general = counted.filter { it.category == ExpenseCategory.GENERAL.key }.sumOf { it.amountInr }
    val total = medical + general
    val medFraction = if (total == 0L) 0f else medical.toFloat() / total

    // The category chips narrow the list; the headline above keeps counting everything, so the
    // two numbers on screen never look like they disagree about the same month.
    val visible = expenses.filter { filter == null || it.category == filter?.key }
    val byMonth = visible.groupBy { YearMonth.from(it.date) }
    val current = byMonth[thisMonth].orEmpty()
    val previousMonths = byMonth.keys.filter { it != thisMonth }.sortedDescending()

    KCard(corner = 20) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        if (allTime) "Spent since you started" else "Spent this month",
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    )
                    Text(
                        Fmt.money(total, currency),
                        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp, color = KC.Ink, letterSpacing = (-0.64).sp,
                    )
                }
                KChip(if (allTime) "This month" else "All time", false) {
                    vm.setMoneyAllTime(!allTime)
                }
            }
            SpendFootnote(expenses, allTime, thisMonth, earliest, total, currency)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(KC.CoralBg),
            ) {
                if (total > 0) {
                    Box(Modifier.fillMaxWidth(medFraction).height(10.dp).background(KC.Clay))
                    Box(Modifier.weight(1f).height(10.dp).background(KC.SeaLight))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("Medical", Fmt.money(medical, currency), KC.Clay)
                Legend("General", Fmt.money(general, currency), KC.SeaLight)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KChip("All", filter == null) { vm.setMoneyFilter(null) }
        KChip("Medical", filter == ExpenseCategory.MEDICAL) { vm.setMoneyFilter(ExpenseCategory.MEDICAL) }
        KChip("General", filter == ExpenseCategory.GENERAL) { vm.setMoneyFilter(ExpenseCategory.GENERAL) }
    }

    SectionLabel("This month")
    KCard {
        if (current.isEmpty()) {
            EmptyLine(
                if (expenses.isEmpty()) "No expenses yet. Tap + to add one."
                else "Nothing this month."
            )
        }
        current.forEachIndexed { i, expense ->
            ExpenseRow(expense, currency, fundName) { onOpen(expense) }
            if (i != current.lastIndex) Divider()
        }
    }

    if (previousMonths.isNotEmpty()) {
        // Collapsed by default: the point of the section is the monthly totals, and opening
        // every month at once would bury them under a year of individual rows.
        val expanded = remember { mutableStateMapOf<YearMonth, Boolean>() }

        SectionLabel("Previous months")
        KCard {
            previousMonths.forEachIndexed { i, month ->
                val items = byMonth.getValue(month)
                val open = expanded[month] == true
                MonthGroupHeader(
                    month = month,
                    total = items.sumOf { it.amountInr },
                    count = items.size,
                    expanded = open,
                    currency = currency,
                ) { expanded[month] = !open }

                if (open) {
                    items.forEach { expense ->
                        Divider()
                        ExpenseRow(expense, currency, fundName) { onOpen(expense) }
                    }
                }
                if (i != previousMonths.lastIndex) Divider()
            }
        }
    }

    if (visible.isNotEmpty()) {
        Text(
            "Tap an expense to change or remove it.",
            fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
        )
    }
}

/**
 * The line under the headline: across all time, how much of the total there is per month —
 * the figure that says whether a large number is large or merely old. For a single month, what
 * the month before came to, so the headline has something to be bigger or smaller than.
 */
@Composable
private fun SpendFootnote(
    expenses: List<ExpenseEntity>,
    allTime: Boolean,
    thisMonth: YearMonth,
    earliest: YearMonth,
    total: Long,
    currency: Currency,
) {
    if (expenses.isEmpty()) return
    val text = if (allTime) {
        val months = ChronoUnit.MONTHS.between(earliest, thisMonth).toInt() + 1
        "${expenses.size} ${Fmt.plural(expenses.size.toLong(), "expense")} over " +
            "$months ${Fmt.plural(months.toLong(), "month")} · " +
            "${Fmt.money(total / months, currency)} a month"
    } else {
        val previous = thisMonth.minusMonths(1)
        val before = expenses.filter { YearMonth.from(it.date) == previous }.sumOf { it.amountInr }
        "${Fmt.money(before, currency)} in ${Fmt.monthYear(previous)}"
    }
    Text(text, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
}

/** A past month: its total is the point, the expenses behind it are opened only on request. */
@Composable
private fun MonthGroupHeader(
    month: YearMonth,
    total: Long,
    count: Int,
    expanded: Boolean,
    currency: Currency,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            KIcons[if (expanded) "expand_more" else "chevron_right"],
            contentDescription = null,
            tint = KC.Faint,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                Fmt.monthYear(month),
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink,
            )
            Text(
                "$count ${Fmt.plural(count.toLong(), "expense")}",
                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
            )
        }
        Text(
            Fmt.money(total, currency),
            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Ink,
        )
    }
}

/** One expense, in whichever month's group it belongs to. */
@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
    currency: Currency,
    fundName: String,
    onOpen: () -> Unit,
) {
    val isMedical = expense.category == ExpenseCategory.MEDICAL.key
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            expense.icon,
            if (isMedical) KC.Clay else KC.SeaMid,
            if (isMedical) KC.ClayBg else KC.SeaBg,
            size = 36, corner = 10, iconSize = 20,
        )
        Column(Modifier.weight(1f)) {
            Text(
                expense.title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = KC.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    Fmt.date(expense.date),
                    expense.vendor,
                    if (expense.paidFromFund) fundName else null,
                ).joinToString(" · "),
                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            Fmt.money(expense.amountInr, currency),
            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Ink,
        )
    }
}



// ── Fund ────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.FundView(
    vm: KilkariViewModel,
    currency: Currency,
    fundName: String,
    accounts: List<FundAccountEntity>,
    balances: Map<Long, Long>,
    unreconciled: Int,
    onEditPlan: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (FundAccountEntity) -> Unit,
    onTransfer: () -> Unit,
    onReconcile: () -> Unit,
    onOpenRow: (FundLedgerRow) -> Unit,
) {
    val balance by vm.fundBalance.collectAsStateWithLifecycle()
    val ledger by vm.fundLedger.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val month = YearMonth.now()
    val inThisMonth = ledger.filter { it.incoming && YearMonth.from(it.date) == month }.sumOf { it.amountInr }
    val outThisMonth = ledger.filter { !it.incoming && YearMonth.from(it.date) == month }.sumOf { it.amountInr }
    val depositedThisMonth = ledger.any {
        it.origin == FundLedgerOrigin.DEPOSIT && YearMonth.from(it.date) == month
    }

    GradientCard(listOf(KC.CoralDeep, KC.Clay)) {
        Text(
            fundName.uppercase(),
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            letterSpacing = 0.5.sp, color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            Fmt.money(balance, currency),
            fontFamily = Display, fontWeight = FontWeight.ExtraBold,
            fontSize = 36.sp, color = Color.White, letterSpacing = (-0.72).sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FundStat("In this month", Fmt.money(inThisMonth, currency), Modifier.weight(1f))
            FundStat("Out this month", Fmt.money(outThisMonth, currency), Modifier.weight(1f))
        }
        if (balance < 0) {
            Text(
                "More has gone out than in — record the deposits you have made.",
                fontFamily = Sans, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f),
            )
        }
    }

    KCard(onClick = onEditPlan) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge("calendar_month", KC.CoralDeep, KC.CoralBg)
            Column(Modifier.weight(1f)) {
                Text(
                    if (settings.fundMonthlyInr > 0) {
                        "${Fmt.money(settings.fundMonthlyInr, currency)} a month"
                    } else {
                        "Set a monthly plan"
                    },
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KC.Ink,
                )
                Text(
                    if (settings.fundMonthlyInr > 0) {
                        depositStatus(settings.fundDepositDay, depositedThisMonth)
                    } else {
                        "How much you move in each month"
                    },
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
            Icon(KIcons["chevron_right"], null, tint = KC.CoralPaler, modifier = Modifier.size(20.dp))
        }
    }

    // Accounts appear once there is more than one, or on demand. A family with a single
    // account never has to think about the idea at all.
    val open = accounts.filterNot { it.archived }
    val archived = accounts.filter { it.archived }
    if (accounts.size > 1) {
        SectionLabel("Accounts")
        KCard {
            (open + archived).forEachIndexed { i, account ->
                AccountRow(
                    name = account.name,
                    note = account.note,
                    archived = account.archived,
                    balance = balances[account.id] ?: 0,
                    currency = currency,
                ) { onEditAccount(account) }
                if (i != accounts.lastIndex) Divider()
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Add account", Modifier.weight(1f), icon = "account_balance") { onAddAccount() }
        if (open.size > 1) {
            SecondaryButton("Transfer", Modifier.weight(1f), icon = "swap_horiz") { onTransfer() }
        }
    }

    KCard(onClick = onReconcile) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge("check_circle", KC.SeaDeep, KC.SeaBg)
            Column(Modifier.weight(1f)) {
                Text(
                    "Check against a statement",
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KC.Ink,
                )
                Text(
                    unreconciled.let { n ->
                        if (n == 0) "Everything recorded is ticked off"
                        else "$n ${Fmt.plural(n.toLong(), "line")} not ticked off yet"
                    },
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
            Icon(KIcons["chevron_right"], null, tint = KC.CoralPaler, modifier = Modifier.size(20.dp))
        }
    }

    SectionLabel("Account activity")
    KCard {
        if (ledger.isEmpty()) {
            EmptyLine("Nothing yet. Tap + to record a deposit.")
        }
        val visible = ledger.take(LEDGER_SHOWN)
        visible.forEachIndexed { i, row ->
            LedgerRow(row, currency) { onOpenRow(row) }
            if (i != visible.lastIndex) Divider()
        }
    }

    Text(
        "Tap any line to change or remove it. Expenses and investment contributions marked as " +
            "paid from $fundName come off this balance automatically — they are not recorded twice.",
        fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
    )
}

@Composable
private fun FundStat(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(caption, fontFamily = Sans, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
        Text(
            value, fontFamily = Sans, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, color = Color.White, maxLines = 1,
        )
    }
}

@Composable
private fun LedgerRow(row: FundLedgerRow, currency: Currency, onClick: () -> Unit) {
    val tint = when (row.origin) {
        FundLedgerOrigin.DEPOSIT -> KC.Teal
        FundLedgerOrigin.WITHDRAWAL -> KC.Danger
        FundLedgerOrigin.EXPENSE -> KC.SeaMid
        FundLedgerOrigin.INVESTMENT -> KC.Clay
    }
    val background = when (row.origin) {
        FundLedgerOrigin.DEPOSIT -> KC.TealBg
        FundLedgerOrigin.WITHDRAWAL -> KC.DangerBg
        FundLedgerOrigin.EXPENSE -> KC.SeaBg
        FundLedgerOrigin.INVESTMENT -> KC.ClayBg
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(row.icon, tint, background, size = 36, corner = 10, iconSize = 20)
        Column(Modifier.weight(1f)) {
            Text(
                row.title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = KC.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(Fmt.date(row.date), row.subtitle.ifBlank { null }).joinToString(" · "),
                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (row.incoming) "+" else "−") + Fmt.money(row.amountInr, currency),
            fontFamily = Sans, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, color = if (row.incoming) KC.Teal else KC.Ink,
        )
    }
}

/**
 * Where the standing deposit stands this month: done, due, or late. Once this month's deposit
 * is recorded the line looks ahead to next month instead.
 */
private fun depositStatus(
    day: Int,
    depositedThisMonth: Boolean,
    today: LocalDate = LocalDate.now(),
): String {
    val dueThisMonth = today.withDayOfMonth(day.coerceIn(1, 28))
    return when {
        depositedThisMonth -> "Done this month · next ${Fmt.date(dueThisMonth.plusMonths(1))}"
        dueThisMonth.isBefore(today) -> "Overdue since ${Fmt.date(dueThisMonth)}"
        dueThisMonth == today -> "Due today"
        else -> "Due ${Fmt.date(dueThisMonth)}"
    }
}

// ── Investments ─────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.InvestView(
    vm: KilkariViewModel,
    currency: Currency,
    onOpen: (InvestmentSummary) -> Unit,
) {
    val investments by vm.investmentSummaries.collectAsStateWithLifecycle()

    val active = investments.filter { it.active }
    val closed = investments.filterNot { it.active }
    val invested = investments.sumOf { it.investedInr }
    val value = investments.sumOf { it.valueInr }
    val gain = value - invested
    val monthly = active.filter { it.kind.recurring }.sumOf { it.monthlyInr ?: 0 }

    KCard(corner = 20) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Invested for the future", fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
                Text(
                    Fmt.money(value, currency),
                    fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp, color = KC.Ink, letterSpacing = (-0.64).sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("Put in", Fmt.money(invested, currency), KC.Coral)
                if (gain != 0L) {
                    Legend(
                        if (gain > 0) "Gain" else "Down",
                        Fmt.money(kotlin.math.abs(gain), currency),
                        if (gain > 0) KC.Teal else KC.Danger,
                    )
                }
            }
            if (monthly > 0) {
                Text(
                    buildString {
                        val plans = active.count { it.kind.recurring }
                        append(Fmt.money(monthly, currency))
                        append(" a month across ")
                        append(plans)
                        append(" recurring ")
                        append(Fmt.plural(plans.toLong(), "plan"))
                    },
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
        }
    }

    if (investments.isEmpty()) {
        KCard {
            EmptyLine("Nothing yet. Tap the button to add an FD, SIP, PPF, Sukanya Samriddhi or anything else.")
        }
    }

    if (active.isNotEmpty()) {
        SectionLabel("Active")
        active.forEach { InvestmentCard(it, currency) { onOpen(it) } }
    }
    if (closed.isNotEmpty()) {
        SectionLabel("Closed")
        closed.forEach { InvestmentCard(it, currency) { onOpen(it) } }
    }
}

@Composable
private fun InvestmentCard(item: InvestmentSummary, currency: Currency, onClick: () -> Unit) {
    val tint = kindTint(item.kind)
    KCard(onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge("savings", tint.first, tint.second)
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name, fontFamily = Sans, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = KC.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            item.kind.label,
                            item.institution,
                            item.interestRate?.let { "${Fmt.trimNum(it)}%" },
                        ).joinToString(" · "),
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Fmt.money(item.valueInr, currency),
                        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KC.Ink,
                    )
                    item.gainInr?.takeIf { it != 0L }?.let { gain ->
                        Text(
                            (if (gain > 0) "+" else "−") + Fmt.money(kotlin.math.abs(gain), currency),
                            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                            color = if (gain > 0) KC.Teal else KC.Danger,
                        )
                    }
                    // Annualised, so a plan paid monthly can be read against a lump sum.
                    item.annualReturn?.let { rate ->
                        Text(
                            "${Fmt.percent(rate)} a year",
                            fontFamily = Sans, fontSize = 11.sp, color = KC.Muted,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    listOfNotNull(
                        item.monthlyInr?.let { "${Fmt.money(it, currency)}/mo" },
                        item.maturityDate?.let { "matures ${Fmt.date(it)}" },
                    ).joinToString(" · ").ifBlank { "Since ${Fmt.date(item.startDate)}" },
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
                if (item.active && item.kind.recurring) {
                    Text(
                        if (item.contributedThisMonth) "Paid this month" else "Due this month",
                        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        color = if (item.contributedThisMonth) KC.Teal else KC.Gold,
                    )
                }
            }
        }
    }
}

private fun kindTint(kind: InvestmentKind): Pair<Color, Color> = when (kind) {
    InvestmentKind.FD, InvestmentKind.RD -> KC.CoralDeep to KC.CoralBg
    InvestmentKind.SIP -> KC.Clay to KC.ClayBg
    InvestmentKind.PPF -> KC.TealDeep to KC.TealBg
    InvestmentKind.SSY -> KC.GoldDeep to KC.GoldBg
    InvestmentKind.GOLD -> KC.GoldDeep to KC.GoldBg
    InvestmentKind.OTHER -> KC.Stone to KC.StoneBg
}

// ── Shared ──────────────────────────────────────────────────────────────────

@Composable
private fun Legend(label: String, value: String, swatch: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(swatch))
        Text(label, fontFamily = Sans, fontSize = 13.sp, color = KC.Ink)
        Text(value, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KC.Ink)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        modifier = Modifier.padding(14.dp),
        fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
}

/** An account and what is in it, on the Fund view. */
@Composable
private fun AccountRow(
    name: String,
    note: String?,
    archived: Boolean,
    balance: Long,
    currency: Currency,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            "account_balance",
            if (archived) KC.Muted else KC.CoralDeep,
            if (archived) KC.StoneBg else KC.CoralBg,
            size = 36, corner = 10, iconSize = 18,
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (archived) "$name · archived" else name,
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (archived) KC.Muted else KC.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            Fmt.money(balance, currency),
            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            color = if (balance < 0) KC.Danger else KC.Ink,
        )
    }
}
