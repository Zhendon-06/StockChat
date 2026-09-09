package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.base.event.TouchParams
import kotlin.math.abs

internal fun FinancialChartView.handleClick(x: Float) {
    if (suppressClick) {
        suppressClick = false
        return
    }
    val next = indexAt(x)
    if (next < 0) return
    clearEvidence()
    setSelection(if (next == selected) -1 else next)
}

internal fun FinancialChartView.handleTouchDown(params: TouchParams) {
    finishForecastAnimation()
    // Touching a flinging chart grabs it where it is.
    stopViewportAnimation()
    suppressClick = false
    val (x, y) = primaryPoint(params)
    downX = x
    downY = y
    if (touchCount(params) >= 2 && zoomable) {
        beginPinch(params)
    } else {
        gesture = FinancialChartView.Gesture.UNDECIDED
        scheduleLongPress(x)
    }
}

internal fun FinancialChartView.handleTouchMove(params: TouchParams) {
    if (touchCount(params) >= 2 && zoomable) {
        if (gesture != FinancialChartView.Gesture.PINCH) beginPinch(params)
        updatePinch(params)
        return
    }
    val (x, y) = primaryPoint(params)
    if (!acceptDrag(x, y)) return
    recordSample(x)
    clearEvidence()
    applyViewport(FinancialViewportMath.pan(panOrigin, x - panOriginX, plotWidth, spec.points.size))
}

internal fun FinancialChartView.release() {
    cancelLongPress()
    when (gesture) {
        FinancialChartView.Gesture.PAN -> {
            suppressClick = true
            val size = spec.points.size
            if (FinancialViewportMath.isOverscrolled(viewport, size)) {
                animateTo(FinancialViewportMath.clamp(viewport, size), FinancialChartView.SETTLE_MILLIS)
            } else {
                val pixelVelocity = FinancialViewportMath.velocity(samples)
                if (abs(pixelVelocity) >= FinancialChartView.MIN_FLING_PIXEL_VELOCITY) {
                    startFling(-pixelVelocity / plotWidth * viewport.count)
                }
            }
        }
        FinancialChartView.Gesture.PINCH, FinancialChartView.Gesture.CROSSHAIR -> suppressClick = true
        FinancialChartView.Gesture.NONE, FinancialChartView.Gesture.UNDECIDED, FinancialChartView.Gesture.REJECTED -> Unit
    }
    gesture = FinancialChartView.Gesture.NONE
    samples.clear()
    setGestureActive(false)
}

internal fun FinancialChartView.beginPan(x: Float) {
    cancelLongPress()
    gesture = FinancialChartView.Gesture.PAN
    suppressClick = true
    setGestureActive(true)
    panOrigin = viewport
    panOriginX = x
    samples.clear()
    recordSample(x)
}

internal fun FinancialChartView.beginPinch(params: TouchParams) {
    cancelLongPress()
    gesture = FinancialChartView.Gesture.PINCH
    suppressClick = true
    setGestureActive(true)
    pinchOrigin = viewport
    pinchStartDistance = distance(params).coerceAtLeast(1f)
    pinchAnchorRatio = plotRatio(midpointX(params))
    clearEvidence()
}

internal fun FinancialChartView.updatePinch(params: TouchParams) {
    val scale = distance(params).coerceAtLeast(1f) / pinchStartDistance
    applyViewport(
        FinancialViewportMath.zoom(
        pinchOrigin, scale, pinchAnchorRatio, spec.points.size, focalRatio = plotRatio(midpointX(params)),
        ),
    )
}

internal fun FinancialChartView.setGestureActive(active: Boolean) {
    if (active == gestureActive) return
    gestureActive = active
    onGestureActiveChanged?.invoke(active)
}

private fun FinancialChartView.acceptDrag(x: Float, y: Float): Boolean = when (gesture) {
    FinancialChartView.Gesture.CROSSHAIR -> {
        updateCrosshair(x)
        false
    }
    FinancialChartView.Gesture.REJECTED -> false
    FinancialChartView.Gesture.PINCH, FinancialChartView.Gesture.NONE -> {
        if (pannable) beginPan(x) else gesture = FinancialChartView.Gesture.REJECTED
        pannable
    }
    FinancialChartView.Gesture.UNDECIDED -> resolveDragIntent(x, y)
    FinancialChartView.Gesture.PAN -> true
}

private fun FinancialChartView.resolveDragIntent(x: Float, y: Float): Boolean {
    val dx = x - downX
    val dy = y - downY
    if (abs(dx) < FinancialChartView.TOUCH_SLOP && abs(dy) < FinancialChartView.TOUCH_SLOP) return false
    cancelLongPress()
    return when {
        abs(dy) > abs(dx) -> {
            gesture = FinancialChartView.Gesture.REJECTED
            false
        }
        !pannable || selected >= 0 -> {
            enterCrosshair(x)
            false
        }
        else -> {
            beginPan(downX)
            true
        }
    }
}
