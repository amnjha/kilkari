package com.kilkari.ui.sheets

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kilkari.data.db.ExpenseEntity
import com.kilkari.data.db.TimelineEntity
import com.kilkari.domain.Currency
import com.kilkari.domain.ExpenseCategory
import com.kilkari.data.db.AlbumEntity
import com.kilkari.data.db.DocumentEntity
import com.kilkari.data.db.EventEntity
import com.kilkari.domain.Fmt
import com.kilkari.ui.components.KChip
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import java.time.LocalDate

/**
 * What was spent. With [existing] the same form reopens a spend already recorded, so a wrong
 * amount or a wrongly categorised one can be put right instead of deleted and retyped.
 */
@Composable
fun ColumnScope.ExpenseSheet(
    currency: Currency,
    fundName: String,
    sources: FundSources = FundSources(),
    existing: ExpenseEntity? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (String, String?, ExpenseCategory, Double, LocalDate, Boolean, Long?) -> Unit,
) {
    var categoryIndex by remember(existing) {
        mutableIntStateOf(
            existing?.let { ExpenseCategory.entries.indexOf(ExpenseCategory.of(it.category)) } ?: 0
        )
    }
    var amount by remember(existing) {
        mutableStateOf(existing?.let { Fmt.plain(it.amountInr, currency) } ?: "")
    }
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var vendor by remember(existing) { mutableStateOf(existing?.vendor.orEmpty()) }
    var date by remember(existing) { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var paidFromFund by remember(existing) { mutableStateOf(existing?.paidFromFund ?: true) }
    var accountId by remember(existing) { mutableStateOf(existing?.fundAccountId) }

    SheetTitle(if (existing == null) "Add expense" else "Edit expense")
    KSegmented(ExpenseCategory.entries.map { it.label }, categoryIndex) { categoryIndex = it }
    SheetField(
        "Amount (${currency.symbol})", amount, "0", big = true,
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    ) { amount = it }
    SheetField("For", title, "e.g. Diapers, size 1") { title = it }
    SheetField("Where", vendor, "Shop or clinic") { vendor = it }
    EntryDateField("Date", date) { date = it }

    FundSourceField(sources.copy(fundName = fundName), currency, paidFromFund, accountId) { on, id ->
        paidFromFund = on
        accountId = id
    }

    val value = amount.toDoubleOrNull()
    PrimaryButton(
        if (existing == null) "Save expense" else "Save changes",
        enabled = value != null && value > 0,
    ) {
        val category = ExpenseCategory.entries[categoryIndex]
        onSave(
            title.trim().ifBlank { if (category == ExpenseCategory.MEDICAL) "Medical expense" else "General expense" },
            vendor.trim().ifBlank { null },
            category,
            value!!,
            date,
            paidFromFund,
            accountId,
        )
    }
    if (onDelete != null) SheetDelete("Delete this expense", onDelete)
}

private val MILESTONE_CHIPS = listOf(
    "First smile", "First laugh", "Rolled over", "First outing",
    "Festival", "Family visit", "Other",
)

/**
 * A moment on the timeline. Reopening [existing] falls back to "Other" with the title typed out
 * when it is not one of the chips — which is the case for anything the app recorded itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.MilestoneSheet(
    earliest: LocalDate? = null,
    existing: TimelineEntity? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (String, String, LocalDate, String?) -> Unit,
) {
    val recorded = existing?.title
    var chip by remember(existing) {
        mutableStateOf(
            when {
                recorded == null -> MILESTONE_CHIPS.first()
                recorded in MILESTONE_CHIPS -> recorded
                else -> "Other"
            }
        )
    }
    var custom by remember(existing) {
        mutableStateOf(if (recorded != null && recorded !in MILESTONE_CHIPS) recorded else "")
    }
    var note by remember(existing) { mutableStateOf(existing?.subtitle.orEmpty()) }
    var album by remember(existing) { mutableStateOf(existing?.albumUrl.orEmpty()) }
    var date by remember(existing) { mutableStateOf(existing?.date ?: LocalDate.now()) }

    SheetTitle(if (existing == null) "Add a moment" else "Edit this moment")
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
    EntryDateField("Date", date, earliest) { date = it }
    SheetField("Google Photos album", album, "Paste a link") { album = it }

    val title = if (chip == "Other") custom.trim() else chip
    PrimaryButton(
        if (existing == null) "Add to timeline" else "Save changes",
        enabled = title.isNotBlank(),
    ) {
        onSave(title, note.trim(), date, album.trim().ifBlank { null })
    }
    if (onDelete != null) SheetDelete("Delete this moment", onDelete)
}

@Composable
fun ColumnScope.AlbumSheet(
    existing: AlbumEntity? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (String, String, String) -> Unit,
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var subtitle by remember(existing) { mutableStateOf(existing?.subtitle.orEmpty()) }
    var url by remember(existing) { mutableStateOf(existing?.url.orEmpty()) }

    SheetTitle(if (existing == null) "Link a photo album" else "Edit album")
    SheetHint("Albums stay in Google Photos — Kilkari only keeps the link.")
    SheetField("Title", title, "e.g. First month") { title = it }
    SheetField("Note", subtitle, "e.g. 184 photos") { subtitle = it }
    SheetField("Link", url, "https://photos.app.goo.gl/…") { url = it }

    PrimaryButton(
        if (existing == null) "Save album" else "Save changes",
        enabled = title.isNotBlank() && url.isNotBlank(),
    ) {
        onSave(title.trim(), subtitle.trim(), url.trim())
    }
    if (onDelete != null) SheetDelete("Remove this album", onDelete)
}

@Composable
fun ColumnScope.EventSheet(
    existing: EventEntity? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (String, String, LocalDate, Boolean) -> Unit,
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember(existing) { mutableStateOf(existing?.subtitle.orEmpty()) }
    var date by remember(existing) { mutableStateOf(existing?.date) }
    var annual by remember(existing) { mutableStateOf(existing?.annual ?: false) }

    SheetTitle(if (existing == null) "Add an event" else "Edit event")
    SheetField("What", title, "e.g. Diwali") { title = it }
    KDateField("Date", date, format = { Fmt.relativeDate(it) }) { date = it }
    SheetField("Note", note, "Optional detail") { note = it }
    KSegmented(listOf("One-off", "Every year"), if (annual) 1 else 0) { annual = it == 1 }

    PrimaryButton(
        if (existing == null) "Save event" else "Save changes",
        enabled = title.isNotBlank() && date != null,
    ) {
        onSave(title.trim(), note.trim(), date!!, annual)
    }
    if (onDelete != null) SheetDelete("Remove this event", onDelete)
}

@Composable
fun ColumnScope.DocumentSheet(
    pageCount: Int,
    /** Filled in when the scan was asked for by name — the Paperwork screen's "Scan it". */
    initialTitle: String = "",
    /** Set when an already filed document is being corrected rather than a new one filed. */
    existing: DocumentEntity? = null,
    onSave: (String, String, LocalDate) -> Unit,
) {
    var title by remember(initialTitle, existing) {
        mutableStateOf(existing?.title ?: initialTitle)
    }
    var tags by remember(existing) { mutableStateOf(existing?.tags.orEmpty()) }
    var filed by remember(existing) { mutableStateOf(existing?.filedOn ?: LocalDate.now()) }

    SheetTitle(if (existing == null) "File this document" else "Edit document")
    SheetHint(
        // A page may have been photographed, picked from the gallery or attached as a file,
        // so the wording no longer assumes the camera.
        if (pageCount == 0) "Nothing added yet — add a page below."
        else "$pageCount ${Fmt.plural(pageCount.toLong(), "page")} attached."
    )
    SheetField("Title", title, "e.g. Birth certificate") { title = it }
    SheetField("Tags", tags, "Legal, ID") { tags = it }
    EntryDateField("Filed", filed) { filed = it }

    PrimaryButton(
        if (existing == null) "Save document" else "Save changes",
        enabled = title.isNotBlank() && pageCount > 0,
    ) {
        onSave(title.trim(), tags.trim(), filed)
    }
}

/**
 * Date row shared by the "what happened" sheets. Defaults to today and never runs ahead of it,
 * so an expense or a moment can be filed against the day it belongs to.
 */
@Composable
private fun EntryDateField(
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
