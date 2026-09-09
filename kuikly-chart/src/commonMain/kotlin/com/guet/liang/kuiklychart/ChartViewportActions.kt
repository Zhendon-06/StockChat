package com.guet.liang.kuiklychart

import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSpec
import com.guet.liang.kuiklychart.api.ChartViewport
import com.guet.liang.kuiklychart.internal.ViewportMath

internal fun ChartView.applySpec(init: ChartSpec.() -> Unit) {
    gestureMode = ChartView.GestureMode.NONE
    gestureMoved = false
    suppressNextClick = false
    val previousDataCount = spec.dataCount()
    val wasShowingFullViewport = ViewportMath.isFull(viewportState, previousDataCount)
    spec.apply(init)
    configuredDataCount = spec.dataCount()
    if (wasShowingFullViewport) {
        setViewportInternal(ViewportMath.full(configuredDataCount), emitEvent = false)
    } else {
        setViewportInternal(
            ViewportMath.normalize(viewportState, configuredDataCount),
            emitEvent = false,
        )
    }
    if (selectionState != null) {
        setSelection(null)
    }
}

public fun ChartView.zoomIn(factor: Float = 1.5f) {
    zoomBy(factor)
}

public fun ChartView.zoomOut(factor: Float = 1.5f) {
    if (factor > 0f) {
        zoomBy(1f / factor)
    }
}

public fun ChartView.panBy(categoryCount: Float) {
    if (!categoryCount.isFinite()) {
        return
    }
    val requestedViewport = ChartViewport(
        viewportState.startIndex + categoryCount,
        viewportState.endIndex + categoryCount,
    )
    setViewportInternal(requestedViewport, emitEvent = true)
}

internal fun ChartView.zoomBy(scale: Float) {
    if (!scale.isFinite() || scale <= 0f) {
        return
    }
    val nextViewport = ViewportMath.zoom(
        viewportState,
        scale,
        0.5f,
        spec.dataCount(),
        spec.interaction.minimumVisiblePoints,
    )
    setViewportInternal(nextViewport, emitEvent = true)
}

internal fun ChartView.setViewportInternal(viewport: ChartViewport, emitEvent: Boolean) {
    val normalizedViewport = ViewportMath.normalize(viewport, spec.dataCount())
    if (normalizedViewport == viewportState) {
        return
    }
    viewportState = normalizedViewport
    if (selectionState != null) {
        setSelection(null)
    }
    if (emitEvent) {
        emit(ChartEvent.VIEWPORT_CHANGED, normalizedViewport)
    }
}

internal fun ChartView.setSelection(selection: ChartSelection?) {
    if (selection == selectionState) {
        return
    }
    selectionState = selection
    emit(ChartEvent.SELECTION_CHANGED, selection)
    if (selection != null) {
        emit(ChartEvent.POINT_SELECTED, selection)
    }
}

