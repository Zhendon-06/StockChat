package com.guet.liang.kuiklychart.api

import com.tencent.kuikly.core.base.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChartSpecDslTest {

    @Test
    fun lineDslKeepsLabelsAndColors() {
        val lineColor = Color(0xFF112233L)
        val firstPointColor = Color(0xFF445566L)
        val secondPointColor = Color(0xFF778899L)
        val valueLabelColor = Color(0xFFAABBCCL)

        val spec = ChartSpec().apply {
            labels("Jan", "Feb")
            line("Revenue", 10f, 20f) {
                color(lineColor)
                pointLabels("Start", "Finish")
                pointColors(firstPointColor, secondPointColor)
                valueLabels(color = valueLabelColor)
            }
        }

        val series = spec.series.single()
        assertEquals(listOf("Jan", "Feb"), spec.labels)
        assertEquals(ChartSeriesType.LINE, series.type)
        assertEquals(listOf("Start", "Finish"), series.pointLabels)
        assertEquals(listOf(lineColor.hexColor), listOf(series.color.hexColor))
        assertEquals(
            listOf(firstPointColor.hexColor, secondPointColor.hexColor),
            series.pointColors.map(Color::hexColor),
        )
        assertTrue(series.showValues)
        assertEquals(valueLabelColor.hexColor, series.valueLabelColor?.hexColor)
    }

    @Test
    fun barDslKeepsCategoryLabelsAndPerBarColors() {
        val firstBarColor = Color(0xFF0088FFL)
        val secondBarColor = Color(0xFFFF8800L)

        val spec = ChartSpec().apply {
            labels("Online", "Store")
            bars("Sales", 8f, 12f) {
                pointLabels("Web", "Retail")
                barColors(firstBarColor, secondBarColor)
            }
        }

        val series = spec.series.single()
        assertEquals(ChartSeriesType.BAR, series.type)
        assertEquals(listOf("Online", "Store"), spec.labels)
        assertEquals(listOf("Web", "Retail"), series.pointLabels)
        assertEquals(
            listOf(firstBarColor.hexColor, secondBarColor.hexColor),
            series.pointColors.map(Color::hexColor),
        )
    }

    @Test
    fun pieDslDerivesSliceLabelsAndColorsFromEntries() {
        val directColor = Color(0xFF00AA66L)
        val spec = ChartSpec().apply {
            pie(
                "Traffic",
                PieEntry("Direct", 70f, directColor),
                PieEntry("Search", 30f),
            )
        }

        val series = spec.series.single()
        assertEquals(ChartSeriesType.PIE, series.type)
        assertEquals(listOf(70f, 30f), series.values)
        assertEquals(listOf("Direct", "Search"), series.pointLabels)
        assertEquals(directColor.hexColor, series.pointColors[0].hexColor)
        assertEquals(ChartPalette.colors[1].hexColor, series.pointColors[1].hexColor)
    }

    @Test
    fun exposedListsAreSnapshotsOfDslState() {
        val spec = ChartSpec().apply {
            labels("A")
            line("Trend", 1f)
        }
        val exposedLabels = spec.labels.toMutableList()
        val exposedSeries = spec.series.toMutableList()
        val exposedValues = spec.series.single().values.toMutableList()

        exposedLabels.add("B")
        exposedSeries.clear()
        exposedValues[0] = 99f

        assertEquals(listOf("A"), spec.labels)
        assertEquals(1, spec.series.size)
        assertEquals(listOf(1f), spec.series.single().values)
    }

    @Test
    fun animationDslKeepsRuntimeTransitionOptions() {
        val spec = ChartSpec().apply {
            animation {
                enabled = false
                durationMillis = 720
                easing = ChartAnimationEasing.EASE_OUT
            }
        }

        assertFalse(spec.animation.enabled)
        assertEquals(720, spec.animation.durationMillis)
        assertEquals(ChartAnimationEasing.EASE_OUT, spec.animation.easing)
    }
}
