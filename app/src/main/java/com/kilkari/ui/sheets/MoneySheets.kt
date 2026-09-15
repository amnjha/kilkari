package com.kilkari.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.domain.Currency
import com.kilkari.domain.Fmt
import com.kilkari.domain.InvestmentKind
import com.kilkari.domain.InvestmentSummary
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.components.SheetStatic
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

private val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val number = KeyboardOptions(keyboardType = KeyboardType.Number)

/** Reusable "came out of the savings account" switch. */
@Composable
private fun FromFundToggle(fundName: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Paid from $fundName", fontFamily = Sans,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink,
            )
            Text(
                "Comes off the balance",
                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
            )
        }
        KSwitch(checked)
    }
}

/** Money in or out of the savings account, independent of any expense. */
@Composable
fun ColumnScope.FundTxnSheet(
    currency: Currency,
    fundName: String,
    suggestedDeposit: Long,
    onSave: (deposit: Boolean, amount: Double, date: LocalDate, note: String?) -> Unit,
) {
    var kindIndex by remember { mutableIntStateOf(0) }
    var amount by remember {
        mutableStateOf(if (suggestedDeposit > 0) Fmt.plain(suggestedDeposit, currency) else "")
    }
    var note by remember { mutableStateOf("") }

    val deposit = kindIndex == 0

    SheetTitle(if (deposit) "Add to $fundName" else "Take out of $fundName")
    KSegmented(listOf("Deposit", "Withdrawal"), kindIndex) { kindIndex = it }
    SheetField("Amount (${currency.symbol})", amount, "0", decimal, big = true) { amount = it }
    SheetField("Note", note, if (deposit) "Monthly top-up" else "What it was for") { note = it }
    SheetStatic("Date", "Today, ${Fmt.date(LocalDate.now())}")

    val value = amount.toDoubleOrNull()
    PrimaryButton(
        if (deposit) "Record deposit" else "Record withdrawal",
        enabled = value != null && value > 0,
    ) {
        onSave(deposit, value!!, LocalDate.now(), note.trim().ifBlank { null })
    }
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
        paidFromFund: Boolean,
    ) -> Unit,
) {
    var kind by remember { mutableStateOf(InvestmentKind.SIP) }
    var name by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf("") }
    var maturityText by remember { mutableStateOf("") }
    var maturityValue by remember { mutableStateOf("") }
    var paidFromFund by remember { mutableStateOf(true) }

    val start = remember(startText) { parseDayMonthYear(startText) } ?: LocalDate.now()
    val maturity = remember(maturityText) { parseDayMonthYear(maturityText) }
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
    SheetField("Started", startText, "DD-MM-YYYY · today if blank", number) { startText = it }
    SheetField("Matures", maturityText, "DD-MM-YYYY · optional", number) { maturityText = it }
    if (!kind.recurring) {
        SheetField(
            "Value at maturity (${currency.symbol})", maturityValue, "Optional", decimal,
        ) { maturityValue = it }
    }
    FromFundToggle(fundName, paidFromFund) { paidFromFund = !paidFromFund }

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
        )
    }
}

/** Actions on an existing holding: log an instalment, restate its value, or close it. */
@Composable
fun ColumnScope.InvestmentDetailSheet(
    investment: InvestmentSummary,
    currency: Currency,
    fundName: String,
    onContribute: (amount: Double, paidFromFund: Boolean) -> Unit,
    onUpdateValue: (value: Double) -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var amount by remember {
        mutableStateOf(investment.monthlyInr?.let { Fmt.plain(it, currency) } ?: "")
    }
    var newValue by remember { mutableStateOf("") }
    var paidFromFund by remember { mutableStateOf(true) }

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

    SheetField("Add contribution (${currency.symbol})", amount, "0", decimal, big = true) { amount = it }
    FromFundToggle(fundName, paidFromFund) { paidFromFund = !paidFromFund }
    val contribution = amount.toDoubleOrNull()
    PrimaryButton("Record contribution", enabled = contribution != null && contribution > 0) {
        onContribute(contribution!!, paidFromFund)
    }

    SheetField("Update value to (${currency.symbol})", newValue, "0", decimal) { newValue = it }
    val updated = newValue.toDoubleOrNull()
    PrimaryButton("Save new value", enabled = updated != null && updated >= 0) {
        onUpdateValue(updated!!)
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
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Rose,
        )
    }
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
