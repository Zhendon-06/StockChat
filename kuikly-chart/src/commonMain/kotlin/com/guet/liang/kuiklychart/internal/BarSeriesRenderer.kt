package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.max
import kotlin.math.min

internal fun drawBarSeries(
    context: CanvasContext,
    spec: ChartSpec,
    geometry: ChartRenderGeometry,
    selection: ChartSelection?,
): List<BarGeometry> {
    val indexedBarSeries = spec.dataSeries.withIndex().filter { it.value.type == ChartSeriesType.BAR }
    if (indexedBarSeries.isEmpty()) {
        return emptyList()
    }
    val renderedBars = mutableListOf<BarGeometry>()
    val barSeriesCount = indexedBarSeries.size
    val groupWidth = geometry.categoryWidth * spec.bars.groupWidthRatio.coerceIn(0.1f, 1f)
    val totalSpacing = spec.bars.barSpacing.coerceAtLeast(0f) * (barSeriesCount - 1)
    val barWidth = ((groupWidth - totalSpacing) / barSeriesCount).coerceAtLeast(1f)
    val dataRange = ViewportRenderMath.visibleDataRange(geometry.viewport, geometry.dataCount)
    val zeroValue = 0f.coerceIn(geometry.scale.minimum, geometry.scale.maximum)
    val zeroVertical = geometry.scale.verticalPosition(zeroValue, geometry.plot)

    for (dataIndex in dataRange) {
        val categoryCenter = geometry.categoryCenter(dataIndex)
        indexedBarSeries.forEachIndexed { barPosition, indexedSeries ->
            val value = indexedSeries.value.dataValues.getOrNull(dataIndex)
            if (value == null || !value.isFinite()) {
                return@forEachIndexed
            }
            val valueVertical = geometry.scale.verticalPosition(value, geometry.plot)
            val requestedBarLeft = categoryCenter - groupWidth / 2f +
            barPosition * (barWidth + spec.bars.barSpacing.coerceAtLeast(0f))
            val requestedBarRight = requestedBarLeft + barWidth
            val barTop = min(valueVertical, zeroVertical)
            val barBottom = max(valueVertical, zeroVertical)
            val requestedBarRect = ChartRect(
                requestedBarLeft,
                barTop,
                requestedBarRight,
                barBottom,
            )
            val visibleBarRect = ChartRect(
                requestedBarLeft.coerceAtLeast(geometry.plot.left),
                barTop,
                requestedBarRight.coerceAtMost(geometry.plot.right),
                barBottom,
            )
            if (visibleBarRect.width <= 0f || visibleBarRect.height <= 0f) {
                return@forEachIndexed
            }
            val isPositive = value >= 0f
            val isSelected = selection?.seriesIndex == indexedSeries.index &&
            selection.dataIndex == dataIndex && selection.type == ChartSeriesType.BAR
            drawRoundedBar(
                context,
                requestedBarRect,
                spec.bars.cornerRadius,
                isPositive,
                resolveDataPointColor(indexedSeries.value, dataIndex),
            )
            if (isSelected) {
                drawRoundedBar(
                    context,
                    requestedBarRect,
                    spec.bars.cornerRadius,
                    isPositive,
                    spec.theme.selectionColor.opacity(0.35f),
                )
            }
            renderedBars.add(BarGeometry(indexedSeries.index, dataIndex, visibleBarRect))
            if (indexedSeries.value.showValues) {
                context.fillStyle(indexedSeries.value.valueLabelColor ?: spec.theme.textColor)
                context.font(spec.theme.valueFontSize)
                context.textAlign(TextAlign.CENTER)
                val labelVertical = if (isPositive) barTop - 5f else barBottom + spec.theme.valueFontSize + 3f
                drawText(
                    context,
                    spec.axes.y.formatter(value),
                    (requestedBarLeft + requestedBarRight) / 2f,
                    labelVertical,
                    TextAlign.CENTER,
                )
            }
        }
    }
    return renderedBars
}
