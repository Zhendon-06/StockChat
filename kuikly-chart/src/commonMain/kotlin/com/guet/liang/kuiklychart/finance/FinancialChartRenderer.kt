package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.abs

internal object FinancialChartRenderer {
    fun draw(c: CanvasContext, width: Float, height: Float, s: FinancialChartSpec, viewport: FinancialViewport, selected: Int, forecastProgress: Float = 1f, evidenceRange: IntRange? = null) {
        val lineMode = s.mode == FinancialChartMode.INTRADAY
        val closeLine = s.mode == FinancialChartMode.CLOSE_LINE
        // Fractional viewport: candles cut by either plot edge are still drawn so pans stay continuous.
        val indices = if (lineMode) s.points.indices.toList() else viewport.visibleIndices(s.points.size).toList()
        val count = viewport.count.coerceAtLeast(1f)
        val points = indices.map { s.points[it] }
        val forecastIntervals = if (closeLine) s.forecastIntervals.filter { (index, interval) ->
            index in s.points.indices && s.forecastStartIndex?.let { index >= it } == true &&
                FinancialChartMath.validInterval(interval, s.points[index].close)
        } else emptyMap()
        rect(c, 0f, 0f, width, height, s.backgroundColor)
        if (points.isEmpty() || width < 50 || height < 150) {
            text(c, "暂无该周期行情", width / 2f, height / 2f, s.mutedColor, center = true)
            return
        }
        val left = 6f
        val right = width - 6f
        val top = 62f
        val bottom = height - if (s.showVolume) 121f else 27f
        val volumeTop = bottom + 49f
        val volumeBottom = height - 10f
        val averages = if (lineMode || closeLine) emptyList() else s.movingAveragePeriods.filter { it > 0 }.take(3)
            .map { it to FinancialChartMath.movingAverage(s.points, it) }
        var (low, high) = FinancialChartMath.priceRange(points, s.previousClose, lineMode)
        // Include visible MA values in the scale, including long windows before the viewport.
        if (!lineMode) averages.forEach { (_, values) ->
            indices.mapNotNull { values[it] }.forEach { low = minOf(low, it); high = maxOf(high, it) }
        }
        if (closeLine) {
            indices.mapNotNull { forecastIntervals[it] }.forEach {
                low = minOf(low, it.lower); high = maxOf(high, it.upper)
            }
            val padding = (high - low) * 0.04f
            low -= padding; high += padding
        }
        fun x(index: Int): Float = left + if (lineMode) {
            s.points[index].slot / s.sessionSlots.coerceAtLeast(1f) * (right - left)
        } else viewport.ratioOf(index) * (right - left)
        fun y(value: Float): Float = top + (high - value) / (high - low).coerceAtLeast(0.0001f) * (bottom - top)
        fun movement(value: Float): Color = when {
            s.previousClose == null -> s.mutedColor
            value > s.previousClose!! -> s.riseColor
            value < s.previousClose!! -> s.fallColor
            else -> s.mutedColor
        }
        // Shade the same exchange interval in both panes, preserving red/green candle bodies.
        val evidence = evidenceRange?.takeIf {
            s.mode == FinancialChartMode.CANDLES && !it.isEmpty() && it.first >= 0 && it.last < s.points.size
        }
        if (evidence != null) {
            val visible = indices.filter { it in evidence }
            if (visible.isNotEmpty()) {
                val halfSlot = (right - left) / count / 2f
                val x1 = (x(visible.first()) - halfSlot).coerceAtLeast(left)
                val x2 = (x(visible.last()) + halfSlot).coerceAtMost(right)
                rect(c, x1, top, x2 - x1, bottom - top, s.evidenceColor.opacity(0.13f))
                if (s.showVolume) rect(c, x1, volumeTop, x2 - x1, volumeBottom - volumeTop, s.evidenceColor.opacity(0.13f))
                line(c, x1, top, x1, if (s.showVolume) volumeBottom else bottom, s.evidenceColor, dashed = true)
                line(c, x2, top, x2, if (s.showVolume) volumeBottom else bottom, s.evidenceColor, dashed = true)
                text(c, "依据区间", x1 + 3f, top + 13f, s.evidenceColor, size = 10f)
            }
        }
        val currentIndex = selected.takeIf { it in indices } ?: indices.last()
        val current = s.points[currentIndex]
        text(c, current.label + if (selected >= 0) "  · 再点取消" else "", left, 12f, s.mutedColor, size = 10f)
        if (closeLine) {
            val forecast = s.forecastStartIndex?.let { currentIndex >= it } == true
            text(c, (if (forecast) "AI 估计 " else "历史收盘 ") + financialNumber(current.close), left, 29f,
                if (forecast) s.forecastColor else s.lineColor, size = 11f)
            val interval = forecastIntervals[currentIndex]
            if (forecast) text(c, interval?.let { "区间 ${financialNumber(it.lower)}–${financialNumber(it.upper)}" } ?: "未提供预测区间",
                right, 45f, s.mutedColor, alignRight = true, size = 10f)
        } else if (lineMode) {
            text(c, "${s.valueLabel} ${financialNumber(current.close)}", left, 29f, s.lineColor, size = 11f)
            if (points.any { it.average != null }) text(c, "均价 ${current.average?.let(::financialNumber) ?: "--"}", right, 29f, s.averageColor, alignRight = true, size = 11f)
        } else {
            text(c, "开 ${financialNumber(current.open)}  高 ${financialNumber(current.high)}", left, 28f, s.textColor, size = 10f)
            text(c, "低 ${financialNumber(current.low)}  收 ${financialNumber(current.close)}", right, 28f, s.textColor, alignRight = true, size = 10f)
        }
        if (closeLine) text(c, if (s.forecastStartIndex != null) "实线 历史  /  虚线 AI" else "历史收盘 · 日线", left, 45f, s.mutedColor, size = 10f)
        else if (!lineMode) averages.forEachIndexed { i, (period, values) ->
            text(c, "MA$period:${values[currentIndex]?.let(::financialNumber) ?: "--"}",
                left + (right - left) * i / 3f, 45f, s.averageColors.getOrElse(i) { s.averageColor }, size = 10f)
        } else text(c, if (points.any { it.average != null }) "蓝线 价格  /  黄线 成交均价" else s.intradayDescription, left, 45f, s.mutedColor, size = 10f)

        repeat(5) { tick ->
            val value = high - (high - low) * tick / 4f
            val yy = y(value)
            line(c, left, yy, right, yy, s.gridColor, dashed = true)
            text(c, financialNumber(value), left + 2, yy + if (tick == 4) -4f else 11f,
                if (lineMode) movement(value) else s.mutedColor, size = 10f)
            if (lineMode && s.previousClose != null && s.previousClose!! > 0) {
                val percent = (value - s.previousClose!!) / s.previousClose!! * 100f
                text(c, (if (percent > 0) "+" else "") + financialNumber(percent) + "%", right - 2,
                    yy + if (tick == 4) -4f else 11f, movement(value), alignRight = true, size = 10f)
            }
        }
        repeat(5) { tick ->
            val xx = left + (right - left) * tick / 4f
            line(c, xx, top, xx, bottom, s.gridColor)
            if (s.showVolume) line(c, xx, volumeTop, xx, volumeBottom, s.gridColor)
        }
        if (closeLine) {
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
                    val first = forecastIntervals[a]; val second = forecastIntervals[b]
                    val progress = segmentProgress(a)
                    if (first != null && second != null && progress > 0f) {
                        val endX = x(a) + (x(b) - x(a)) * progress
                        c.beginPath(); c.moveTo(x(a), y(first.upper))
                        c.lineTo(endX, y(first.upper + (second.upper - first.upper) * progress))
                        c.lineTo(endX, y(first.lower + (second.lower - first.lower) * progress)); c.lineTo(x(a), y(first.lower)); c.closePath()
                        c.fillStyle(s.forecastColor.opacity(0.14f)); c.fill()
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
        } else if (lineMode) {
            // Fill only the elapsed exchange session. Missing future data is never extrapolated.
            c.beginPath(); c.moveTo(x(indices.first()), bottom)
            indices.forEach { c.lineTo(x(it), y(s.points[it].close)) }
            c.lineTo(x(indices.last()), bottom); c.closePath()
            c.fillStyle(s.lineColor.opacity(0.08f)); c.fill()
            path(c, indices, { x(it) }, { s.points[it].close.let(::y) }, s.lineColor, 1.3f)
            path(c, indices, { x(it) }, { s.points[it].average?.takeIf { value -> value.isFinite() && value > 0f }?.let(::y) }, s.averageColor, 1.2f)
            s.previousClose?.takeIf { it in low..high }?.let {
                line(c, left, y(it), right, y(it), s.mutedColor, dashed = true)
            }
        } else {
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
        val labels = if (lineMode) s.sessionLabels.map { it.slot / s.sessionSlots.coerceAtLeast(1f) to it.text }
            else listOf(0f, 0.5f, 1f).map { fraction ->
                // Anchor time labels to the candle actually under each position, not to partially visible edge candles.
                fraction to s.points[viewport.indexAt(fraction, s.points.size).coerceIn(indices.first(), indices.last())].label
            }
        labels.forEach { (fraction, label) ->
            text(c, label, left + (right - left) * fraction, bottom + 17f, s.mutedColor,
                center = fraction > 0f && fraction < 1f, alignRight = fraction == 1f, size = 10f)
        }
        if (s.showVolume) {
        text(c, "成交量  ${current.volume?.let(::financialVolume) ?: "--"}${s.volumeUnit}", left, bottom + 38f, s.mutedColor, size = 11f)
        val maxVolume = points.mapNotNull { it.volume }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val barWidth = if (lineMode) ((right - left) / s.sessionSlots.coerceAtLeast(1f) * 0.7f).coerceAtLeast(0.5f)
            else ((right - left) / count * 0.66f).coerceAtLeast(0.7f)
        indices.forEach { i ->
            val p = s.points[i]
            p.volume?.let { volume ->
                val barHeight = volume / maxVolume * (volumeBottom - volumeTop)
                val previous = if (lineMode) s.points.getOrNull(i - 1)?.close ?: s.previousClose ?: p.open else p.open
                rect(c, x(i) - barWidth / 2f, volumeBottom - barHeight, barWidth, barHeight,
                    if (p.close >= previous) s.riseColor else s.fallColor)
            }
        }
        }
        if (selected in indices) {
            val xx = x(selected)
            val yy = y(s.points[selected].close)
            line(c, xx, top, xx, if (s.showVolume) volumeBottom else bottom, s.mutedColor, dashed = true)
            line(c, left, yy, right, yy, s.mutedColor, dashed = true)
            val label = financialNumber(s.points[selected].close)
            rect(c, right - 66f, (yy - 10f).coerceIn(top, bottom - 20f), 66f, 20f, s.textColor)
            text(c, label, right - 4f, (yy - 10f).coerceIn(top, bottom - 20f) + 14f, s.backgroundColor, alignRight = true)
        }
    }

    internal fun path(c: CanvasContext, indices: List<Int>, x: (Int) -> Float, y: (Int) -> Float?, color: Color, width: Float) {
        var started = false
        c.beginPath()
        indices.forEach { i ->
            val yy = y(i)
            if (yy == null) started = false else {
                if (!started) c.moveTo(x(i), yy) else c.lineTo(x(i), yy)
                started = true
            }
        }
        c.strokeStyle(color); c.lineWidth(width); c.stroke()
    }

    internal fun line(c: CanvasContext, x1: Float, y1: Float, x2: Float, y2: Float, color: Color, dashed: Boolean = false) {
        c.beginPath(); c.strokeStyle(color); c.lineWidth(0.6f)
        if (!dashed) { c.moveTo(x1, y1); c.lineTo(x2, y2) } else {
            val length = maxOf(abs(x2 - x1), abs(y2 - y1)).coerceAtLeast(1f)
            var distance = 0f
            while (distance < length) {
                val end = (distance + 4f).coerceAtMost(length)
                c.moveTo(x1 + (x2 - x1) * distance / length, y1 + (y2 - y1) * distance / length)
                c.lineTo(x1 + (x2 - x1) * end / length, y1 + (y2 - y1) * end / length)
                distance += 8f
            }
        }
        c.stroke()
    }

    internal fun rect(c: CanvasContext, x: Float, y: Float, width: Float, height: Float, color: Color) {
        c.beginPath(); c.moveTo(x, y); c.lineTo(x + width, y); c.lineTo(x + width, y + height)
        c.lineTo(x, y + height); c.closePath(); c.fillStyle(color); c.fill()
    }

    internal fun text(c: CanvasContext, value: String, x: Float, y: Float, color: Color,
        center: Boolean = false, alignRight: Boolean = false, size: Float = 11f) {
        c.font(size); c.fillStyle(color); c.textAlign(TextAlign.LEFT)
        val measured = if (center || alignRight) c.measureText(value).width else 0f
        c.fillText(value, x - if (center) measured / 2f else measured, y)
    }
}
