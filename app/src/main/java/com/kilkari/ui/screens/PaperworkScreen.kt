package com.kilkari.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.Fmt
import com.kilkari.domain.Paperwork
import com.kilkari.domain.PaperworkStatus
import com.kilkari.domain.PaperworkStep
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.GradientCard
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KRow
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.KSwitch
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.sheets.PaperworkSheet
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * The four identity documents, in the order they are applied for: birth certificate, then
 * Aadhaar, then passport, then PAN.
 *
 * Only one is ever "next". It sits in the hero card with its due date and a one-tap way to
 * record it; the rail below shows the whole chain, so what has been done and what is still
 * to come are read at a glance. Any step opens to its details, what to carry, and its dates.
 */
@Composable
fun PaperworkScreen(vm: KilkariViewModel, go: NavActions) {
    val steps by vm.paperwork.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /** The key of the step whose sheet is open. */
    var editing by remember { mutableStateOf<String?>(null) }

    // Arriving from a Today row lands straight on that document's sheet.
    val pendingEntry by vm.pendingEntry.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEntry) {
        val entry = pendingEntry ?: return@LaunchedEffect
        if (entry.startsWith("docs:")) {
            editing = entry.removePrefix("docs:")
            vm.consumeEntry()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.toast("Reminders need notification permission")
    }

    fun ensurePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val b = baby ?: return
    val active = steps.firstOrNull { it.status == PaperworkStatus.ACTIVE }
    val obtained = steps.count { it.status == PaperworkStatus.OBTAINED }
    val docsReminder = reminders.firstOrNull { it.key == "docs" }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DetailBar("Paperwork", go::back)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (active != null) {
                    NextDocumentCard(
                        step = active,
                        onObtained = { vm.markPaperworkObtained(active) },
                        onDetails = { editing = active.kind.key },
                    )
                } else if (steps.isNotEmpty()) {
                    AllSettledCard(steps, obtained)
                }

                SectionLabel("In this order", Modifier.padding(top = 2.dp))
                KCard {
                    steps.forEachIndexed { i, step ->
                        StepRow(
                            step = step,
                            railAbove = if (i == 0) null else railColour(steps[i - 1]),
                            railBelow = if (i == steps.lastIndex) null else railColour(step),
                            onClick = { editing = step.kind.key },
                        )
                    }
                }

                if (docsReminder != null) {
                    KCard {
                        KRow(
                            title = "Notify me",
                            subtitle = "A week before, the day before, on the day — then weekly while it waits",
                            icon = "notifications_active",
                            iconTint = KC.ClayDeep,
                            iconBg = KC.ClayBg,
                            divider = false,
                            onClick = {
                                if (!docsReminder.enabled) ensurePermission()
                                vm.setReminderEnabled(docsReminder, !docsReminder.enabled)
                            },
                        ) {
                            KSwitch(docsReminder.enabled)
                        }
                    }
                }

                Text(
                    "Each office asks for the document before it, so Kilkari asks for one at a " +
                        "time: the birth certificate ${Paperwork.BIRTH_CERTIFICATE_DAYS} days from " +
                        "${b.name}'s birth, then each of the others a little after the last. The " +
                        "dates are suggestions — open a step to set your own, or to set it aside.",
                    fontFamily = Sans, fontSize = 12.sp, lineHeight = 18.sp, color = KC.Muted,
                )
            }
        }

        val open = steps.firstOrNull { it.kind.key == editing }
        KSheet(open != null, onDismiss = { editing = null }) {
            if (open != null) {
                PaperworkSheet(
                    step = open,
                    dob = b.dob,
                    onSave = { status, settledOn, targetDate, note ->
                        vm.savePaperwork(open, status, settledOn, targetDate, note)
                        editing = null
                    },
                    onScan = {
                        editing = null
                        vm.requestEntry("scan:${open.kind.key}")
                        go.push(Routes.DOCUMENTS)
                    },
                    onOpenDocument = { id ->
                        editing = null
                        vm.selectDocument(id)
                        go.push(Routes.DOCUMENT_DETAIL)
                    },
                )
            }
        }
    }
}

/**
 * The document whose turn it is. Its due date is the headline; recording it is one tap, and
 * everything else — what to carry, the dates, setting it aside — is behind Details.
 */
@Composable
private fun NextDocumentCard(step: PaperworkStep, onObtained: () -> Unit, onDetails: () -> Unit) {
    val days = step.inDays ?: 0
    val due = step.dueDate

    GradientCard(listOf(KC.ClayDeep, KC.Clay, KC.Gold), onClick = onDetails) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NEXT UP · ${step.index + 1} OF ${Paperwork.KINDS.size}",
                fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, letterSpacing = 0.5.sp,
                color = Color.White.copy(alpha = 0.85f),
            )
            HeroPill(Fmt.dueText(days), emphasis = days < 0)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    step.kind.title,
                    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (due != null) "Due ${Fmt.dayAndDate(due)} · ${step.kind.leadText}" else step.kind.leadText,
                    fontFamily = Sans, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KIcons[step.kind.icon], null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeroButton("Mark obtained", solid = true, onClick = onObtained)
            HeroButton("Details", solid = false, onClick = onDetails)
        }
    }
}

/** Shown once nothing is left to chase: everything obtained, or what remains set aside. */
@Composable
private fun AllSettledCard(steps: List<PaperworkStep>, obtained: Int) {
    val setAside = steps.filter { it.status == PaperworkStatus.SKIPPED }
    KCard(corner = 16, background = KC.TealBg, border = KC.TealRing) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KC.Teal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KIcons["check"], null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (setAside.isEmpty()) "All four in hand" else "Nothing waiting",
                    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = KC.TealDeep,
                )
                Text(
                    if (setAside.isEmpty()) {
                        "Birth certificate, Aadhaar, passport and PAN — all obtained."
                    } else {
                        "$obtained of ${steps.size} obtained · " +
                            setAside.joinToString(", ") { it.kind.title } +
                            " set aside. Open one to pick it back up."
                    },
                    fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp, color = KC.TealDeep,
                )
            }
        }
    }
}

/**
 * One document on the rail. The node says where it stands; the line running through it
 * turns teal once the chain has passed.
 */
@Composable
private fun StepRow(step: PaperworkStep, railAbove: Color?, railBelow: Color?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.width(28.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(2.dp).height(12.dp).background(railAbove ?: Color.Transparent))
            StepNode(step)
            Box(Modifier.width(2.dp).weight(1f).background(railBelow ?: Color.Transparent))
        }

        Column(Modifier.weight(1f).padding(top = 14.dp, bottom = 14.dp)) {
            Text(
                step.kind.title,
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = when (step.status) {
                    PaperworkStatus.WAITING -> KC.MutedStrong
                    PaperworkStatus.SKIPPED -> KC.Faint
                    else -> KC.Ink
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                stepSubtitle(step),
                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }

        Box(Modifier.padding(top = 16.dp)) {
            when (step.status) {
                PaperworkStatus.OBTAINED -> Text(
                    "Done", fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KC.Teal,
                )
                PaperworkStatus.ACTIVE -> Text(
                    Fmt.dueBadge(step.inDays ?: 0),
                    fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = if (step.overdue) KC.Danger else KC.ClayDeep,
                )
                PaperworkStatus.SKIPPED -> Text(
                    "Set aside", fontFamily = Sans, fontSize = 12.sp, color = KC.Faint,
                )
                PaperworkStatus.WAITING -> Icon(
                    KIcons["chevron_right"], null, tint = KC.CoralPaler, modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StepNode(step: PaperworkStep) {
    val shape = RoundedCornerShape(percent = 50)
    val (background, tint, ring) = when (step.status) {
        PaperworkStatus.OBTAINED -> Triple(KC.Teal, Color.White, KC.Teal)
        PaperworkStatus.ACTIVE -> Triple(KC.Clay, Color.White, KC.ClayBg2)
        PaperworkStatus.SKIPPED -> Triple(KC.StoneBg, KC.StoneMid, KC.StoneBg)
        PaperworkStatus.WAITING -> Triple(KC.Surface, KC.Faint, KC.Track)
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(shape)
            .background(background)
            .border(2.dp, ring, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            KIcons[if (step.status == PaperworkStatus.OBTAINED) "check" else step.kind.icon],
            null, tint = tint, modifier = Modifier.size(15.dp),
        )
    }
}

private fun stepSubtitle(step: PaperworkStep): String = when (step.status) {
    PaperworkStatus.OBTAINED -> listOfNotNull(
        step.settledOn?.let { "Obtained ${Fmt.date(it)}" },
        step.documentTitle?.let { "scan filed" },
    ).joinToString(" · ")
    PaperworkStatus.ACTIVE -> step.dueDate?.let { "Due ${Fmt.dayAndDate(it)} · ${step.kind.leadText}" }
        ?: step.kind.leadText
    PaperworkStatus.WAITING -> step.dueDate?.let { "Your date: ${Fmt.date(it)}" }
        ?: step.previous?.let { "After the ${it.title.lowercase()}" }
        ?: step.kind.leadText
    PaperworkStatus.SKIPPED -> "Set aside · tap to pick it back up"
}

/** The rail turns teal below a step the chain has moved past. */
private fun railColour(step: PaperworkStep): Color = when (step.status) {
    PaperworkStatus.OBTAINED, PaperworkStatus.SKIPPED -> KC.TealLight
    else -> KC.Track
}

@Composable
private fun HeroPill(text: String, emphasis: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (emphasis) Color.White else Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text, fontFamily = Sans, fontSize = 12.sp,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasis) KC.Danger else Color.White,
        )
    }
}

@Composable
private fun HeroButton(label: String, solid: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (solid) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            color = if (solid) KC.ClayDeep else Color.White,
        )
    }
}
