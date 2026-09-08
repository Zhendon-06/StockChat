package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartViewport
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportMathTest {

    @Test
    fun fullCoversEveryDataIndex() {
        assertEquals(ChartViewport(0f, 4f), ViewportMath.full(5))
        assertEquals(ChartViewport(0f, 0f), ViewportMath.full(0))
    }

    @Test
    fun initialShowsTheRequestedTrailingWindow() {
        assertEquals(ChartViewport(6f, 9f), ViewportMath.initial(10, 4))
        assertEquals(ViewportMath.full(10), ViewportMath.initial(10, 0))
        assertEquals(ViewportMath.full(10), ViewportMath.initial(10, 10))
    }

    @Test
    fun normalizePreservesSpanWhileClampingToDataBounds() {
        assertEquals(
            ChartViewport(0f, 3f),
            ViewportMath.normalize(ChartViewport(-2f, 1f), 10),
        )
        assertEquals(
            ChartViewport(5f, 9f),
            ViewportMath.normalize(ChartViewport(7f, 11f), 10),
        )
        assertEquals(
            ChartViewport(4f, 4f),
            ViewportMath.normalize(ChartViewport(4f, 1f), 10),
        )
    }

    @Test
    fun panConvertsPixelMovementToIndexMovementAndClamps() {
        assertEquals(
            ChartViewport(3f, 6f),
            ViewportMath.pan(
                viewport = ChartViewport(2f, 5f),
                horizontalDelta = -25f,
                plotWidth = 100f,
                dataCount = 10,
            ),
        )
        assertEquals(
            ChartViewport(0f, 3f),
            ViewportMath.pan(
                viewport = ChartViewport(0f, 3f),
                horizontalDelta = 50f,
                plotWidth = 100f,
                dataCount = 10,
            ),
        )
    }

    @Test
    fun panPreservesFractionalCategoryMovement() {
        assertViewport(
            expectedStart = 2.4f,
            expectedEnd = 5.4f,
            actual = ViewportMath.pan(
                viewport = ChartViewport(2f, 5f),
                horizontalDelta = -10f,
                plotWidth = 100f,
                dataCount = 10,
            ),
        )
    }

    @Test
    fun visibleDataRangeTracksCentersAcrossFractionalViewportEdges() {
        assertEquals(
            2..5,
            ViewportRenderMath.visibleDataRange(ChartViewport(2f, 5f), 12),
        )
        assertEquals(
            2..5,
            ViewportRenderMath.visibleDataRange(ChartViewport(2.25f, 5.25f), 12),
        )
        assertEquals(
            3..6,
            ViewportRenderMath.visibleDataRange(ChartViewport(2.51f, 5.51f), 12),
        )
    }

    @Test
    fun categoryLabelsStayGloballyAnchoredDuringFractionalPanning() {
        val initial = ViewportRenderMath.categoryLabelIndices(
            ChartViewport(2f, 7f),
            dataCount = 20,
            maxLabelCount = 3,
        )
        val fractionallyPanned = ViewportRenderMath.categoryLabelIndices(
            ChartViewport(2.25f, 7.25f),
            dataCount = 20,
            maxLabelCount = 3,
        )

        assertEquals(listOf(2, 4, 6), initial)
        assertEquals(initial, fractionallyPanned)
        assertEquals(
            listOf(4, 6, 8),
            ViewportRenderMath.categoryLabelIndices(
                ChartViewport(2.51f, 7.51f),
                dataCount = 20,
                maxLabelCount = 3,
            ),
        )
    }

    @Test
    fun categoryLabelAnchorsHandleSingleLabelAndDataBounds() {
        assertEquals(
            listOf(0),
            ViewportRenderMath.categoryLabelIndices(
                ChartViewport(0f, 3f),
                dataCount = 10,
                maxLabelCount = 1,
            ),
        )
        assertEquals(
            emptyList(),
            ViewportRenderMath.categoryLabelIndices(
                ChartViewport(0f, 0f),
                dataCount = 0,
                maxLabelCount = 4,
            ),
        )
    }

    @Test
    fun zoomKeepsTheFocalPointAndHonorsMinimumVisiblePoints() {
        assertViewport(
            expectedStart = 2.25f,
            expectedEnd = 6.75f,
            actual = ViewportMath.zoom(
                viewport = ChartViewport(0f, 9f),
                scale = 2f,
                focalRatio = 0.5f,
                dataCount = 10,
                minimumVisiblePoints = 3,
            ),
        )
        assertViewport(
            expectedStart = 3.5f,
            expectedEnd = 5.5f,
            actual = ViewportMath.zoom(
                viewport = ChartViewport(0f, 9f),
                scale = 100f,
                focalRatio = 0.5f,
                dataCount = 10,
                minimumVisiblePoints = 3,
            ),
        )
    }

    @Test
    fun nonFiniteInputsNeverPoisonTheViewport() {
        assertEquals(
            ViewportMath.full(5),
            ViewportMath.normalize(ChartViewport(Float.NaN, 3f), 5),
        )
        assertEquals(
            ChartViewport(1f, 3f),
            ViewportMath.pan(ChartViewport(1f, 3f), Float.POSITIVE_INFINITY, 100f, 5),
        )
        assertEquals(
            ChartViewport(1f, 3f),
            ViewportMath.zoom(ChartViewport(1f, 3f), Float.NaN, 0.5f, 5, 2),
        )
    }

    private fun assertViewport(
        expectedStart: Float,
        expectedEnd: Float,
        actual: ChartViewport,
    ) {
        assertEquals(expectedStart, actual.startIndex, 0.0001f)
        assertEquals(expectedEnd, actual.endIndex, 0.0001f)
    }
}
