package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartCurve
import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSeries
import com.guet.liang.kuiklychart.api.ChartSpec
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.min

internal fun drawAreaSeries(
    context: CanvasContext,
    geometry: ChartRenderGeometry,
    chartSeries: ChartSeries,
) {
    val baseValue = 0f.coerceIn(geometry.scale.minimum, geometry.scale.maximum)
    val baseVertical = geometry.scale.verticalPosition(baseValue, geometry.plot)
    createLineSegments(chartSeries, geometry).forEach { segment ->
        if (segment.isEmpty()) {
            return@forEach
        }
        context.beginPath()
        context.moveTo(segment.first().point.horizontal, baseVertical)
        context.lineTo(segment.first().point.horizontal, segment.first().point.vertical)
        appendSeriesPath(context, segment, chartSeries.curve, moveToFirst = false)
        context.lineTo(segment.last().point.horizontal, baseVertical)
        context.closePath()
        val fillColor = chartSeries.fillColor ?: chartSeries.color
        val gradient = context.createLinearGradient(
            geometry.plot.left,
            geometry.plot.top,
            geometry.plot.left,
            geometry.plot.bottom,
        )
        val fillOpacity = chartSeries.fillOpacity.coerceIn(0f, 1f)
        gradient.addColorStop(0f, fillColor.opacity(fillOpacity))
        gradient.addColorStop(1f, fillColor.opacity(fillOpacity * 0.08f))
        context.fillStyle(gradient)
        context.fill()
    }
}

internal fun drawLineSeries(
    context: CanvasContext,
    spec: ChartSpec,
    geometry: ChartRenderGeometry,
    chartSeries: ChartSeries,
    selection: ChartSelection?,
) {
    val seriesIndex = spec.dataSeries.indexOf(chartSeries)
    context.strokeStyle(chartSeries.color)
    context.lineWidth(chartSeries.lineWidth.coerceAtLeast(0.5f))
    context.lineCapRound()
    createLineSegments(chartSeries, geometry).forEach { segment ->
        if (segment.isEmpty()) {
            return@forEach
        }
        context.beginPath()
        appendSeriesPath(context, segment, chartSeries.curve, moveToFirst = true)
        context.stroke()

        segment.forEach { point ->
            drawLinePoint(context, spec, chartSeries, selection, seriesIndex, point)
        }
    }
}

private fun createLineSegments(
    chartSeries: ChartSeries,
    geometry: ChartRenderGeometry,
): List<List<IndexedChartPoint>> {
    val segments = mutableListOf<MutableList<IndexedChartPoint>>()
    var currentSegment = mutableListOf<IndexedChartPoint>()
    val dataRange = ViewportRenderMath.visibleDataRange(
        geometry.viewport,
        min(geometry.dataCount, chartSeries.dataValues.size),
    )

    for (dataIndex in dataRange) {
        val value = chartSeries.dataValues.getOrNull(dataIndex)
        if (value == null || !value.isFinite()) {
            if (currentSegment.isNotEmpty()) {
                segments.add(currentSegment)
                currentSegment = mutableListOf()
            }
        } else {
            currentSegment.add(
                IndexedChartPoint(
                dataIndex,
                value,
                ChartPoint(
                geometry.categoryCenter(dataIndex),
                geometry.scale.verticalPosition(value, geometry.plot),
                ),
                ),
            )
        }
    }
    if (currentSegment.isNotEmpty()) {
        segments.add(currentSegment)
    }
    return segments
}

private fun appendSeriesPath(
    context: CanvasContext,
    segment: List<IndexedChartPoint>,
    curve: ChartCurve,
    moveToFirst: Boolean,
) {
    if (segment.isEmpty()) {
        return
    }
    if (moveToFirst) {
        context.moveTo(segment.first().point.horizontal, segment.first().point.vertical)
    }
    for (pointIndex in 1 until segment.size) {
        val previous = segment[pointIndex - 1].point
        val current = segment[pointIndex].point
        if (curve == ChartCurve.SMOOTH) {
            val controlOffset = (current.horizontal - previous.horizontal) / 3f
            context.bezierCurveTo(
                previous.horizontal + controlOffset,
                previous.vertical,
                current.horizontal - controlOffset,
                current.vertical,
                current.horizontal,
                current.vertical,
            )
        } else {
            context.lineTo(current.horizontal, current.vertical)
        }
    }
}
private data class IndexedChartPoint(
    val dataIndex: Int,
    val value: Float,
    val point: ChartPoint,
)

private fun drawLinePoint(
    context: CanvasContext,
    spec: ChartSpec,
    chartSeries: ChartSeries,
    selection: ChartSelection?,
    seriesIndex: Int,
    indexedPoint: IndexedChartPoint,
) {
    val selected = selection?.seriesIndex == seriesIndex &&
    selection.dataIndex == indexedPoint.dataIndex
    if (chartSeries.showPoints || selected) {
        val pointRadius = if (selected) chartSeries.pointRadius + 2f else chartSeries.pointRadius
        drawCircle(
            context,
            indexedPoint.point.horizontal,
            indexedPoint.point.vertical,
            pointRadius.coerceAtLeast(1f),
            chartSeries.color,
        )
        if (selected) {
            drawCircle(
                context,
                indexedPoint.point.horizontal,
                indexedPoint.point.vertical,
                (pointRadius - 2f).coerceAtLeast(1f),
                spec.theme.selectionColor,
            )
        }
    }
    if (chartSeries.showValues) {
        context.fillStyle(chartSeries.valueLabelColor ?: chartSeries.color)
        context.font(spec.theme.valueFontSize)
        context.textAlign(TextAlign.CENTER)
        drawText(
            context,
            spec.axes.y.formatter(indexedPoint.value),
            indexedPoint.point.horizontal,
            indexedPoint.point.vertical - chartSeries.pointRadius - 5f,
            TextAlign.CENTER,
        )
    }
}
