package com.kilkari.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.DueTask
import com.kilkari.domain.DueTaskKind
import com.kilkari.data.db.BabyEntity
import com.kilkari.ui.components.KSheet
import com.kilkari.ui.sheets.BabySheet
import com.kilkari.domain.Fmt
import com.kilkari.domain.LogKind
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.CheckRing
import com.kilkari.ui.components.GradientCard
import com.kilkari.ui.components.bleedHorizontal
import com.kilkari.ui.components.IconBadge
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.Monogram
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Three takes on the home screen, chosen in Settings:
 * A · Agenda (default), B · Hero, C · Checklist. All three read the same underlying state.
 */
@Composable
fun TodayScreen(vm: KilkariViewModel, go: NavActions) {
    val baby by vm.baby.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val b = baby ?: return

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val editDetails = { editing = true }
            when (settings.todayVariant) {
                "B" -> TodayHero(vm, go, b.name, b.dob, editDetails)
                "C" -> TodayChecklist(vm, go, b.dob)
                else -> TodayAgenda(vm, go, b.name, b.dob, editDetails)
            }
        }

        KSheet(editing, onDismiss = { editing = false }) {
            BabySheet(b) { name, dob, place, weight, length, head ->
                vm.updateBabyDetails(name, dob, place, weight, length, head)
                editing = false
            }
        }
    }
}

// ── Variant A · Agenda ──────────────────────────────────────────────────────

@Composable
private fun TodayAgenda(
    vm: KilkariViewModel,
    go: NavActions,
    name: String,
    dob: LocalDate,
    onEditDetails: () -> Unit,
) {
    val nextVac by vm.nextVaccine.collectAsStateWithLifecycle()
    val latest by vm.latestPerKind.collectAsStateWithLifecycle()
    val openSleep by vm.openSleep.collectAsStateWithLifecycle()

    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(greeting(), fontFamily = Sans, fontSize = 13.sp, color = KC.Muted)
            Text("$name is ${Fmt.age(dob)}", style = ScreenTitle, color = KC.Ink)
        }
        // The avatar is the way into the child's details from here.
        Box(Modifier.clip(RoundedCornerShape(percent = 50)).clickable(onClick = onEditDetails)) {
            Monogram(name.take(1).uppercase())
        }
    }

    nextVac?.let { g ->
        GradientCard(listOf(KC.Coral, KC.Clay), onClick = { go.push(Routes.VACCINES) }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "NEXT UP", fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, letterSpacing = 0.5.sp, color = Color.White.copy(alpha = 0.85f),
                )
                Pill(Fmt.dueText(g.inDays))
            }
            Text(
                "${g.label} vaccines · ${g.count} ${Fmt.plural(g.count.toLong(), "dose")}",
                fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White,
            )
            Text(
                "${Fmt.date(g.dueDate)} · ${g.names}",
                fontFamily = Sans, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            WhiteButton("See schedule") { go.push(Routes.VACCINES) }
        }
    }

    SectionLabel("Last logged", Modifier.padding(top = 2.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(LogKind.FEED, LogKind.SLEEP, LogKind.DIAPER).forEach { kind ->
            val spec = tileSpec(kind)
            val agoText = when {
                kind == LogKind.SLEEP && openSleep != null ->
                    "asleep ${Fmt.elapsed(openSleep!!.startAt)}"
                else -> Fmt.ago(latest[kind]?.startAt)
            }
            KCard(Modifier.weight(1f), corner = 14, onClick = { go.tab(Routes.LOG) }) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(KIcons[spec.icon], null, tint = spec.fg, modifier = Modifier.size(20.dp))
                    Text(spec.title, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KC.Ink)
                    Text(agoText, fontFamily = Sans, fontSize = 11.sp, color = KC.Muted, maxLines = 1)
                }
            }
        }
    }

    SectionLabel("Today", Modifier.padding(top = 2.dp))
    DueTaskList(vm, go, "Nothing due right now. 🎈")
}

// ── Variant B · Hero ────────────────────────────────────────────────────────

@Composable
private fun TodayHero(
    vm: KilkariViewModel,
    go: NavActions,
    name: String,
    dob: LocalDate,
    onEditDetails: () -> Unit,
) {
    val nextVac by vm.nextVaccine.collectAsStateWithLifecycle()
    val growth by vm.growth.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()

    val ageDays = Fmt.daysSince(dob)
    val latestWeight = growth.lastOrNull { it.weightKg != null }?.weightKg
    val birthdayDays = Fmt.daysUntil(Fmt.nextBirthday(dob))

    Box(
        Modifier
            .fillMaxWidth()
            .bleedHorizontal(16.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(KC.CoralDeep, KC.Clay, KC.Gold)))
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onEditDetails),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.take(1).uppercase(), fontFamily = Display,
                        fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color.White,
                    )
                }
                Icon(
                    KIcons["notifications"], null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable { go.push(Routes.REMINDERS) }
                        .padding(8.dp),
                )
            }
            Column {
                Text(
                    name, fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp, color = Color.White, letterSpacing = (-0.6).sp,
                )
                Text(
                    "${Fmt.age(dob)} old · $ageDays ${Fmt.plural(ageDays.toLong(), "day")} of you",
                    fontFamily = Sans, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("Weight", Fmt.weight(latestWeight), Modifier.weight(1f))
                HeroStat(
                    "Next vaccine",
                    nextVac?.let { "${it.inDays.coerceAtLeast(0)} days" } ?: "—",
                    Modifier.weight(1f),
                )
                HeroStat("Next birthday", "$birthdayDays d", Modifier.weight(1f))
            }
        }
    }

    SectionLabel("Today")
    DueTaskList(vm, go, "Nothing due right now. 🎈")

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel("Latest moment")
        Text(
            "Timeline",
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Coral,
            modifier = Modifier.clickable { go.push(Routes.TIMELINE) },
        )
    }
    val moment = timeline.firstOrNull()
    if (moment == null) {
        KCard(corner = 16) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "No moments yet", fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink,
                )
                Text(
                    "Add one from the timeline.",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
        }
    } else {
        KCard(corner = 16, onClick = { go.push(Routes.TIMELINE) }) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    moment.title, fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KC.Ink,
                )
                Text(
                    "${Fmt.date(moment.date)} · ${moment.subtitle}",
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeroStat(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(caption, fontFamily = Sans, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
        Text(
            value, fontFamily = Sans, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, color = Color.White, maxLines = 1,
        )
    }
}

// ── Variant C · Checklist ───────────────────────────────────────────────────

@Composable
private fun TodayChecklist(vm: KilkariViewModel, go: NavActions, dob: LocalDate) {
    val tasks by vm.dueTasks.collectAsStateWithLifecycle()
    val nextVac by vm.nextVaccine.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val doneCount = tasks.count { it.done }
    val pct = if (tasks.isEmpty()) 0 else doneCount * 100 / tasks.size
    val dayCount = Fmt.daysSince(dob, today)

    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                "${Fmt.dayAndDate(today)} · day $dayCount",
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )
            Text("$doneCount of ${tasks.size} done", style = ScreenTitle, color = KC.Ink)
        }
        ProgressRing(pct)
    }

    DueTaskList(vm, go, "Nothing due right now. 🎈")

    SectionLabel("Coming up")

    nextVac?.takeIf { it.inDays > 0 }?.let { g ->
        UpcomingRow(
            icon = "vaccines", tint = KC.GoldDeep, background = KC.GoldBg,
            border = KC.GoldRing,
            title = "${g.label} vaccines",
            subtitle = "${Fmt.date(g.dueDate)} · ${g.count} ${Fmt.plural(g.count.toLong(), "dose")}",
            trailing = Fmt.dueBadge(g.inDays),
        ) { go.push(Routes.VACCINES) }
    }

    appointments.firstOrNull { it.startAt.toLocalDate().isAfter(today) }?.let { appt ->
        UpcomingRow(
            icon = "stethoscope", tint = KC.Coral, background = KC.Surface, border = KC.Border,
            title = appt.title,
            subtitle = listOfNotNull(
                Fmt.dayAndDate(appt.startAt.toLocalDate()),
                Fmt.time(appt.startAt),
                appt.place,
            ).joinToString(", "),
            trailing = Fmt.dueBadge(Fmt.daysUntil(appt.startAt.toLocalDate())),
        ) { go.push(Routes.APPOINTMENTS) }
    }

    events.firstOrNull { it.date.isAfter(today) }?.let { ev ->
        UpcomingRow(
            icon = ev.icon, tint = KC.Gold, background = KC.Surface, border = KC.Border,
            title = ev.title,
            subtitle = Fmt.dayAndDate(ev.date),
            trailing = Fmt.dueBadge(Fmt.daysUntil(ev.date)),
        ) { go.push(Routes.EVENTS) }
    }
}

@Composable
private fun ProgressRing(percent: Int) {
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = KC.BorderStrong, startAngle = -90f, sweepAngle = 360f,
                useCenter = true, size = Size(size.width, size.height),
            )
            drawArc(
                color = KC.Clay, startAngle = -90f, sweepAngle = 360f * percent / 100f,
                useCenter = true, size = Size(size.width, size.height),
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(KC.Screen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$percent%", fontFamily = Sans, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = KC.Coral,
            )
        }
    }
}

@Composable
private fun UpcomingRow(
    icon: String,
    tint: Color,
    background: Color,
    border: Color,
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
) {
    KCard(corner = 14, background = background, border = border, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KIcons[icon], null, tint = tint, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = KC.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(trailing, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tint)
        }
    }
}

// ── Shared ──────────────────────────────────────────────────────────────────

/**
 * The one list of outstanding work, shared by all three layouts so they cannot disagree.
 * Tapping a tickable task completes it; anything else opens the screen that owns it.
 */
@Composable
private fun DueTaskList(vm: KilkariViewModel, go: NavActions, emptyText: String) {
    val tasks by vm.dueTasks.collectAsStateWithLifecycle()

    if (tasks.isEmpty()) {
        KCard(corner = 14) {
            Text(
                emptyText,
                modifier = Modifier.padding(14.dp),
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )
        }
        return
    }

    tasks.forEach { task -> DueTaskRow(task, vm, go) }
}

@Composable
private fun DueTaskRow(task: DueTask, vm: KilkariViewModel, go: NavActions) {
    val skin = taskSkin(task)
    KCard(corner = 14) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    when {
                        // Anything that needs data takes you to where that data is entered,
                        // and completes itself once the entry lands.
                        task.requiresEntry && task.route != null -> {
                            vm.requestEntry(task.id.removePrefix("rem:"))
                            go.open(task.route)
                        }
                        task.completable -> vm.setTaskDone(task, !task.done)
                        task.route != null -> go.open(task.route)
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (task.completable) {
                // Only reminders carry an icon the parent picked; everything else keeps the
                // plain ring rather than repeating an icon the row's text already says.
                CheckRing(task.done, glyph = task.icon.takeIf { task.kind == DueTaskKind.REMINDER })
            } else {
                IconBadge(task.icon, skin.first, skin.second)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = if (task.done) KC.Faint else KC.Ink,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (task.subtitle.isNotBlank()) {
                    Text(
                        task.subtitle,
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.dismissible && !task.done) {
                Text(
                    "Dismiss",
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { vm.dismissTask(task) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Faint,
                )
            }
            Text(
                task.trailing,
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                // Only genuine lateness is red; the per-kind tint made every medicine look overdue.
                color = when {
                    task.done -> KC.Faint
                    task.overdue -> KC.Danger
                    else -> KC.Muted
                },
            )
        }
    }
}

private fun taskSkin(task: DueTask): Pair<Color, Color> = when (task.kind) {
    DueTaskKind.MEDICATION -> KC.DangerDeep to KC.DangerBg2
    DueTaskKind.APPOINTMENT -> KC.Coral to KC.CoralBg
    DueTaskKind.VACCINE -> KC.GoldDeep to KC.GoldBg
    DueTaskKind.SLEEP -> KC.CoralDeep to KC.CoralBg
    DueTaskKind.CHECKLIST -> KC.Coral to KC.CoralBg
    DueTaskKind.REMINDER -> KC.SeaDeep to KC.SeaBg
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontFamily = Sans, fontSize = 12.sp, color = Color.White)
    }
}

@Composable
private fun WhiteButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KC.Coral)
    }
}

private fun greeting(): String {
    val h = LocalDateTime.now().hour
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

internal data class TileSpec(val icon: String, val title: String, val fg: Color, val bg: Color)

internal fun tileSpec(kind: LogKind): TileSpec = when (kind) {
    LogKind.FEED -> TileSpec("water_drop", "Feed", KC.GoldDeep, KC.GoldBg)
    LogKind.SLEEP -> TileSpec("bedtime", "Sleep", KC.CoralDeep, KC.CoralBg)
    LogKind.DIAPER -> TileSpec("baby_changing_station", "Diaper", KC.SeaDeep, KC.SeaBg)
    LogKind.MEDICINE -> TileSpec("pill", "Medicine", KC.DangerDeep, KC.DangerBg2)
    LogKind.GROWTH -> TileSpec("monitor_weight", "Growth", KC.TealDeep, KC.TealBg)
    LogKind.TOOTH -> TileSpec("dentistry", "Teeth", KC.Clay, KC.ClayBg)
}
