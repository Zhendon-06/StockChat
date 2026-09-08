package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartCurve
import com.guet.liang.kuiklychart.api.ChartLegendPosition
import com.guet.liang.kuiklychart.api.ChartPalette
import com.guet.liang.kuiklychart.api.ChartSelection
import com.guet.liang.kuiklychart.api.ChartSeries
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.guet.liang.kuiklychart.api.ChartViewport
import com.guet.liang.kuiklychart.api.PieLabelMode
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.FontStyle
import com.tencent.kuikly.core.views.FontWeight
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object ChartRenderer {
    private const val AXIS_LABEL_GAP = 8f
    private const val TITLE_LINE_HEIGHT = 22f
    private const val SUBTITLE_LINE_HEIGHT = 17f
    private const val LEGEND_MARKER_SIZE = 8f

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
            renderPie(context, spec, chartLayout.plot, viewport, dataCount, selection)
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
            drawAreaSeries(context, spec, geometry, chartSeries)
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

    private fun renderPie(
        context: CanvasContext,
        spec: ChartSpec,
        plot: ChartRect,
        viewport: ChartViewport,
        dataCount: Int,
        selection: ChartSelection?,
    ): ChartRenderGeometry {
        val chartSeriesIndex = spec.dataSeries.indexOfFirst { it.type == ChartSeriesType.PIE }
        val chartSeries = spec.dataSeries.getOrNull(chartSeriesIndex)
        val positiveValues = chartSeries?.dataValues?.map { value ->
            value?.takeIf { it.isFinite() && it > 0f } ?: 0f
        }.orEmpty()
        val totalValue = positiveValues.sum()
        val emptyScale = ValueScale(0f, 1f, listOf(0f, 1f))
        if (chartSeries == null || totalValue <= 0f || plot.width <= 1f || plot.height <= 1f) {
            drawEmptyState(context, spec, plot)
            return ChartRenderGeometry(plot, viewport, emptyScale, dataCount)
        }

        val baseCenterHorizontal = (plot.left + plot.right) / 2f
        val baseCenterVertical = (plot.top + plot.bottom) / 2f
        val outerRadius = min(plot.width, plot.height) * 0.43f
        val innerRadius = outerRadius * spec.pie.innerRadiusRatio.coerceIn(0f, 0.9f)
        val fullCircle = (PI * 2.0).toFloat()
        val pieSlices = mutableListOf<PieSliceGeometry>()
        var sliceStart = spec.pie.startAngle

        if (innerRadius > 0f && spec.pie.holeColor != null) {
            drawCircle(
                context,
                baseCenterHorizontal,
                baseCenterVertical,
                innerRadius,
                spec.pie.holeColor!!,
            )
        }

        positiveValues.forEachIndexed { dataIndex, value ->
            if (value <= 0f) {
                return@forEachIndexed
            }
            val sliceSweep = fullCircle * (value / totalValue)
            val sliceEnd = sliceStart + sliceSweep
            val middleAngle = sliceStart + sliceSweep / 2f
            val isSelected = selection?.type == ChartSeriesType.PIE &&
                selection.seriesIndex == chartSeriesIndex && selection.dataIndex == dataIndex
            val selectedOffset = if (isSelected) spec.pie.selectedOffset else 0f
            val centerHorizontal = baseCenterHorizontal + cos(middleAngle) * selectedOffset
            val centerVertical = baseCenterVertical + sin(middleAngle) * selectedOffset
            val spacing = min(spec.pie.sliceSpacingAngle.coerceAtLeast(0f), sliceSweep * 0.35f)
            val drawStart = sliceStart + spacing / 2f
            val drawEnd = sliceEnd - spacing / 2f
            val sliceColor = resolveDataPointColor(chartSeries, dataIndex)

            drawPieSlice(
                context,
                centerHorizontal,
                centerVertical,
                outerRadius,
                innerRadius,
                drawStart,
                drawEnd,
                sliceColor,
                isSelected,
                spec,
            )
            pieSlices.add(
                PieSliceGeometry(
                    chartSeriesIndex,
                    dataIndex,
                    centerHorizontal,
                    centerVertical,
                    outerRadius,
                    innerRadius,
                    drawStart,
                    drawEnd,
                ),
            )
            drawPieLabel(
                context,
                spec,
                chartSeries,
                dataIndex,
                value,
                totalValue,
                centerHorizontal,
                centerVertical,
                outerRadius,
                innerRadius,
                middleAngle,
            )
            sliceStart = sliceEnd
        }

        if (innerRadius > 0f) {
            val chosen = selection?.takeIf { spec.pie.showSelectionInCenter && it.type == ChartSeriesType.PIE }
            val centerText = chosen?.label ?: spec.pie.centerText
            val centerSubtext = chosen?.let {
                val percent = positiveValues.getOrNull(it.dataIndex)?.div(totalValue)?.times(100f) ?: 0f
                "${com.guet.liang.kuiklychart.finance.financialNumber(percent, 1)}%"
            } ?: spec.pie.centerSubtext
            fun centered(value: String, baseline: Float, color: Color, preferredSize: Float) {
                var fontSize = preferredSize
                context.font(fontSize)
                val availableWidth = innerRadius * 1.65f
                val measured = context.measureText(value).width
                if (measured > availableWidth) fontSize = (fontSize * availableWidth / measured).coerceAtLeast(8f)
                context.font(fontSize); context.fillStyle(color)
                drawText(context, value, baseCenterHorizontal, baseline, TextAlign.CENTER)
            }
            centered(centerText, baseCenterVertical - 2f, spec.theme.textColor, 15f)
            centered(centerSubtext, baseCenterVertical + 16f, spec.theme.mutedTextColor, 10f)
        }
        val geometry = ChartRenderGeometry(plot, viewport, emptyScale, dataCount, pieSlices)
        if (selection != null && selection.type == ChartSeriesType.PIE) {
            drawPieSelectionTooltip(context, spec, geometry, selection)
        }
        return geometry
    }

    private fun drawCartesianGridAndAxes(
        context: CanvasContext,
        spec: ChartSpec,
        geometry: ChartRenderGeometry,
    ) {
        val plot = geometry.plot
        val gridColor = spec.grid.color ?: spec.theme.gridColor
        val horizontalAxisColor = spec.axes.x.axisColor ?: spec.theme.axisColor
        val verticalAxisColor = spec.axes.y.axisColor ?: spec.theme.axisColor
        val horizontalLabelColor = spec.axes.x.labelColor ?: spec.theme.mutedTextColor
        val verticalLabelColor = spec.axes.y.labelColor ?: spec.theme.mutedTextColor

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

        context.lineWidth(1f)
        if (spec.axes.y.visible) {
            context.strokeStyle(verticalAxisColor)
            drawLine(context, plot.left, plot.top, plot.left, plot.bottom)
        }
        if (spec.axes.x.visible) {
            context.strokeStyle(horizontalAxisColor)
            drawLine(context, plot.left, plot.bottom, plot.right, plot.bottom)
        }

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

    private fun drawAreaSeries(
        context: CanvasContext,
        spec: ChartSpec,
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

    private fun drawLineSeries(
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

            segment.forEach { indexedPoint ->
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
        }
    }

    private fun drawBarSeries(
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

    private fun drawCartesianSelection(
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

    private fun drawPieSelectionTooltip(
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

    private fun drawTooltip(
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

    private fun drawPieSlice(
        context: CanvasContext,
        centerHorizontal: Float,
        centerVertical: Float,
        outerRadius: Float,
        innerRadius: Float,
        startAngle: Float,
        endAngle: Float,
        color: Color,
        selected: Boolean,
        spec: ChartSpec,
    ) {
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
        context.closePath()
        context.fillStyle(color)
        context.fill()
        if (selected) {
            context.strokeStyle(spec.theme.selectionColor)
            context.lineWidth(2f)
            context.stroke()
        }
    }

    private fun drawPieLabel(
        context: CanvasContext,
        spec: ChartSpec,
        chartSeries: ChartSeries,
        dataIndex: Int,
        value: Float,
        totalValue: Float,
        centerHorizontal: Float,
        centerVertical: Float,
        outerRadius: Float,
        innerRadius: Float,
        middleAngle: Float,
    ) {
        val percent = value / totalValue
        if (spec.pie.labelMode == PieLabelMode.NONE || percent < spec.pie.minimumLabelPercent) {
            return
        }
        val label = chartSeries.dataPointLabels.getOrNull(dataIndex) ?: spec.categoryLabel(dataIndex)
        val percentText = "${(percent * 100f + 0.5f).toInt()}%"
        val valueText = spec.axes.y.formatter(value)
        val text = when (spec.pie.labelMode) {
            PieLabelMode.NONE -> ""
            PieLabelMode.LABEL -> label
            PieLabelMode.VALUE -> valueText
            PieLabelMode.PERCENT -> percentText
            PieLabelMode.LABEL_AND_PERCENT -> "$label $percentText"
        }
        val labelRadius = if (innerRadius > 0f) {
            (outerRadius + innerRadius) / 2f
        } else {
            outerRadius * 0.66f
        }
        context.fillStyle(Color.WHITE)
        context.font(FontStyle.NORMAL, FontWeight.SEMISOLID, spec.theme.valueFontSize)
        context.textAlign(TextAlign.CENTER)
        drawText(
            context,
            text,
            centerHorizontal + cos(middleAngle) * labelRadius,
            centerVertical + sin(middleAngle) * labelRadius + 3f,
            TextAlign.CENTER,
        )
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

    private fun calculateChartLayout(
        spec: ChartSpec,
        width: Float,
        height: Float,
        pieMode: Boolean,
        legendHeight: Float,
        yLabelWidth: Float,
    ): ChartLayout {
        val padding = spec.theme.contentPadding
        val contentLeft = padding.left
        val contentRight = (width - padding.right).coerceAtLeast(contentLeft)
        var contentTop = padding.top
        var contentBottom = (height - padding.bottom).coerceAtLeast(contentTop)
        val titleTop = contentTop

        if (spec.title.isNotEmpty()) {
            contentTop += TITLE_LINE_HEIGHT
        }
        if (spec.subtitle.isNotEmpty()) {
            contentTop += SUBTITLE_LINE_HEIGHT
        }

        var legendTop = contentTop
        if (spec.legend.position == ChartLegendPosition.TOP && legendHeight > 0f) {
            legendTop = contentTop
            contentTop += legendHeight + 8f
        } else if (spec.legend.position == ChartLegendPosition.BOTTOM && legendHeight > 0f) {
            contentBottom -= legendHeight + 8f
            legendTop = contentBottom + 8f
        }

        var plotLeft = contentLeft
        var plotBottom = contentBottom
        if (!pieMode && spec.axes.y.visible) {
            plotLeft += if (spec.axes.y.showLabels) max(46f, yLabelWidth + AXIS_LABEL_GAP + 2f) else 0f
        }
        if (!pieMode && spec.axes.x.visible) {
            plotBottom -= 27f
        }
        return ChartLayout(
            plot = ChartRect(
                plotLeft.coerceAtMost(contentRight),
                contentTop.coerceAtMost(plotBottom),
                contentRight,
                plotBottom.coerceAtLeast(contentTop),
            ),
            titleLeft = contentLeft,
            titleTop = titleTop,
            legendTop = legendTop,
        )
    }

    private fun drawTitles(
        context: CanvasContext,
        spec: ChartSpec,
        left: Float,
        top: Float,
    ) {
        var baseline = top
        context.textAlign(TextAlign.LEFT)
        if (spec.title.isNotEmpty()) {
            baseline += spec.theme.titleFontSize
            context.fillStyle(spec.theme.textColor)
            context.font(FontStyle.NORMAL, FontWeight.SEMISOLID, spec.theme.titleFontSize)
            drawText(context, spec.title, left, baseline, TextAlign.LEFT)
            baseline = top + TITLE_LINE_HEIGHT
        }
        if (spec.subtitle.isNotEmpty()) {
            baseline += spec.theme.subtitleFontSize
            context.fillStyle(spec.theme.mutedTextColor)
            context.font(spec.theme.subtitleFontSize)
            drawText(context, spec.subtitle, left, baseline, TextAlign.LEFT)
        }
    }

    private fun createLegendItems(spec: ChartSpec, pieMode: Boolean): List<LegendItem> {
        if (spec.legend.position == ChartLegendPosition.NONE) {
            return emptyList()
        }
        if (pieMode) {
            val chartSeries = spec.dataSeries.firstOrNull { it.type == ChartSeriesType.PIE } ?: return emptyList()
            return chartSeries.dataValues.mapIndexedNotNull { dataIndex, value ->
                if (value == null || !value.isFinite() || value <= 0f) {
                    null
                } else {
                    LegendItem(
                        chartSeries.dataPointLabels.getOrNull(dataIndex) ?: spec.categoryLabel(dataIndex),
                        resolveDataPointColor(chartSeries, dataIndex),
                    )
                }
            }
        }
        return spec.dataSeries.filter { it.type != ChartSeriesType.PIE && it.name.isNotEmpty() }
            .map { LegendItem(it.name, it.color) }
    }

    private fun calculateLegendLayout(
        context: CanvasContext,
        spec: ChartSpec,
        items: List<LegendItem>,
        width: Float,
    ): LegendLayout {
        if (items.isEmpty()) {
            return LegendLayout(emptyList(), 0f)
        }
        val padding = spec.theme.contentPadding
        val availableRight = (width - padding.right).coerceAtLeast(padding.left)
        val fontSize = spec.legend.fontSize ?: spec.theme.labelFontSize
        val lineHeight = max(fontSize, LEGEND_MARKER_SIZE)
        context.font(fontSize)
        var currentHorizontal = padding.left
        var currentBaseline = lineHeight
        val placements = mutableListOf<LegendPlacement>()

        items.forEach { item ->
            val textWidth = context.measureText(item.label).width
            val itemWidth = LEGEND_MARKER_SIZE + 5f + textWidth
            if (currentHorizontal > padding.left && currentHorizontal + itemWidth > availableRight) {
                currentHorizontal = padding.left
                currentBaseline += lineHeight + spec.legend.rowSpacing
            }
            placements.add(LegendPlacement(item, currentHorizontal, currentBaseline))
            currentHorizontal += itemWidth + spec.legend.itemSpacing
        }
        return LegendLayout(placements, currentBaseline)
    }

    private fun drawLegend(
        context: CanvasContext,
        spec: ChartSpec,
        layout: LegendLayout,
        top: Float,
    ) {
        if (layout.placements.isEmpty()) {
            return
        }
        val fontSize = spec.legend.fontSize ?: spec.theme.labelFontSize
        context.font(fontSize)
        context.textAlign(TextAlign.LEFT)
        layout.placements.forEach { placement ->
            val baseline = top + placement.baseline
            drawCircle(
                context,
                placement.horizontal + LEGEND_MARKER_SIZE / 2f,
                baseline - fontSize * 0.35f,
                LEGEND_MARKER_SIZE / 2f,
                placement.item.color,
            )
            context.fillStyle(spec.legend.textColor ?: spec.theme.textColor)
            drawText(
                context,
                placement.item.label,
                placement.horizontal + LEGEND_MARKER_SIZE + 5f,
                baseline,
                TextAlign.LEFT,
            )
        }
    }

    private fun categoryLabelIndices(spec: ChartSpec, geometry: ChartRenderGeometry): List<Int> {
        return ViewportRenderMath.categoryLabelIndices(
            geometry.viewport,
            geometry.dataCount,
            spec.axes.x.maxLabelCount,
        )
    }

    private fun drawEmptyState(context: CanvasContext, spec: ChartSpec, plot: ChartRect) {
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

    private fun drawBackground(context: CanvasContext, width: Float, height: Float, color: Color) {
        drawRect(context, ChartRect(0f, 0f, width, height), color)
    }

    private fun drawLine(
        context: CanvasContext,
        startHorizontal: Float,
        startVertical: Float,
        endHorizontal: Float,
        endVertical: Float,
    ) {
        context.beginPath()
        context.moveTo(startHorizontal, startVertical)
        context.lineTo(endHorizontal, endVertical)
        context.stroke()
    }

    private fun drawDashedLine(
        context: CanvasContext,
        startHorizontal: Float,
        startVertical: Float,
        endHorizontal: Float,
        endVertical: Float,
        pattern: List<Float>,
    ) {
        val validPattern = pattern.filter { it > 0f }
        if (validPattern.isEmpty()) {
            drawLine(context, startHorizontal, startVertical, endHorizontal, endVertical)
            return
        }
        val horizontalDelta = endHorizontal - startHorizontal
        val verticalDelta = endVertical - startVertical
        val lineLength = sqrt(horizontalDelta * horizontalDelta + verticalDelta * verticalDelta)
        if (lineLength <= 0f) {
            return
        }
        val horizontalUnit = horizontalDelta / lineLength
        val verticalUnit = verticalDelta / lineLength
        var travelled = 0f
        var patternIndex = 0
        var shouldDraw = true
        while (travelled < lineLength) {
            val segmentLength = min(validPattern[patternIndex % validPattern.size], lineLength - travelled)
            if (shouldDraw) {
                drawLine(
                    context,
                    startHorizontal + horizontalUnit * travelled,
                    startVertical + verticalUnit * travelled,
                    startHorizontal + horizontalUnit * (travelled + segmentLength),
                    startVertical + verticalUnit * (travelled + segmentLength),
                )
            }
            travelled += segmentLength
            patternIndex += 1
            shouldDraw = !shouldDraw
        }
    }

    private fun drawText(
        context: CanvasContext,
        text: String,
        anchorHorizontal: Float,
        baselineVertical: Float,
        alignment: TextAlign,
    ) {
        val textWidth = if (alignment == TextAlign.LEFT) 0f else context.measureText(text).width
        val drawHorizontal = when (alignment) {
            TextAlign.LEFT -> anchorHorizontal
            TextAlign.CENTER -> anchorHorizontal - textWidth / 2f
            TextAlign.RIGHT -> anchorHorizontal - textWidth
        }
        context.textAlign(TextAlign.LEFT)
        context.fillText(text, drawHorizontal, baselineVertical)
    }

    private fun drawCircle(
        context: CanvasContext,
        centerHorizontal: Float,
        centerVertical: Float,
        radius: Float,
        color: Color,
    ) {
        if (radius <= 0f) {
            return
        }
        context.beginPath()
        context.arc(
            centerHorizontal,
            centerVertical,
            radius,
            0f,
            (PI * 2.0).toFloat(),
            false,
        )
        context.closePath()
        context.fillStyle(color)
        context.fill()
    }

    private fun drawRect(context: CanvasContext, rect: ChartRect, color: Color) {
        context.beginPath()
        context.moveTo(rect.left, rect.top)
        context.lineTo(rect.right, rect.top)
        context.lineTo(rect.right, rect.bottom)
        context.lineTo(rect.left, rect.bottom)
        context.closePath()
        context.fillStyle(color)
        context.fill()
    }

    private fun drawRoundedRect(
        context: CanvasContext,
        rect: ChartRect,
        radius: Float,
        color: Color,
    ) {
        val safeRadius = radius.coerceIn(0f, min(rect.width, rect.height) / 2f)
        context.beginPath()
        context.moveTo(rect.left + safeRadius, rect.top)
        context.lineTo(rect.right - safeRadius, rect.top)
        context.quadraticCurveTo(rect.right, rect.top, rect.right, rect.top + safeRadius)
        context.lineTo(rect.right, rect.bottom - safeRadius)
        context.quadraticCurveTo(rect.right, rect.bottom, rect.right - safeRadius, rect.bottom)
        context.lineTo(rect.left + safeRadius, rect.bottom)
        context.quadraticCurveTo(rect.left, rect.bottom, rect.left, rect.bottom - safeRadius)
        context.lineTo(rect.left, rect.top + safeRadius)
        context.quadraticCurveTo(rect.left, rect.top, rect.left + safeRadius, rect.top)
        context.closePath()
        context.fillStyle(color)
        context.fill()
    }

    private fun drawRoundedBar(
        context: CanvasContext,
        rect: ChartRect,
        radius: Float,
        positive: Boolean,
        color: Color,
    ) {
        if (rect.width <= 0f || rect.height <= 0f) {
            return
        }
        val safeRadius = radius.coerceIn(0f, min(rect.width / 2f, rect.height))
        context.beginPath()
        if (positive) {
            context.moveTo(rect.left, rect.bottom)
            context.lineTo(rect.left, rect.top + safeRadius)
            context.quadraticCurveTo(rect.left, rect.top, rect.left + safeRadius, rect.top)
            context.lineTo(rect.right - safeRadius, rect.top)
            context.quadraticCurveTo(rect.right, rect.top, rect.right, rect.top + safeRadius)
            context.lineTo(rect.right, rect.bottom)
        } else {
            context.moveTo(rect.left, rect.top)
            context.lineTo(rect.right, rect.top)
            context.lineTo(rect.right, rect.bottom - safeRadius)
            context.quadraticCurveTo(rect.right, rect.bottom, rect.right - safeRadius, rect.bottom)
            context.lineTo(rect.left + safeRadius, rect.bottom)
            context.quadraticCurveTo(rect.left, rect.bottom, rect.left, rect.bottom - safeRadius)
        }
        context.closePath()
        context.fillStyle(color)
        context.fill()
    }

    private data class IndexedChartPoint(
        val dataIndex: Int,
        val value: Float,
        val point: ChartPoint,
    )

    private data class LegendItem(
        val label: String,
        val color: Color,
    )

    private data class LegendPlacement(
        val item: LegendItem,
        val horizontal: Float,
        val baseline: Float,
    )

    private data class LegendLayout(
        val placements: List<LegendPlacement>,
        val height: Float,
    )

    private data class ChartLayout(
        val plot: ChartRect,
        val titleLeft: Float,
        val titleTop: Float,
        val legendTop: Float,
    )
}

internal fun resolveDataPointColor(chartSeries: ChartSeries, dataIndex: Int): Color {
    return if (chartSeries.dataPointColors.isEmpty()) {
        if (chartSeries.type == ChartSeriesType.PIE) {
            ChartPalette.colors[dataIndex % ChartPalette.colors.size]
        } else {
            chartSeries.color
        }
    } else {
        chartSeries.dataPointColors[dataIndex % chartSeries.dataPointColors.size]
    }
}
