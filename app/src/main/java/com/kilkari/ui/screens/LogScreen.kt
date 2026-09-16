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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.db.GrowthEntity
import com.kilkari.data.db.LogEntryEntity
import com.kilkari.data.db.MedicationEntity
import com.kilkari.domain.BreastSide
import com.kilkari.domain.DiaperKind
import com.kilkari.domain.FeedType
import com.kilkari.domain.Fmt
import com.kilkari.domain.LogKind
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.sheets.DiaperSheet
import com.kilkari.ui.sheets.FeedSheet
import com.kilkari.ui.sheets.GrowthSheet
import com.kilkari.ui.sheets.MedicineDoseSheet
import com.kilkari.ui.sheets.MedicineSheet
import com.kilkari.ui.sheets.SleepSheet
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle
import java.time.LocalDate
import java.time.LocalDateTime

/** Six quick-log tiles over the day's entries. Tapping a tile opens its sheet. */
@Composable
fun LogScreen(vm: KilkariViewModel, go: NavActions) {
    val latest by vm.latestPerKind.collectAsStateWithLifecycle()
    val openSleep by vm.openSleep.collectAsStateWithLifecycle()
    val todayLogs by vm.todayLogs.collectAsStateWithLifecycle()
    val diapers by vm.diapersToday.collectAsStateWithLifecycle()
    val teeth by vm.teeth.collectAsStateWithLifecycle()
    val growth by vm.growth.collectAsStateWithLifecycle()
    val medications by vm.medications.collectAsStateWithLifecycle()
    val baby by vm.baby.collectAsStateWithLifecycle()

    val recentLogs by vm.recentLogs.collectAsStateWithLifecycle()

    var sheet by remember { mutableStateOf<LogKind?>(null) }

    /** Non-null while an entry already saved is open for correction. */
    var editing by remember { mutableStateOf<LogEntryEntity?>(null) }

    val latestGrowth = growth.lastOrNull()
    val today = LocalDate.now()
    val earlier = recentLogs.filter { it.startAt.toLocalDate() != today }.take(EARLIER_SHOWN)

    /** Teeth live on their own screen, so their rows point there rather than at a sheet. */
    fun open(entry: LogEntryEntity) {
        if (LogKind.of(entry.kind) == LogKind.TOOTH) go.push(Routes.TEETH) else editing = entry
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Log", style = ScreenTitle, color = KC.Ink)
                Text(
                    "Tap a tile to add an entry, or an entry below to change it.",
                    fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                )
            }

            val rows = LogKind.entries.chunked(2)
            rows.forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { kind ->
                        val spec = tileSpec(kind)
                        val ago = when {
                            kind == LogKind.SLEEP && openSleep != null -> "asleep ${Fmt.elapsed(openSleep!!.startAt)}"
                            kind == LogKind.TOOTH -> if (teeth.isEmpty()) "—" else "${teeth.size} in"
                            else -> Fmt.ago(latest[kind]?.startAt)
                        }
                        val detail = when (kind) {
                            LogKind.FEED -> latest[kind]?.let { feedDetail(it) } ?: "Nothing logged yet"
                            LogKind.SLEEP -> openSleep?.let { "Since ${Fmt.time(it.startAt)}" }
                                ?: latest[kind]?.let { "Last: ${Fmt.time(it.startAt)}" } ?: "Nothing logged yet"
                            LogKind.DIAPER -> if (diapers == 0) "None today" else "$diapers today"
                            LogKind.MEDICINE -> medications.firstOrNull { it.active }
                                ?.let { "${it.name} · ${it.dose}" } ?: "No active medicine"
                            LogKind.GROWTH -> latestGrowth?.let {
                                listOfNotNull(
                                    it.weightKg?.let(Fmt::weight),
                                    it.lengthCm?.let(Fmt::length),
                                ).joinToString(" · ").ifBlank { "No measurements" }
                            } ?: "No measurements"
                            LogKind.TOOTH -> "${teeth.size} of 20 erupted"
                        }
                        LogTile(
                            icon = spec.icon, title = spec.title, ago = ago, detail = detail,
                            fg = spec.fg, bg = spec.bg, modifier = Modifier.weight(1f),
                        ) {
                            if (kind == LogKind.TOOTH) go.push(Routes.TEETH) else sheet = kind
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }

            SectionLabel("Today's entries", Modifier.padding(top = 4.dp))
            KCard {
                if (todayLogs.isEmpty()) {
                    Text(
                        "Nothing logged today yet.",
                        modifier = Modifier.padding(14.dp),
                        fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                    )
                }
                todayLogs.forEachIndexed { i, entry ->
                    LogEntryRow(entry, Fmt.time(entry.startAt)) { open(entry) }
                    if (i != todayLogs.lastIndex) RowDivider()
                }
            }

            // Back-dated entries drop off "today" the moment the day rolls over, so they are
            // listed here too rather than becoming impossible to correct.
            if (earlier.isNotEmpty()) {
                SectionLabel("Earlier", Modifier.padding(top = 4.dp))
                KCard {
                    earlier.forEachIndexed { i, entry ->
                        LogEntryRow(entry, Fmt.date(entry.startAt.toLocalDate())) { open(entry) }
                        if (i != earlier.lastIndex) RowDivider()
                    }
                }
            }
        }

        KSheet(sheet != null, onDismiss = { sheet = null }) {
            when (sheet) {
                LogKind.FEED -> FeedSheet { type: FeedType, side: BreastSide?, amount: Int, at: LocalDateTime ->
                    vm.logFeed(type, side, amount, at); sheet = null
                }
                LogKind.SLEEP -> SleepSheet(
                    asleepSince = openSleep?.startAt,
                    onStart = { place, from, to -> vm.logSleepStart(place, from, to); sheet = null },
                    onEnd = { at -> vm.logSleepEnd(at); sheet = null },
                )
                LogKind.DIAPER -> DiaperSheet { kind: DiaperKind, at: LocalDateTime ->
                    vm.logDiaper(kind, at); sheet = null
                }
                LogKind.MEDICINE -> MedicineSheet(
                    medications = medications.filter { it.active },
                    onLog = { med, at -> vm.logMedicine(med, at); sheet = null },
                    onManage = { sheet = null; go.push(Routes.MEDS) },
                )
                LogKind.GROWTH -> GrowthSheet(
                    weightHint = latestGrowth?.weightKg?.let(Fmt::trimNum) ?: "3.9",
                    lengthHint = latestGrowth?.lengthCm?.let(Fmt::trimNum) ?: "52",
                    headHint = latestGrowth?.headCm?.let(Fmt::trimNum) ?: "36",
                    earliest = baby?.dob,
                ) { date, w, l, h ->
                    vm.addGrowth(date, w, l, h); sheet = null
                }
                else -> Unit
            }
        }

        val edit = editing
        KSheet(edit != null, onDismiss = { editing = null }) {
            if (edit != null) EditEntrySheet(vm, edit, baby?.dob, medications, growth) { editing = null }
        }
    }
}

/**
 * Reopens one saved entry in the sheet that created it. Growth is the odd one out: the parent
 * saw a single action, but it wrote a measurement plus the journal row announcing it, and the
 * two are paired back up by date here.
 */
@Composable
private fun ColumnScope.EditEntrySheet(
    vm: KilkariViewModel,
    entry: LogEntryEntity,
    dob: LocalDate?,
    medications: List<MedicationEntity>,
    growth: List<GrowthEntity>,
    onClose: () -> Unit,
) {
    when (LogKind.of(entry.kind)) {
        LogKind.FEED -> FeedSheet(
            existing = entry,
            onDelete = { vm.deleteLog(entry); onClose() },
        ) { type: FeedType, side: BreastSide?, amount: Int, at: LocalDateTime ->
            vm.updateLog(
                entry.copy(feedType = type.key, side = side?.key, amount = amount, startAt = at)
            )
            onClose()
        }
        LogKind.SLEEP -> SleepSheet(
            asleepSince = null,
            existing = entry,
            onDelete = { vm.deleteLog(entry); onClose() },
            onStart = { place, from, to ->
                vm.updateLog(entry.copy(place = place, startAt = from, endAt = to))
                onClose()
            },
            onEnd = { onClose() },
        )
        LogKind.DIAPER -> DiaperSheet(
            existing = entry,
            onDelete = { vm.deleteLog(entry); onClose() },
        ) { kind: DiaperKind, at: LocalDateTime ->
            vm.updateLog(entry.copy(diaperKind = kind.key, startAt = at))
            onClose()
        }
        LogKind.MEDICINE -> MedicineDoseSheet(
            entry = entry,
            earliest = medications.firstOrNull { it.id == entry.medicationId }?.startDate ?: dob,
            onDelete = { vm.deleteLog(entry); onClose() },
        ) { at ->
            vm.updateLog(entry.copy(startAt = at))
            onClose()
        }
        LogKind.GROWTH -> {
            val measurement = growth.lastOrNull { it.date == entry.startAt.toLocalDate() }
            val latest = growth.lastOrNull()
            GrowthSheet(
                weightHint = latest?.weightKg?.let(Fmt::trimNum) ?: "3.9",
                lengthHint = latest?.lengthCm?.let(Fmt::trimNum) ?: "52",
                headHint = latest?.headCm?.let(Fmt::trimNum) ?: "36",
                earliest = dob,
                existing = measurement,
                onDelete = { vm.deleteGrowth(entry, measurement); onClose() },
            ) { date, w, l, h ->
                vm.updateGrowth(entry, measurement, date, w, l, h)
                onClose()
            }
        }
        LogKind.TOOTH -> Unit
    }
}

/** How far back the "Earlier" list reaches — enough to correct a slip, not a whole history. */
private const val EARLIER_SHOWN = 30

/** One journal line. [trailing] carries the time for today's rows and the date for older ones. */
@Composable
private fun LogEntryRow(entry: LogEntryEntity, trailing: String, onClick: () -> Unit) {
    val spec = tileSpec(LogKind.of(entry.kind))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KIcons[spec.icon], null, tint = spec.fg, modifier = Modifier.size(20.dp))
        Text(
            entryText(entry),
            modifier = Modifier.weight(1f),
            fontFamily = Sans, fontSize = 14.sp, color = KC.Ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(trailing, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
}

@Composable
private fun LogTile(
    icon: String,
    title: String,
    ago: String,
    detail: String,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KCard(modifier, corner = 20, background = bg, border = null, onClick = onClick) {
        Column(
            Modifier.padding(16.dp).heightIn(min = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KIcons[icon], null, tint = fg, modifier = Modifier.size(26.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        ago, fontFamily = Sans, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = fg, maxLines = 1,
                    )
                }
            }
            Column {
                Text(title, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = KC.Ink)
                Text(
                    detail,
                    fontFamily = Sans, fontSize = 12.sp, color = KC.MutedStrong,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun feedDetail(entry: LogEntryEntity): String = when (FeedType.of(entry.feedType)) {
    FeedType.BREAST -> listOfNotNull(
        entry.side?.let { if (it == "L") "Left" else "Right" },
        entry.amount?.let { "$it min" },
    ).joinToString(" · ")
    FeedType.BOTTLE -> "Bottle · ${entry.amount ?: 0} ml"
    FeedType.SOLID -> "Solids · ${entry.amount ?: 0} g"
}

/** One-line description of a log row for "Today's entries". */
internal fun entryText(entry: LogEntryEntity): String = when (LogKind.of(entry.kind)) {
    LogKind.FEED -> "${FeedType.of(entry.feedType).label} feed · ${feedDetail(entry)}"
    LogKind.SLEEP -> if (entry.endAt == null) {
        "Fell asleep${entry.place?.let { " · $it" }.orEmpty()}"
    } else {
        "Slept ${Fmt.elapsed(entry.startAt, entry.endAt)}${entry.place?.let { " · $it" }.orEmpty()}"
    }
    LogKind.DIAPER -> "Diaper · ${DiaperKind.of(entry.diaperKind).label.lowercase()}"
    LogKind.MEDICINE -> listOfNotNull(entry.medicationName, entry.dose).joinToString(" · ")
    LogKind.GROWTH -> "Measurement recorded"
    LogKind.TOOTH -> "Tooth appeared"
}
