package com.guet.liang.kuiklychart.ui

import com.guet.liang.kuiklychart.ChartView

import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.tencent.kuikly.core.base.ViewContainer

/** Adds a generic chart that can combine line, area, and bar series. */
public fun ViewContainer<*, *>.Chart(init: ChartView.() -> Unit) {
    addChild(ChartView(ChartSeriesType.LINE), init)
}

/** Adds a chart whose `series(...)` shorthand renders lines. */
public fun ViewContainer<*, *>.LineChart(init: ChartView.() -> Unit) {
    addChild(ChartView(ChartSeriesType.LINE), init)
}

/** Adds a chart whose `series(...)` shorthand renders grouped bars. */
public fun ViewContainer<*, *>.BarChart(init: ChartView.() -> Unit) {
    addChild(ChartView(ChartSeriesType.BAR), init)
}

/** Adds a chart whose `series(...)` shorthand renders a filled area. */
public fun ViewContainer<*, *>.AreaChart(init: ChartView.() -> Unit) {
    addChild(ChartView(ChartSeriesType.AREA), init)
}

/** Adds a pie or donut chart. Global labels map to `series(...)` values. */
public fun ViewContainer<*, *>.PieChart(init: ChartView.() -> Unit) {
    addChild(ChartView(ChartSeriesType.PIE), init)
}
