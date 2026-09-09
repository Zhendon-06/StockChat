package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartSpec
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign

internal fun drawCartesianGridAndAxes(
    context: CanvasContext,
    spec: ChartSpec,
    geometry: ChartRenderGeometry,
) {
    val plot = geometry.plot
    val gridColor = spec.grid.color ?: spec.theme.gridColor

    context.lineWidth(spec.grid.lineWidth.coerceAtLeast(0.5f))
    context.strokeStyle(gridColor)
    if (spec.grid.horizontal) {
        geometry.scale.ticks.forEach { tickValue ->
            val verticalPosition = geometry.scale.verticalPosition(tickValue, plot)
            drawDashedLine(
                context,
                plot.left,
                verticalPosition,
                plot.right,
                verticalPosition,
                spec.grid.dashPattern,
            )
        }
    }

    val visibleIndices = categoryLabelIndices(spec, geometry)
    if (spec.grid.vertical) {
        visibleIndices.forEach { dataIndex ->
            val horizontalPosition = geometry.categoryCenter(dataIndex)
            drawDashedLine(
                context,
                horizontalPosition,
                plot.top,
                horizontalPosition,
                plot.bottom,
                spec.grid.dashPattern,
            )
        }
    }

    drawCartesianAxes(context, spec, geometry, visibleIndices)
}

private fun drawCartesianAxes(
    context: CanvasContext,
    spec: ChartSpec,
    geometry: ChartRenderGeometry,
    visibleIndices: List<Int>,
) {
    val plot = geometry.plot
    val horizontalAxisColor = spec.axes.x.axisColor ?: spec.theme.axisColor
    val verticalAxisColor = spec.axes.y.axisColor ?: spec.theme.axisColor
    context.lineWidth(1f)
    if (spec.axes.y.visible) {
        context.strokeStyle(verticalAxisColor)
        drawLine(context, plot.left, plot.top, plot.left, plot.bottom)
    }
    if (spec.axes.x.visible) {
        context.strokeStyle(horizontalAxisColor)
        drawLine(context, plot.left, plot.bottom, plot.right, plot.bottom)
    }

    drawCartesianAxisLabels(context, spec, geometry, visibleIndices)
}

private fun drawCartesianAxisLabels(
    context: CanvasContext,
    spec: ChartSpec,
    geometry: ChartRenderGeometry,
    visibleIndices: List<Int>,
) {
    val plot = geometry.plot
    val horizontalLabelColor = spec.axes.x.labelColor ?: spec.theme.mutedTextColor
    val verticalLabelColor = spec.axes.y.labelColor ?: spec.theme.mutedTextColor
    if (spec.axes.y.visible && spec.axes.y.showLabels) {
        context.fillStyle(verticalLabelColor)
        context.font(spec.axes.y.labelFontSize ?: spec.theme.labelFontSize)
        context.textAlign(TextAlign.RIGHT)
        geometry.scale.ticks.forEach { tickValue ->
            val verticalPosition = geometry.scale.verticalPosition(tickValue, plot)
            drawText(
                context,
                spec.axes.y.formatter(tickValue),
                plot.left - AXIS_LABEL_GAP,
                verticalPosition + 3f,
                TextAlign.RIGHT,
            )
        }
    }
    if (spec.axes.x.visible && spec.axes.x.showLabels) {
        context.fillStyle(horizontalLabelColor)
        context.font(spec.axes.x.labelFontSize ?: spec.theme.labelFontSize)
        context.textAlign(TextAlign.CENTER)
        visibleIndices.forEach { dataIndex ->
            val label = spec.axes.x.formatter(spec.categoryLabel(dataIndex), dataIndex)
            drawText(
                context,
                label,
                geometry.categoryCenter(dataIndex),
                plot.bottom + 17f,
                TextAlign.CENTER,
            )
        }
    }
}
