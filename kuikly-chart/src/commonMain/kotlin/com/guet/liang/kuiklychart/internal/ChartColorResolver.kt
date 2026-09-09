package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartPalette
import com.guet.liang.kuiklychart.api.ChartSeries
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.tencent.kuikly.core.base.Color

// 数据点颜色解析。

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
