package com.kilkari.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.kilkari.domain.ExpenseCategory
import com.kilkari.domain.Fmt
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KFab
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.sheets.ExpenseSheet
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle
import java.time.YearMonth

/** This month's spend split Medical / General, with a filterable ledger below. */
@Composable
fun MoneyScreen(vm: KilkariViewModel) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val filter by vm.moneyFilter.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    val month = YearMonth.now()
    val thisMonth = expenses.filter { YearMonth.from(it.date) == month }
    val medical = thisMonth.filter { it.category == ExpenseCategory.MEDICAL.key }.sumOf { it.amountInr }
    val general = thisMonth.filter { it.category == ExpenseCategory.GENERAL.key }.sumOf { it.amountInr }
    val total = medical + general
    val medFraction = if (total == 0L) 0f else medical.toFloat() / total

    val visible = expenses.filter { filter == null || it.category == filter?.key }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Money", style = ScreenTitle, color = KC.Ink)
                Text(
                    month.month.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }

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
                            .background(KC.IndigoBg),
                    ) {
                        if (total > 0) {
                            Box(Modifier.fillMaxWidth(medFraction).height(10.dp).background(KC.Violet))
                            Box(Modifier.weight(1f).height(10.dp).background(KC.SkyBright))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend("Medical", Fmt.money(medical, currency), KC.Violet)
                        Legend("General", Fmt.money(general, currency), KC.SkyBright)
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
                    Text(
                        "No expenses yet. Tap + to add one.",
                        modifier = Modifier.padding(14.dp),
                        fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                    )
                }
                visible.forEachIndexed { i, expense ->
                    val medical = expense.category == ExpenseCategory.MEDICAL.key
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            expense.icon,
                            if (medical) KC.Violet else KC.SkyMid,
                            if (medical) KC.VioletBg else KC.SkyBg,
                            size = 36, corner = 10, iconSize = 20,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                expense.title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = KC.Ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(Fmt.date(expense.date), expense.vendor).joinToString(" · "),
                                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            Fmt.money(expense.amountInr, currency),
                            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KC.Ink,
                        )
                    }
                    if (i != visible.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
                    }
                }
            }
        }

        KFab("add") { sheetOpen = true }

        KSheet(sheetOpen, onDismiss = { sheetOpen = false }) {
            ExpenseSheet(currency) { title, vendor, category, amount ->
                vm.addExpense(title, vendor, category, amount)
                sheetOpen = false
            }
        }
    }
}

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
