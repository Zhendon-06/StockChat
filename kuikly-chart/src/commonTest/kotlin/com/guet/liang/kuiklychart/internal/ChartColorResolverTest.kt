package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartSpec
import com.tencent.kuikly.core.base.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ChartColorResolverTest {

    @Test
    fun barColorsAreResolvedByDataIndexAndRepeat() {
        val firstColor = Color(0xFF2563EBL)
        val secondColor = Color(0xFFDB2777L)
        val spec = ChartSpec().apply {
            bars("Sales", 10f, 20f, 30f) {
                barColors(firstColor, secondColor)
            }
        }
        val series = spec.series.single()

        assertEquals(firstColor.hexColor, resolveDataPointColor(series, 0).hexColor)
        assertEquals(secondColor.hexColor, resolveDataPointColor(series, 1).hexColor)
        assertEquals(firstColor.hexColor, resolveDataPointColor(series, 2).hexColor)
    }

    @Test
    fun barsWithoutPerBarColorsUseTheSeriesColor() {
        val seriesColor = Color(0xFF059669L)
        val spec = ChartSpec().apply {
            bars("Sales", 10f, 20f) {
                color(seriesColor)
            }
        }
        val series = spec.series.single()

        assertEquals(seriesColor.hexColor, resolveDataPointColor(series, 0).hexColor)
        assertEquals(seriesColor.hexColor, resolveDataPointColor(series, 1).hexColor)
    }
}
