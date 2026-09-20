package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.FundTxnEntity
import com.kilkari.domain.Fmt
import com.kilkari.domain.FundTxnKind
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.CheckRing
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.theme.KDepth
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate
import kotlin.math.abs

/**
 * Checking the app's record of an account against a bank statement.
 *
 * The app's balance is derived from what has been entered, so the only way to know it is right
 * was to add it up by hand. Here each movement up to the statement date can be ticked, the
 * ticked total is compared against the closing balance off the statement, and the difference is
 * shown until it is nothing. Finishing stamps the ticked lines with the statement date, so the
 * next reconciliation starts where this one left off.
 *
 * Only deposits and withdrawals are ticked. Expenses and investment contributions come off the
 * balance too, but they are entered from their own screens and are shown here as context.
 */
@Composable
fun ReconcileScreen(vm: KilkariViewModel, go: NavActions) {
    val currency by vm.currency.collectAsStateWithLifecycle()
    val accounts by vm.fundAccounts.collectAsStateWithLifecycle()
    val transactions by vm.fundTransactions.collectAsStateWithLifecycle()

    val open = accounts.filterNot { it.archived }
    var accountIndex by remember(open) { mutableStateOf(0) }
    val account = open.getOrNull(accountIndex.coerceIn(0, (open.size - 1).coerceAtLeast(0)))

    var statementDate by remember { mutableStateOf(LocalDate.now()) }
    var statementBalance by remember { mutableStateOf("") }

    // Ticks live for the length of the visit and are written only when it is finished, so a
    // half-done pass over a statement leaves nothing behind.
    val ticked = remember(account?.id) { mutableStateMapOf<Long, Boolean>() }

    val lines = transactions
        .filter { account != null && it.accountId == account.id }
        .filter { !it.date.isAfter(statementDate) }
        .sortedWith(compareBy({ it.date }, { it.id }))

    fun isTicked(row: FundTxnEntity) = ticked[row.id] ?: (row.reconciledOn != null)

    val clearedInr = lines.filter(::isTicked).sumOf { signed(it) }
    val statementInr = statementBalance.toDoubleOrNull()?.let { Fmt.toInr(it, currency) }
    val difference = statementInr?.let { it - clearedInr }

    Column(Modifier.fillMaxSize()) {
        DetailBar("Check against a statement", go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = KDepth.navClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (account == null) {
                KCard {
                    Text(
                        "No account to check yet. Record a deposit first.",
                        modifier = Modifier.padding(14.dp),
                        fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                    )
                }
                return@Column
            }

            if (open.size > 1) {
                KSegmented(open.map { it.name }, accountIndex) { accountIndex = it }
            }

            KCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KDateField(
                        "Statement date", statementDate, sheetStyle = false,
                        selectableTo = LocalDate.now(), format = { Fmt.relativeDate(it) },
                    ) { statementDate = it }
                    SheetField(
                        "Closing balance (${currency.symbol})", statementBalance, "0",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    ) { statementBalance = it }
                }
            }

            KCard(background = if (difference == 0L) KC.TealBg else KC.Surface) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Ticked so far: ${Fmt.money(clearedInr, currency)}",
                        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KC.Ink,
                    )
                    Text(
                        when {
                            difference == null -> "Enter the closing balance from the statement to compare."
                            difference == 0L -> "Matches the statement exactly."
                            difference > 0 -> "${Fmt.money(abs(difference), currency)} on the statement is not ticked here."
                            else -> "${Fmt.money(abs(difference), currency)} ticked here is not on the statement."
                        },
                        fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp,
                        color = if (difference == 0L) KC.TealDeep else KC.Muted,
                    )
                }
            }

            SectionLabel("Deposits and withdrawals")
            KCard {
                if (lines.isEmpty()) {
                    Text(
                        "Nothing recorded on this account up to that date.",
                        modifier = Modifier.padding(14.dp),
                        fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                    )
                }
                lines.forEachIndexed { i, row ->
                    val on = isTicked(row)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { ticked[row.id] = !on }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckRing(on, rounded = true)
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.note?.takeIf { it.isNotBlank() }
                                    ?: if (row.kind == FundTxnKind.DEPOSIT.key) "Deposit" else "Withdrawal",
                                fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = KC.Ink,
                            )
                            Text(
                                listOfNotNull(
                                    Fmt.date(row.date),
                                    row.reconciledOn?.let { "checked ${Fmt.date(it)}" },
                                ).joinToString(" · "),
                                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                            )
                        }
                        Text(
                            (if (signed(row) >= 0) "+" else "−") + Fmt.money(abs(row.amountInr), currency),
                            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (signed(row) >= 0) KC.Teal else KC.Ink,
                        )
                    }
                    if (i != lines.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
                    }
                }
            }

            val changes = lines.filter { isTicked(it) != (it.reconciledOn != null) }
            PrimaryButton(
                if (changes.isEmpty()) "Nothing to save" else "Mark ${changes.size} as checked",
                enabled = changes.isNotEmpty(),
            ) {
                changes.forEach { vm.setReconciled(it, if (isTicked(it)) statementDate else null) }
                go.back()
            }

            Text(
                "Expenses and contributions paid from this account come off the balance too. " +
                    "They are entered from their own screens, so they are not ticked here.",
                fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
            )
        }
    }
}

/** Positive into the account, negative out of it. */
private fun signed(row: FundTxnEntity): Long =
    if (row.kind == FundTxnKind.DEPOSIT.key) row.amountInr else -row.amountInr
