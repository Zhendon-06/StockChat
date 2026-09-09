package com.guet.liang.kuiklychart.finance

import kotlin.math.abs

import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.line
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.path
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.rect
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.text

internal fun FinancialRenderFrame.drawCloseLine(forecastProgress: Float) {
    val boundary = s.forecastStartIndex ?: s.points.size
    // Keep the final axis range stable while revealing only the forecast geometry.
    val revealEnd = (boundary - 1) + (s.points.size - boundary) * forecastProgress.coerceIn(0f, 1f)
    val forecastIndices = indices.filter { it >= boundary }
    fun segmentProgress(a: Int): Float = (revealEnd - a).coerceIn(0f, 1f)
    if (forecastIndices.isNotEmpty()) {
        val boundaryX = x((boundary - 1).coerceAtLeast(indices.first())).coerceIn(left, right)
        rect(c, boundaryX, top, (right - boundaryX) * forecastProgress, bottom - top, s.forecastColor.opacity(0.05f))
        line(c, boundaryX, top, boundaryX, bottom, s.forecastColor, dashed = true)
        text(c, "AI 预测", right - 3f, top + 12f, s.forecastColor, alignRight = true, size = 10f)
        // Only adjacent, explicitly supplied intervals form a band; missing bounds stay gaps.
        forecastIndices.zipWithNext().forEach { (a, b) ->
            val first = forecastIntervals[a]
            val second = forecastIntervals[b]
            val progress = segmentProgress(a)
            if (first != null && second != null && progress > 0f) {
                val endX = x(a) + (x(b) - x(a)) * progress
                c.beginPath()
                c.moveTo(x(a), y(first.upper))
                c.lineTo(endX, y(first.upper + (second.upper - first.upper) * progress))
                c.lineTo(endX, y(first.lower + (second.lower - first.lower) * progress))
                c.lineTo(x(a), y(first.lower))
                c.closePath()
                c.fillStyle(s.forecastColor.opacity(0.14f))
                c.fill()
            }
        }
    }
    path(c, indices.filter { it < boundary }, { x(it) }, { y(s.points[it].close) }, s.lineColor, 1.6f)
    indices.filter { it >= (boundary - 1).coerceAtLeast(0) }.zipWithNext().forEach { (a, b) ->
        val progress = segmentProgress(a)
        if (progress > 0f) line(c, x(a), y(s.points[a].close),
            x(a) + (x(b) - x(a)) * progress,
            y(s.points[a].close + (s.points[b].close - s.points[a].close) * progress),
            s.forecastColor, dashed = true)
    }
}

internal fun FinancialRenderFrame.drawIntraday() {
    // Fill only the elapsed exchange session. Missing future data is never extrapolated.
    c.beginPath()
    c.moveTo(x(indices.first()), bottom)
    indices.forEach { c.lineTo(x(it), y(s.points[it].close)) }
    c.lineTo(x(indices.last()), bottom)
    c.closePath()
    c.fillStyle(s.lineColor.opacity(0.08f))
    c.fill()
    path(c, indices, { x(it) }, { s.points[it].close.let(::y) }, s.lineColor, 1.3f)
    path(c, indices, ::x,
        { s.points[it].average?.takeIf { value -> value.isFinite() && value > 0f }?.let(::y) },
        s.averageColor, 1.2f)
    s.previousClose?.takeIf { it in low..high }?.let {
        line(c, left, y(it), right, y(it), s.mutedColor, dashed = true)
    }
}

internal fun FinancialRenderFrame.drawCandles() {
    val bodyWidth = ((right - left) / count * 0.66f).coerceAtLeast(0.7f)
    indices.forEach { index ->
        val p = s.points[index]
        val color = if (p.close >= p.open) s.riseColor else s.fallColor
        line(c, x(index), y(p.high), x(index), y(p.low), color)
        val bodyTop = y(maxOf(p.open, p.close))
        rect(c, x(index) - bodyWidth / 2, bodyTop, bodyWidth, maxOf(1f, abs(y(p.open) - y(p.close))), color)
    }
    averages.forEachIndexed { index, (_, values) ->
        path(c, indices, { x(it) }, { values[it]?.let(::y) }, s.averageColors.getOrElse(index) { s.averageColor }, 1.1f)
    }
    listOf(indices.maxBy { s.points[it].high } to true, indices.minBy { s.points[it].low } to false).forEach { (i, isHigh) ->
        val value = if (isHigh) s.points[i].high else s.points[i].low
        val xx = x(i).coerceIn(left + 26f, right - 26f)
        text(c, financialNumber(value), xx, y(value) + if (isHigh) -5f else 13f, s.textColor, center = true, size = 11f)
    }
    val lastY = y(points.last().close)
    line(c, left, lastY, right, lastY, s.averageColor, dashed = true)
}

