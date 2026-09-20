package com.kilkari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.data.seed.VaccineSchedules
import com.kilkari.domain.Fmt
import com.kilkari.domain.ToothChart
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.GradientCard
import androidx.compose.foundation.shape.CircleShape
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KIcons
import com.kilkari.ui.components.SectionLabel
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.theme.KDepth
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import com.kilkari.ui.theme.ScreenTitle
import java.time.LocalDateTime

/** Hub for everything clinical: vaccines hero, four tiles, and a recent-activity list. */
@Composable
fun HealthScreen(vm: KilkariViewModel, go: NavActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val groups by vm.vaccineGroups.collectAsStateWithLifecycle()
    val nextVac by vm.nextVaccine.collectAsStateWithLifecycle()
    val growth by vm.growth.collectAsStateWithLifecycle()
    val teeth by vm.teeth.collectAsStateWithLifecycle()
    val meds by vm.medications.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()

    val doneCount = groups.sumOf { it.doneCount }
    val total = groups.sumOf { it.count }
    val pct = if (total == 0) 0 else doneCount * 100 / total
    val latestGrowth = growth.lastOrNull()
    val activeMeds = meds.filter { it.active }
    val nextAppt = appointments.firstOrNull { !it.startAt.isBefore(LocalDateTime.now()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = KDepth.navClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Health", style = ScreenTitle, color = KC.Ink)

        GradientCard(listOf(KC.TealDeep, KC.SeaMid), onClick = { go.push(Routes.VACCINES) }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KIcons["vaccines"], null, tint = Color.White, modifier = Modifier.size(26.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        VaccineSchedules.byId(settings.scheduleId).shortName,
                        fontFamily = Sans, fontSize = 12.sp, color = Color.White,
                    )
                }
            }
            Text(
                "Vaccinations",
                fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White,
            )
            Text(
                nextVac?.let { "$doneCount given · next ${it.label} on ${Fmt.date(it.dueDate)}" }
                    ?: "$doneCount given",
                fontFamily = Sans, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(pct / 100f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HealthTile(
                "monitor_weight", KC.LeafDeep, KC.LeafWash, "Growth",
                latestGrowth?.let {
                    listOfNotNull(
                        it.weightKg?.let { w -> Fmt.weight(w, settings.metricUnits) },
                        it.lengthCm?.let { l -> Fmt.length(l, settings.metricUnits) },
                        it.headCm?.let { h -> Fmt.length(h, settings.metricUnits) },
                    ).joinToString(" · ")
                }?.ifBlank { "No measurements yet" } ?: "No measurements yet",
                Modifier.weight(1f).fillMaxHeight(),
            ) { go.push(Routes.GROWTH) }
            HealthTile(
                "dentistry", KC.LeafDeep, KC.LeafWash, "Teeth",
                // Points at the tooth actually due next rather than repeating the first one
                // forever, which stopped being true the moment it came through.
                ToothChart.nextExpected(teeth).let { next ->
                    if (next == null) "All ${ToothChart.TOTAL} through"
                    else "${teeth.size} of ${ToothChart.TOTAL} · next ~${next.second.fromMonth} mo"
                },
                Modifier.weight(1f).fillMaxHeight(),
            ) { go.push(Routes.TEETH) }
        }
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HealthTile(
                "pill", KC.CoralDeep, KC.CoralWash, "Medications",
                if (activeMeds.isEmpty()) "None active"
                else "${activeMeds.size} active · ${activeMeds.first().name}",
                Modifier.weight(1f).fillMaxHeight(),
            ) { go.push(Routes.MEDS) }
            HealthTile(
                "stethoscope", KC.CoralDeep, KC.CoralWash, "Appointments",
                nextAppt?.let { "Next: ${Fmt.dayAndDate(it.startAt.toLocalDate())} ${Fmt.time(it.startAt)}" }
                    ?: "Nothing booked",
                Modifier.weight(1f).fillMaxHeight(),
            ) { go.push(Routes.APPOINTMENTS) }
        }
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HealthTile(
                "medical_services", KC.SkyDeep, KC.SkyWash, "Doctors",
                if (doctors.isEmpty()) "Add the people you see"
                else "${doctors.size} saved · ${doctors.first().name}",
                Modifier.weight(1f).fillMaxHeight(),
            ) { go.push(Routes.DOCTORS) }
            Box(Modifier.weight(1f))
        }

        SectionLabel("Recent", Modifier.padding(top = 4.dp))
        KCard {
            val recent = buildList {
                growth.takeLast(2).reversed().forEach { g ->
                    val prev = growth.getOrNull(growth.indexOf(g) - 1)?.weightKg
                    val delta = if (prev != null && g.weightKg != null) {
                        " (${Fmt.grams(g.weightKg - prev, settings.metricUnits)})"
                    } else ""
                    add(
                        Triple(
                            "monitor_weight" to KC.LeafDeep,
                            "Weight ${Fmt.weight(g.weightKg, settings.metricUnits)}$delta",
                            g.date,
                        )
                    )
                }
                meds.take(2).forEach { m ->
                    add(Triple("pill" to KC.ClayDeep, "${m.name} started, ${m.dose}", m.startDate))
                }
                timeline.filter { it.icon == "vaccines" }.take(2).forEach { t ->
                    add(Triple("vaccines" to KC.TealDeep, t.title, t.date))
                }
            }.sortedByDescending { it.third }.take(4)

            if (recent.isEmpty()) {
                Text(
                    "Nothing recorded yet.",
                    modifier = Modifier.padding(14.dp),
                    fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
                )
            }
            recent.forEachIndexed { i, (iconAndTint, text, date) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        KIcons[iconAndTint.first], null,
                        tint = iconAndTint.second, modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text, modifier = Modifier.weight(1f),
                        fontFamily = Sans, fontSize = 14.sp, color = KC.Ink,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(Fmt.date(date), fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
                }
                if (i != recent.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(KC.Divider))
                }
            }
        }
    }
}

/**
 * One of the five things health is made of, drawn the way the log tiles are: the card itself
 * wears the colour and the icon sits in a white disc on top of it. Five white cards with five
 * small coloured dots is a settings list; five coloured cards is a place.
 *
 * Three hues across the five, a row at a time: green for what grows, coral for what asks
 * something of you, blue for the people you keep on file. Five different colours was a grid
 * that took longer to read than one colour did.
 */
@Composable
private fun HealthTile(
    icon: String,
    fg: Color,
    wash: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KCard(modifier, corner = 26, background = wash, border = null, onClick = onClick) {
        Column(
            Modifier.fillMaxHeight().padding(16.dp).heightIn(min = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KIcons[icon], null, tint = fg, modifier = Modifier.size(23.dp))
            }
            Column {
                Text(title, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = KC.Ink)
                Text(
                    subtitle,
                    fontFamily = Sans, fontSize = 12.sp, color = KC.MutedStrong,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
