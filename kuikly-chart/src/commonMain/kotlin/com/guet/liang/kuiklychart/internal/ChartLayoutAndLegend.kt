package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartLegendPosition
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.FontStyle
import com.tencent.kuikly.core.views.FontWeight
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.max

// 图表布局计算、标题与图例绘制。

internal fun calculateChartLayout(
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

internal fun drawTitles(
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

internal fun createLegendItems(spec: ChartSpec, pieMode: Boolean): List<LegendItem> {
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

internal fun calculateLegendLayout(
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

internal fun drawLegend(
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

internal fun categoryLabelIndices(spec: ChartSpec, geometry: ChartRenderGeometry): List<Int> {
    return ViewportRenderMath.categoryLabelIndices(
        geometry.viewport,
        geometry.dataCount,
        spec.axes.x.maxLabelCount,
    )
}

internal data class LegendItem(
    val label: String,
    val color: Color,
)

internal data class LegendPlacement(
    val item: LegendItem,
    val horizontal: Float,
    val baseline: Float,
)

internal data class LegendLayout(
    val placements: List<LegendPlacement>,
    val height: Float,
)

internal data class ChartLayout(
    val plot: ChartRect,
    val titleLeft: Float,
    val titleTop: Float,
    val legendTop: Float,
)
