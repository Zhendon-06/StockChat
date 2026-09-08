package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.guet.liang.kuiklychart.api.ChartViewport
import com.guet.liang.kuiklychart.api.PieEntry
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChartHitTesterTest {

    @Test
    fun cartesianHitSelectsNearestSeriesAtTheTouchedCategory() {
        val spec = ChartSpec().apply {
            labels("A", "B")
            line("Low", 2f, 2f)
            line("High", 8f, 8f)
        }
        val geometry = ChartRenderGeometry(
            plot = ChartRect(0f, 0f, 200f, 100f),
            viewport = ChartViewport(0f, 1f),
            scale = ValueScale(0f, 10f, listOf(0f, 5f, 10f)),
            dataCount = 2,
        )

        val selection = ChartHitTester.selectionAt(spec, geometry, 150f, 25f)

        requireNotNull(selection)
        assertEquals(1, selection.seriesIndex)
        assertEquals(1, selection.dataIndex)
        assertEquals("High", selection.seriesName)
        assertEquals("B", selection.label)
        assertEquals(8f, selection.value)
        assertEquals(ChartSeriesType.LINE, selection.type)
        assertNull(ChartHitTester.selectionAt(spec, geometry, -1f, 25f))
    }

    @Test
    fun fractionalViewportClampsHitsToRenderedEdgePoints() {
        val spec = ChartSpec().apply {
            labels("A", "B", "C", "D", "E")
            line("Trend", 1f, 2f, 3f, 4f, 5f)
        }
        val geometry = ChartRenderGeometry(
            plot = ChartRect(0f, 0f, 300f, 100f),
            viewport = ChartViewport(0.8f, 3.2f),
            scale = ValueScale(0f, 5f, listOf(0f, 5f)),
            dataCount = 5,
        )

        val leftEdge = ChartHitTester.selectionAt(spec, geometry, 0f, 80f)
        val rightEdge = ChartHitTester.selectionAt(spec, geometry, 300f, 20f)

        requireNotNull(leftEdge)
        requireNotNull(rightEdge)
        assertEquals(1, leftEdge.dataIndex)
        assertEquals(3, rightEdge.dataIndex)
    }

    @Test
    fun groupedBarHitUsesTheActualBarRectangle() {
        val spec = ChartSpec().apply {
            labels("A")
            bars("First", 6f)
            bars("Second", 9f)
        }
        val geometry = ChartRenderGeometry(
            plot = ChartRect(0f, 0f, 120f, 100f),
            viewport = ChartViewport(0f, 0f),
            scale = ValueScale(0f, 10f, listOf(0f, 5f, 10f)),
            dataCount = 1,
            bars = listOf(
                BarGeometry(0, 0, ChartRect(20f, 40f, 55f, 100f)),
                BarGeometry(1, 0, ChartRect(65f, 10f, 100f, 100f)),
            ),
        )

        val selection = ChartHitTester.selectionAt(spec, geometry, 80f, 50f)

        requireNotNull(selection)
        assertEquals(1, selection.seriesIndex)
        assertEquals("Second", selection.seriesName)
        assertNull(ChartHitTester.selectionAt(spec, geometry, 60f, 50f))
    }

    @Test
    fun pieHitRespectsSliceAnglesAndDonutHole() {
        val spec = ChartSpec().apply {
            pie(
                "Share",
                PieEntry("First", 1f),
                PieEntry("Second", 3f),
            )
        }
        val halfPi = (PI / 2.0).toFloat()
        val fullCircle = (PI * 2.0).toFloat()
        val geometry = ChartRenderGeometry(
            plot = ChartRect(0f, 0f, 100f, 100f),
            viewport = ChartViewport(0f, 1f),
            scale = ValueScale(0f, 1f, listOf(0f, 1f)),
            dataCount = 2,
            pieSlices = listOf(
                PieSliceGeometry(0, 0, 50f, 50f, 40f, 10f, 0f, halfPi),
                PieSliceGeometry(0, 1, 50f, 50f, 40f, 10f, halfPi, fullCircle),
            ),
        )

        val first = ChartHitTester.selectionAt(spec, geometry, 70f, 50f)
        val second = ChartHitTester.selectionAt(spec, geometry, 30f, 50f)

        requireNotNull(first)
        requireNotNull(second)
        assertEquals(0, first.dataIndex)
        assertEquals("First", first.label)
        assertEquals(1, second.dataIndex)
        assertEquals("Second", second.label)
        assertEquals(ChartSeriesType.PIE, second.type)
        assertNull(ChartHitTester.selectionAt(spec, geometry, 50f, 50f))
        assertNull(ChartHitTester.selectionAt(spec, geometry, 95f, 50f))
    }

    @Test
    fun pieHitDoesNotSelectTheVisualSliceGap() {
        val spec = ChartSpec().apply {
            pie("Share", PieEntry("Only", 1f))
        }
        val geometry = ChartRenderGeometry(
            plot = ChartRect(0f, 0f, 100f, 100f),
            viewport = ChartViewport(0f, 0f),
            scale = ValueScale(0f, 1f, listOf(0f, 1f)),
            dataCount = 1,
            pieSlices = listOf(
                PieSliceGeometry(0, 0, 50f, 50f, 40f, 0f, 0.1f, (PI * 2.0 - 0.1).toFloat()),
            ),
        )

        assertNull(ChartHitTester.selectionAt(spec, geometry, 70f, 50f))
        assertEquals(0, ChartHitTester.selectionAt(spec, geometry, 50f, 70f)?.dataIndex)
    }
}
