package com.guet.liang.kuiklychart.finance

import com.guet.liang.kuiklychart.finance.FinancialViewportMath.TouchSample
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.View
import kotlin.time.TimeSource

/**
 * Price, MA/VWAP, time axis and volume share one viewport and one crosshair.
 *
 * Candle gestures: one finger drags the fractional viewport pixel by pixel with inertia and a
 * rubber-band edge, two fingers zoom around the pinch centre, a tap toggles the crosshair, and while
 * the crosshair is showing (or on charts that cannot pan) a horizontal slide scrubs it along the
 * finger. A long press also opens the crosshair and a double tap returns to the latest candles.
 */
public class FinancialChartView : ComposeView<ComposeAttr, ComposeEvent>() {
    internal var forecastProgress by observable(1f)
    internal var forecastTimeout: String? = null
    internal var forecastGeneration = 0
    internal var loaded = false
    internal var spec = FinancialChartSpec()
    internal var revision by observable(0)
    internal var viewport by observable(FinancialViewport(0f, FinancialViewportMath.MIN_COUNT))
    internal var selected by observable(-1)
    internal var evidenceRange by observable<IntRange?>(null)
    public var onEvidenceCleared: (() -> Unit)? = null
    public var onSelectionChanged: ((Int?) -> Unit)? = null

    /**
     * `true` from the moment a horizontal pan, pinch or crosshair scrub begins until the finger lifts.
     * Hosts nested in a vertical `Scroller` should bind this to `scrollEnable(!active)`: Kuikly's
     * Android/iOS renderers ignore `CaptureRule`, so this is what keeps a wandering finger from
     * handing the gesture to the page mid-drag.
     */
    public var onGestureActiveChanged: ((Boolean) -> Unit)? = null
    internal var gestureActive = false

    internal var plotLeft = PLOT_INSET
    internal var plotWidth = 1f

    internal val clock = TimeSource.Monotonic.markNow()
    internal var gesture = Gesture.NONE
    internal var downX = 0f
    internal var downY = 0f
    internal var panOrigin = FinancialViewport(0f, FinancialViewportMath.MIN_COUNT)
    internal var panOriginX = 0f
    internal var pinchOrigin = FinancialViewport(0f, FinancialViewportMath.MIN_COUNT)
    internal var pinchStartDistance = 1f
    internal var pinchAnchorRatio = 0.5f
    internal val samples = ArrayList<TouchSample>()
    internal var suppressClick = false
    internal var longPressTimeout: String? = null
    internal var viewportTimeout: String? = null
    internal var viewportGeneration = 0

    override fun createAttr(): ComposeAttr = ComposeAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    public fun chart(init: FinancialChartSpec.() -> Unit) {
        spec = FinancialChartSpec().apply(init).also { config ->
            config.points = config.points.filter(FinancialChartMath::valid)
        }
        stopViewportAnimation()
        cancelLongPress()
        gesture = Gesture.NONE
        setGestureActive(false)
        viewport = FinancialViewportMath.latest(spec.visibleCount, spec.points.size)
        selected = -1
        clearEvidence()
        revision++
        prepareForecastAnimation()
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        loaded = true
        prepareForecastAnimation()
    }

    override fun viewWillUnload() {
        loaded = false
        finishForecastAnimation()
        stopViewportAnimation()
        cancelLongPress()
        setGestureActive(false)
        super.viewWillUnload()
    }

    /** Indices must refer to the validated series supplied to chart(). */
    public fun highlightRange(range: IntRange): Boolean {
        if (spec.mode != FinancialChartMode.CANDLES) return false
        val target = financialEvidenceViewport(spec.points.size, range) ?: return false
        finishForecastAnimation()
        animateTo(FinancialViewport(target.start.toFloat(), target.count.toFloat()), EVIDENCE_MILLIS)
        setSelection(-1)
        evidenceRange = range
        return true
    }

    public fun clearEvidence() {
        if (evidenceRange == null) return
        evidenceRange = null
        onEvidenceCleared?.invoke()
    }

    /** Zooms around the centre; when the newest candle is visible it stays pinned to the right. */
    public fun zoom(factor: Float) {
        if (!factor.isFinite() || factor <= 0f || !zoomable) return
        finishForecastAnimation()
        clearEvidence()
        setSelection(-1)
        animateTo(FinancialViewportMath.zoomAroundCenter(viewport, factor, spec.points.size), ZOOM_MILLIS)
    }

    public fun resetViewport() {
        clearEvidence()
        finishForecastAnimation()
        setSelection(-1)
        animateTo(FinancialViewportMath.latest(spec.visibleCount, spec.points.size), ZOOM_MILLIS)
    }

    override fun body(): ViewBuilder {
        val owner = this
        return {
            View {
                attr {
                    absolutePositionAllZero()
                    // Horizontal drags and the long-press crosshair stay on the chart; vertical drags scroll the page.
                    capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL), CaptureRule.longPress())
                }
                event {
                    click { params -> owner.handleClick(params.x) }
                    doubleClick { owner.resetViewport() }
                    longPress { params ->
                        when (params.state) {
                            LONG_PRESS_START -> owner.enterCrosshair(params.x)
                            LONG_PRESS_MOVE -> if (owner.gesture == Gesture.CROSSHAIR) owner.updateCrosshair(params.x)
                            else -> if (owner.gesture == Gesture.CROSSHAIR) owner.release()
                        }
                    }
                    touchDown(isSync = true) { params -> owner.handleTouchDown(params) }
                    touchMove(isSync = true) { params -> owner.handleTouchMove(params) }
                    touchUp(isSync = true) { owner.release() }
                    touchCancel(isSync = true) { owner.release() }
                }
                Canvas({
                        attr {
                            absolutePositionAllZero()
                            touchEnable(false)
                        }
                }) {
                    context, width, height ->
                    owner.plotLeft = PLOT_INSET
                    owner.plotWidth = (width - PLOT_INSET * 2f).coerceAtLeast(1f)
                    if (owner.revision >= 0) FinancialChartRenderer.draw(
                        context, width, height, owner.spec, owner.viewport, owner.selected, owner.forecastProgress, owner.evidenceRange,
                    )
                }
            }
        }
    }

    internal val pannable: Boolean get() = spec.mode != FinancialChartMode.INTRADAY && spec.points.isNotEmpty()
    internal val zoomable: Boolean get() = pannable

    /** Drives [frame] with the elapsed milliseconds per tick until it returns `false`. */

    internal enum class Gesture { NONE, UNDECIDED, PAN, PINCH, CROSSHAIR, REJECTED }

    internal companion object {
        const val PLOT_INSET = 6f
        const val FRAME_MILLIS = 16
        const val MAX_FRAME_MILLIS = 64f
        const val TOUCH_SLOP = 4f
        const val LONG_PRESS_MILLIS = 400
        const val ZOOM_MILLIS = 220
        const val SETTLE_MILLIS = 260
        const val EVIDENCE_MILLIS = 320
        const val MAX_SAMPLES = 8
        /** Pixels per millisecond below which a released drag simply stops. */
        const val MIN_FLING_PIXEL_VELOCITY = 0.05f
        const val LONG_PRESS_START = "start"
        const val LONG_PRESS_MOVE = "move"
    }
}

public fun ViewContainer<*, *>.FinancialChart(init: FinancialChartView.() -> Unit) {
    addChild(FinancialChartView(), init)
}
