package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionPoint
import kotlin.test.*

class PredictionChartDataTest {
    @Test fun refusesForecastWithoutHistoricalAnchor() {
        val result = predictionChartData(emptyList(), listOf(StockPredictionPoint("2026-09-09", 12f, 11f, 13f)))
        assertTrue(result.points.isEmpty())
        assertNull(result.forecastStart)
        assertTrue(result.intervals.isEmpty())
    }

    @Test fun keepsDailyHistoryAndForecastAlignedWithBounds() {
        val result = predictionChartData(
            listOf(StockPredictionHistoryPoint("2026-09-07", 10f), StockPredictionHistoryPoint("2026-09-08", 11f)),
            listOf(StockPredictionPoint("2026-09-09", 12f, 10.5f, 13f)),
        )
        assertEquals(2, result.forecastStart)
        assertEquals(listOf(10f, 11f, 12f), result.points.map { it.close })
        assertEquals("2026-09-09", result.points.last().label)
        assertEquals(10.5f, result.intervals[2]?.lower)
    }
    @Test fun missingOrInvertedIntervalsAreNeverInvented() {
        val result = predictionChartData(
            listOf(StockPredictionHistoryPoint("2026-09-08", 11f)),
            listOf(StockPredictionPoint("2026-09-09", 12f), StockPredictionPoint("2026-09-10", 13f, 14f, 12f)),
        )
        assertEquals(3, result.points.size)
        assertTrue(result.intervals.isEmpty())
    }
    @Test fun rejectsInvalidAndNonFuturePricesWithoutShiftingIntervalIndices() {
        val result = predictionChartData(
            listOf(StockPredictionHistoryPoint("2026-09-08", 11f)),
            listOf(StockPredictionPoint("2026-09-07", 10f), StockPredictionPoint("2026-09-09", Float.NaN),
                StockPredictionPoint("2026-09-10", 12f, 11f, 13f)),
        )
        assertEquals(2, result.points.size)
        assertEquals(setOf(1), result.intervals.keys)
    }
}
