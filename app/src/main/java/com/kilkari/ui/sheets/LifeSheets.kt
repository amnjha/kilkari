package com.kilkari.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kilkari.domain.Currency
import com.kilkari.domain.ExpenseCategory
import com.kilkari.domain.Fmt
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.components.SheetStatic
import java.time.LocalDate

@Composable
fun ColumnScope.ExpenseSheet(
    currency: Currency,
    onSave: (String, String?, ExpenseCategory, Double) -> Unit,
) {
    var categoryIndex by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }

    SheetTitle("Add expense")
    KSegmented(ExpenseCategory.entries.map { it.label }, categoryIndex) { categoryIndex = it }
    SheetField(
        "Amount (${currency.symbol})", amount, "0", big = true,
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    ) { amount = it }
    SheetField("For", title, "e.g. Diapers, size 1") { title = it }
    SheetField("Where", vendor, "Shop or clinic") { vendor = it }
    SheetStatic("Date", "Today, ${Fmt.date(LocalDate.now())}")

    val value = amount.toDoubleOrNull()
    PrimaryButton("Save expense", enabled = value != null && value > 0) {
        val category = ExpenseCategory.entries[categoryIndex]
        onSave(
            title.trim().ifBlank { if (category == ExpenseCategory.MEDICAL) "Medical expense" else "General expense" },
            vendor.trim().ifBlank { null },
            category,
            value!!,
        )
    }
}

private val MILESTONE_CHIPS = listOf(
    "First smile", "First laugh", "Rolled over", "First outing",
    "Festival", "Family visit", "Other",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.MilestoneSheet(onSave: (String, String, String?) -> Unit) {
    var chip by remember { mutableStateOf(MILESTONE_CHIPS.first()) }
    var custom by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }

    SheetTitle("Add a moment")
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MILESTONE_CHIPS.forEach { label -> KChip(label, chip == label) { chip = label } }
    }
    if (chip == "Other") {
        SheetField("Title", custom, "What happened?") { custom = it }
    }
    SheetField("Note", note, "A line to remember it by") { note = it }
    SheetStatic("Date", "Today, ${Fmt.date(LocalDate.now())}")
    SheetField("Google Photos album", album, "Paste a link") { album = it }

    val title = if (chip == "Other") custom.trim() else chip
    PrimaryButton("Add to timeline", enabled = title.isNotBlank()) {
        onSave(title, note.trim(), album.trim().ifBlank { null })
    }
}

@Composable
fun ColumnScope.AlbumSheet(onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    SheetTitle("Link a photo album")
    SheetHint("Albums stay in Google Photos — Kilkari only keeps the link.")
    SheetField("Title", title, "e.g. First month") { title = it }
    SheetField("Note", subtitle, "e.g. 184 photos") { subtitle = it }
    SheetField("Link", url, "https://photos.app.goo.gl/…") { url = it }

    PrimaryButton("Save album", enabled = title.isNotBlank() && url.isNotBlank()) {
        onSave(title.trim(), subtitle.trim(), url.trim())
    }
}

@Composable
fun ColumnScope.EventSheet(onSave: (String, String, LocalDate, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var annual by remember { mutableStateOf(false) }

    val date = remember(dateText) { parseDayMonthYear(dateText) }

    SheetTitle("Add an event")
    SheetField("What", title, "e.g. Diwali") { title = it }
    SheetField(
        "Date", dateText, "DD-MM-YYYY",
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
    ) { dateText = it }
    SheetField("Note", note, "Optional detail") { note = it }
    KSegmented(listOf("One-off", "Every year"), if (annual) 1 else 0) { annual = it == 1 }

    PrimaryButton("Save event", enabled = title.isNotBlank() && date != null) {
        onSave(title.trim(), note.trim(), date!!, annual)
    }
}

@Composable
fun ColumnScope.DocumentSheet(pageCount: Int, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    SheetTitle("File this scan")
    SheetHint(
        if (pageCount == 0) "No pages captured — the scan was cancelled."
        else "$pageCount ${Fmt.plural(pageCount.toLong(), "page")} captured."
    )
    SheetField("Title", title, "e.g. Birth certificate") { title = it }
    SheetField("Tags", tags, "Legal, ID") { tags = it }
    SheetStatic("Filed", "Today, ${Fmt.date(LocalDate.now())}")

    PrimaryButton("Save document", enabled = title.isNotBlank() && pageCount > 0) {
        onSave(title.trim(), tags.trim())
    }
}
