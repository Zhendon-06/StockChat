package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartAnimationEasing
import com.guet.liang.kuiklychart.api.ChartSpec
import com.tencent.kuikly.core.base.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChartDataAnimationTest {

    @Test
    fun transitionInterpolatesOldValuesAndRestoresExactTarget() {
        val spec = ChartSpec().apply {
            line("Revenue", 10f, -20f)
        }
        val snapshot = ChartDataSnapshot.capture(spec)
        spec.series.single().values(30f, 20f)
        val transition = ChartDataTransition.create(snapshot, spec)

        transition.apply(0f)
        assertEquals(listOf(10f, -20f), spec.series.single().values)

        transition.apply(0.5f)
        assertEquals(listOf(20f, 0f), spec.series.single().values)

        transition.apply(1f)
        assertEquals(listOf(30f, 20f), spec.series.single().values)
    }

    @Test
    fun newPointsGrowFromZeroAndTargetGapsStayGaps() {
        val spec = ChartSpec().apply {
            area("Users", listOf(8f, null))
        }
        val snapshot = ChartDataSnapshot.capture(spec)
        spec.series.single().values(listOf(12f, 20f, null, Float.NaN))
        val transition = ChartDataTransition.create(snapshot, spec)

        transition.apply(0.5f)

        val values = spec.series.single().values
        assertEquals(10f, values[0])
        assertEquals(10f, values[1])
        assertNull(values[2])
        assertTrue(values[3]?.isNaN() == true)
    }

    @Test
    fun finiteValuesExitThroughZeroBeforeNonFiniteTargetsAreRestored() {
        val spec = ChartSpec().apply {
            line("Trend", 8f, -6f, 4f)
        }
        val snapshot = ChartDataSnapshot.capture(spec)
        spec.series.single().values(listOf(null, Float.NaN, Float.POSITIVE_INFINITY))
        val transition = ChartDataTransition.create(snapshot, spec)

        assertTrue(transition.hasChanges)
        transition.apply(0f)
        assertEquals(listOf(8f, -6f, 4f), spec.series.single().values)

        transition.apply(0.5f)
        assertEquals(listOf(4f, -3f, 2f), spec.series.single().values)

        transition.apply(1f)
        val targetValues = spec.series.single().values
        assertNull(targetValues[0])
        assertTrue(targetValues[1]?.isNaN() == true)
        assertEquals(Float.POSITIVE_INFINITY, targetValues[2])
    }

    @Test
    fun shortenedSeriesKeepsTrailingValuesUntilTheyReachZero() {
        val spec = ChartSpec().apply {
            area("Users", 10f, 20f, -30f)
        }
        val snapshot = ChartDataSnapshot.capture(spec)
        spec.series.single().values(14f)
        val transition = ChartDataTransition.create(snapshot, spec)

        transition.apply(0f)
        assertEquals(listOf(10f, 20f, -30f), spec.series.single().values)

        transition.apply(0.5f)
        assertEquals(listOf(12f, 10f, -15f), spec.series.single().values)

        transition.apply(1f)
        assertEquals(listOf(14f), spec.series.single().values)
    }

    @Test
    fun removedSeriesIsMountedOnlyDuringItsExitTransition() {
        val spec = ChartSpec().apply {
            bars("Removed", 10f, -20f) {
                color(Color(0xFF336699L))
            }
        }
        val removedSeries = spec.series.single()
        val snapshot = ChartDataSnapshot.capture(spec)
        spec.clearSeries()
        val transition = ChartDataTransition.create(snapshot, spec)

        assertTrue(transition.hasChanges)
        assertTrue(spec.series.isEmpty())

        transition.apply(0f)
        assertSame(removedSeries, spec.series.single())
        assertEquals(listOf(10f, -20f), removedSeries.values)
        assertEquals(0xFF336699L, removedSeries.color.hexColor)

        transition.apply(0.5f)
        assertEquals(listOf(5f, -10f), removedSeries.values)
        assertEquals(0x7FL, removedSeries.color.hexColor ushr 24)

        transition.apply(1f)
        assertTrue(spec.series.isEmpty())
        assertEquals(listOf(10f, -20f), removedSeries.values)
        assertEquals(0xFF336699L, removedSeries.color.hexColor)
    }

    @Test
    fun removedSeriesKeepsItsOriginalOrderDuringExit() {
        val spec = ChartSpec().apply {
            bars("Removed", 10f)
            bars("Remaining", 20f)
        }
        val removedSeries = spec.series[0]
        val snapshot = ChartDataSnapshot.capture(spec)
        spec.clearSeries()
        val remainingSeries = spec.bars("Remaining", 30f)
        val transition = ChartDataTransition.create(snapshot, spec)

        transition.apply(0.5f)

        assertSame(removedSeries, spec.series[0])
        assertSame(remainingSeries, spec.series[1])
    }

    @Test
    fun interruptedRemovedSeriesContinuesFromItsDisplayedValuesAndCleansUp() {
        val spec = ChartSpec().apply {
            line("Removed", 20f)
        }
        val initial = ChartDataSnapshot.capture(spec)
        spec.clearSeries()
        val firstTransition = ChartDataTransition.create(initial, spec)
        firstTransition.apply(0.4f)
        val displayed = ChartDataSnapshot.capture(spec)

        firstTransition.apply(1f)
        assertTrue(spec.series.isEmpty())

        val continuedTransition = ChartDataTransition.create(displayed, spec)
        continuedTransition.apply(0f)
        assertEquals(listOf(12f), spec.series.single().values)

        continuedTransition.apply(0.5f)
        assertEquals(listOf(6f), spec.series.single().values)

        continuedTransition.apply(1f)
        assertTrue(spec.series.isEmpty())
    }

    @Test
    fun easingCurvesKeepEndpointsAndExpectedMidpoints() {
        ChartAnimationEasing.entries.forEach { easing ->
            assertEquals(0f, easing.transform(0f))
            assertEquals(1f, easing.transform(1f))
            assertTrue(easing.transform(0.25f) in 0f..1f)
            assertTrue(easing.transform(0.75f) in 0f..1f)
        }
        assertEquals(0.25f, ChartAnimationEasing.LINEAR.transform(0.25f))
        assertEquals(0.0625f, ChartAnimationEasing.EASE_IN.transform(0.25f))
        assertEquals(0.4375f, ChartAnimationEasing.EASE_OUT.transform(0.25f))
        assertEquals(0.5f, ChartAnimationEasing.EASE_IN_OUT.transform(0.5f))
    }

    @Test
    fun reorderedSeriesFollowTheirNamesInsteadOfTheirOldPositions() {
        val spec = ChartSpec().apply {
            line("First", 10f)
            line("Second", 80f)
        }
        val snapshot = ChartDataSnapshot.capture(spec)
        spec.clearSeries()
        spec.line("Second", 100f)
        spec.line("First", 30f)

        val transition = ChartDataTransition.create(snapshot, spec)
        transition.apply(0f)

        assertEquals(listOf(80f), spec.series[0].values)
        assertEquals(listOf(10f), spec.series[1].values)
    }

    @Test
    fun interruptedTransitionCanContinueFromTheDisplayedFrameToItsTarget() {
        val spec = ChartSpec().apply {
            line("Revenue", 0f)
        }
        val initial = ChartDataSnapshot.capture(spec)
        spec.series.single().values(100f)
        val firstTransition = ChartDataTransition.create(initial, spec)
        firstTransition.apply(0.4f)
        val displayed = ChartDataSnapshot.capture(spec)

        firstTransition.apply(1f)
        spec.title = "Style-only update"
        val continuedTransition = ChartDataTransition.create(displayed, spec)
        continuedTransition.apply(0f)
        assertEquals(listOf(40f), spec.series.single().values)

        continuedTransition.apply(1f)
        assertEquals(listOf(100f), spec.series.single().values)
    }

    @Test
    fun finiteExtremeValuesStayFiniteDuringInterpolation() {
        val spec = ChartSpec().apply {
            bars("Range", -Float.MAX_VALUE)
        }
        val snapshot = ChartDataSnapshot.capture(spec)
        spec.series.single().values(Float.MAX_VALUE)

        ChartDataTransition.create(snapshot, spec).apply(0.5f)

        assertTrue(spec.series.single().values.single()?.isFinite() == true)
    }
}
