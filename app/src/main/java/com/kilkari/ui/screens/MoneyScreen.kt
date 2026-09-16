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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.ContributionEntity
import com.kilkari.data.db.ExpenseEntity
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
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.sheets.ContributionSheet
import com.kilkari.ui.sheets.ExpenseSheet
import com.kilkari.ui.sheets.FundPlanSheet
import com.kilkari.ui.sheets.FundTxnSheet
import com.kilkari.ui.sheets.InvestmentDetailSheet
import com.kilkari.ui.sheets.InvestmentSheet
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle
import java.time.LocalDate
import java.time.YearMonth

private enum class MoneySheet { EXPENSE, FUND_TXN, FUND_PLAN, INVESTMENT }

/** How much of the account's history the Fund view lists. */
private const val LEDGER_SHOWN = 60

/**
 * Three views over the same money: what has been **spent**, the savings account it is
 * **funded** from, and what is being **invested** for later.
 */
@Composable
fun MoneyScreen(vm: KilkariViewModel) {
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
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    1 -> FundView(
                        vm, currency, fundName,
                        onEditPlan = { sheet = MoneySheet.FUND_PLAN },
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
                MoneySheet.EXPENSE -> ExpenseSheet(currency, fundName) { title, vendor, category, amount, date, fromFund ->
                    vm.addExpense(title, vendor, category, amount, date, fromFund)
                    sheet = null
                }
                MoneySheet.FUND_TXN -> FundTxnSheet(
                    currency = currency,
                    fundName = fundName,
                    suggestedDeposit = settings.fundMonthlyInr,
                ) { deposit, amount, date, note ->
                    if (deposit) vm.addFundDeposit(amount, date, note)
                    else vm.addFundWithdrawal(amount, date, note)
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
                MoneySheet.INVESTMENT -> InvestmentSheet(currency, fundName) {
                    name, kind, institution, opening, monthly, rate, start, maturity, maturityValue, fromFund ->
                    vm.addInvestment(
                        name, kind, institution, opening, monthly, rate,
                        start, maturity, maturityValue, fromFund,
                    )
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
                    onContribute = { amount, date, fromFund ->
                        vm.addContribution(open.id, amount, date, fromFund)
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
                    existing = expense,
                    onDelete = { vm.deleteExpense(expense); editingExpense = null },
                ) { title, vendor, category, amount, date, fromFund ->
                    vm.updateExpense(expense, title, vendor, category, amount, date, fromFund)
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
                    existing = txn,
                    onDelete = { vm.deleteFundTransaction(txn); editingTxn = null },
                ) { deposit, amount, date, note ->
                    vm.updateFundTransaction(txn, deposit, amount, date, note)
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
                    onDelete = { vm.deleteContribution(contribution); editingContribution = null },
                ) { amount, date, fromFund ->
                    vm.updateContribution(contribution, amount, date, fromFund)
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

    val month = YearMonth.now()
    val thisMonth = expenses.filter { YearMonth.from(it.date) == month }
    val medical = thisMonth.filter { it.category == ExpenseCategory.MEDICAL.key }.sumOf { it.amountInr }
    val general = thisMonth.filter { it.category == ExpenseCategory.GENERAL.key }.sumOf { it.amountInr }
    val total = medical + general
    val medFraction = if (total == 0L) 0f else medical.toFloat() / total
    val visible = expenses.filter { filter == null || it.category == filter?.key }

    KCard(corner = 20) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Spent this month", fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
                Text(
                    Fmt.money(total, currency),
                    fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp, color = KC.Ink, letterSpacing = (-0.64).sp,
                )
            }
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

    KCard {
        if (visible.isEmpty()) {
            EmptyLine("No expenses yet. Tap + to add one.")
        }
        visible.forEachIndexed { i, expense ->
            val isMedical = expense.category == ExpenseCategory.MEDICAL.key
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(expense) }
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
            if (i != visible.lastIndex) Divider()
        }
    }

    if (visible.isNotEmpty()) {
        Text(
            "Tap an expense to change or remove it.",
            fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
        )
    }
}

// ── Fund ────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.FundView(
    vm: KilkariViewModel,
    currency: Currency,
    fundName: String,
    onEditPlan: () -> Unit,
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
