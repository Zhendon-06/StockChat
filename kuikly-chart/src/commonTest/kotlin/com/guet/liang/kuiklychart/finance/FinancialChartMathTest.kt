package com.guet.liang.kuiklychart.finance

import kotlin.test.*

class FinancialChartMathTest {
    private fun point(close: Float) = FinancialPoint("day", close, close, close, close, 0f)
    @Test fun averageHasNoInventedWarmupAndUsesAllHistory() {
        val values = FinancialChartMath.movingAverage((1..25).map { point(it.toFloat()) }, 20)
        assertTrue(values.take(19).all { it == null })
        assertEquals(10.5f, values[19])
        assertEquals(15.5f, values.last())
    }
    @Test fun intradayIsSymmetricAroundPreviousCloseIncludingAverage() {
        val (low, high) = FinancialChartMath.priceRange(listOf(point(11f).copy(average = 8f)), 10f, true)
        assertEquals(10f - low, high - 10f, 0.00001f)
        assertTrue(low < 8f && high > 11f)
    }
    @Test fun flatAndEmptyRangesRemainFinite() {
        val (low, high) = FinancialChartMath.priceRange(listOf(point(10f)), null, false)
        assertTrue(low < 10f && high > 10f)
        assertEquals(0f to 1f, FinancialChartMath.priceRange(emptyList(), null, true))
    }
    @Test fun malformedOhlcAndNegativeVolumesAreRejected() {
        assertFalse(FinancialChartMath.valid(point(10f).copy(high = 9f)))
        assertFalse(FinancialChartMath.valid(point(10f).copy(volume = -1f)))
        assertFalse(FinancialChartMath.valid(point(Float.NaN)))
        assertTrue(FinancialChartMath.valid(point(10f).copy(volume = null)))
    }
    @Test fun forecastBoundsMustBeFiniteAndContainPrediction() {
        assertTrue(FinancialChartMath.validInterval(FinancialInterval(9f, 11f), 10f))
        assertFalse(FinancialChartMath.validInterval(FinancialInterval(11f, 9f), 10f))
        assertFalse(FinancialChartMath.validInterval(FinancialInterval(9f, Float.NaN), 10f))
        assertFalse(FinancialChartMath.validInterval(FinancialInterval(-1f, 11f), 10f))
        assertFalse(FinancialChartMath.validInterval(FinancialInterval(8f, 9f), 10f))
    }
    @Test fun formattingPreservesZerosAndSign() {
        assertEquals("16.20", financialNumber(16.2f))
        assertEquals("-1.25万", financialVolume(-12500f))
        assertEquals("--", financialNumber(Float.NaN))
    }
}
