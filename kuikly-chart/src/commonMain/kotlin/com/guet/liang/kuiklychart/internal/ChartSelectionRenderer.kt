package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSpec
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.FontStyle
import com.tencent.kuikly.core.views.FontWeight
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal fun drawCartesianSelection(
    context: CanvasContext,
    spec: ChartSpec,
    geometry: ChartRenderGeometry,
    selection: ChartSelection,
) {
    val chartSeries = spec.dataSeries.getOrNull(selection.seriesIndex) ?: return
    val selectedValue = chartSeries.dataValues.getOrNull(selection.dataIndex) ?: return
    val selectedBar = geometry.bars.firstOrNull {
        it.seriesIndex == selection.seriesIndex && it.dataIndex == selection.dataIndex
    }
    val selectedHorizontal = selectedBar?.let { (it.rect.left + it.rect.right) / 2f }
    ?: geometry.categoryCenter(selection.dataIndex)
    val selectedVertical = selectedBar?.let {
        if (selectedValue >= 0f) it.rect.top else it.rect.bottom
    } ?: geometry.scale.verticalPosition(selectedValue, geometry.plot)

    if (spec.tooltip.crosshairEnabled) {
        context.strokeStyle(spec.theme.crosshairColor)
        context.lineWidth(1f)
        drawDashedLine(
            context,
            selectedHorizontal,
            geometry.plot.top,
            selectedHorizontal,
            geometry.plot.bottom,
            listOf(3f, 3f),
        )
        drawDashedLine(
            context,
            geometry.plot.left,
            selectedVertical,
            geometry.plot.right,
            selectedVertical,
            listOf(3f, 3f),
        )
    }
    if (spec.tooltip.enabled) {
        drawTooltip(context, spec, geometry.plot, selection, ChartPoint(selectedHorizontal, selectedVertical))
    }
}

internal fun drawPieSelectionTooltip(
    context: CanvasContext,
    spec: ChartSpec,
    geometry: ChartRenderGeometry,
    selection: ChartSelection,
) {
    val pieSlice = geometry.pieSlices.firstOrNull {
        it.seriesIndex == selection.seriesIndex && it.dataIndex == selection.dataIndex
    } ?: return
    if (!spec.tooltip.enabled) {
        return
    }
    val middleAngle = (pieSlice.startAngle + pieSlice.endAngle) / 2f
    val tooltipPoint = ChartPoint(
        pieSlice.centerHorizontal + cos(middleAngle) * pieSlice.outerRadius * 0.72f,
        pieSlice.centerVertical + sin(middleAngle) * pieSlice.outerRadius * 0.72f,
    )
    drawTooltip(context, spec, geometry.plot, selection, tooltipPoint)
}

internal fun drawTooltip(
    context: CanvasContext,
    spec: ChartSpec,
    plot: ChartRect,
    selection: ChartSelection,
    anchor: ChartPoint,
) {
    val valueFormatter = spec.tooltip.valueFormatter ?: spec.axes.y.formatter
    val title = selection.label
    val valueLine = if (spec.tooltip.showSeriesName && selection.seriesName.isNotEmpty()) {
        "${selection.seriesName}  ${valueFormatter(selection.value)}"
    } else {
        valueFormatter(selection.value)
    }
    val fontSize = spec.theme.valueFontSize
    context.font(fontSize)
    val contentWidth = max(context.measureText(title).width, context.measureText(valueLine).width)
    val tooltipWidth = contentWidth + 18f
    val tooltipHeight = 42f
    var tooltipLeft = anchor.horizontal + 10f
    if (tooltipLeft + tooltipWidth > plot.right) {
        tooltipLeft = anchor.horizontal - tooltipWidth - 10f
    }
    tooltipLeft = tooltipLeft.coerceIn(plot.left, max(plot.left, plot.right - tooltipWidth))
    var tooltipTop = anchor.vertical - tooltipHeight - 10f
    if (tooltipTop < plot.top) {
        tooltipTop = anchor.vertical + 10f
    }
    tooltipTop = tooltipTop.coerceIn(plot.top, max(plot.top, plot.bottom - tooltipHeight))

    drawRoundedRect(
        context,
        ChartRect(tooltipLeft, tooltipTop, tooltipLeft + tooltipWidth, tooltipTop + tooltipHeight),
        7f,
        spec.theme.tooltipBackgroundColor,
    )
    context.fillStyle(spec.theme.tooltipTextColor)
    context.textAlign(TextAlign.LEFT)
    context.font(fontSize)
    drawText(context, title, tooltipLeft + 9f, tooltipTop + 15f, TextAlign.LEFT)
    context.font(FontStyle.NORMAL, FontWeight.SEMISOLID, fontSize)
    drawText(context, valueLine, tooltipLeft + 9f, tooltipTop + 32f, TextAlign.LEFT)
}

internal fun drawEmptyState(context: CanvasContext, spec: ChartSpec, plot: ChartRect) {
    context.fillStyle(spec.theme.mutedTextColor)
    context.font(spec.theme.labelFontSize)
    context.textAlign(TextAlign.CENTER)
    drawText(
        context,
        spec.emptyText,
        (plot.left + plot.right) / 2f,
        (plot.top + plot.bottom) / 2f,
        TextAlign.CENTER,
    )
}
