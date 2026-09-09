package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import kotlin.math.abs

internal fun FinancialChartView.scheduleLongPress(x: Float) {
    cancelLongPress()
    if (!loaded) return
    longPressTimeout = setTimeout(FinancialChartView.LONG_PRESS_MILLIS) {
        longPressTimeout = null
        if (gesture == FinancialChartView.Gesture.UNDECIDED) enterCrosshair(x)
    }
}

internal fun FinancialChartView.cancelLongPress() {
    longPressTimeout?.let { clearTimeout(it) }
    longPressTimeout = null
}

internal fun FinancialChartView.enterCrosshair(x: Float) {
    if (gesture != FinancialChartView.Gesture.UNDECIDED && gesture != FinancialChartView.Gesture.NONE) return
    cancelLongPress()
    stopViewportAnimation()
    gesture = FinancialChartView.Gesture.CROSSHAIR
    suppressClick = true
    setGestureActive(true)
    clearEvidence()
    updateCrosshair(x)
}

internal fun FinancialChartView.updateCrosshair(x: Float) {
    val index = indexAt(x)
    if (index >= 0) setSelection(index)
}

internal fun FinancialChartView.indexAt(x: Float): Int {
    if (spec.points.isEmpty()) return -1
    val ratio = plotRatio(x)
    if (spec.mode == FinancialChartMode.INTRADAY) {
        val slot = ratio * spec.sessionSlots
        return spec.points.indices.minByOrNull { abs(spec.points[it].slot - slot) } ?: -1
    }
    return viewport.indexAt(ratio, spec.points.size)
}

internal fun FinancialChartView.plotRatio(x: Float): Float = ((x - plotLeft) / plotWidth).coerceIn(0f, 1f)

internal fun FinancialChartView.applyViewport(next: FinancialViewport) {
    if (next == viewport) return
    viewport = next
    setSelection(-1)
}

internal fun FinancialChartView.setSelection(index: Int) {
    if (index == selected) return
    selected = index
    onSelectionChanged?.invoke(index.takeIf { it >= 0 })
}

