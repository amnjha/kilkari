package com.kilkari.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.data.db.ContributionEntity
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.data.db.FundAccountEntity
import com.kilkari.ui.components.RadioDot
import com.kilkari.domain.Currency
import com.kilkari.domain.Fmt
import com.kilkari.domain.FundTxnKind
import com.kilkari.domain.InvestmentKind
import com.kilkari.domain.InvestmentSummary
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.components.SheetStatic
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

private val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val number = KeyboardOptions(keyboardType = KeyboardType.Number)

/** Reusable "came out of the savings account" switch. */
/**
 * The accounts a sheet can spend from, and what is in them. Passed as one thing so every money
 * sheet asks the same question the same way.
 */
data class FundSources(
    val accounts: List<FundAccountEntity> = emptyList(),
    val balances: Map<Long, Long> = emptyMap(),
    val fundName: String = "the fund",
)

@Composable
/**
 * Where the money came from: one of the fund's accounts, or somewhere else entirely.
 *
 * With a single account this is the switch it has always been — a family that never opens a
 * second one should not have to meet the idea of accounts. With more than one, the accounts
 * appear underneath it, because "paid from the fund" no longer says which.
 */
fun ColumnScope.FundSourceField(
    sources: FundSources,
    currency: Currency,
    fromFund: Boolean,
    accountId: Long?,
    onChange: (fromFund: Boolean, accountId: Long?) -> Unit,
) {
    val fundName = sources.fundName
    val balances = sources.balances
    val named = sources.accounts.filterNot { it.archived }.ifEmpty { sources.accounts }
    val chosen = accountId ?: named.firstOrNull()?.id

    SheetToggleRow(
        title = if (named.size > 1) "Paid from the fund" else "Paid from $fundName",
        subtitle = "Comes off the balance",
        checked = fromFund,
    ) { onChange(!fromFund, chosen) }

    if (fromFund && named.size > 1) {
        named.forEach { account ->
            SheetChoiceRow(
                title = account.name,
                subtitle = Fmt.money(balances[account.id] ?: 0, currency),
                selected = account.id == chosen,
                onPick = { onChange(true, account.id) },
            ) { RadioDot(account.id == chosen) }
        }
    }
}

/**
 * Money in or out of the savings account, independent of any expense. With [existing] the same
 * form reopens a movement already recorded — including switching it between the two directions.
 */
@Composable
fun ColumnScope.FundTxnSheet(
    currency: Currency,
    fundName: String,
    suggestedDeposit: Long,
    sources: FundSources = FundSources(),
    existing: FundTxnEntity? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (deposit: Boolean, amount: Double, date: LocalDate, note: String?, accountId: Long?) -> Unit,
) {
    var kindIndex by remember(existing) {
        mutableIntStateOf(if (FundTxnKind.of(existing?.kind) == FundTxnKind.WITHDRAWAL) 1 else 0)
    }
    var amount by remember(existing) {
        mutableStateOf(
            when {
                existing != null -> Fmt.plain(existing.amountInr, currency)
                suggestedDeposit > 0 -> Fmt.plain(suggestedDeposit, currency)
                else -> ""
            }
        )
    }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    var date by remember(existing) { mutableStateOf(existing?.date ?: LocalDate.now()) }
    val open = sources.accounts.filterNot { it.archived }
    var accountId by remember(existing, sources.accounts) {
        mutableStateOf(existing?.accountId ?: open.firstOrNull()?.id)
    }

    val deposit = kindIndex == 0

    SheetTitle(
        when {
            existing != null -> if (deposit) "Edit deposit" else "Edit withdrawal"
            deposit -> "Add to $fundName"
            else -> "Take out of $fundName"
        }
    )
    KSegmented(listOf("Deposit", "Withdrawal"), kindIndex) { kindIndex = it }
    SheetField("Amount (${currency.symbol})", amount, "0", decimal, big = true) { amount = it }
    SheetField("Note", note, if (deposit) "Monthly top-up" else "What it was for") { note = it }
    MovementDateField("Date", date) { date = it }

    // Only worth asking once there is more than one place the money could sit.
    if (open.size > 1) {
        SheetLabel(if (deposit) "Into" else "Out of")
        open.forEach { account ->
            SheetChoiceRow(
                title = account.name,
                subtitle = Fmt.money(sources.balances[account.id] ?: 0, currency),
                selected = account.id == accountId,
                onPick = { accountId = account.id },
            ) { RadioDot(account.id == accountId) }
        }
    }

    val value = amount.toDoubleOrNull()
    PrimaryButton(
        when {
            existing != null -> "Save changes"
            deposit -> "Record deposit"
            else -> "Record withdrawal"
        },
        enabled = value != null && value > 0,
    ) {
        onSave(deposit, value!!, date, note.trim().ifBlank { null }, accountId)
    }
    if (onDelete != null) SheetDelete("Delete this entry", onDelete)
}

/**
 * A contribution already recorded against a holding. Correcting one moves both the invested
 * total and — where it came out of the fund — the fund balance, since neither is mirrored.
 */
@Composable
fun ColumnScope.ContributionSheet(
    contribution: ContributionEntity,
    investmentName: String,
    currency: Currency,
    fundName: String,
    earliest: LocalDate? = null,
    onDelete: () -> Unit,
    sources: FundSources = FundSources(),
    onSave: (amount: Double, date: LocalDate, fromFund: Boolean, accountId: Long?) -> Unit,
) {
    var amount by remember(contribution) {
        mutableStateOf(Fmt.plain(contribution.amountInr, currency))
    }
    var date by remember(contribution) { mutableStateOf(contribution.date) }
    var paidFromFund by remember(contribution) { mutableStateOf(contribution.paidFromFund) }
    var accountId by remember(contribution) { mutableStateOf(contribution.fundAccountId) }

    SheetTitle("Edit contribution")
    SheetHint(investmentName)
    SheetField("Amount (${currency.symbol})", amount, "0", decimal, big = true) { amount = it }
    MovementDateField("Paid on", date, earliest = earliest) { date = it }
    FundSourceField(sources, currency, paidFromFund, accountId) { on, id ->
        paidFromFund = on
        accountId = id
    }

    val value = amount.toDoubleOrNull()
    PrimaryButton("Save changes", enabled = value != null && value > 0) {
        onSave(value!!, date, paidFromFund, accountId)
    }
    SheetDelete("Delete this contribution", onDelete)
}

/** The standing monthly top-up: how much, on which day, and what to call the account. */
@Composable
fun ColumnScope.FundPlanSheet(
    currency: Currency,
    currentMonthly: Long,
    currentDay: Int,
    currentName: String,
    onSave: (monthly: Double, day: Int, name: String) -> Unit,
) {
    var amount by remember {
        mutableStateOf(if (currentMonthly > 0) Fmt.plain(currentMonthly, currency) else "")
    }
    var day by remember { mutableStateOf(currentDay.toString()) }
    var name by remember { mutableStateOf(currentName) }

    SheetTitle("Monthly plan")
    SheetHint("What you move into the account each month. Kilkari reminds you when it is due.")
    SheetField("Account name", name, "Baby fund") { name = it }
    SheetField("Amount (${currency.symbol})", amount, "0", decimal, big = true) { amount = it }
    SheetField("Day of month", day, "1", number) { day = it.filter(Char::isDigit).take(2) }

    val value = amount.toDoubleOrNull()
    val dayValue = day.toIntOrNull()
    PrimaryButton(
        "Save plan",
        enabled = value != null && value > 0 && dayValue != null && dayValue in 1..28,
    ) {
        onSave(value!!, dayValue!!, name.trim())
    }
}

/** Opens a holding. The fields shown follow the kind: recurring plans ask for an instalment. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.InvestmentSheet(
    currency: Currency,
    fundName: String,
    sources: FundSources = FundSources(),
    onSave: (
        name: String,
        kind: InvestmentKind,
        institution: String?,
        opening: Double?,
        monthly: Double?,
        rate: Double?,
        start: LocalDate,
        maturity: LocalDate?,
        maturityValue: Double?,
        fromFund: Boolean,
        accountId: Long?,
    ) -> Unit,
) {
    var kind by remember { mutableStateOf(InvestmentKind.SIP) }
    var name by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var maturity by remember { mutableStateOf<LocalDate?>(null) }
    var maturityValue by remember { mutableStateOf("") }
    var paidFromFund by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf<Long?>(null) }

    val value = amount.toDoubleOrNull()

    SheetTitle("Add an investment")
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InvestmentKind.entries.forEach { k -> KChip(k.label, kind == k) { kind = k } }
    }

    SheetField("Name", name, kindPlaceholder(kind)) { name = it }
    SheetField("Bank or fund house", institution, "Optional") { institution = it }
    SheetField(
        if (kind.recurring) "Monthly (${currency.symbol})" else "Amount (${currency.symbol})",
        amount, "0", decimal, big = true,
    ) { amount = it }
    SheetField("Interest rate (% p.a.)", rate, "Optional", decimal) { rate = it }
    KDateField(
        "Started", start, selectableTo = LocalDate.now(),
        format = { Fmt.relativeDate(it) },
    ) { start = it }
    KDateField("Matures", maturity, placeholder = "Optional") { maturity = it }
    if (!kind.recurring) {
        SheetField(
            "Value at maturity (${currency.symbol})", maturityValue, "Optional", decimal,
        ) { maturityValue = it }
    }
    FundSourceField(sources, currency, paidFromFund, accountId) { on, id ->
        paidFromFund = on
        accountId = id
    }

    PrimaryButton("Save investment", enabled = name.isNotBlank() && value != null && value > 0) {
        onSave(
            name.trim(),
            kind,
            institution.trim().ifBlank { null },
            if (kind.recurring) value else value,
            if (kind.recurring) value else null,
            rate.toDoubleOrNull(),
            start,
            maturity,
            maturityValue.toDoubleOrNull(),
            paidFromFund,
            accountId,
        )
    }
}

/**
 * Actions on an existing holding: log an instalment, restate its value, or close it. The
 * contribution ledger is listed because it *is* the invested total — a mistyped instalment is
 * corrected by opening its row, not by restating the value on top of it.
 */
@Composable
fun ColumnScope.InvestmentDetailSheet(
    investment: InvestmentSummary,
    currency: Currency,
    fundName: String,
    contributions: List<ContributionEntity> = emptyList(),
    sources: FundSources = FundSources(),
    onContribute: (amount: Double, date: LocalDate, fromFund: Boolean, accountId: Long?) -> Unit,
    onUpdateValue: (value: Double, asOf: LocalDate) -> Unit,
    onEditContribution: (ContributionEntity) -> Unit = {},
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var amount by remember {
        mutableStateOf(investment.monthlyInr?.let { Fmt.plain(it, currency) } ?: "")
    }
    var newValue by remember { mutableStateOf("") }
    var paidFromFund by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var contributedOn by remember { mutableStateOf(LocalDate.now()) }
    var valuedOn by remember { mutableStateOf(LocalDate.now()) }

    SheetTitle(investment.name)
    SheetHint(
        listOfNotNull(
            investment.kind.label,
            investment.institution,
            investment.interestRate?.let { "${Fmt.trimNum(it)}% p.a." },
        ).joinToString(" · ")
    )

    SheetStatic("Invested", Fmt.money(investment.investedInr, currency))
    investment.currentValueInr?.let { SheetStatic("Current value", Fmt.money(it, currency)) }
    investment.maturityValueInr?.let { SheetStatic("At maturity", Fmt.money(it, currency)) }
    investment.maturityDate?.let { SheetStatic("Matures", Fmt.dateFull(it)) }

    if (contributions.isNotEmpty()) {
        val shown = contributions.take(CONTRIBUTIONS_SHOWN)
        SheetHint("Contributions — tap one to change it")
        shown.forEach { row ->
            ContributionRow(row, currency, fundName) { onEditContribution(row) }
        }
        if (contributions.size > shown.size) {
            SheetHint("Showing the latest ${shown.size} of ${contributions.size}.")
        }
    }

    SheetField("Add contribution (${currency.symbol})", amount, "0", decimal, big = true) { amount = it }
    MovementDateField("Paid on", contributedOn, earliest = investment.startDate) { contributedOn = it }
    FundSourceField(sources, currency, paidFromFund, accountId) { on, id ->
        paidFromFund = on
        accountId = id
    }
    val contribution = amount.toDoubleOrNull()
    PrimaryButton("Record contribution", enabled = contribution != null && contribution > 0) {
        onContribute(contribution!!, contributedOn, paidFromFund, accountId)
    }

    SheetField("Update value to (${currency.symbol})", newValue, "0", decimal) { newValue = it }
    MovementDateField("Value as of", valuedOn, earliest = investment.startDate) { valuedOn = it }
    val updated = newValue.toDoubleOrNull()
    PrimaryButton("Save new value", enabled = updated != null && updated >= 0) {
        onUpdateValue(updated!!, valuedOn)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (investment.active) "Mark closed" else "Reopen",
            modifier = Modifier
                .weight(1f)
                .clickable { onSetActive(!investment.active) }
                .padding(vertical = 8.dp),
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Muted,
        )
        Text(
            "Delete",
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(vertical = 8.dp),
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Danger,
        )
    }
}

/** How much of a long contribution ledger a sheet shows before it stops being readable. */
private const val CONTRIBUTIONS_SHOWN = 12

/** One line of a holding's contribution ledger; tapping it opens that entry for editing. */
@Composable
private fun ContributionRow(
    contribution: ContributionEntity,
    currency: Currency,
    fundName: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KC.Screen)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOfNotNull(
                Fmt.date(contribution.date),
                if (contribution.paidFromFund) fundName else null,
            ).joinToString(" · "),
            fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
        )
        Text(
            Fmt.money(contribution.amountInr, currency),
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink,
        )
    }
}

/** Money moved on a day that may not be today: dates default to now and stop there. */
@Composable
private fun MovementDateField(
    label: String,
    value: LocalDate,
    earliest: LocalDate? = null,
    onPick: (LocalDate) -> Unit,
) {
    KDateField(
        label, value, selectableFrom = earliest, selectableTo = LocalDate.now(),
        format = { Fmt.relativeDate(it) }, onPick = onPick,
    )
}

private fun kindPlaceholder(kind: InvestmentKind) = when (kind) {
    InvestmentKind.FD -> "e.g. SBI 2-year FD"
    InvestmentKind.RD -> "e.g. Post office RD"
    InvestmentKind.SIP -> "e.g. Parag Parikh Flexi Cap"
    InvestmentKind.PPF -> "e.g. PPF account"
    InvestmentKind.SSY -> "e.g. Sukanya Samriddhi"
    InvestmentKind.GOLD -> "e.g. Sovereign gold bond"
    InvestmentKind.OTHER -> "What is it?"
}
