package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSeries
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.guet.liang.kuiklychart.api.ChartViewport
import com.guet.liang.kuiklychart.api.PieLabelMode
import com.guet.liang.kuiklychart.finance.financialNumber
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.FontStyle
import com.tencent.kuikly.core.views.FontWeight
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Draws pie geometry and labels using one immutable render request. */
internal class PieRenderer(
    private val context: CanvasContext,
    private val spec: ChartSpec,
    private val plot: ChartRect,
    private val viewport: ChartViewport,
    private val dataCount: Int,
    private val selection: ChartSelection?,
) {
    private val seriesIndex = spec.dataSeries.indexOfFirst { it.type == ChartSeriesType.PIE }
    private val series = spec.dataSeries.getOrNull(seriesIndex)
    private val positiveValues = series?.dataValues?.map { value ->
        value?.takeIf { it.isFinite() && it > 0f } ?: 0f
    }.orEmpty()
    private val totalValue = positiveValues.sum()
    private val centerHorizontal = (plot.left + plot.right) / 2f
    private val centerVertical = (plot.top + plot.bottom) / 2f
    private val outerRadius = min(plot.width, plot.height) * 0.43f
    private val innerRadius = outerRadius * spec.pie.innerRadiusRatio.coerceIn(0f, 0.9f)

    fun render(): ChartRenderGeometry {
        val emptyGeometry = ChartRenderGeometry(plot, viewport, ValueScale(0f, 1f, listOf(0f, 1f)), dataCount)
        val chartSeries = series
        val hasSpace = plot.width > 1f && plot.height > 1f
        if (chartSeries == null || totalValue <= 0f || !hasSpace) {
            drawEmptyState(context, spec, plot)
            return emptyGeometry
        }
        if (innerRadius > 0f) {
            drawCircle(context, centerHorizontal, centerVertical, innerRadius, spec.pie.holeColor ?: spec.theme.backgroundColor)
        }
        val slices = drawSlices(chartSeries)
        if (innerRadius > 0f) drawCenter()
        val geometry = emptyGeometry.copy(pieSlices = slices)
        selection?.takeIf { it.type == ChartSeriesType.PIE }?.let {
            drawPieSelectionTooltip(context, spec, geometry, it)
        }
        return geometry
    }

    private fun drawSlices(chartSeries: ChartSeries): List<PieSliceGeometry> {
        val slices = mutableListOf<PieSliceGeometry>()
        var sliceStart = spec.pie.startAngle
        positiveValues.forEachIndexed { dataIndex, value ->
            if (value <= 0f) return@forEachIndexed
            val sliceSweep = (PI * 2.0).toFloat() * (value / totalValue)
            val middleAngle = sliceStart + sliceSweep / 2f
            val selected = selection?.type == ChartSeriesType.PIE &&
            selection.seriesIndex == seriesIndex && selection.dataIndex == dataIndex
            val offset = if (selected) spec.pie.selectedOffset else 0f
            val spacing = min(spec.pie.sliceSpacingAngle.coerceAtLeast(0f), sliceSweep * 0.35f)
            val slice = PieSliceGeometry(
                seriesIndex, dataIndex,
                centerHorizontal + cos(middleAngle) * offset,
                centerVertical + sin(middleAngle) * offset,
                outerRadius, innerRadius,
                sliceStart + spacing / 2f,
                sliceStart + sliceSweep - spacing / 2f,
            )
            drawSlice(slice, resolveDataPointColor(chartSeries, dataIndex), selected)
            slices.add(slice)
            drawLabel(chartSeries, slice, value, middleAngle)
            sliceStart += sliceSweep
        }
        return slices
    }

    private fun drawCenter() {
        val chosen = selection?.takeIf { spec.pie.showSelectionInCenter && it.type == ChartSeriesType.PIE }
        val title = chosen?.label ?: spec.pie.centerText
        val subtitle = chosen?.let {
            val percent = positiveValues.getOrNull(it.dataIndex)?.div(totalValue)?.times(100f) ?: 0f
            "${financialNumber(percent, 1)}%"
        } ?: spec.pie.centerSubtext
        drawCenteredText(title, centerVertical - 2f, spec.theme.textColor, 15f)
        drawCenteredText(subtitle, centerVertical + 16f, spec.theme.mutedTextColor, 10f)
    }

    private fun drawCenteredText(value: String, baseline: Float, color: Color, preferredSize: Float) {
        context.font(preferredSize)
        val availableWidth = innerRadius * 1.65f
        val measured = context.measureText(value).width
        val fontSize = if (measured > availableWidth) {
            (preferredSize * availableWidth / measured).coerceAtLeast(8f)
        } else preferredSize
        context.font(fontSize)
        context.fillStyle(color)
        drawText(context, value, centerHorizontal, baseline, TextAlign.CENTER)
    }

    private fun drawSlice(slice: PieSliceGeometry, color: Color, selected: Boolean) {
        with(slice) {
            context.beginPath()
            if (innerRadius > 0f) {
                context.moveTo(
                    centerHorizontal + cos(startAngle) * outerRadius,
                    centerVertical + sin(startAngle) * outerRadius,
                )
                context.arc(centerHorizontal, centerVertical, outerRadius, startAngle, endAngle, false)
                context.lineTo(
                    centerHorizontal + cos(endAngle) * innerRadius,
                    centerVertical + sin(endAngle) * innerRadius,
                )
                context.arc(centerHorizontal, centerVertical, innerRadius, endAngle, startAngle, true)
            } else {
                context.moveTo(centerHorizontal, centerVertical)
                context.arc(centerHorizontal, centerVertical, outerRadius, startAngle, endAngle, false)
            }
        }
        context.closePath()
        context.fillStyle(color)
        context.fill()
        if (selected) {
            context.strokeStyle(spec.theme.selectionColor)
            context.lineWidth(2f)
            context.stroke()
        }
    }

    private fun drawLabel(chartSeries: ChartSeries, slice: PieSliceGeometry, value: Float, middleAngle: Float) {
        val percent = value / totalValue
        if (spec.pie.labelMode == PieLabelMode.NONE || percent < spec.pie.minimumLabelPercent) return
        val label = chartSeries.dataPointLabels.getOrNull(slice.dataIndex) ?: spec.categoryLabel(slice.dataIndex)
        val percentText = "${(percent * 100f + 0.5f).toInt()}%"
        val valueText = spec.axes.y.formatter(value)
        val text = when (spec.pie.labelMode) {
            PieLabelMode.NONE -> ""
            PieLabelMode.LABEL -> label
            PieLabelMode.VALUE -> valueText
            PieLabelMode.PERCENT -> percentText
            PieLabelMode.LABEL_AND_PERCENT -> "$label $percentText"
        }
        val labelRadius = if (innerRadius > 0f) (outerRadius + innerRadius) / 2f else outerRadius * 0.66f
        context.fillStyle(Color.WHITE)
        context.font(FontStyle.NORMAL, FontWeight.SEMISOLID, spec.theme.valueFontSize)
        context.textAlign(TextAlign.CENTER)
        drawText(
            context, text,
            slice.centerHorizontal + cos(middleAngle) * labelRadius,
            slice.centerVertical + sin(middleAngle) * labelRadius + 3f,
            TextAlign.CENTER,
        )
    }
}
