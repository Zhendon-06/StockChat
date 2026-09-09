package com.guet.liang.kuiklychart.finance

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor

/**
 * Continuous candle viewport. [start] is the fractional index drawn at the left plot edge and
 * [count] the fractional number of slots spanning the plot, so pans and pinches move by pixels
 * instead of snapping candle by candle. Partial candles at both edges are expected.
 */
public data class FinancialViewport(val start: Float, val count: Float) {
    public val end: Float get() = start + count

    /** Inclusive index range of candles that touch the plot, clamped to the data. */
    public fun visibleIndices(size: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val first = floor(start).toInt().coerceIn(0, size - 1)
        val last = (ceil(end).toInt() - 1).coerceIn(first, size - 1)
        return first..last
    }

    /** Horizontal ratio (0 at the left plot edge, 1 at the right) of a slot centre. */
    public fun ratioOf(index: Int): Float = (index - start + 0.5f) / count.coerceAtLeast(MIN_COUNT_EPSILON)

    /** Candle under a horizontal plot ratio; the result is clamped into the visible data. */
    public fun indexAt(ratio: Float, size: Int): Int {
        val visible = visibleIndices(size)
        if (visible.isEmpty()) return -1
        return floor(start + ratio * count).toInt().coerceIn(visible.first, visible.last)
    }

    private companion object {
        const val MIN_COUNT_EPSILON = 0.001f
    }
}

/** Pure viewport arithmetic for pan, pinch, fling and edge behaviour; shared by every platform. */
public object FinancialViewportMath {
    public const val MIN_COUNT: Float = 10f
    public const val MAX_COUNT: Float = 240f

    /** Dragging past the first or last candle is allowed up to this share of the viewport. */
    public const val OVERSCROLL_RATIO: Float = 0.25f

    /** Time constant of the exponential fling decay, in milliseconds. */
    public const val FLING_TIME_CONSTANT_MILLIS: Float = 400f

    /** Flings slower than this, measured at the plot in pixels per millisecond, stop. */
    public const val FLING_STOP_SPEED: Float = 0.01f

    private const val RUBBER_BAND_RESISTANCE = 0.55f

    public fun clampCount(count: Float, size: Int): Float {
        val upper = MAX_COUNT.coerceAtMost(size.coerceAtLeast(1).toFloat())
        val lower = MIN_COUNT.coerceAtMost(upper)
        val safe = if (count.isFinite()) count else lower
        return safe.coerceIn(lower, upper)
    }

    public fun maxStart(count: Float, size: Int): Float = (size - count).coerceAtLeast(0f)

    /** Keeps the viewport inside the data; the last candle stays flush with the right edge. */
    public fun clamp(viewport: FinancialViewport, size: Int): FinancialViewport {
        val count = clampCount(viewport.count, size)
        val start = if (viewport.start.isFinite()) viewport.start else 0f
        return FinancialViewport(start.coerceIn(0f, maxStart(count, size)), count)
    }

    /** Whether the viewport currently shows over-scroll space beyond the data. */
    public fun isOverscrolled(viewport: FinancialViewport, size: Int): Boolean =
        viewport.start < 0f || viewport.start > maxStart(viewport.count, size)

    /** Latest data view used by [FinancialChartSpec.visibleCount] and reset. */
    public fun latest(visibleCount: Int, size: Int): FinancialViewport {
        val count = clampCount(visibleCount.toFloat(), size)
        return FinancialViewport(maxStart(count, size), count)
    }

    /**
     * Drags [origin] by [deltaPixels] on a plot [plotWidth] wide. Beyond the data the movement is
     * damped like a rubber band and never exceeds [OVERSCROLL_RATIO] of the viewport.
     */
    public fun pan(origin: FinancialViewport, deltaPixels: Float, plotWidth: Float, size: Int): FinancialViewport {
        if (!deltaPixels.isFinite() || size <= 0) return clamp(origin, size)
        val count = clampCount(origin.count, size)
        val raw = origin.start - deltaPixels / plotWidth.coerceAtLeast(1f) * count
        val upper = maxStart(count, size)
        val limit = count * OVERSCROLL_RATIO
        val start = when {
            raw < 0f -> -rubberBand(-raw, limit)
            raw > upper -> upper + rubberBand(raw - upper, limit)
            else -> raw
        }
        return FinancialViewport(start, count)
    }

    /**
     * Scales [origin] by [scale] (>1 zooms in) while the data under [anchorRatio] at the pinch start
     * lands under [focalRatio] now, so fingers can zoom and drag in one motion.
     */
    public fun zoom(
        origin: FinancialViewport,
        scale: Float,
        anchorRatio: Float,
        size: Int,
        focalRatio: Float = anchorRatio,
    ): FinancialViewport {
        if (!scale.isFinite() || scale <= 0f) return clamp(origin, size)
        val count = clampCount(origin.count / scale, size)
        val anchorIndex = origin.start + anchorRatio.coerceIn(0f, 1f) * origin.count
        val start = anchorIndex - focalRatio.coerceIn(0f, 1f) * count
        return clamp(FinancialViewport(start, count), size)
    }

    /** Zooms around the centre, or keeps the newest candle pinned when the view already shows it. */
    public fun zoomAroundCenter(origin: FinancialViewport, scale: Float, size: Int): FinancialViewport {
        val atLatest = origin.end >= size - 0.001f
        return zoom(origin, scale, if (atLatest) 1f else 0.5f, size)
    }

    /**
     * Advances an inertial scroll by [elapsedMillis]. Velocity is in candles per millisecond.
     * Returns `null` once the fling has finished, either by slowing down or by reaching an edge.
     */
    public fun fling(
        viewport: FinancialViewport,
        velocity: Float,
        elapsedMillis: Float,
        plotWidth: Float,
        size: Int,
    ): FlingStep? {
        if (!velocity.isFinite() || elapsedMillis <= 0f) return null
        val pixelSpeed = abs(velocity) * plotWidth.coerceAtLeast(1f) / viewport.count.coerceAtLeast(1f)
        if (pixelSpeed < FLING_STOP_SPEED) return null
        val decay = exp(-elapsedMillis / FLING_TIME_CONSTANT_MILLIS)
        // Integrate the exponentially decaying velocity over the frame.
        val travelled = velocity * FLING_TIME_CONSTANT_MILLIS * (1f - decay)
        val moved = FinancialViewport(viewport.start + travelled, viewport.count)
        val clamped = clamp(moved, size)
        val hitEdge = abs(clamped.start - moved.start) > 0.0001f
        return FlingStep(clamped, if (hitEdge) 0f else velocity * decay, finished = hitEdge)
    }

    /**
     * Velocity from touch samples, ignoring samples older than [windowMillis] before the last one.
     * Result is in pixels per millisecond; `0` when there is no usable history.
     */
    public fun velocity(samples: List<TouchSample>, windowMillis: Float = 100f): Float {
        if (samples.size < 2) return 0f
        val latest = samples.last()
        val oldest = samples.firstOrNull { latest.timeMillis - it.timeMillis <= windowMillis } ?: return 0f
        val elapsed = latest.timeMillis - oldest.timeMillis
        if (elapsed <= 0f || oldest === latest) return 0f
        return (latest.x - oldest.x) / elapsed
    }

    /** Ease-out interpolation between two viewports for settle and programmatic zoom animations. */
    public fun interpolate(from: FinancialViewport, to: FinancialViewport, progress: Float): FinancialViewport {
        val eased = 1f - (1f - progress.coerceIn(0f, 1f)).let { it * it * it }
        return FinancialViewport(
            from.start + (to.start - from.start) * eased,
            from.count + (to.count - from.count) * eased,
        )
    }

    private fun rubberBand(overshoot: Float, limit: Float): Float {
        if (limit <= 0f || overshoot <= 0f) return 0f
        return limit * (1f - exp(-RUBBER_BAND_RESISTANCE * overshoot / limit))
    }

    public data class FlingStep(val viewport: FinancialViewport, val velocity: Float, val finished: Boolean)

    public data class TouchSample(val x: Float, val timeMillis: Float)
}
