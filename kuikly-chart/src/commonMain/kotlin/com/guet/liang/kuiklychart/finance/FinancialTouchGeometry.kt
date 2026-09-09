package com.guet.liang.kuiklychart.finance

import com.guet.liang.kuiklychart.finance.FinancialViewportMath.TouchSample
import com.tencent.kuikly.core.base.event.TouchParams
import kotlin.math.sqrt

internal fun FinancialChartView.now(): Float = clock.elapsedNow().inWholeMicroseconds / 1000f

internal fun FinancialChartView.recordSample(x: Float) {
    samples.add(TouchSample(x, now()))
    while (samples.size > FinancialChartView.MAX_SAMPLES) samples.removeAt(0)
}

internal fun FinancialChartView.touchCount(params: TouchParams): Int = params.touches.size.coerceAtLeast(1)

internal fun FinancialChartView.primaryPoint(params: TouchParams): Pair<Float, Float> {
    val touch = params.touches.firstOrNull() ?: return params.x to params.y
    return touch.x to touch.y
}

internal fun FinancialChartView.midpointX(params: TouchParams): Float =
if (params.touches.size < 2) params.x else (params.touches[0].x + params.touches[1].x) / 2f

internal fun FinancialChartView.distance(params: TouchParams): Float {
    if (params.touches.size < 2) return 0f
    val dx = params.touches[0].x - params.touches[1].x
    val dy = params.touches[0].y - params.touches[1].y
    return sqrt(dx * dx + dy * dy)
}

