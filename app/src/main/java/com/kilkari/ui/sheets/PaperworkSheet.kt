package com.kilkari.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.domain.Fmt
import com.kilkari.domain.PaperworkStatus
import com.kilkari.domain.PaperworkStep
import com.kilkari.ui.components.KDateField
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.PrimaryButton
import com.kilkari.ui.components.SheetField
import com.kilkari.ui.components.SourceRow
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate

/** The three things a parent can say about a document, in the order the segmented control shows them. */
private val CHOICES = listOf(PaperworkStatus.WAITING, PaperworkStatus.OBTAINED, PaperworkStatus.SKIPPED)
private val CHOICE_LABELS = listOf("To do", "Obtained", "Set aside")

/**
 * One identity document: what it is for, what to carry, and where it stands.
 *
 * The suggested date is shown as such, so a parent who moves it can always find their way
 * back; setting a document aside moves the chain on without pretending it was obtained.
 */
@Composable
fun ColumnScope.PaperworkSheet(
    step: PaperworkStep,
    dob: LocalDate,
    onSave: (status: PaperworkStatus, settledOn: LocalDate?, targetDate: LocalDate?, note: String) -> Unit,
    onScan: () -> Unit,
    onOpenDocument: (Long) -> Unit,
) {
    val key = step.kind.key
    val today = LocalDate.now()

    var choice by remember(key) {
        mutableIntStateOf(
            when (step.status) {
                PaperworkStatus.OBTAINED -> 1
                PaperworkStatus.SKIPPED -> 2
                else -> 0
            }
        )
    }
    var settledOn by remember(key) { mutableStateOf(step.settledOn ?: today) }
    var target by remember(key) { mutableStateOf(step.dueDate) }
    var note by remember(key) { mutableStateOf(step.note) }

    SheetTitle(step.kind.title)
    SheetHint(step.kind.why)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KC.Screen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "YOU'LL NEED",
            fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            color = KC.Muted, letterSpacing = 0.6.sp,
        )
        step.kind.needs.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("·", fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KC.ClayDeep)
                Text(line, fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Ink)
            }
        }
    }

    Text(
        "WHERE IT STANDS",
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        color = KC.Muted, letterSpacing = 0.6.sp,
    )
    KSegmented(CHOICE_LABELS, choice) { choice = it }

    when (CHOICES[choice]) {
        PaperworkStatus.OBTAINED -> KDateField(
            "Obtained on", settledOn,
            selectableFrom = dob, selectableTo = today,
            format = { Fmt.relativeDate(it) },
        ) { settledOn = it }

        PaperworkStatus.SKIPPED -> SheetHint(
            "Set aside for now: the next document becomes due instead, and this one waits " +
                "here until you pick it back up.",
        )

        else -> {
            KDateField(
                "Remind me by", target,
                placeholder = step.previous?.let { "After the ${it.title.lowercase()}" } ?: "Pick a date",
                selectableFrom = dob,
            ) { target = it }
            val suggested = step.suggestedDate
            when {
                suggested != null && target != suggested -> Text(
                    "Suggested: ${Fmt.dateFull(suggested)}, ${step.kind.leadText}. Tap to use it.",
                    modifier = Modifier.clickable { target = suggested },
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = KC.Coral,
                )
                suggested != null -> SheetHint("${step.kind.leadText} — change it if the office says otherwise.")
                target == null -> SheetHint(
                    "Becomes due ${step.kind.leadText.lowercase()}. Set a date to start sooner.",
                )
                else -> SheetHint("Your own date. It becomes the one chased once its turn comes.")
            }
        }
    }

    SheetField("Note", note, "Application no., office, fees…") { note = it }

    val documentId = step.documentId
    if (documentId != null) {
        SourceRow("folder_open", "Scan filed", step.documentTitle ?: "Open it in Documents") {
            onOpenDocument(documentId)
        }
    } else {
        SourceRow("document_scanner", "Scan it into Documents", "Filing a scan records it as obtained") {
            onScan()
        }
    }

    PrimaryButton("Save") {
        val status = CHOICES[choice]
        // A document already set aside keeps the day it was, so the next one's date holds still.
        val settled = when (status) {
            PaperworkStatus.OBTAINED -> settledOn
            PaperworkStatus.SKIPPED -> step.settledOn.takeIf { step.status == PaperworkStatus.SKIPPED } ?: today
            else -> null
        }
        onSave(status, settled, target?.takeIf { it != step.suggestedDate }, note.trim())
    }
}
