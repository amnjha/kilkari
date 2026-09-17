package com.kilkari.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.domain.DayValue
import com.kilkari.domain.Fmt
import com.kilkari.domain.Metric
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Chart pieces for the insights screen.
 *
 * Built to the dataviz mark specs rather than by taste: thin columns with a rounded data end
 * and a square baseline, a surface gap rather than a stroke between marks, hairline solid grid,
 * and text that always wears a text colour, never the series colour. Every figure a chart shows
 * is also reachable without the chart, so a tooltip never gates a value.
 */

private val HEADER_HEIGHT = 36.dp
private val PLOT_HEIGHT = 96.dp
private val MAX_COLUMN = 24.dp
private val SURFACE_GAP = 2.dp

/**
 * A headline figure: what it is, the number, and how it moved against the window before.
 *
 * The change is shown in ink with a direction word, not green or red. More feeds or less sleep
 * is not good or bad in itself, and colouring it would be a judgement the data cannot make.
 */
@Composable
fun InsightFigure(
    label: String,
    value: String,
    unit: String?,
    metric: Metric?,
    previousLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontFamily = Sans, fontSize = 12.sp, color = KC.Muted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // The sans face, semibold, proportional figures: a display face on a figure reads
            // as decoration rather than data.
            Text(value, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = KC.Ink)
            if (unit != null) {
                Text(
                    unit, modifier = Modifier.padding(bottom = 3.dp),
                    fontFamily = Sans, fontSize = 12.sp, color = KC.Muted,
                )
            }
        }
        changeText(metric, previousLabel)?.let {
            Text(it, fontFamily = Sans, fontSize = 11.sp, color = KC.MutedStrong)
        }
    }
}

/** "Up 12% on the previous 7 days", or null when there is nothing honest to compare. */
fun changeText(metric: Metric?, previousLabel: String): String? {
    val change = metric?.change ?: return null
    val percent = (abs(change) * 100).roundToInt()
    return when {
        percent == 0 -> "Same as the $previousLabel"
        change > 0 -> "Up $percent% on the $previousLabel"
        else -> "Down $percent% on the $previousLabel"
    }
}

/**
 * One value per day as columns, one series, one colour.
 *
 * A day with nothing logged has no column rather than a zero one: an empty slot and a zero are
 * different things, and for feeds, sleep or changes a logged day is never actually zero.
 *
 * Tapping or dragging anywhere selects the nearest day, because thirty columns across a phone
 * are too narrow to aim at. The header then reads that day; otherwise it reads the average.
 * With [onOpenDay], a selected day and each table row lead to that day's entries, so a day
 * that stands out can be looked into rather than only noticed.
 */
@Composable
fun DailyColumns(
    days: List<DayValue>,
    color: Color,
    describe: (Double) -> String,
    averageText: String,
    modifier: Modifier = Modifier,
    onOpenDay: ((LocalDate) -> Unit)? = null,
) {
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val top = niceMax(days.mapNotNull { it.value }.maxOrNull() ?: 0.0)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val pick = selected?.let { days.getOrNull(it) }
        // A fixed height, so the link appearing on selection does not push the plot down.
        Row(
            Modifier.fillMaxWidth().height(HEADER_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    pick == null -> averageText
                    pick.value == null -> "${Fmt.dayAndDate(pick.date)}: not logged"
                    else -> "${Fmt.dayAndDate(pick.date)}: ${describe(pick.value)}"
                },
                modifier = Modifier.weight(1f),
                fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Ink,
            )
            if (pick != null && onOpenDay != null) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onOpenDay(pick.date) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Open day",
                        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = KC.Coral,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            // The axis carries the one tick worth reading: the top of the scale.
            Column(
                Modifier.width(28.dp).height(PLOT_HEIGHT),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(tick(top), fontFamily = Sans, fontSize = 10.sp, color = KC.Muted)
                Text("0", fontFamily = Sans, fontSize = 10.sp, color = KC.Muted)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(PLOT_HEIGHT)
                    .semantics { contentDescription = averageText }
                    .pointerInput(days) {
                        detectTapGestures { offset ->
                            selected = indexAt(offset.x, size.width.toFloat(), days.size)
                        }
                    }
                    .pointerInput(days) {
                        detectHorizontalDragGestures(onDragEnd = {}) { change, _ ->
                            selected = indexAt(change.position.x, size.width.toFloat(), days.size)
                        }
                    },
            ) {
                val slot = size.width / days.size.coerceAtLeast(1)
                val gap = SURFACE_GAP.toPx()
                val barWidth = minOf(MAX_COLUMN.toPx(), (slot - gap).coerceAtLeast(1f))
                val radius = minOf(4.dp.toPx(), barWidth / 2f)

                // Hairline, solid, recessive: the top of the scale and the baseline.
                drawLine(KC.ChartGrid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
                drawLine(KC.ChartGrid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)

                days.forEachIndexed { i, day ->
                    val v = day.value ?: return@forEachIndexed
                    if (top <= 0.0) return@forEachIndexed
                    val h = (v / top).toFloat() * size.height
                    val left = slot * i + (slot - barWidth) / 2f
                    val emphasised = selected == null || selected == i
                    val path = Path().apply {
                        // Rounded at the data end, square where it meets the baseline.
                        addRoundRect(
                            RoundRect(
                                left = left, top = size.height - h, right = left + barWidth, bottom = size.height,
                                topLeftCornerRadius = CornerRadius(radius),
                                topRightCornerRadius = CornerRadius(radius),
                                bottomLeftCornerRadius = CornerRadius.Zero,
                                bottomRightCornerRadius = CornerRadius.Zero,
                            )
                        )
                    }
                    drawPath(path, color.copy(alpha = if (emphasised) 1f else 0.35f))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.firstOrNull()?.let { Text(Fmt.date(it.date), fontFamily = Sans, fontSize = 10.sp, color = KC.Muted) }
            days.lastOrNull()?.let { Text(Fmt.date(it.date), fontFamily = Sans, fontSize = 10.sp, color = KC.Muted) }
        }

        DayTable(days, describe, onOpenDay)
    }
}

/**
 * Every day's figure as rows, folded away by default.
 *
 * The chart's equivalent in plain text: nothing is readable only by tapping a column.
 */
@Composable
private fun DayTable(days: List<DayValue>, describe: (Double) -> String, onOpenDay: ((LocalDate) -> Unit)?) {
    var open by remember(days) { mutableStateOf(false) }
    Text(
        if (open) "Hide day by day" else "Show day by day",
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable { open = !open }
            .padding(vertical = 6.dp),
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = KC.Coral,
    )
    if (open) {
        Column {
            days.asReversed().forEach { day ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .let { row -> if (onOpenDay != null) row.clickable { onOpenDay(day.date) } else row }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(Fmt.dayAndDate(day.date), fontFamily = Sans, fontSize = 12.sp, color = KC.MutedStrong)
                    Text(
                        day.value?.let(describe) ?: "not logged",
                        fontFamily = Sans, fontSize = 12.sp,
                        color = if (day.value == null) KC.Faint else KC.Ink,
                    )
                }
            }
        }
    }
}

/** One part of a whole, and what it is called. */
data class Share(val label: String, val amount: Double)

/**
 * Part-to-whole as one thin bar, split by a surface gap, with a legend beneath.
 *
 * The legend is the identity channel, so the colours never have to be matched by eye alone,
 * and it carries the percentages: a figure squeezed inside a narrow segment would be clipped.
 */
@Composable
fun SplitBar(shares: List<Share>, modifier: Modifier = Modifier) {
    val total = shares.sumOf { it.amount }
    if (total <= 0.0) return
    val visible = shares.filter { it.amount > 0 }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val gap = SURFACE_GAP.toPx()
            val usable = size.width - gap * (visible.size - 1).coerceAtLeast(0)
            var x = 0f
            visible.forEachIndexed { i, share ->
                val w = (share.amount / total).toFloat() * usable
                val colour = KC.chartCategorical[shares.indexOf(share)]
                drawRoundRect(colour, Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2f))
                x += w + gap
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            shares.forEachIndexed { i, share ->
                if (share.amount <= 0) return@forEachIndexed
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(KC.chartCategorical[i]))
                    Text(
                        "${share.label} ${((share.amount / total) * 100).roundToInt()}%",
                        fontFamily = Sans, fontSize = 12.sp, color = KC.MutedStrong,
                    )
                }
            }
        }
    }
}

/**
 * A ratio against its limit: doses given out of the days they were due.
 *
 * The track is a light step of the fill's own ramp rather than grey, so the whole bar reads as
 * one measure. The count is written out beside it, so the bar is never the only way to read it.
 */
@Composable
fun Meter(label: String, taken: Int, expected: Int, modifier: Modifier = Modifier) {
    val fraction = if (expected <= 0) 0f else (taken.toFloat() / expected).coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KC.Ink)
            Text(
                "$taken of $expected ${Fmt.plural(expected.toLong(), "day")}",
                fontFamily = Sans, fontSize = 12.sp, color = KC.MutedStrong,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(KC.ChartSeaTrack, cornerRadius = r)
            if (fraction > 0f) drawRoundRect(KC.ChartSea, size = Size(size.width * fraction, size.height), cornerRadius = r)
        }
    }
}

private fun indexAt(x: Float, width: Float, count: Int): Int? {
    if (count == 0 || width <= 0f) return null
    return ((x / width) * count).toInt().coerceIn(0, count - 1)
}

/** Rounds the top of the scale up to a clean number, so the one tick is readable. */
private fun niceMax(value: Double): Double {
    if (value <= 0.0) return 0.0
    val step = when {
        value <= 5 -> 1.0
        value <= 12 -> 2.0
        value <= 30 -> 5.0
        value <= 120 -> 20.0
        value <= 600 -> 100.0
        else -> 250.0
    }
    return ceil(value / step) * step
}

private fun tick(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
