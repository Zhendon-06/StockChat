package com.guet.liang.kuiklychart.api

import com.tencent.kuikly.core.base.Color

/** Shared visual tokens for all chart types. */
@KuiklyChartDsl
public class ChartTheme {
    public var backgroundColor: Color = Color.WHITE
    public var textColor: Color = Color(0xFF475569L)
    public var mutedTextColor: Color = Color(0xFF94A3B8L)
    public var axisColor: Color = Color(0xFFCBD5E1L)
    public var gridColor: Color = Color(0xFFE2E8F0L)
    public var crosshairColor: Color = Color(0xFF64748BL)
    public var tooltipBackgroundColor: Color = Color(0xE61E293BL)
    public var tooltipTextColor: Color = Color.WHITE
    public var selectionColor: Color = Color.WHITE
    public var titleFontSize: Float = 16f
    public var subtitleFontSize: Float = 11f
    public var labelFontSize: Float = 10f
    public var valueFontSize: Float = 10f
    public var contentPadding: ChartInsets = ChartInsets()

    public fun contentPadding(all: Float) {
        contentPadding = ChartInsets(all, all, all, all)
    }

    public fun contentPadding(left: Float, top: Float, right: Float, bottom: Float) {
        contentPadding = ChartInsets(left, top, right, bottom)
    }
}

/** X-axis options for category-based charts. */
@KuiklyChartDsl
public class CategoryAxisConfig {
    public var visible: Boolean = true
    public var showLabels: Boolean = true
    public var maxLabelCount: Int = 7
    public var labelFontSize: Float? = null
    public var labelColor: Color? = null
    public var axisColor: Color? = null
    public var formatter: (label: String, index: Int) -> String = { label, _ -> label }
}

/** Y-axis and numeric scale options. */
@KuiklyChartDsl
public class ValueAxisConfig {
    public var visible: Boolean = true
    public var showLabels: Boolean = true
    public var minimum: Float? = null
    public var maximum: Float? = null
    public var tickCount: Int = 5
    public var includeZero: Boolean = false
    public var labelFontSize: Float? = null
    public var labelColor: Color? = null
    public var axisColor: Color? = null
    public var formatter: (value: Float) -> String = ::formatChartValue
}

/** Cartesian axis configuration. */
@KuiklyChartDsl
public class ChartAxisConfig {
    public val x: CategoryAxisConfig = CategoryAxisConfig()
    public val y: ValueAxisConfig = ValueAxisConfig()

    public fun x(init: CategoryAxisConfig.() -> Unit) {
        x.apply(init)
    }

    public fun y(init: ValueAxisConfig.() -> Unit) {
        y.apply(init)
    }
}

/** Horizontal and vertical plot-grid options. */
@KuiklyChartDsl
public class ChartGridConfig {
    public var horizontal: Boolean = true
    public var vertical: Boolean = false
    public var color: Color? = null
    public var lineWidth: Float = 1f
    public var dashPattern: List<Float> = listOf(4f, 4f)

    public fun dashed(vararg intervals: Float) {
        dashPattern = intervals.toList()
    }

    public fun solid() {
        dashPattern = emptyList()
    }
}

/** Legend layout and typography options. */
@KuiklyChartDsl
public class ChartLegendConfig {
    public var position: ChartLegendPosition = ChartLegendPosition.TOP
    public var fontSize: Float? = null
    public var textColor: Color? = null
    public var itemSpacing: Float = 14f
    public var rowSpacing: Float = 6f
}

/** Grouped-bar layout options. */
@KuiklyChartDsl
public class BarChartConfig {
    public var groupWidthRatio: Float = 0.72f
    public var barSpacing: Float = 3f
    public var cornerRadius: Float = 4f
}

/** Pie and donut layout options. */
@KuiklyChartDsl
public class PieChartConfig {
    public var innerRadiusRatio: Float = 0f
    public var startAngle: Float = -1.5707964f
    public var sliceSpacingAngle: Float = 0.012f
    public var selectedOffset: Float = 7f
    public var labelMode: PieLabelMode = PieLabelMode.PERCENT
    public var minimumLabelPercent: Float = 0.05f
    public var holeColor: Color? = null
    public var centerText: String = ""
    public var centerSubtext: String = ""
    public var showSelectionInCenter: Boolean = true
}

/** Tooltip and crosshair options. */
@KuiklyChartDsl
public class ChartTooltipConfig {
    public var enabled: Boolean = true
    public var crosshairEnabled: Boolean = true
    public var showSeriesName: Boolean = true
    public var valueFormatter: ((Float) -> String)? = null
}

/** Gesture and selection behavior. */
@KuiklyChartDsl
public class ChartInteractionConfig {
    public var selectionEnabled: Boolean = true
    public var panEnabled: Boolean = false
    public var zoomEnabled: Boolean = false
    public var resetViewportOnDoubleClick: Boolean = true
    public var minimumVisiblePoints: Int = 3
    public var initialVisiblePoints: Int = 0
    public var dismissSelectionOnOutsideTap: Boolean = true
}

/** Runtime data-transition options used by `ChartView.update`. */
@KuiklyChartDsl
public class ChartAnimationConfig {
    /** Enables interpolation between the currently displayed and replacement data. */
    public var enabled: Boolean = true

    /** Transition duration in milliseconds. Zero or a negative value applies data immediately. */
    public var durationMillis: Int = 450

    /** Easing curve applied to interpolation progress. */
    public var easing: ChartAnimationEasing = ChartAnimationEasing.EASE_IN_OUT
}

internal fun formatChartValue(value: Float): String {
    val absoluteValue = kotlin.math.abs(value)
    return when {
        absoluteValue >= 1_000_000f -> "${formatCompactDecimal(value / 1_000_000f)}M"
        absoluteValue >= 1_000f -> "${formatCompactDecimal(value / 1_000f)}K"
        value == value.toInt().toFloat() -> value.toInt().toString()
        absoluteValue >= 10f -> formatCompactDecimal(value)
        else -> formatTwoDecimals(value)
    }
}

private fun formatCompactDecimal(value: Float): String {
    val roundedValue = kotlin.math.round(value * 10f) / 10f
    return if (roundedValue == roundedValue.toInt().toFloat()) {
        roundedValue.toInt().toString()
    } else {
        roundedValue.toString()
    }
}

private fun formatTwoDecimals(value: Float): String {
    val roundedValue = kotlin.math.round(value * 100f) / 100f
    return roundedValue.toString()
}
