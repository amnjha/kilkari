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
    val b = baby ?: return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (settings.todayVariant) {
            "B" -> TodayHero(vm, go, b.name, b.dob)
            "C" -> TodayChecklist(vm, go, b.dob)
            else -> TodayAgenda(vm, go, b.name, b.dob)
        }
    }
}

// ── Variant A · Agenda ──────────────────────────────────────────────────────

@Composable
private fun TodayAgenda(vm: KilkariViewModel, go: NavActions, name: String, dob: LocalDate) {
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
        Monogram(name.take(1).uppercase())
    }

    nextVac?.let { g ->
        GradientCard(listOf(KC.Indigo, KC.Violet), onClick = { go.push(Routes.VACCINES) }) {
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

    SectionLabel("Today", Modifier.padding(top = 2.dp))
    TodayTaskList(vm, go)

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
}

// ── Variant B · Hero ────────────────────────────────────────────────────────

@Composable
private fun TodayHero(vm: KilkariViewModel, go: NavActions, name: String, dob: LocalDate) {
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
            .background(Brush.linearGradient(listOf(KC.IndigoDeep, KC.Violet, KC.Fuchsia)))
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
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.take(1).uppercase(), fontFamily = Display,
                        fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color.White,
                    )
                }
                Icon(
                    KIcons["notifications"], null,
                    tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(24.dp),
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
    TodayTaskList(vm, go)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel("Latest moment")
        Text(
            "Timeline",
            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Indigo,
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
    val checklist by vm.checklist.collectAsStateWithLifecycle()
    val nextVac by vm.nextVaccine.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val doneCount = checklist.count { it.done }
    val pct = if (checklist.isEmpty()) 0 else doneCount * 100 / checklist.size
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
            Text("$doneCount of ${checklist.size} done", style = ScreenTitle, color = KC.Ink)
        }
        ProgressRing(pct)
    }

    KCard {
        checklist.forEachIndexed { i, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.setChecklistDone(row, !row.done) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckRing(row.done)
                Text(
                    row.title,
                    modifier = Modifier.weight(1f),
                    fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = if (row.done) KC.Faint else KC.Ink,
                    textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                Text(row.timeText, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
            }
            if (i != checklist.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
            }
        }
        if (checklist.isEmpty()) {
            Text(
                "Nothing scheduled today.",
                modifier = Modifier.padding(14.dp),
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )
        }
    }

    SectionLabel("Coming up")

    nextVac?.let { g ->
        UpcomingRow(
            icon = "vaccines", tint = KC.FuchsiaDeep, background = KC.FuchsiaBg,
            border = KC.FuchsiaRing,
            title = "${g.label} vaccines",
            subtitle = "${Fmt.date(g.dueDate)} · ${g.count} ${Fmt.plural(g.count.toLong(), "dose")}",
            trailing = Fmt.dueBadge(g.inDays),
        ) { go.push(Routes.VACCINES) }
    }

    appointments.firstOrNull { !it.startAt.isBefore(LocalDateTime.now()) }?.let { appt ->
        UpcomingRow(
            icon = "stethoscope", tint = KC.Indigo, background = KC.Surface, border = KC.Border,
            title = appt.title,
            subtitle = listOfNotNull(
                Fmt.dayAndDate(appt.startAt.toLocalDate()),
                Fmt.time(appt.startAt),
                appt.place,
            ).joinToString(", "),
            trailing = Fmt.dueBadge(Fmt.daysUntil(appt.startAt.toLocalDate())),
        ) { go.push(Routes.APPOINTMENTS) }
    }

    events.firstOrNull { !it.date.isBefore(today) }?.let { ev ->
        UpcomingRow(
            icon = ev.icon, tint = KC.Amber, background = KC.Surface, border = KC.Border,
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
                color = KC.Violet, startAngle = -90f, sweepAngle = 360f * percent / 100f,
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
                fontSize = 12.sp, color = KC.Indigo,
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

/** The "Today" agenda: medicines still to take, a nap in progress, appointments today. */
@Composable
private fun TodayTaskList(vm: KilkariViewModel, go: NavActions) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val doses by vm.medicationDoses.collectAsStateWithLifecycle()
    val openSleep by vm.openSleep.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    val takenToday = remember(doses) { doses.filter { it.date == today }.map { it.medicationId }.toSet() }
    val dueMeds = meds.filter { it.active && it.id !in takenToday }
    val todayAppointments = appointments.filter { it.startAt.toLocalDate() == today }

    if (dueMeds.isEmpty() && openSleep == null && todayAppointments.isEmpty()) {
        KCard(corner = 14) {
            Text(
                "Nothing due right now. 🎈",
                modifier = Modifier.padding(14.dp),
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )
        }
        return
    }

    dueMeds.forEach { med ->
        TaskRow(
            icon = "pill", tint = KC.RoseDeep, background = KC.RoseBg2,
            title = "${med.name} · ${med.dose}",
            subtitle = med.scheduleText,
            trailing = "due",
        ) { go.push(Routes.MEDS) }
    }

    openSleep?.let { sleep ->
        TaskRow(
            icon = "bedtime", tint = KC.IndigoDeep, background = KC.IndigoBg,
            title = "Napping now",
            subtitle = "Since ${Fmt.time(sleep.startAt)}${sleep.place?.let { " · $it" }.orEmpty()}",
            trailing = Fmt.elapsed(sleep.startAt),
        ) { go.tab(Routes.LOG) }
    }

    todayAppointments.forEach { appt ->
        TaskRow(
            icon = "stethoscope", tint = KC.Indigo, background = KC.IndigoBg,
            title = appt.title,
            subtitle = listOfNotNull(appt.doctor, appt.place).joinToString(" · "),
            trailing = Fmt.time(appt.startAt),
        ) { go.push(Routes.APPOINTMENTS) }
    }
}

@Composable
private fun TaskRow(
    icon: String,
    tint: Color,
    background: Color,
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
) {
    KCard(corner = 14, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, tint, background)
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontFamily = Sans, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = KC.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(trailing, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = KC.Indigo)
        }
    }
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
        Text(label, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = KC.Indigo)
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
    LogKind.FEED -> TileSpec("water_drop", "Feed", KC.FuchsiaDeep, KC.FuchsiaBg)
    LogKind.SLEEP -> TileSpec("bedtime", "Sleep", KC.IndigoDeep, KC.IndigoBg)
    LogKind.DIAPER -> TileSpec("baby_changing_station", "Diaper", KC.SkyDeep, KC.SkyBg)
    LogKind.MEDICINE -> TileSpec("pill", "Medicine", KC.RoseDeep, KC.RoseBg2)
    LogKind.GROWTH -> TileSpec("monitor_weight", "Growth", KC.GreenDeep, KC.GreenBg)
    LogKind.TOOTH -> TileSpec("dentistry", "Teeth", KC.Orange, KC.OrangeBg)
}
