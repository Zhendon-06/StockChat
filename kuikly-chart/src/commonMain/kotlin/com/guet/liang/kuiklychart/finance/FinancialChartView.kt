package com.guet.liang.kuiklychart.finance

import com.guet.liang.kuiklychart.finance.FinancialViewportMath.TouchSample
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import kotlin.math.abs
import kotlin.math.sqrt
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
    private var forecastProgress by observable(1f)
    private var forecastTimeout: String? = null
    private var forecastGeneration = 0
    private var loaded = false
    private var spec = FinancialChartSpec()
    private var revision by observable(0)
    private var viewport by observable(FinancialViewport(0f, FinancialViewportMath.MIN_COUNT))
    private var selected by observable(-1)
    private var evidenceRange by observable<IntRange?>(null)
    public var onEvidenceCleared: (() -> Unit)? = null
    public var onSelectionChanged: ((Int?) -> Unit)? = null

    /**
     * `true` from the moment a horizontal pan, pinch or crosshair scrub begins until the finger lifts.
     * Hosts nested in a vertical `Scroller` should bind this to `scrollEnable(!active)`: Kuikly's
     * Android/iOS renderers ignore `CaptureRule`, so this is what keeps a wandering finger from
     * handing the gesture to the page mid-drag.
     */
    public var onGestureActiveChanged: ((Boolean) -> Unit)? = null
    private var gestureActive = false

    private var plotLeft = PLOT_INSET
    private var plotWidth = 1f

    private val clock = TimeSource.Monotonic.markNow()
    private var gesture = Gesture.NONE
    private var downX = 0f
    private var downY = 0f
    private var panOrigin = FinancialViewport(0f, FinancialViewportMath.MIN_COUNT)
    private var panOriginX = 0f
    private var pinchOrigin = FinancialViewport(0f, FinancialViewportMath.MIN_COUNT)
    private var pinchStartDistance = 1f
    private var pinchAnchorRatio = 0.5f
    private val samples = ArrayList<TouchSample>()
    private var suppressClick = false
    private var longPressTimeout: String? = null
    private var viewportTimeout: String? = null
    private var viewportGeneration = 0

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

    private fun finishForecastAnimation() {
        forecastGeneration++
        forecastTimeout?.let { clearTimeout(it) }
        forecastTimeout = null
        forecastProgress = 1f
    }

    private fun prepareForecastAnimation() {
        finishForecastAnimation()
        val boundary = spec.forecastStartIndex ?: return
        if (spec.mode != FinancialChartMode.CLOSE_LINE || boundary !in 1..spec.points.lastIndex ||
            spec.forecastRevealDurationMillis <= 0) return
        forecastProgress = 0f
        if (!loaded) return
        val generation = forecastGeneration
        val startedAt = TimeSource.Monotonic.markNow()
        fun scheduleFrame() {
            forecastTimeout = setTimeout(FRAME_MILLIS) {
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
                Canvas({ attr { absolutePositionAllZero(); touchEnable(false) } }) { context, width, height ->
                    owner.plotLeft = PLOT_INSET
                    owner.plotWidth = (width - PLOT_INSET * 2f).coerceAtLeast(1f)
                    if (owner.revision >= 0) FinancialChartRenderer.draw(
                        context, width, height, owner.spec, owner.viewport, owner.selected, owner.forecastProgress, owner.evidenceRange,
                    )
                }
            }
        }
    }

    private val pannable: Boolean get() = spec.mode != FinancialChartMode.INTRADAY && spec.points.isNotEmpty()
    private val zoomable: Boolean get() = pannable

    private fun handleClick(x: Float) {
        if (suppressClick) {
            suppressClick = false
            return
        }
        val next = indexAt(x)
        if (next < 0) return
        clearEvidence()
        setSelection(if (next == selected) -1 else next)
    }

    private fun handleTouchDown(params: TouchParams) {
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
            gesture = Gesture.UNDECIDED
            scheduleLongPress(x)
        }
    }

    private fun handleTouchMove(params: TouchParams) {
        if (touchCount(params) >= 2 && zoomable) {
            if (gesture != Gesture.PINCH) beginPinch(params)
            updatePinch(params)
            return
        }
        val (x, y) = primaryPoint(params)
        when (gesture) {
            Gesture.CROSSHAIR -> { updateCrosshair(x); return }
            Gesture.REJECTED -> return
            Gesture.PINCH, Gesture.NONE -> if (pannable) beginPan(x) else { gesture = Gesture.REJECTED; return }
            Gesture.UNDECIDED -> {
                val dx = x - downX
                val dy = y - downY
                if (abs(dx) < TOUCH_SLOP && abs(dy) < TOUCH_SLOP) return
                cancelLongPress()
                if (abs(dy) > abs(dx)) {
                    gesture = Gesture.REJECTED
                    return
                }
                // With a crosshair showing, or on charts that cannot pan, a horizontal slide scrubs the crosshair.
                if (!pannable || selected >= 0) {
                    enterCrosshair(x)
                    return
                }
                beginPan(downX)
            }
            Gesture.PAN -> Unit
        }
        recordSample(x)
        clearEvidence()
        applyViewport(FinancialViewportMath.pan(panOrigin, x - panOriginX, plotWidth, spec.points.size))
    }

    private fun release() {
        cancelLongPress()
        when (gesture) {
            Gesture.PAN -> {
                suppressClick = true
                val size = spec.points.size
                if (FinancialViewportMath.isOverscrolled(viewport, size)) {
                    animateTo(FinancialViewportMath.clamp(viewport, size), SETTLE_MILLIS)
                } else {
                    val pixelVelocity = FinancialViewportMath.velocity(samples)
                    if (abs(pixelVelocity) >= MIN_FLING_PIXEL_VELOCITY) {
                        startFling(-pixelVelocity / plotWidth * viewport.count)
                    }
                }
            }
            Gesture.PINCH, Gesture.CROSSHAIR -> suppressClick = true
            Gesture.NONE, Gesture.UNDECIDED, Gesture.REJECTED -> Unit
        }
        gesture = Gesture.NONE
        samples.clear()
        setGestureActive(false)
    }

    private fun beginPan(x: Float) {
        cancelLongPress()
        gesture = Gesture.PAN
        suppressClick = true
        setGestureActive(true)
        panOrigin = viewport
        panOriginX = x
        samples.clear()
        recordSample(x)
    }

    private fun beginPinch(params: TouchParams) {
        cancelLongPress()
        gesture = Gesture.PINCH
        suppressClick = true
        setGestureActive(true)
        pinchOrigin = viewport
        pinchStartDistance = distance(params).coerceAtLeast(1f)
        pinchAnchorRatio = plotRatio(midpointX(params))
        clearEvidence()
    }

    private fun updatePinch(params: TouchParams) {
        val scale = distance(params).coerceAtLeast(1f) / pinchStartDistance
        applyViewport(
            FinancialViewportMath.zoom(
                pinchOrigin, scale, pinchAnchorRatio, spec.points.size, focalRatio = plotRatio(midpointX(params)),
            ),
        )
    }

    private fun scheduleLongPress(x: Float) {
        cancelLongPress()
        if (!loaded) return
        longPressTimeout = setTimeout(LONG_PRESS_MILLIS) {
            longPressTimeout = null
            if (gesture == Gesture.UNDECIDED) enterCrosshair(x)
        }
    }

    private fun cancelLongPress() {
        longPressTimeout?.let { clearTimeout(it) }
        longPressTimeout = null
    }

    private fun enterCrosshair(x: Float) {
        if (gesture != Gesture.UNDECIDED && gesture != Gesture.NONE) return
        cancelLongPress()
        stopViewportAnimation()
        gesture = Gesture.CROSSHAIR
        suppressClick = true
        setGestureActive(true)
        clearEvidence()
        updateCrosshair(x)
    }

    private fun updateCrosshair(x: Float) {
        val index = indexAt(x)
        if (index >= 0) setSelection(index)
    }

    private fun indexAt(x: Float): Int {
        if (spec.points.isEmpty()) return -1
        val ratio = plotRatio(x)
        if (spec.mode == FinancialChartMode.INTRADAY) {
            val slot = ratio * spec.sessionSlots
            return spec.points.indices.minByOrNull { abs(spec.points[it].slot - slot) } ?: -1
        }
        return viewport.indexAt(ratio, spec.points.size)
    }

    private fun plotRatio(x: Float): Float = ((x - plotLeft) / plotWidth).coerceIn(0f, 1f)

    private fun applyViewport(next: FinancialViewport) {
        if (next == viewport) return
        viewport = next
        setSelection(-1)
    }

    private fun setGestureActive(active: Boolean) {
        if (active == gestureActive) return
        gestureActive = active
        onGestureActiveChanged?.invoke(active)
    }

    private fun setSelection(index: Int) {
        if (index == selected) return
        selected = index
        onSelectionChanged?.invoke(index.takeIf { it >= 0 })
    }

    private fun startFling(indexVelocity: Float) {
        var velocity = indexVelocity
        runViewportFrames { elapsed ->
            val step = FinancialViewportMath.fling(viewport, velocity, elapsed, plotWidth, spec.points.size)
                ?: return@runViewportFrames false
            velocity = step.velocity
            applyViewport(step.viewport)
            !step.finished
        }
    }

    private fun animateTo(target: FinancialViewport, durationMillis: Int) {
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

    /** Drives [frame] with the elapsed milliseconds per tick until it returns `false`. */
    private fun runViewportFrames(frame: (Float) -> Boolean) {
        stopViewportAnimation()
        if (!loaded) return
        val generation = viewportGeneration
        var last = now()
        fun schedule() {
            viewportTimeout = setTimeout(FRAME_MILLIS) {
                if (!loaded || generation != viewportGeneration) return@setTimeout
                viewportTimeout = null
                val current = now()
                val elapsed = (current - last).coerceIn(1f, MAX_FRAME_MILLIS)
                last = current
                if (frame(elapsed)) schedule()
            }
        }
        schedule()
    }

    private fun stopViewportAnimation() {
        viewportGeneration++
        viewportTimeout?.let { clearTimeout(it) }
        viewportTimeout = null
    }

    private fun now(): Float = clock.elapsedNow().inWholeMicroseconds / 1000f

    private fun recordSample(x: Float) {
        samples.add(TouchSample(x, now()))
        while (samples.size > MAX_SAMPLES) samples.removeAt(0)
    }

    private fun touchCount(params: TouchParams): Int = params.touches.size.coerceAtLeast(1)

    private fun primaryPoint(params: TouchParams): Pair<Float, Float> {
        val touch = params.touches.firstOrNull() ?: return params.x to params.y
        return touch.x to touch.y
    }

    private fun midpointX(params: TouchParams): Float =
        if (params.touches.size < 2) params.x else (params.touches[0].x + params.touches[1].x) / 2f

    private fun distance(params: TouchParams): Float {
        if (params.touches.size < 2) return 0f
        val dx = params.touches[0].x - params.touches[1].x
        val dy = params.touches[0].y - params.touches[1].y
        return sqrt(dx * dx + dy * dy)
    }

    private enum class Gesture { NONE, UNDECIDED, PAN, PINCH, CROSSHAIR, REJECTED }

    private companion object {
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
