package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import kotlin.math.abs
import kotlin.math.roundToInt

/** Independent scales prevent money amounts and percentage changes from sharing a misleading axis. */
public data class DualAxisPoint(val label: String, val left: Float?, val right: Float?)
public class DualAxisChartSpec {
    public var points: List<DualAxisPoint> = emptyList()
    public var leftName: String = "净额"
    public var rightName: String = "涨跌幅"
    public var leftFormatter: (Float) -> String = ::financialVolume
    public var rightFormatter: (Float) -> String = { financialNumber(it) + "%" }
    public var leftColor: Color = Color(0xFFEBAB45L)
    public var rightColor: Color = Color(0xFF287BF3L)
    public var backgroundColor: Color = Color.WHITE
    public var mutedColor: Color = Color(0xFF89919DL)
    public var gridColor: Color = Color(0xFFECEEF2L)
}

public class DualAxisChartView : ComposeView<ComposeAttr, ComposeEvent>() {
    private var spec = DualAxisChartSpec()
    private var revision by observable(0)
    private var selected by observable(-1)
    private var plotWidth = 1f
    override fun createAttr(): ComposeAttr = ComposeAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()
    public fun chart(init: DualAxisChartSpec.() -> Unit) { spec = DualAxisChartSpec().apply(init); selected = -1; revision++ }
    override fun body(): ViewBuilder {
        val owner = this
        return {
            Canvas({
                attr { absolutePositionAllZero() }
                event { click { params ->
                    val next = (((params.x - 54f) / owner.plotWidth).coerceIn(0f, 1f) * (owner.spec.points.size - 1)).roundToInt()
                    owner.selected = if (owner.selected == next) -1 else next
                } }
            }) { c, width, height ->
                val s = owner.spec
                val points = s.points
                owner.plotWidth = (width - 104f).coerceAtLeast(1f)
                if (owner.revision >= 0) {
                    with(FinancialChartRenderer) {
                        rect(c, 0f, 0f, width, height, s.backgroundColor)
                        if (points.isEmpty()) text(c, "暂无数据", width / 2f, height / 2f, s.mutedColor, center = true)
                        else {
                            val left = 54f; val right = width - 50f; val top = 42f; val bottom = height - 26f
                            fun scale(values: List<Float?>): Pair<Float, Float> {
                                val finite = values.filterNotNull().filter(Float::isFinite)
                                val low = minOf(0f, finite.minOrNull() ?: 0f)
                                val high = maxOf(0f, finite.maxOrNull() ?: 1f)
                                val pad = maxOf((high - low) * 0.08f, 0.01f)
                                return low - pad to high + pad
                            }
                            val a = scale(points.map { it.left }); val b = scale(points.map { it.right })
                            fun y(value: Float, range: Pair<Float, Float>) = top + (range.second - value) / (range.second - range.first) * (bottom - top)
                            fun x(index: Int) = left + index.toFloat() / (points.size - 1).coerceAtLeast(1) * (right - left)
                            val index = owner.selected.takeIf { it in points.indices } ?: points.lastIndex
                            text(c, s.leftName + " " + (points[index].left?.let(s.leftFormatter) ?: "--"), 0f, 12f, s.leftColor, size = 10f)
                            text(c, s.rightName + " " + (points[index].right?.let(s.rightFormatter) ?: "--"), width, 12f, s.rightColor, alignRight = true, size = 10f)
                            text(c, points[index].label, 0f, 28f, s.mutedColor, size = 10f)
                            repeat(5) { tick ->
                                val yy = top + (bottom - top) * tick / 4f
                                line(c, left, yy, right, yy, s.gridColor, dashed = true)
                                text(c, s.leftFormatter(a.second - (a.second - a.first) * tick / 4f), left - 5f, yy + 4f, s.mutedColor, alignRight = true, size = 9f)
                                text(c, s.rightFormatter(b.second - (b.second - b.first) * tick / 4f), right + 5f, yy + 4f, s.mutedColor, size = 9f)
                            }
                            path(c, points.indices.toList(), ::x, { points[it].left?.takeIf(Float::isFinite)?.let { v -> y(v, a) } }, s.leftColor, 1.3f)
                            path(c, points.indices.toList(), ::x, { points[it].right?.takeIf(Float::isFinite)?.let { v -> y(v, b) } }, s.rightColor, 1.3f)
                            listOf(0, points.size / 2, points.lastIndex).distinct().forEach { i ->
                                text(c, points[i].label.takeLast(5), x(i), height - 7f, s.mutedColor,
                                    center = i != 0 && i != points.lastIndex, alignRight = i == points.lastIndex, size = 10f)
                            }
                            if (owner.selected in points.indices) line(c, x(index), top, x(index), bottom, s.mutedColor, dashed = true)
                        }
                    }
                }
            }
        }
    }
}

public fun ViewContainer<*, *>.DualAxisChart(init: DualAxisChartView.() -> Unit) {
    addChild(DualAxisChartView(), init)
}
