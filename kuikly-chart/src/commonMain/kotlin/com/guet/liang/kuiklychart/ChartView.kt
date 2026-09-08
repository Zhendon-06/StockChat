package com.guet.liang.kuiklychart

import com.guet.liang.kuiklychart.api.ChartAnimationEasing
import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.guet.liang.kuiklychart.api.ChartViewport
import com.guet.liang.kuiklychart.internal.ChartDataSnapshot
import com.guet.liang.kuiklychart.internal.ChartDataTransition
import com.guet.liang.kuiklychart.internal.ChartHitTester
import com.guet.liang.kuiklychart.internal.ChartRenderGeometry
import com.guet.liang.kuiklychart.internal.ChartRenderer
import com.guet.liang.kuiklychart.internal.ScaleMath
import com.guet.liang.kuiklychart.internal.ValueScale
import com.guet.liang.kuiklychart.internal.ViewportMath
import com.guet.liang.kuiklychart.internal.transform
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.View
import kotlin.math.sqrt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Event callbacks emitted by every chart convenience component. */
public class ChartEvent : ComposeEvent() {
    /** Called for both selection and selection clearing. */
    public fun selectionChanged(handler: (ChartSelection?) -> Unit) {
        registerEvent(SELECTION_CHANGED) { value -> handler(value as? ChartSelection) }
    }

    /** Called only after a point, bar, or pie slice is selected. */
    public fun pointSelected(handler: (ChartSelection) -> Unit) {
        registerEvent(POINT_SELECTED) { value ->
            (value as? ChartSelection)?.let(handler)
        }
    }

    /** Called whenever a gesture or public method changes the visible range. */
    public fun viewportChanged(handler: (ChartViewport) -> Unit) {
        registerEvent(VIEWPORT_CHANGED) { value ->
            (value as? ChartViewport)?.let(handler)
        }
    }

    internal companion object {
        const val SELECTION_CHANGED = "chartSelectionChanged"
        const val POINT_SELECTED = "chartPointSelected"
        const val VIEWPORT_CHANGED = "chartViewportChanged"
    }
}

/**
 * Kuikly Canvas chart component shared by line, bar, area, pie, and mixed charts.
 * Configure it with [chart], style it with the regular Kuikly `attr` block, and
 * observe interactions with the `event` block.
 */
public class ChartView internal constructor(
    defaultSeriesType: ChartSeriesType,
) : ComposeView<ComposeAttr, ChartEvent>() {
    private val spec: ChartSpec = ChartSpec(defaultSeriesType)

    private var renderRevision by observable(0)
    private var viewportState by observable(ChartViewport(0f, 0f))
    private var selectionState by observable<ChartSelection?>(null)
    private var lastRenderGeometry: ChartRenderGeometry? = null
    private var configuredDataCount: Int = 0
    private var viewLoaded: Boolean = false

    private var animationTimeoutRef: String? = null
    private var activeDataTransition: ChartDataTransition? = null
    private var animationStartedAt: TimeMark? = null
    private var activeAnimationDurationMillis: Int = 0
    private var activeAnimationEasing: ChartAnimationEasing = ChartAnimationEasing.LINEAR
    private var animationGeneration: Int = 0
    private var animationStartScale: ValueScale? = null
    private var animationTargetScale: ValueScale? = null
    private var animationScaleOverride: ValueScale? = null

    private var gestureMode: GestureMode = GestureMode.NONE
    private var gestureStartHorizontal: Float = 0f
    private var gestureStartVertical: Float = 0f
    private var gestureStartViewport: ChartViewport = ChartViewport(0f, 0f)
    private var pinchStartDistance: Float = 0f
    private var gestureMoved: Boolean = false
    private var suppressNextClick: Boolean = false

    /** Current selected datum, or `null` when no tooltip is visible. */
    public val currentSelection: ChartSelection?
        get() = selectionState

    /** Current visible range in category-index coordinates. */
    public val currentViewport: ChartViewport
        get() = viewportState

    override fun createAttr(): ComposeAttr = ComposeAttr()

    override fun createEvent(): ChartEvent = ChartEvent()

    /** Applies chart data and grouped visual/interaction configuration. */
    public fun chart(init: ChartSpec.() -> Unit) {
        cancelDataAnimation(restoreTarget = true)
        applySpec(init)
        renderRevision += 1
    }

    /**
     * Replaces runtime data and animates from the currently displayed values by default.
     */
    public fun update(init: ChartSpec.() -> Unit) {
        update(animated = true, init = init)
    }

    /** Replaces runtime data, optionally bypassing the configured transition. */
    public fun update(
        animated: Boolean,
        init: ChartSpec.() -> Unit,
    ) {
        val previousData = ChartDataSnapshot.capture(spec)
        cancelDataAnimation(restoreTarget = true)
        applySpec(init)
        val transition = ChartDataTransition.create(previousData, spec)
        val targetScale = ScaleMath.calculate(spec, viewportState)
        val shouldAnimate = animated &&
            spec.animation.enabled &&
            spec.animation.durationMillis > 0 &&
            viewLoaded &&
            lastRenderGeometry != null &&
            transition.hasChanges
        if (shouldAnimate) {
            startDataAnimation(transition, targetScale)
        } else {
            renderRevision += 1
        }
    }

    private fun applySpec(init: ChartSpec.() -> Unit) {
        gestureMode = GestureMode.NONE
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

    /** Shows the full data range and clears the active selection. */
    public fun resetViewport() {
        setViewportInternal(ViewportMath.full(spec.dataCount()), emitEvent = true)
        clearSelection()
    }

    /** Sets a visible category range. Fractional indices are supported. */
    public fun setViewport(startIndex: Float, endIndex: Float) {
        if (!startIndex.isFinite() || !endIndex.isFinite()) {
            return
        }
        setViewportInternal(ChartViewport(startIndex, endIndex), emitEvent = true)
    }

    /** Zooms in around the current viewport center. */
    public fun zoomIn(factor: Float = 1.5f) {
        zoomBy(factor)
    }

    /** Zooms out around the current viewport center. */
    public fun zoomOut(factor: Float = 1.5f) {
        if (factor > 0f) {
            zoomBy(1f / factor)
        }
    }

    /** Pans by a number of category slots; positive values move forward. */
    public fun panBy(categoryCount: Float) {
        if (!categoryCount.isFinite()) {
            return
        }
        val requestedViewport = ChartViewport(
            viewportState.startIndex + categoryCount,
            viewportState.endIndex + categoryCount,
        )
        setViewportInternal(requestedViewport, emitEvent = true)
    }

    /** Selects a datum programmatically. */
    public fun select(seriesIndex: Int, dataIndex: Int) {
        val chartSeries = spec.dataSeries.getOrNull(seriesIndex) ?: return
        val value = chartSeries.dataValues.getOrNull(dataIndex)?.takeIf(Float::isFinite) ?: return
        setSelection(
            ChartSelection(
                seriesIndex,
                dataIndex,
                chartSeries.name,
                chartSeries.dataPointLabels.getOrNull(dataIndex) ?: spec.categoryLabel(dataIndex),
                value,
                chartSeries.type,
            ),
        )
    }

    /** Clears tooltip, crosshair, and selected-slice state. */
    public fun clearSelection() {
        setSelection(null)
    }

    override fun created() {
        super.created()
        configuredDataCount = spec.dataCount()
        viewportState = ViewportMath.initial(
            configuredDataCount,
            spec.interaction.initialVisiblePoints,
        )
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        viewLoaded = true
    }

    override fun viewWillUnload() {
        viewLoaded = false
        cancelDataAnimation(restoreTarget = true)
        super.viewWillUnload()
    }

    override fun viewDestroyed() {
        lastRenderGeometry = null
        super.viewDestroyed()
    }

    override fun body(): ViewBuilder {
        val chartView = this
        return {
            Canvas({
                attr {
                    absolutePositionAllZero()
                    touchEnable(false)
                }
            }) { canvasContext, width, height ->
                val currentRevision = chartView.renderRevision
                if (currentRevision >= 0) {
                    chartView.lastRenderGeometry = ChartRenderer.render(
                        canvasContext,
                        width,
                        height,
                        chartView.spec,
                        chartView.viewportState,
                        chartView.selectionState,
                        chartView.animationScaleOverride,
                    )
                }
            }
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(Color.TRANSPARENT)
                }
                event {
                    click { clickParams ->
                        chartView.handleClick(clickParams.x, clickParams.y)
                    }
                    doubleClick {
                        if (chartView.spec.interaction.resetViewportOnDoubleClick) {
                            chartView.resetViewport()
                        }
                    }
                    touchDown(isSync = true) { touchParams ->
                        chartView.handleTouchDown(touchParams)
                    }
                    touchMove(isSync = true) { touchParams ->
                        chartView.handleTouchMove(touchParams)
                    }
                    touchUp(isSync = true) {
                        chartView.finishGesture()
                    }
                    touchCancel(isSync = true) {
                        chartView.finishGesture()
                    }
                }
            }
        }
    }

    private fun handleClick(horizontal: Float, vertical: Float) {
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

    private fun handleTouchDown(touchParams: TouchParams) {
        suppressNextClick = false
        val touchPoints = touchPoints(touchParams)
        if (spec.interaction.zoomEnabled && touchPoints.size >= 2) {
            gestureMoved = false
            gestureStartViewport = viewportState
            gestureMode = GestureMode.PINCH
            pinchStartDistance = distance(touchPoints[0], touchPoints[1]).coerceAtLeast(1f)
        } else {
            gestureMode = GestureMode.NONE
            gestureStartHorizontal = touchPoints[0].horizontal
            gestureStartVertical = touchPoints[0].vertical
            gestureStartViewport = viewportState
            gestureMoved = false
        }
    }

    private fun handleTouchMove(touchParams: TouchParams) {
        val touchPoints = touchPoints(touchParams)
        if (spec.interaction.zoomEnabled && touchPoints.size >= 2) {
            if (gestureMode != GestureMode.PINCH) {
                gestureStartViewport = viewportState
                pinchStartDistance = distance(touchPoints[0], touchPoints[1]).coerceAtLeast(1f)
                gestureMode = GestureMode.PINCH
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
            return
        }

        if (
            !spec.interaction.panEnabled ||
            touchPoints.size != 1 ||
            gestureMode == GestureMode.PINCH ||
            gestureMode == GestureMode.VERTICAL
        ) {
            return
        }

        val touchPoint = touchPoints[0]
        val horizontalDelta = touchPoint.horizontal - gestureStartHorizontal
        val verticalDelta = touchPoint.vertical - gestureStartVertical
        if (gestureMode == GestureMode.NONE) {
            if (kotlin.math.abs(horizontalDelta) <= 3f && kotlin.math.abs(verticalDelta) <= 3f) {
                return
            }
            if (kotlin.math.abs(verticalDelta) > kotlin.math.abs(horizontalDelta)) {
                gestureMode = GestureMode.VERTICAL
                return
            }
            gestureMode = GestureMode.PAN
        }
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

    private fun finishGesture() {
        suppressNextClick = gestureMoved
        gestureMode = GestureMode.NONE
        gestureMoved = false
    }

    private fun zoomBy(scale: Float) {
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

    private fun setViewportInternal(viewport: ChartViewport, emitEvent: Boolean) {
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

    private fun setSelection(selection: ChartSelection?) {
        if (selection == selectionState) {
            return
        }
        selectionState = selection
        emit(ChartEvent.SELECTION_CHANGED, selection)
        if (selection != null) {
            emit(ChartEvent.POINT_SELECTED, selection)
        }
    }

    private fun touchPoints(touchParams: TouchParams): List<GesturePoint> {
        if (touchParams.touches.isEmpty()) {
            return listOf(GesturePoint(touchParams.x, touchParams.y))
        }
        return touchParams.touches.map { touch -> GesturePoint(touch.x, touch.y) }
    }

    private fun distance(first: GesturePoint, second: GesturePoint): Float {
        val horizontalDelta = first.horizontal - second.horizontal
        val verticalDelta = first.vertical - second.vertical
        return sqrt(horizontalDelta * horizontalDelta + verticalDelta * verticalDelta)
    }

    private fun startDataAnimation(
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

    private fun scheduleAnimationFrame() {
        val scheduledGeneration = animationGeneration
        animationTimeoutRef = setTimeout(ANIMATION_FRAME_INTERVAL_MILLIS) {
            if (scheduledGeneration != animationGeneration) {
                return@setTimeout
            }
            animationTimeoutRef = null
            renderAnimationFrame()
        }
    }

    private fun renderAnimationFrame() {
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

    private fun cancelDataAnimation(restoreTarget: Boolean) {
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

    private fun refreshSelectionForCurrentData() {
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

    private enum class GestureMode {
        NONE,
        PAN,
        PINCH,
        VERTICAL,
    }

    private data class GesturePoint(
        val horizontal: Float,
        val vertical: Float,
    )

    private companion object {
        const val ANIMATION_FRAME_INTERVAL_MILLIS: Int = 16
    }
}

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
