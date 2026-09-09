package com.guet.liang.kuiklychart.finance

import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.line
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.path
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.rect
import com.guet.liang.kuiklychart.finance.FinancialChartRenderer.text
import com.tencent.kuikly.core.views.CanvasContext

/** Renders independently scaled series in one aligned time plot. */
internal class DualAxisRenderer(
    private val canvas: CanvasContext,
    private val width: Float,
    private val height: Float,
    private val spec: DualAxisChartSpec,
    private val selected: Int,
) {
    private val points = spec.points
    private val left = 54f
    private val right = width - 50f
    private val top = 42f
    private val bottom = height - 26f
    private val leftScale = scale(points.map { it.left })
    private val rightScale = scale(points.map { it.right })

    fun draw() {
        rect(canvas, 0f, 0f, width, height, spec.backgroundColor)
        if (points.isEmpty()) {
            text(canvas, "暂无数据", width / 2f, height / 2f, spec.mutedColor, center = true)
            return
        }
        val index = selected.takeIf { it in points.indices } ?: points.lastIndex
        drawHeader(index)
        drawAxes()
        path(canvas, points.indices.toList(), ::x,
            { points[it].left?.takeIf(Float::isFinite)?.let { value -> y(value, leftScale) } }, spec.leftColor, 1.3f)
        path(canvas, points.indices.toList(), ::x,
            { points[it].right?.takeIf(Float::isFinite)?.let { value -> y(value, rightScale) } }, spec.rightColor, 1.3f)
        if (selected in points.indices) {
            line(canvas, x(index), top, x(index), bottom, spec.mutedColor, dashed = true)
        }
    }

    private fun drawHeader(index: Int) {
        text(canvas, spec.leftName + " " + (points[index].left?.let(spec.leftFormatter) ?: "--"),
            0f, 12f, spec.leftColor, size = 10f)
        text(canvas, spec.rightName + " " + (points[index].right?.let(spec.rightFormatter) ?: "--"),
            width, 12f, spec.rightColor, alignRight = true, size = 10f)
        text(canvas, points[index].label, 0f, 28f, spec.mutedColor, size = 10f)
    }

    private fun drawAxes() {
        repeat(5) { tick ->
            val vertical = top + (bottom - top) * tick / 4f
            val leftValue = leftScale.second - (leftScale.second - leftScale.first) * tick / 4f
            val rightValue = rightScale.second - (rightScale.second - rightScale.first) * tick / 4f
            line(canvas, left, vertical, right, vertical, spec.gridColor, dashed = true)
            text(canvas, spec.leftFormatter(leftValue), left - 5f, vertical + 4f,
                spec.mutedColor, alignRight = true, size = 9f)
            text(canvas, spec.rightFormatter(rightValue), right + 5f, vertical + 4f, spec.mutedColor, size = 9f)
        }
        listOf(0, points.size / 2, points.lastIndex).distinct().forEach { index ->
            text(canvas, points[index].label.takeLast(5), x(index), height - 7f, spec.mutedColor,
                center = index != 0 && index != points.lastIndex, alignRight = index == points.lastIndex, size = 10f)
        }
    }

    private fun scale(values: List<Float?>): Pair<Float, Float> {
        val finite = values.filterNotNull().filter(Float::isFinite)
        val low = minOf(0f, finite.minOrNull() ?: 0f)
        val high = maxOf(0f, finite.maxOrNull() ?: 1f)
        val padding = maxOf((high - low) * 0.08f, 0.01f)
        return low - padding to high + padding
    }

    private fun y(value: Float, range: Pair<Float, Float>): Float =
    top + (range.second - value) / (range.second - range.first) * (bottom - top)

    private fun x(index: Int): Float =
    left + index.toFloat() / (points.size - 1).coerceAtLeast(1) * (right - left)
}
