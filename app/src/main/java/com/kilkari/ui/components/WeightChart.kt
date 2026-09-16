package com.kilkari.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.domain.Fmt
import com.kilkari.domain.GrowthStandards
import com.kilkari.domain.Sex
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** One recorded weight, placed by the child's age when it was taken. */
data class WeightPoint(val ageMonths: Double, val kg: Double)

private const val CHART_HEIGHT_DP = 180
private const val LEFT_GUTTER = 34f
private const val BOTTOM_GUTTER = 22f
private const val TOP_PAD = 10f

/**
 * Weight against age, with the WHO median behind it.
 *
 * A bar per reading said nothing useful: bars measure quantities you compare side by side, and
 * these are one quantity moving over time, unevenly spaced. A line shows the shape — which is
 * the whole question with growth — and gives the reference curve something to be read against.
 */
@Composable
fun WeightChart(points: List<WeightPoint>, sex: Sex?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Sans, fontSize = 10.sp, color = KC.Muted)

    // Always show a few months even for a newborn with one reading, so the first point is not
    // stranded against the left edge of an axis with no span.
    val oldest = points.maxOfOrNull { it.ageMonths } ?: 0.0
    val xMax = ceil(maxOf(oldest, 3.0) * 1.15).coerceAtMost(GrowthStandards.MAX_MONTHS.toDouble())

    val reference = (0..xMax.roundToInt()).mapNotNull { m ->
        GrowthStandards.medianWeightKg(m.toDouble(), sex)?.let { WeightPoint(m.toDouble(), it) }
    }

    val all = points.map { it.kg } + reference.map { it.kg }
    val (yLow, yHigh, yStep) = niceScale(
        (all.minOrNull() ?: 0.0) - 0.4,
        (all.maxOrNull() ?: 10.0) + 0.4,
    )

    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
        val plotLeft = LEFT_GUTTER.dp.toPx()
        val plotBottom = size.height - BOTTOM_GUTTER.dp.toPx()
        val plotTop = TOP_PAD.dp.toPx()
        val plotWidth = size.width - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun x(months: Double) = plotLeft + (months / xMax).toFloat() * plotWidth
        fun y(kg: Double) = plotBottom - ((kg - yLow) / (yHigh - yLow)).toFloat() * plotHeight

        drawGrid(measurer, labelStyle, yLow, yHigh, yStep, plotLeft, plotTop, plotBottom, size.width, ::y)
        drawMonthLabels(measurer, labelStyle, xMax, plotBottom, ::x)

        // The reference first, so a child's own line is never hidden behind it.
        if (reference.size >= 2) {
            drawPolyline(
                reference.map { Offset(x(it.ageMonths), y(it.kg)) },
                color = KC.Sea,
                strokeWidth = 2.dp.toPx(),
                dashed = true,
            )
        }

        val plotted = points.sortedBy { it.ageMonths }.map { Offset(x(it.ageMonths), y(it.kg)) }
        if (plotted.size >= 2) {
            drawPolyline(plotted, color = KC.Coral, strokeWidth = 2.5.dp.toPx(), dashed = false)
        }
        plotted.forEach { at ->
            drawCircle(KC.Screen, radius = 4.5.dp.toPx(), center = at)
            drawCircle(KC.Coral, radius = 3.dp.toPx(), center = at)
        }
    }
}

/** Which line is which. Without it the dotted curve is just an unexplained second line. */
@Composable
fun WeightChartLegend(childName: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendKey(childName, KC.Coral, dashed = false)
        LegendKey("WHO median", KC.Sea, dashed = true)
    }
}

@Composable
private fun LegendKey(label: String, color: Color, dashed: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                } else {
                    null
                },
            )
        }
        Box { androidx.compose.material3.Text(label, fontFamily = Sans, fontSize = 11.sp, color = KC.Muted) }
    }
}

private fun DrawScope.drawPolyline(
    at: List<Offset>,
    color: Color,
    strokeWidth: Float,
    dashed: Boolean,
) {
    if (at.size < 2) return
    val path = Path().apply {
        moveTo(at.first().x, at.first().y)
        at.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            } else {
                null
            },
        ),
    )
}

/**
 * An axis that lands on round kilograms.
 *
 * Dividing the data's own range into equal parts gives ticks like 3.8 and 5.5, which are
 * unreadable as a scale — the step is chosen from familiar sizes first, then the range widened
 * to fit whole multiples of it.
 */
private fun niceScale(low: Double, high: Double): Triple<Double, Double, Double> {
    val span = (high - low).coerceAtLeast(1.0)
    val step = listOf(0.5, 1.0, 2.0, 2.5, 5.0, 10.0).firstOrNull { span / it <= 5 } ?: 20.0
    val bottom = (floor(low / step) * step).coerceAtLeast(0.0)
    val top = ceil(high / step) * step
    return Triple(bottom, top, step)
}

/** Horizontal rules with their kilogram value, enough to read a weight off without clutter. */
private fun DrawScope.drawGrid(
    measurer: TextMeasurer,
    style: TextStyle,
    yLow: Double,
    yHigh: Double,
    yStep: Double,
    plotLeft: Float,
    plotTop: Float,
    plotBottom: Float,
    width: Float,
    y: (Double) -> Float,
) {
    val steps = ((yHigh - yLow) / yStep).roundToInt().coerceAtLeast(1)
    repeat(steps + 1) { i ->
        val kg = yLow + yStep * i
        val at = y(kg).coerceIn(plotTop, plotBottom)
        drawLine(
            color = KC.Border,
            start = Offset(plotLeft, at),
            end = Offset(width, at),
            strokeWidth = 1f,
        )
        val label = measurer.measure(Fmt.trimNum(kg), style)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(0f, at - label.size.height / 2f),
        )
    }
}

/** Age ticks in whole months, thinned out so the labels never collide. */
private fun DrawScope.drawMonthLabels(
    measurer: TextMeasurer,
    style: TextStyle,
    xMax: Double,
    plotBottom: Float,
    x: (Double) -> Float,
) {
    val months = xMax.roundToInt()
    val every = when {
        months <= 6 -> 1
        months <= 12 -> 2
        else -> 4
    }
    (0..months step every).forEach { m ->
        val label = measurer.measure(if (m == 0) "Birth" else "${m}m", style)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                (x(m.toDouble()) - label.size.width / 2f).coerceAtMost(size.width - label.size.width),
                plotBottom + 5.dp.toPx(),
            ),
        )
    }
}
