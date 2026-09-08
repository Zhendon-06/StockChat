package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScaleMathTest {

    @Test
    fun positiveLineValuesProduceAPositiveNiceScale() {
        val spec = ChartSpec().apply {
            line("Revenue", 12f, 18f, 25f)
        }

        val scale = ScaleMath.calculate(spec, ViewportMath.full(3))

        assertEquals(10f, scale.minimum, 0.0001f)
        assertEquals(25f, scale.maximum, 0.0001f)
        assertEquals(listOf(10f, 15f, 20f, 25f), scale.ticks)
    }

    @Test
    fun negativeLineValuesKeepTheScaleBelowZero() {
        val spec = ChartSpec().apply {
            line("Change", -25f, -12f, -18f)
        }

        val scale = ScaleMath.calculate(spec, ViewportMath.full(3))

        assertEquals(-25f, scale.minimum, 0.0001f)
        assertEquals(-10f, scale.maximum, 0.0001f)
        assertEquals(listOf(-25f, -20f, -15f, -10f), scale.ticks)
    }

    @Test
    fun positiveBarsIncludeZero() {
        val spec = ChartSpec().apply {
            bars("Orders", 4f, 8f, 12f)
        }

        val scale = ScaleMath.calculate(spec, ViewportMath.full(3))

        assertEquals(0f, scale.minimum, 0.0001f)
        assertTrue(scale.maximum >= 12f)
        assertTrue(0f in scale.ticks)
    }

    @Test
    fun explicitAxisRangeOverridesCalculatedBounds() {
        val spec = ChartSpec().apply {
            axes {
                y {
                    minimum = -10f
                    maximum = 40f
                    tickCount = 5
                }
            }
            line("Temperature", 0f, 15f, 30f)
        }

        val scale = ScaleMath.calculate(spec, ViewportMath.full(3))

        assertEquals(-10f, scale.minimum, 0.0001f)
        assertEquals(40f, scale.maximum, 0.0001f)
        assertEquals(-10f, scale.ticks.first(), 0.0001f)
        assertEquals(40f, scale.ticks.last(), 0.0001f)
    }

    @Test
    fun explicitWideAxisRangeProducesEvenTicksAcrossTheRange() {
        val spec = ChartSpec().apply {
            axes {
                y {
                    minimum = 0f
                    maximum = 100f
                    tickCount = 5
                }
            }
            line("Narrow data", 48f, 50f, 52f)
        }

        val scale = ScaleMath.calculate(spec, ViewportMath.full(3))

        assertEquals(listOf(0f, 20f, 40f, 60f, 80f, 100f), scale.ticks)
    }

    @Test
    fun reversedExplicitAxisBoundsAreNormalized() {
        val spec = ChartSpec().apply {
            axes {
                y {
                    minimum = 40f
                    maximum = -10f
                }
            }
            line("Temperature", 0f, 15f, 30f)
        }

        val scale = ScaleMath.calculate(spec, ViewportMath.full(3))

        assertEquals(-10f, scale.minimum)
        assertEquals(40f, scale.maximum)
    }

    @Test
    fun emptyDataUsesFiniteFallbackScale() {
        val scale = ScaleMath.calculate(ChartSpec(), ViewportMath.full(0))

        assertEquals(0f, scale.minimum, 0.0001f)
        assertEquals(1f, scale.maximum, 0.0001f)
        assertTrue(scale.ticks.isNotEmpty())
        assertTrue(scale.ticks.all(Float::isFinite))
    }

    @Test
    fun animatedScaleInterpolatesBoundsAndEndsAtTheExactTarget() {
        val start = ValueScale(0f, 100f, listOf(0f, 50f, 100f))
        val target = ValueScale(-50f, 150f, listOf(-50f, 0f, 50f, 100f, 150f))

        val midpoint = start.interpolateTo(target, 0.5f)

        assertEquals(-25f, midpoint.minimum, 0.0001f)
        assertEquals(125f, midpoint.maximum, 0.0001f)
        assertEquals(4, midpoint.ticks.size)
        assertEquals(3, start.interpolateTo(target, 0.1f).ticks.size)
        assertEquals(5, start.interpolateTo(target, 0.9f).ticks.size)
        assertEquals(start, start.interpolateTo(target, 0f))
        assertEquals(target, start.interpolateTo(target, 1f))
    }
}
