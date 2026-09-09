package com.guet.liang.kuiklychart.finance

import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.line
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.rect
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.text

internal fun FinancialRenderFrame.drawEvidence(evidenceRange: IntRange?) {
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
}

internal fun FinancialRenderFrame.drawHeader() {
    text(c, current.label + if (selected >= 0) "  · 再点取消" else "", left, 12f, s.mutedColor, size = 10f)
    drawCurrentValues()
    drawSeriesLegend()
}

private fun FinancialRenderFrame.drawCurrentValues() {
    if (closeLine) {
        val forecast = s.forecastStartIndex?.let { currentIndex >= it } == true
        text(c, (if (forecast) "AI 估计 " else "历史收盘 ") + financialNumber(current.close), left, 29f,
            if (forecast) s.forecastColor else s.lineColor, size = 11f)
        val interval = forecastIntervals[currentIndex]
        if (forecast) text(c, interval?.let { "区间 ${financialNumber(it.lower)}–${financialNumber(it.upper)}" } ?: "未提供预测区间",
            right, 45f, s.mutedColor, alignRight = true, size = 10f)
    } else if (lineMode) {
        text(c, "${s.valueLabel} ${financialNumber(current.close)}", left, 29f, s.lineColor, size = 11f)
        if (points.any { it.average != null }) {
            text(c, "均价 ${current.average?.let(::financialNumber) ?: "--"}", right, 29f,
                s.averageColor, alignRight = true, size = 11f)
        }
    } else {
        text(c, "开 ${financialNumber(current.open)}  高 ${financialNumber(current.high)}", left, 28f, s.textColor, size = 10f)
        text(c, "低 ${financialNumber(current.low)}  收 ${financialNumber(current.close)}", right, 28f,
            s.textColor, alignRight = true, size = 10f)
    }
}

private fun FinancialRenderFrame.drawSeriesLegend() {
    if (closeLine) text(c, if (s.forecastStartIndex != null) "实线 历史  /  虚线 AI" else "历史收盘 · 日线", left, 45f, s.mutedColor, size = 10f)
    else if (!lineMode) averages.forEachIndexed { i, (period, values) ->
        text(c, "MA$period:${values[currentIndex]?.let(::financialNumber) ?: "--"}",
            left + (right - left) * i / 3f, 45f, s.averageColors.getOrElse(i) { s.averageColor }, size = 10f)
    } else {
        val description = if (points.any { it.average != null }) "蓝线 价格  /  黄线 成交均价" else s.intradayDescription
        text(c, description, left, 45f, s.mutedColor, size = 10f)
    }

}

internal fun FinancialRenderFrame.drawGrid() {
    repeat(5) { tick ->
        val value = high - (high - low) * tick / 4f
        val yy = y(value)
        line(c, left, yy, right, yy, s.gridColor, dashed = true)
        text(c, financialNumber(value), left + 2, yy + if (tick == 4) -4f else 11f,
            if (lineMode) movement(value) else s.mutedColor, size = 10f)
        if (lineMode && prevClose != null && prevClose > 0) {
            val percent = (value - prevClose) / prevClose * 100f
            text(c, (if (percent > 0) "+" else "") + financialNumber(percent) + "%", right - 2,
                yy + if (tick == 4) -4f else 11f, movement(value), alignRight = true, size = 10f)
        }
    }
    repeat(5) { tick ->
        val xx = left + (right - left) * tick / 4f
        line(c, xx, top, xx, bottom, s.gridColor)
        if (s.showVolume) line(c, xx, volumeTop, xx, volumeBottom, s.gridColor)
    }
}

