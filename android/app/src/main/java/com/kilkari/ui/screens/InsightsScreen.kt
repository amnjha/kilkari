package com.kilkari.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kilkari.domain.FeedType
import com.kilkari.domain.Fmt
import com.kilkari.domain.InsightReport
import com.kilkari.domain.Units
import com.kilkari.ui.KilkariViewModel
import com.kilkari.ui.components.DailyColumns
import com.kilkari.ui.components.DetailBar
import com.kilkari.ui.components.InsightFigure
import com.kilkari.ui.components.KCard
import com.kilkari.ui.components.KSegmented
import com.kilkari.ui.components.Meter
import com.kilkari.ui.components.Share
import com.kilkari.ui.components.SplitBar
import com.kilkari.ui.nav.NavActions
import com.kilkari.ui.nav.Routes
import com.kilkari.ui.theme.KDepth
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val WINDOWS = listOf(7, 30)

/**
 * Averages over the last week or month, drawn from what has been logged.
 *
 * Each area says how many days it rests on. An average of two logged days and one of thirty
 * look identical as numbers, and only one of them is worth planning around.
 */
@Composable
fun InsightsScreen(vm: KilkariViewModel, go: NavActions) {
    val report by vm.insights.collectAsStateWithLifecycle()
    val days by vm.insightDays.collectAsStateWithLifecycle()
    val metric by vm.metricUnits.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        DetailBar("Insights", go::back)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = KDepth.navClearance),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // One control above everything it scopes, so every card below reads the same span.
            KSegmented(WINDOWS.map { "Last $it days" }, WINDOWS.indexOf(days)) {
                vm.setInsightDays(WINDOWS[it])
            }
            Text(
                "Averages per logged day, not counting today.",
                fontFamily = Sans, fontSize = 13.sp, color = KC.Muted,
            )

            val r = report ?: return@Column
            val previous = "previous $days days"
            val nothing = r.feeding.loggedDays == 0 && r.sleep.loggedDays == 0 &&
                r.diapers.loggedDays == 0 && r.medicines.isEmpty() && r.growth == null

            if (nothing) {
                KCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Nothing logged in the last $days days",
                            fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = KC.Ink,
                        )
                        Text(
                            "Log feeds, sleep and diapers from the Log tab, and averages will build up here from the next day.",
                            fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted,
                        )
                    }
                }
                return@Column
            }

            val openDay: (LocalDate) -> Unit = { day ->
                vm.showLogDay(day)
                go.push(Routes.LOG_DAY)
            }
            FeedingCard(r, previous, openDay)
            SleepCard(r, previous, openDay)
            DiaperCard(r, previous, openDay)
            if (r.medicines.isNotEmpty()) MedicineCard(r)
            r.growth?.let { GrowthCard(it.gramsPerWeek, it.since, metric) }
        }
    }
}

@Composable
private fun InsightCard(title: String, loggedDays: Int?, days: Int, content: @Composable () -> Unit) {
    KCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = KC.Ink)
                if (loggedDays != null) {
                    Text(
                        "Logged on $loggedDays of $days ${Fmt.plural(days.toLong(), "day")}",
                        fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                    )
                }
            }
            content()
        }
    }
}

/** Two figures side by side; a lone figure keeps its half rather than stretching. */
@Composable
private fun FigureRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        content(Modifier.weight(1f))
    }
}

@Composable
private fun EmptyArea(text: String) {
    Text(text, fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp, color = KC.Muted)
}

@Composable
private fun FeedingCard(r: InsightReport, previous: String, openDay: (LocalDate) -> Unit) {
    val f = r.feeding
    InsightCard("Feeding", f.loggedDays, r.days) {
        if (f.loggedDays == 0) {
            EmptyArea("No feeds logged in these ${r.days} days.")
            return@InsightCard
        }

        FigureRow { m ->
            InsightFigure("Feeds a day", oneDecimal(f.feedsPerDay.current), null, f.feedsPerDay, previous, m)
            InsightFigure(
                "Typical gap", f.typicalGapMinutes?.let(::hoursMinutes) ?: "Not enough", null, null, previous, m,
            )
        }

        val amounts = listOfNotNull(
            f.bottleMlPerDay.current?.let { Triple("Bottle a day", "${it.roundToInt()}", "ml") to f.bottleMlPerDay },
            f.breastMinutesPerDay.current?.let { Triple("Breastfeeding a day", "${it.roundToInt()}", "min") to f.breastMinutesPerDay },
            f.solidsGramsPerDay.current?.let { Triple("Solids a day", "${it.roundToInt()}", "g") to f.solidsGramsPerDay },
        )
        amounts.chunked(2).forEach { pair ->
            FigureRow { m ->
                pair.forEach { (spec, metric) ->
                    InsightFigure(spec.first, spec.second, spec.third, metric, previous, m)
                }
                if (pair.size == 1) Spacer(m)
            }
        }

        DailyColumns(
            days = f.daily,
            color = KC.ChartGold,
            describe = { "${it.roundToInt()} ${Fmt.plural(it.roundToInt().toLong(), "feed")}" },
            averageText = "Feeds each day",
            onOpenDay = openDay,
        )

        // Only worth a bar when there is more than one kind to split between.
        if (f.mix.values.count { it > 0 } > 1) {
            SubHeading("Feed type")
            SplitBar(FeedType.entries.map { Share(it.label, f.mix[it]?.toDouble() ?: 0.0) })
        }

        f.leftShare?.let { left ->
            SubHeading("Breast side")
            SplitBar(listOf(Share("Left", left), Share("Right", 1 - left)))
        }
    }
}

@Composable
private fun SleepCard(r: InsightReport, previous: String, openDay: (LocalDate) -> Unit) {
    val s = r.sleep
    InsightCard("Sleep", s.loggedDays, r.days) {
        if (s.loggedDays == 0) {
            EmptyArea("No finished sleeps logged in these ${r.days} days.")
            return@InsightCard
        }

        FigureRow { m ->
            InsightFigure(
                "Sleep a day", s.totalMinutesPerDay.current?.let(::hoursMinutes) ?: "", null,
                s.totalMinutesPerDay, previous, m,
            )
            InsightFigure("Sleeps a day", oneDecimal(s.napsPerDay.current), null, s.napsPerDay, previous, m)
        }
        FigureRow { m ->
            InsightFigure("Average sleep", s.averageSleepMinutes?.let(::hoursMinutes) ?: "", null, null, previous, m)
            InsightFigure("Longest sleep", s.longestMinutes?.let(::hoursMinutes) ?: "", null, null, previous, m)
        }

        DailyColumns(
            days = s.daily,
            color = KC.ChartCoral,
            describe = { hoursMinutes(it * 60) },
            averageText = "Hours asleep each day",
            onOpenDay = openDay,
        )

        val day = s.dayMinutesPerDay ?: 0.0
        val night = s.nightMinutesPerDay ?: 0.0
        if (day + night > 0) {
            SubHeading("Day and night, 7am to 7pm")
            SplitBar(listOf(Share("Day", day), Share("Night", night)))
        }
    }
}

@Composable
private fun DiaperCard(r: InsightReport, previous: String, openDay: (LocalDate) -> Unit) {
    val d = r.diapers
    InsightCard("Diapers", d.loggedDays, r.days) {
        if (d.loggedDays == 0) {
            EmptyArea("No diapers logged in these ${r.days} days.")
            return@InsightCard
        }
        FigureRow { m ->
            InsightFigure("Diapers a day", oneDecimal(d.perDay.current), null, d.perDay, previous, m)
            InsightFigure("Wet a day", oneDecimal(d.wetPerDay), null, null, previous, m)
        }
        FigureRow { m ->
            InsightFigure("Dirty a day", oneDecimal(d.dirtyPerDay), null, null, previous, m)
            Spacer(m)
        }
        DailyColumns(
            days = d.daily,
            color = KC.ChartSea,
            describe = { "${it.roundToInt()} ${Fmt.plural(it.roundToInt().toLong(), "diaper")}" },
            averageText = "Diapers each day",
            onOpenDay = openDay,
        )
    }
}

@Composable
private fun MedicineCard(r: InsightReport) {
    InsightCard("Medicine", null, r.days) {
        Text(
            "Days a dose was recorded, out of the days each medicine was due.",
            fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp, color = KC.Muted,
        )
        r.medicines.forEach { Meter(it.name, it.taken, it.expected) }
    }
}

@Composable
private fun GrowthCard(gramsPerWeek: Double, since: java.time.LocalDate, metricUnits: Boolean) {
    KCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Growth", fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = KC.Ink)
            // Ounces rather than pounds: a week's gain in pounds would round to nothing.
            val shown = if (metricUnits) gramsPerWeek else gramsPerWeek / Units.G_PER_OZ
            val rounded = shown.roundToInt()
            InsightFigure(
                label = "Weight change a week",
                value = (if (rounded > 0) "+" else if (rounded < 0) "−" else "") + abs(rounded),
                unit = if (metricUnits) "g" else "oz",
                metric = null,
                previousLabel = "",
            )
            Text(
                "Measured from the weigh-in on ${Fmt.date(since)}.",
                fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
            )
        }
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(text, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.MutedStrong)
}

private fun oneDecimal(value: Double?): String {
    if (value == null) return ""
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun hoursMinutes(minutes: Double): String {
    val total = minutes.roundToInt()
    val h = total / 60
    val m = total % 60
    return when {
        h == 0 -> "$m m"
        m == 0 -> "$h h"
        else -> "$h h $m m"
    }
}
