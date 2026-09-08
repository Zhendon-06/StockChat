package com.guet.liang.kuiklychart

import com.guet.liang.kuiklychart.api.ChartSeriesType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChartViewTest {

    @Test
    fun updatingDataClearsASelectionFromThePreviousSpec() {
        val chartView = ChartView(ChartSeriesType.LINE)
        chartView.chart {
            labels("Old")
            line("Old series", 1f)
        }
        chartView.select(0, 0)
        assertEquals("Old series", chartView.currentSelection?.seriesName)

        chartView.update {
            clearSeries()
            labels("New")
            line("New series", 9f)
        }

        assertNull(chartView.currentSelection)
    }

    @Test
    fun nonFiniteValuesAndViewportArgumentsAreIgnored() {
        val chartView = ChartView(ChartSeriesType.LINE)
        chartView.chart {
            line("Invalid", Float.NaN, Float.POSITIVE_INFINITY)
        }
        chartView.created()

        chartView.select(0, 0)
        chartView.setViewport(Float.NaN, 1f)
        chartView.panBy(Float.POSITIVE_INFINITY)

        assertNull(chartView.currentSelection)
        assertEquals(0f, chartView.currentViewport.startIndex)
        assertEquals(1f, chartView.currentViewport.endIndex)
    }

    @Test
    fun fullViewportExpandsWhenDataGrowsButZoomedViewportIsPreserved() {
        val chartView = ChartView(ChartSeriesType.LINE)
        chartView.chart {
            labels("A", "B", "C")
            line("Series", 1f, 2f, 3f)
        }
        chartView.created()

        chartView.update(animated = false) {
            labels("A", "B", "C", "D", "E")
            series.single().values(1f, 2f, 3f, 4f, 5f)
        }

        assertEquals(0f, chartView.currentViewport.startIndex)
        assertEquals(4f, chartView.currentViewport.endIndex)

        chartView.setViewport(1f, 3f)
        chartView.update(animated = false) {
            labels("A", "B", "C", "D", "E", "F")
            series.single().values(1f, 2f, 3f, 4f, 5f, 6f)
        }

        assertEquals(1f, chartView.currentViewport.startIndex)
        assertEquals(3f, chartView.currentViewport.endIndex)
    }
}
