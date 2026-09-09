package com.guet.liang.kuiklychart.finance

import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.line
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.rect
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.text

internal fun FinancialRenderFrame.drawTimeLabels() {
    val labels = if (lineMode) s.sessionLabels.map { it.slot / s.sessionSlots.coerceAtLeast(1f) to it.text }
    else listOf(0f, 0.5f, 1f).map { fraction ->
        // Anchor time labels to the candle actually under each position, not to partially visible edge candles.
        fraction to s.points[viewport.indexAt(fraction, s.points.size).coerceIn(indices.first(), indices.last())].label
    }
    labels.forEach { (fraction, label) ->
        text(c, label, left + (right - left) * fraction, bottom + 17f, s.mutedColor,
            center = fraction > 0f && fraction < 1f, alignRight = fraction == 1f, size = 10f)
    }
}

internal fun FinancialRenderFrame.drawVolume() {
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

internal fun FinancialRenderFrame.drawSelection() {
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

