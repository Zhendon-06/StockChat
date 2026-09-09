package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.abs

/** Coordinates financial chart panes while keeping rendering stages independent. */
internal object FinancialChartRenderer {
    fun draw(
        c: CanvasContext,
        width: Float,
        height: Float,
        s: FinancialChartSpec,
        viewport: FinancialViewport,
        selected: Int,
        forecastProgress: Float = 1f,
        evidenceRange: IntRange? = null,
    ) {
        rect(c, 0f, 0f, width, height, s.backgroundColor)
        val frame = FinancialRenderFrame(c, width, height, s, viewport, selected)
        if (frame.points.isEmpty() || width < 50 || height < 150) {
            text(c, "暂无该周期行情", width / 2f, height / 2f, s.mutedColor, center = true)
            return
        }
        frame.drawEvidence(evidenceRange)
        frame.drawHeader()
        frame.drawGrid()
        when (s.mode) {
            FinancialChartMode.CLOSE_LINE -> frame.drawCloseLine(forecastProgress)
            FinancialChartMode.INTRADAY -> frame.drawIntraday()
            FinancialChartMode.CANDLES -> frame.drawCandles()
        }
        frame.drawTimeLabels()
        if (s.showVolume) frame.drawVolume()
        frame.drawSelection()
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
        c.strokeStyle(color)
        c.lineWidth(width)
        c.stroke()
    }

    internal fun line(c: CanvasContext, x1: Float, y1: Float, x2: Float, y2: Float, color: Color, dashed: Boolean = false) {
        c.beginPath()
        c.strokeStyle(color)
        c.lineWidth(0.6f)
        if (!dashed) {
            c.moveTo(x1, y1)
            c.lineTo(x2, y2)
        } else {
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
        c.beginPath()
        c.moveTo(x, y)
        c.lineTo(x + width, y)
        c.lineTo(x + width, y + height)
        c.lineTo(x, y + height)
        c.closePath()
        c.fillStyle(color)
        c.fill()
    }

    internal fun text(c: CanvasContext, value: String, x: Float, y: Float, color: Color,
        center: Boolean = false, alignRight: Boolean = false, size: Float = 11f) {
        c.font(size)
        c.fillStyle(color)
        c.textAlign(TextAlign.LEFT)
        val measured = if (center || alignRight) c.measureText(value).width else 0f
        c.fillText(value, x - if (center) measured / 2f else measured, y)
    }
}
