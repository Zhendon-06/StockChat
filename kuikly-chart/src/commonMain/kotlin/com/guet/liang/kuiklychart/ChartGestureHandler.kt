package com.guet.liang.kuiklychart

import com.guet.liang.kuiklychart.internal.ChartHitTester
import com.guet.liang.kuiklychart.internal.ViewportMath
import com.tencent.kuikly.core.base.event.TouchParams
import kotlin.math.sqrt

internal fun ChartView.handleClick(horizontal: Float, vertical: Float) {
    if (suppressNextClick) {
        suppressNextClick = false
        return
    }
    if (!spec.interaction.selectionEnabled) {
        return
    }
    val geometry = lastRenderGeometry ?: return
    val selection = ChartHitTester.selectionAt(spec, geometry, horizontal, vertical)
    if (selection != null || spec.interaction.dismissSelectionOnOutsideTap) {
        setSelection(selection)
    }
}

internal fun ChartView.handleTouchDown(touchParams: TouchParams) {
    suppressNextClick = false
    val touchPoints = touchPoints(touchParams)
    if (spec.interaction.zoomEnabled && touchPoints.size >= 2) {
        gestureMoved = false
        gestureStartViewport = viewportState
        gestureMode = ChartView.GestureMode.PINCH
        pinchStartDistance = distance(touchPoints[0], touchPoints[1]).coerceAtLeast(1f)
    } else {
        gestureMode = ChartView.GestureMode.NONE
        gestureStartHorizontal = touchPoints[0].horizontal
        gestureStartVertical = touchPoints[0].vertical
        gestureStartViewport = viewportState
        gestureMoved = false
    }
}

internal fun ChartView.handleTouchMove(touchParams: TouchParams) {
    val touchPoints = touchPoints(touchParams)
    if (spec.interaction.zoomEnabled && touchPoints.size >= 2) {
        movePinch(touchPoints)
    } else {
        movePan(touchPoints)
    }
}

private fun ChartView.movePinch(touchPoints: List<ChartView.GesturePoint>) {
    if (gestureMode != ChartView.GestureMode.PINCH) {
        gestureStartViewport = viewportState
        pinchStartDistance = distance(touchPoints[0], touchPoints[1]).coerceAtLeast(1f)
        gestureMode = ChartView.GestureMode.PINCH
    }
    val geometry = lastRenderGeometry ?: return
    val currentDistance = distance(touchPoints[0], touchPoints[1]).coerceAtLeast(1f)
    val focalHorizontal = (touchPoints[0].horizontal + touchPoints[1].horizontal) / 2f
    val focalRatio = if (geometry.plot.width <= 0f) {
        0.5f
    } else {
        (focalHorizontal - geometry.plot.left) / geometry.plot.width
    }
    val nextViewport = ViewportMath.zoom(
        gestureStartViewport,
        currentDistance / pinchStartDistance,
        focalRatio,
        spec.dataCount(),
        spec.interaction.minimumVisiblePoints,
    )
    gestureMoved = gestureMoved || kotlin.math.abs(currentDistance - pinchStartDistance) > 3f
    setViewportInternal(nextViewport, emitEvent = true)
}

private fun ChartView.movePan(touchPoints: List<ChartView.GesturePoint>) {

    if (
        !spec.interaction.panEnabled || touchPoints.size != 1 ||
        gestureMode in listOf(ChartView.GestureMode.PINCH, ChartView.GestureMode.VERTICAL)
    ) {
        return
    }

    val touchPoint = touchPoints[0]
    val horizontalDelta = touchPoint.horizontal - gestureStartHorizontal
    val verticalDelta = touchPoint.vertical - gestureStartVertical
    if (!acceptPan(horizontalDelta, verticalDelta)) return
    val geometry = lastRenderGeometry ?: return
    gestureMoved = true
    val nextViewport = ViewportMath.pan(
        gestureStartViewport,
        horizontalDelta,
        geometry.plot.width,
        spec.dataCount(),
    )
    setViewportInternal(nextViewport, emitEvent = true)
}

internal fun ChartView.finishGesture() {
    suppressNextClick = gestureMoved
    gestureMode = ChartView.GestureMode.NONE
    gestureMoved = false
}

internal fun ChartView.touchPoints(touchParams: TouchParams): List<ChartView.GesturePoint> {
    if (touchParams.touches.isEmpty()) {
        return listOf(ChartView.GesturePoint(touchParams.x, touchParams.y))
    }
    return touchParams.touches.map { touch -> ChartView.GesturePoint(touch.x, touch.y) }
}

internal fun ChartView.distance(first: ChartView.GesturePoint, second: ChartView.GesturePoint): Float {
    val horizontalDelta = first.horizontal - second.horizontal
    val verticalDelta = first.vertical - second.vertical
    return sqrt(horizontalDelta * horizontalDelta + verticalDelta * verticalDelta)
}

private fun ChartView.acceptPan(horizontalDelta: Float, verticalDelta: Float): Boolean {
    if (gestureMode != ChartView.GestureMode.NONE) return true
    if (kotlin.math.abs(horizontalDelta) <= 3f && kotlin.math.abs(verticalDelta) <= 3f) return false
    val vertical = kotlin.math.abs(verticalDelta) > kotlin.math.abs(horizontalDelta)
    gestureMode = if (vertical) ChartView.GestureMode.VERTICAL else ChartView.GestureMode.PAN
    return !vertical
}
