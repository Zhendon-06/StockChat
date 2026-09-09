package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartLegendPosition
import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.guet.liang.kuiklychart.api.ChartViewport
import com.tencent.kuikly.core.views.CanvasContext

internal const val AXIS_LABEL_GAP = 8f

internal const val TITLE_LINE_HEIGHT = 22f

internal const val SUBTITLE_LINE_HEIGHT = 17f

internal const val LEGEND_MARKER_SIZE = 8f

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal object ChartRenderer {
    fun render(
        context: CanvasContext,
        width: Float,
        height: Float,
        spec: ChartSpec,
        requestedViewport: ChartViewport,
        selection: ChartSelection?,
        valueScaleOverride: ValueScale? = null,
    ): ChartRenderGeometry {
        drawBackground(context, width, height, spec.theme.backgroundColor)
        val dataCount = spec.dataCount()
        val viewport = ViewportMath.normalize(requestedViewport, dataCount)
        val pieMode = spec.dataSeries.any { it.type == ChartSeriesType.PIE } &&
        spec.dataSeries.none { it.type != ChartSeriesType.PIE }
        val legendItems = createLegendItems(spec, pieMode)
        val legendLayout = calculateLegendLayout(context, spec, legendItems, width)
        val valueScale = if (pieMode) null else valueScaleOverride ?: ScaleMath.calculate(spec, viewport)
        val yLabelWidth = if (!pieMode && spec.axes.y.visible && spec.axes.y.showLabels) {
            context.font(spec.axes.y.labelFontSize ?: spec.theme.labelFontSize)
            valueScale?.ticks?.maxOfOrNull { context.measureText(spec.axes.y.formatter(it)).width } ?: 0f
        } else 0f
        val chartLayout = calculateChartLayout(spec, width, height, pieMode, legendLayout.height, yLabelWidth)

        drawTitles(context, spec, chartLayout.titleLeft, chartLayout.titleTop)
        if (spec.legend.position == ChartLegendPosition.TOP) {
            drawLegend(context, spec, legendLayout, chartLayout.legendTop)
        } else if (spec.legend.position == ChartLegendPosition.BOTTOM) {
            drawLegend(context, spec, legendLayout, chartLayout.legendTop)
        }

        return if (pieMode) {
            PieRenderer(context, spec, chartLayout.plot, viewport, dataCount, selection).render()
        } else {
            renderCartesian(
                context,
                spec,
                chartLayout.plot,
                viewport,
                dataCount,
                selection,
                valueScale,
            )
        }
    }

    private fun renderCartesian(
        context: CanvasContext,
        spec: ChartSpec,
        plot: ChartRect,
        viewport: ChartViewport,
        dataCount: Int,
        selection: ChartSelection?,
        valueScaleOverride: ValueScale?,
    ): ChartRenderGeometry {
        val valueScale = valueScaleOverride ?: ScaleMath.calculate(spec, viewport)
        var geometry = ChartRenderGeometry(plot, viewport, valueScale, dataCount)
        val hasRenderableValue = spec.dataSeries.any { chartSeries ->
            chartSeries.type != ChartSeriesType.PIE && chartSeries.dataValues.any { it?.isFinite() == true }
        }
        if (!hasRenderableValue || plot.width <= 1f || plot.height <= 1f) {
            drawEmptyState(context, spec, plot)
            return geometry
        }

        drawCartesianGridAndAxes(context, spec, geometry)
        spec.dataSeries.filter { it.type == ChartSeriesType.AREA }.forEach { chartSeries ->
            drawAreaSeries(context, geometry, chartSeries)
        }
        geometry = geometry.copy(bars = drawBarSeries(context, spec, geometry, selection))
        spec.dataSeries.filter { it.type == ChartSeriesType.AREA }.forEach { chartSeries ->
            drawLineSeries(context, spec, geometry, chartSeries, selection)
        }
        spec.dataSeries.filter { it.type == ChartSeriesType.LINE }.forEach { chartSeries ->
            drawLineSeries(context, spec, geometry, chartSeries, selection)
        }

        if (selection != null && selection.type != ChartSeriesType.PIE) {
            drawCartesianSelection(context, spec, geometry, selection)
        }
        return geometry
    }

}
