package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

internal object ChartHitTester {
    fun selectionAt(
        spec: ChartSpec,
        geometry: ChartRenderGeometry,
        horizontal: Float,
        vertical: Float,
    ): ChartSelection? {
        return if (geometry.pieSlices.isNotEmpty()) {
            pieSelectionAt(spec, geometry, horizontal, vertical)
        } else {
            cartesianSelectionAt(spec, geometry, horizontal, vertical)
        }
    }

    private fun cartesianSelectionAt(
        spec: ChartSpec,
        geometry: ChartRenderGeometry,
        horizontal: Float,
        vertical: Float,
    ): ChartSelection? {
        if (!geometry.plot.contains(horizontal, vertical) || geometry.categoryWidth <= 0f) {
            return null
        }
        geometry.bars.firstOrNull { it.rect.contains(horizontal, vertical) }?.let { bar ->
            return selection(spec, bar.seriesIndex, bar.dataIndex)
        }

        val firstDataIndex = ceil(geometry.viewport.startIndex - 0.5f).toInt().coerceAtLeast(0)
        val lastDataIndex = floor(geometry.viewport.endIndex + 0.5f).toInt()
            .coerceAtMost(geometry.dataCount - 1)
        if (lastDataIndex < firstDataIndex) {
            return null
        }
        val categoryPosition = geometry.viewport.startIndex +
            (horizontal - geometry.plot.left) / geometry.categoryWidth
        val dataIndex = floor(categoryPosition).toInt().coerceIn(firstDataIndex, lastDataIndex)
        var selectedSeriesIndex = -1
        var nearestDistance = Float.POSITIVE_INFINITY

        spec.dataSeries.forEachIndexed { seriesIndex, chartSeries ->
            if (chartSeries.type == ChartSeriesType.PIE || chartSeries.type == ChartSeriesType.BAR) {
                return@forEachIndexed
            }
            val value = chartSeries.dataValues.getOrNull(dataIndex)
            if (value == null || !value.isFinite()) {
                return@forEachIndexed
            }
            val valueVertical = geometry.scale.verticalPosition(value, geometry.plot)
            val distance = abs(vertical - valueVertical)
            if (distance < nearestDistance) {
                nearestDistance = distance
                selectedSeriesIndex = seriesIndex
            }
        }
        if (selectedSeriesIndex < 0) {
            return null
        }
        return selection(spec, selectedSeriesIndex, dataIndex)
    }

    private fun selection(spec: ChartSpec, seriesIndex: Int, dataIndex: Int): ChartSelection? {
        val selectedSeries = spec.dataSeries.getOrNull(seriesIndex) ?: return null
        val selectedValue = selectedSeries.dataValues.getOrNull(dataIndex) ?: return null
        if (!selectedValue.isFinite()) {
            return null
        }
        return ChartSelection(
            seriesIndex,
            dataIndex,
            selectedSeries.name,
            selectedSeries.dataPointLabels.getOrNull(dataIndex) ?: spec.categoryLabel(dataIndex),
            selectedValue,
            selectedSeries.type,
        )
    }

    private fun pieSelectionAt(
        spec: ChartSpec,
        geometry: ChartRenderGeometry,
        horizontal: Float,
        vertical: Float,
    ): ChartSelection? {
        geometry.pieSlices.forEach { pieSlice ->
            val horizontalDelta = horizontal - pieSlice.centerHorizontal
            val verticalDelta = vertical - pieSlice.centerVertical
            val distance = sqrt(horizontalDelta * horizontalDelta + verticalDelta * verticalDelta)
            if (distance < pieSlice.innerRadius || distance > pieSlice.outerRadius) {
                return@forEach
            }
            val touchAngle = normalizeAngle(atan2(verticalDelta, horizontalDelta))
            val startAngle = normalizeAngle(pieSlice.startAngle)
            val sweep = (pieSlice.endAngle - pieSlice.startAngle)
                .coerceIn(0f, (PI * 2.0).toFloat())
            val angleFromStart = normalizeAngle(touchAngle - startAngle)
            if (angleFromStart <= sweep) {
                val chartSeries = spec.dataSeries.getOrNull(pieSlice.seriesIndex) ?: return null
                val value = chartSeries.dataValues.getOrNull(pieSlice.dataIndex) ?: return null
                val label = chartSeries.dataPointLabels.getOrNull(pieSlice.dataIndex)
                    ?: spec.categoryLabel(pieSlice.dataIndex)
                return ChartSelection(
                    pieSlice.seriesIndex,
                    pieSlice.dataIndex,
                    chartSeries.name,
                    label,
                    value,
                    ChartSeriesType.PIE,
                )
            }
        }
        return null
    }
}
