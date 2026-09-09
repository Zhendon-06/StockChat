package com.guet.liang.kuiklychart

import com.guet.liang.kuiklychart.api.ChartAnimationEasing
import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.guet.liang.kuiklychart.api.ChartViewport
import com.guet.liang.kuiklychart.internal.ChartDataSnapshot
import com.guet.liang.kuiklychart.internal.ChartDataTransition
import com.guet.liang.kuiklychart.internal.ChartRenderGeometry
import com.guet.liang.kuiklychart.internal.ChartRenderer
import com.guet.liang.kuiklychart.internal.ScaleMath
import com.guet.liang.kuiklychart.internal.ValueScale
import com.guet.liang.kuiklychart.internal.ViewportMath
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.View
import kotlin.time.TimeMark

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
    internal val spec: ChartSpec = ChartSpec(defaultSeriesType)

    internal var renderRevision by observable(0)
    internal var viewportState by observable(ChartViewport(0f, 0f))
    internal var selectionState by observable<ChartSelection?>(null)
    internal var lastRenderGeometry: ChartRenderGeometry? = null
    internal var configuredDataCount: Int = 0
    internal var viewLoaded: Boolean = false

    internal var animationTimeoutRef: String? = null
    internal var activeDataTransition: ChartDataTransition? = null
    internal var animationStartedAt: TimeMark? = null
    internal var activeAnimationDurationMillis: Int = 0
    internal var activeAnimationEasing: ChartAnimationEasing = ChartAnimationEasing.LINEAR
    internal var animationGeneration: Int = 0
    internal var animationStartScale: ValueScale? = null
    internal var animationTargetScale: ValueScale? = null
    internal var animationScaleOverride: ValueScale? = null

    internal var gestureMode: GestureMode = GestureMode.NONE
    internal var gestureStartHorizontal: Float = 0f
    internal var gestureStartVertical: Float = 0f
    internal var gestureStartViewport: ChartViewport = ChartViewport(0f, 0f)
    internal var pinchStartDistance: Float = 0f
    internal var gestureMoved: Boolean = false
    internal var suppressNextClick: Boolean = false

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

    /** Zooms out around the current viewport center. */

    /** Pans by a number of category slots; positive values move forward. */

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

    internal enum class GestureMode {
        NONE,
        PAN,
        PINCH,
        VERTICAL,
    }

    internal data class GesturePoint(
        val horizontal: Float,
        val vertical: Float,
    )

    internal companion object {
        const val ANIMATION_FRAME_INTERVAL_MILLIS: Int = 16
    }
}
