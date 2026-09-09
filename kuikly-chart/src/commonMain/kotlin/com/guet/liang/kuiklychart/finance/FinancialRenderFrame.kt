package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext

/** Coordinates and data shared by financial rendering stages. */
internal class FinancialRenderFrame(
    val c: CanvasContext,
    val width: Float,
    val height: Float,
    val s: FinancialChartSpec,
    val viewport: FinancialViewport,
    val selected: Int,
) {
    val lineMode = s.mode == FinancialChartMode.INTRADAY
    val closeLine = s.mode == FinancialChartMode.CLOSE_LINE
    // Fractional viewport: candles cut by either plot edge are still drawn so pans stay continuous.
    val indices = if (lineMode) s.points.indices.toList() else viewport.visibleIndices(s.points.size).toList()
    val count = viewport.count.coerceAtLeast(1f)
    val points = indices.map { s.points[it] }
    val forecastIntervals = if (closeLine) s.forecastIntervals.filter { (index, interval) ->
        index in s.points.indices && s.forecastStartIndex?.let { index >= it } == true &&
        FinancialChartMath.validInterval(interval, s.points[index].close)
    } else emptyMap()
    val left = 6f
    val right = width - 6f
    val top = 62f
    val bottom = height - if (s.showVolume) 121f else 27f
    val volumeTop = bottom + 49f
    val volumeBottom = height - 10f
    val averages = if (lineMode || closeLine) emptyList() else s.movingAveragePeriods.filter { it > 0 }.take(3)
    .map { it to FinancialChartMath.movingAverage(s.points, it) }
    private val priceBounds = calculatePriceBounds()
    val low = priceBounds.first
    val high = priceBounds.second

    private fun calculatePriceBounds(): Pair<Float, Float> {
        var (low, high) = FinancialChartMath.priceRange(points, s.previousClose, lineMode)
        // Include visible MA values in the scale, including long windows before the viewport.
        if (!lineMode) averages.forEach { (_, values) ->
            indices.mapNotNull {
                values[it]
            }.forEach {
                low = minOf(low, it)
                high = maxOf(high, it)
            }
        }
        if (closeLine) {
            indices.mapNotNull { forecastIntervals[it] }.forEach {
                low = minOf(low, it.lower)
                high = maxOf(high, it.upper)
            }
            val padding = (high - low) * 0.04f
            low -= padding
            high += padding
        }
        return low to high
    }

    fun x(index: Int): Float = left + if (lineMode) {
        s.points[index].slot / s.sessionSlots.coerceAtLeast(1f) * (right - left)
    } else viewport.ratioOf(index) * (right - left)
    fun y(value: Float): Float = top + (high - value) / (high - low).coerceAtLeast(0.0001f) * (bottom - top)
    val prevClose = s.previousClose
    fun movement(value: Float): Color = when {
        prevClose == null -> s.mutedColor
        value > prevClose -> s.riseColor
        value < prevClose -> s.fallColor
        else -> s.mutedColor
    }
    val currentIndex: Int get() = selected.takeIf { it in indices } ?: indices.last()
    val current: FinancialPoint get() = s.points[currentIndex]
}
