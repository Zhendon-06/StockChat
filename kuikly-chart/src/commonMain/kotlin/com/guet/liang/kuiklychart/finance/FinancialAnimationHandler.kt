package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import kotlin.time.TimeSource

internal fun FinancialChartView.finishForecastAnimation() {
    forecastGeneration++
    forecastTimeout?.let { clearTimeout(it) }
    forecastTimeout = null
    forecastProgress = 1f
}

internal fun FinancialChartView.prepareForecastAnimation() {
    finishForecastAnimation()
    val boundary = spec.forecastStartIndex ?: return
    if (spec.mode != FinancialChartMode.CLOSE_LINE || boundary !in 1..spec.points.lastIndex ||
        spec.forecastRevealDurationMillis <= 0) return
    forecastProgress = 0f
    if (!loaded) return
    val generation = forecastGeneration
    val startedAt = TimeSource.Monotonic.markNow()
    fun scheduleFrame() {
        forecastTimeout = setTimeout(FinancialChartView.FRAME_MILLIS) {
            if (!loaded || generation != forecastGeneration) return@setTimeout
            forecastTimeout = null
            val progress = (startedAt.elapsedNow().inWholeMilliseconds.toFloat() /
                spec.forecastRevealDurationMillis).coerceIn(0f, 1f)
            forecastProgress = 1f - (1f - progress) * (1f - progress)
            if (progress < 1f) scheduleFrame()
        }
    }
    scheduleFrame()
}

internal fun FinancialChartView.startFling(indexVelocity: Float) {
    var velocity = indexVelocity
    runViewportFrames { elapsed ->
        val step = FinancialViewportMath.fling(viewport, velocity, elapsed, plotWidth, spec.points.size)
        ?: return@runViewportFrames false
        velocity = step.velocity
        applyViewport(step.viewport)
        !step.finished
    }
}

internal fun FinancialChartView.animateTo(target: FinancialViewport, durationMillis: Int) {
    val from = viewport
    if (!loaded || durationMillis <= 0 || from == target) {
        stopViewportAnimation()
        applyViewport(target)
        return
    }
    val startedAt = now()
    runViewportFrames {
        val progress = ((now() - startedAt) / durationMillis).coerceIn(0f, 1f)
        applyViewport(FinancialViewportMath.interpolate(from, target, progress))
        progress < 1f
    }
}

internal fun FinancialChartView.runViewportFrames(frame: (Float) -> Boolean) {
    stopViewportAnimation()
    if (!loaded) return
    val generation = viewportGeneration
    var last = now()
    fun schedule() {
        viewportTimeout = setTimeout(FinancialChartView.FRAME_MILLIS) {
            if (!loaded || generation != viewportGeneration) return@setTimeout
            viewportTimeout = null
            val current = now()
            val elapsed = (current - last).coerceIn(1f, FinancialChartView.MAX_FRAME_MILLIS)
            last = current
            if (frame(elapsed)) schedule()
        }
    }
    schedule()
}

internal fun FinancialChartView.stopViewportAnimation() {
    viewportGeneration++
    viewportTimeout?.let { clearTimeout(it) }
    viewportTimeout = null
}

