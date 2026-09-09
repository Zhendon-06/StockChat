package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import kotlin.math.roundToInt

/** Independent scales prevent money amounts and percentage changes from sharing a misleading axis. */
public data class DualAxisPoint(val label: String, val left: Float?, val right: Float?)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
public class DualAxisChartView : ComposeView<ComposeAttr, ComposeEvent>() {
    private var spec = DualAxisChartSpec()
    private var revision by observable(0)
    private var selected by observable(-1)
    private var plotWidth = 1f
    override fun createAttr(): ComposeAttr = ComposeAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()
    public fun chart(init: DualAxisChartSpec.() -> Unit) {
        spec = DualAxisChartSpec().apply(init)
        selected = -1
        revision++
    }
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
                    DualAxisRenderer(c, width, height, s, owner.selected).draw()
                }
            }
        }
    }
}

public fun ViewContainer<*, *>.DualAxisChart(init: DualAxisChartView.() -> Unit) {
    addChild(DualAxisChartView(), init)
}
