package com.guet.liang.kuiklychart

import com.guet.liang.kuiklychart.internal.ChartDataTransition
import com.guet.liang.kuiklychart.internal.ValueScale
import com.guet.liang.kuiklychart.internal.transform
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import kotlin.time.TimeSource

internal fun ChartView.startDataAnimation(
    transition: ChartDataTransition,
    targetScale: ValueScale,
) {
    activeDataTransition = transition
    activeAnimationDurationMillis = spec.animation.durationMillis.coerceAtLeast(1)
    activeAnimationEasing = spec.animation.easing
    animationStartScale = lastRenderGeometry?.scale
    animationTargetScale = targetScale
    animationScaleOverride = animationStartScale
    animationStartedAt = TimeSource.Monotonic.markNow()
    transition.apply(0f)
    renderRevision += 1
    scheduleAnimationFrame()
}

internal fun ChartView.scheduleAnimationFrame() {
    val scheduledGeneration = animationGeneration
    animationTimeoutRef = setTimeout(ChartView.ANIMATION_FRAME_INTERVAL_MILLIS) {
        if (scheduledGeneration != animationGeneration) {
            return@setTimeout
        }
        animationTimeoutRef = null
        renderAnimationFrame()
    }
}

internal fun ChartView.renderAnimationFrame() {
    val transition = activeDataTransition ?: return
    val startedAt = animationStartedAt ?: return
    val linearProgress = (
        startedAt.elapsedNow().inWholeMilliseconds.toFloat() /
        activeAnimationDurationMillis.toFloat()
    ).coerceIn(0f, 1f)
    val easedProgress = activeAnimationEasing.transform(linearProgress)
    transition.apply(easedProgress)
    animationScaleOverride = animationStartScale?.interpolateTo(
        animationTargetScale ?: animationStartScale ?: return,
        easedProgress,
    )
    refreshSelectionForCurrentData()
    if (linearProgress >= 1f) {
        animationScaleOverride = null
    }
    renderRevision += 1
    if (linearProgress >= 1f) {
        activeDataTransition = null
        animationStartedAt = null
        animationStartScale = null
        animationTargetScale = null
    } else if (viewLoaded) {
        scheduleAnimationFrame()
    }
}

internal fun ChartView.cancelDataAnimation(restoreTarget: Boolean) {
    animationGeneration += 1
    animationTimeoutRef?.let { timeoutRef -> clearTimeout(timeoutRef) }
    if (restoreTarget) {
        activeDataTransition?.apply(1f)
    }
    animationTimeoutRef = null
    activeDataTransition = null
    animationStartedAt = null
    animationStartScale = null
    animationTargetScale = null
    animationScaleOverride = null
}

internal fun ChartView.refreshSelectionForCurrentData() {
    val selection = selectionState ?: return
    val chartSeries = spec.dataSeries.getOrNull(selection.seriesIndex)
    val value = chartSeries?.dataValues?.getOrNull(selection.dataIndex)
    if (chartSeries == null || value == null || !value.isFinite()) {
        setSelection(null)
        return
    }
    selectionState = selection.copy(
        seriesName = chartSeries.name,
        label = chartSeries.dataPointLabels.getOrNull(selection.dataIndex)
        ?: spec.categoryLabel(selection.dataIndex),
        value = value,
        type = chartSeries.type,
    )
}

