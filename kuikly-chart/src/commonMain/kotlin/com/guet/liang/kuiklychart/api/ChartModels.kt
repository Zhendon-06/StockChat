package com.guet.liang.kuiklychart.api

import com.tencent.kuikly.core.base.Color

/** Marks the type-safe Kuikly Chart configuration scope. */
@DslMarker
public annotation class KuiklyChartDsl

/** Supported series renderers. Cartesian series can be combined in one chart. */
public enum class ChartSeriesType {
    LINE,
    AREA,
    BAR,
    PIE,
}

/** Line interpolation mode. */
public enum class ChartCurve {
    STRAIGHT,
    SMOOTH,
}

/** Timing curve used when chart data changes through `ChartView.update`. */
public enum class ChartAnimationEasing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
}

/** Legend placement inside the chart canvas. */
public enum class ChartLegendPosition {
    NONE,
    TOP,
    BOTTOM,
}

/** Text rendered on pie and donut slices. */
public enum class PieLabelMode {
    NONE,
    LABEL,
    VALUE,
    PERCENT,
    LABEL_AND_PERCENT,
}

/** Insets reserved between the component edge and chart content. */
public data class ChartInsets(
    public val left: Float = 12f,
    public val top: Float = 12f,
    public val right: Float = 12f,
    public val bottom: Float = 12f,
)

/** A value used by the pie-series DSL. */
public data class PieEntry(
    public val label: String,
    public val value: Float,
    public val color: Color? = null,
)

/** Data returned when a user selects a point, bar, or slice. */
public data class ChartSelection(
    public val seriesIndex: Int,
    public val dataIndex: Int,
    public val seriesName: String,
    public val label: String,
    public val value: Float,
    public val type: ChartSeriesType,
)

/** The visible category range after panning or zooming. */
public data class ChartViewport(
    public val startIndex: Float,
    public val endIndex: Float,
) {
    public val visiblePointCount: Float
        get() = (endIndex - startIndex + 1f).coerceAtLeast(1f)
}

/** Built-in colors used when a series or slice does not declare a color. */
public object ChartPalette {
    public val colors: List<Color> = listOf(
        Color(0xFF5B8FF9L),
        Color(0xFF61DDAAL),
        Color(0xFF65789BL),
        Color(0xFFF6BD16L),
        Color(0xFF7262FDL),
        Color(0xFF78D3F8L),
        Color(0xFF9661B9L),
        Color(0xFFF6903DL),
        Color(0xFF008685L),
        Color(0xFFF08BB4L),
    )
}
