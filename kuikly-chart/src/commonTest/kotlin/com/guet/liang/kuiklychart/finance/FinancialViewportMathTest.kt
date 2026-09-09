package com.guet.liang.kuiklychart.finance

import com.guet.liang.kuiklychart.finance.FinancialViewportMath.TouchSample
import kotlin.math.abs
import kotlin.test.*

class FinancialViewportMathTest {
    private val size = 240
    private val plotWidth = 300f

    @Test fun panMovesByPixelsWithoutSnappingToWholeCandles() {
        val origin = FinancialViewport(100f, 60f)
        val dragged = FinancialViewportMath.pan(origin, deltaPixels = -5f, plotWidth = plotWidth, size = size)
        assertEquals(60f, dragged.count)
        assertEquals(101f, dragged.start, 0.0001f)
        val tiny = FinancialViewportMath.pan(origin, -1f, plotWidth, size)
        assertTrue(tiny.start > 100f && tiny.start < 100.5f, "sub-candle drags must move the viewport")
    }

    @Test fun overscrollIsDampedAndBounded() {
        val origin = FinancialViewport(0f, 60f)
        val slight = FinancialViewportMath.pan(origin, 30f, plotWidth, size)
        assertTrue(slight.start < 0f)
        assertTrue(abs(slight.start) < 30f / plotWidth * 60f, "movement past the edge is resisted")
        val huge = FinancialViewportMath.pan(origin, 100000f, plotWidth, size)
        assertTrue(abs(huge.start) <= 60f * FinancialViewportMath.OVERSCROLL_RATIO + 0.001f)
        assertTrue(FinancialViewportMath.isOverscrolled(huge, size))
        assertEquals(FinancialViewport(0f, 60f), FinancialViewportMath.clamp(huge, size))
        val right = FinancialViewportMath.pan(FinancialViewport(180f, 60f), -100000f, plotWidth, size)
        assertTrue(right.start > 180f && right.start <= 180f + 18.001f)
        assertEquals(180f, FinancialViewportMath.clamp(right, size).start)
    }

    @Test fun zoomKeepsAnchoredCandleUnderTheFingers() {
        val origin = FinancialViewport(100f, 60f)
        val anchorIndex = origin.start + 0.25f * origin.count
        val zoomed = FinancialViewportMath.zoom(origin, scale = 2f, anchorRatio = 0.25f, size = size)
        assertEquals(30f, zoomed.count)
        assertEquals(anchorIndex, zoomed.start + 0.25f * zoomed.count, 0.001f)
        val dragged = FinancialViewportMath.zoom(origin, 2f, anchorRatio = 0.25f, size = size, focalRatio = 0.75f)
        assertEquals(anchorIndex, dragged.start + 0.75f * dragged.count, 0.001f)
    }

    @Test fun zoomRespectsCountLimitsAndDataBounds() {
        val tooFar = FinancialViewportMath.zoom(FinancialViewport(100f, 60f), 100f, 0.5f, size)
        assertEquals(FinancialViewportMath.MIN_COUNT, tooFar.count)
        val tooWide = FinancialViewportMath.zoom(FinancialViewport(100f, 60f), 0.01f, 0.5f, size)
        assertEquals(240f, tooWide.count)
        assertEquals(0f, tooWide.start)
        val small = FinancialViewportMath.zoom(FinancialViewport(0f, 5f), 0.5f, 0.5f, 5)
        assertEquals(5f, small.count)
        assertEquals(0f, small.start)
    }

    @Test fun centreZoomPinsNewestCandleOnlyWhenItIsVisible() {
        val latest = FinancialViewportMath.latest(60, size)
        assertEquals(FinancialViewport(180f, 60f), latest)
        val zoomedLatest = FinancialViewportMath.zoomAroundCenter(latest, 1.5f, size)
        assertEquals(240f, zoomedLatest.end, 0.001f)
        val history = FinancialViewport(50f, 60f)
        val zoomedHistory = FinancialViewportMath.zoomAroundCenter(history, 1.5f, size)
        assertEquals(80f, zoomedHistory.start + zoomedHistory.count / 2f, 0.001f)
    }

    @Test fun flingDecaysAndStopsAtTheEdge() {
        var viewport = FinancialViewport(100f, 60f)
        var velocity = -0.2f
        var frames = 0
        while (true) {
            val step = FinancialViewportMath.fling(viewport, velocity, 16f, plotWidth, size) ?: break
            assertTrue(abs(step.velocity) < abs(velocity) || step.finished)
            viewport = step.viewport
            velocity = step.velocity
            frames++
            if (step.finished) break
            assertTrue(frames < 1000, "fling must terminate")
        }
        assertTrue(frames > 5, "a fast fling coasts over several frames")
        assertTrue(viewport.start < 100f)
        assertTrue(viewport.start >= 0f)
        val stopped = FinancialViewportMath.fling(FinancialViewport(0f, 60f), -1f, 16f, plotWidth, size)
        assertNotNull(stopped)
        assertTrue(stopped.finished)
        assertEquals(0f, stopped.viewport.start)
        assertNull(FinancialViewportMath.fling(viewport, 0.00001f, 16f, plotWidth, size))
    }

    @Test fun velocityUsesOnlyRecentSamples() {
        assertEquals(0f, FinancialViewportMath.velocity(listOf(TouchSample(10f, 0f))))
        val samples = listOf(TouchSample(0f, 0f), TouchSample(500f, 900f), TouchSample(520f, 950f), TouchSample(540f, 1000f))
        assertEquals(0.4f, FinancialViewportMath.velocity(samples), 0.0001f)
        val stale = listOf(TouchSample(0f, 0f), TouchSample(100f, 50f), TouchSample(100f, 400f))
        assertEquals(0f, FinancialViewportMath.velocity(stale))
    }

    @Test fun fractionalViewportExposesPartialEdgeCandlesAndHitTesting() {
        val viewport = FinancialViewport(10.4f, 20f)
        assertEquals(10..30, viewport.visibleIndices(size))
        assertEquals(10, viewport.indexAt(0f, size))
        assertEquals(30, viewport.indexAt(1f, size))
        assertEquals(20, viewport.indexAt(0.5f, size))
        assertTrue(viewport.ratioOf(10) < 0.05f && viewport.ratioOf(30) > 0.95f)
        assertEquals(0..4, FinancialViewport(-3f, 10f).visibleIndices(5))
        assertTrue(FinancialViewport(0f, 10f).visibleIndices(0).isEmpty())
        assertEquals(-1, FinancialViewport(0f, 10f).indexAt(0.5f, 0))
    }

    @Test fun interpolationEasesBetweenViewports() {
        val from = FinancialViewport(0f, 60f)
        val to = FinancialViewport(100f, 30f)
        assertEquals(from, FinancialViewportMath.interpolate(from, to, 0f))
        assertEquals(to, FinancialViewportMath.interpolate(from, to, 1f))
        val half = FinancialViewportMath.interpolate(from, to, 0.5f)
        assertTrue(half.start > 50f, "ease-out covers more than half the distance at half time")
    }
}
