package com.kilkari.ui.sheets

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.kilkari.data.db.FundAccountEntity
import com.kilkari.domain.Currency
import com.kilkari.domain.Fmt
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.RadioDot
import com.kilkari.ui.components.SheetField
import java.time.LocalDate

/** Opening or correcting one of the accounts the child's money sits in. */
@Composable
fun ColumnScope.FundAccountSheet(
    existing: FundAccountEntity? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (name: String, note: String?, archived: Boolean) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    var archived by remember(existing) { mutableStateOf(existing?.archived ?: false) }

    SheetTitle(if (existing == null) "Add an account" else "Edit ${existing.name}")
    SheetHint("Where the money actually sits — a bank account, a gift envelope, a cash tin.")
    SheetField("Name", name, "e.g. Grandparents' gifts") { name = it }
    SheetField("Note", note, "Optional — bank, account number, whose it is") { note = it }

    if (existing != null) {
        SheetToggleRow(
            title = "Archived",
            subtitle = "Kept out of the way; its balance still counts towards the total",
            checked = archived,
        ) { archived = !archived }
    }

    PrimaryButton(
        if (existing == null) "Add account" else "Save changes",
        enabled = name.isNotBlank(),
    ) {
        onSave(name.trim(), note.trim().ifBlank { null }, archived)
    }
    if (onDelete != null) SheetDelete("Remove this account", onDelete)
}

/**
 * Moving money from one account to another.
 *
 * Recorded as a withdrawal and a matching deposit, so neither account's balance is anything
 * other than the sum of what went through it.
 */
@Composable
fun ColumnScope.TransferSheet(
    accounts: List<FundAccountEntity>,
    currency: Currency,
    balances: Map<Long, Long>,
    onSave: (fromId: Long, toId: Long, amount: Double, date: LocalDate, note: String?) -> Unit,
) {
    var fromId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var toId by remember(accounts) { mutableStateOf(accounts.getOrNull(1)?.id ?: 0L) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }

    SheetTitle("Move money between accounts")
    SheetHint("Nothing enters or leaves the fund — the total is unchanged.")

    SheetLabel("From")
    accounts.forEach { account ->
        AccountChoiceRow(
            account = account,
            balance = balances[account.id] ?: 0,
            currency = currency,
            selected = account.id == fromId,
        ) {
            fromId = account.id
            if (toId == account.id) toId = accounts.firstOrNull { it.id != account.id }?.id ?: 0L
        }
    }

    SheetLabel("To")
    accounts.filter { it.id != fromId }.forEach { account ->
        AccountChoiceRow(
            account = account,
            balance = balances[account.id] ?: 0,
            currency = currency,
            selected = account.id == toId,
        ) { toId = account.id }
    }

    SheetField(
        "Amount (${currency.symbol})", amount, "0", big = true,
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    ) { amount = it }
    KDateField("Date", date, selectableTo = LocalDate.now(), format = { Fmt.relativeDate(it) }) {
        date = it
    }
    SheetField("Note", note, "Optional") { note = it }

    val value = amount.toDoubleOrNull()
    PrimaryButton(
        "Record transfer",
        enabled = value != null && value > 0 && fromId != 0L && toId != 0L && fromId != toId,
    ) {
        onSave(fromId, toId, value!!, date, note.trim().ifBlank { null })
    }
}

/** One account as a choice, with what is in it. */
@Composable
fun ColumnScope.AccountChoiceRow(
    account: FundAccountEntity,
    balance: Long,
    currency: Currency,
    selected: Boolean,
    onPick: () -> Unit,
) {
    SheetChoiceRow(
        title = account.name,
        subtitle = Fmt.money(balance, currency),
        selected = selected,
        onPick = onPick,
    ) { RadioDot(selected) }
}
