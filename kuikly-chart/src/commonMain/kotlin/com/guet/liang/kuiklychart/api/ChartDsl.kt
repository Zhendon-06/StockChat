package com.guet.liang.kuiklychart.api

import com.tencent.kuikly.core.base.Color

/** A single data series and its visual style. */
@KuiklyChartDsl
public class ChartSeries internal constructor(
    public val type: ChartSeriesType,
    public var name: String,
    values: List<Float?>,
    defaultColor: Color,
) {
    private val mutableValues: MutableList<Float?> = values.toMutableList()
    private val mutablePointColors: MutableList<Color> = mutableListOf()
    private val mutablePointLabels: MutableList<String> = mutableListOf()

    public val values: List<Float?>
        get() = mutableValues.toList()

    public val pointColors: List<Color>
        get() = mutablePointColors.toList()

    public val pointLabels: List<String>
        get() = mutablePointLabels.toList()

    internal val dataValues: List<Float?>
        get() = mutableValues

    internal val dataPointColors: List<Color>
        get() = mutablePointColors

    internal val dataPointLabels: List<String>
        get() = mutablePointLabels

    public var color: Color = defaultColor
    public var fillColor: Color? = null
    public var fillOpacity: Float = 0.24f
    public var lineWidth: Float = 2.5f
    public var curve: ChartCurve = ChartCurve.STRAIGHT
    public var showPoints: Boolean = true
    public var pointRadius: Float = 3.5f
    public var showValues: Boolean = false
    public var valueLabelColor: Color? = null

    public fun values(vararg values: Float) {
        replaceDataValues(values.map { it })
    }

    public fun values(values: List<Float?>) {
        replaceDataValues(values)
    }

    public fun color(color: Color) {
        this.color = color
    }

    public fun pointColors(vararg colors: Color) {
        mutablePointColors.clear()
        mutablePointColors.addAll(colors)
    }

    public fun pointColors(colors: List<Color>) {
        mutablePointColors.clear()
        mutablePointColors.addAll(colors)
    }

    /** Sets the fill color for each bar. Colors repeat when fewer colors than values are supplied. */
    public fun barColors(vararg colors: Color) {
        barColors(colors.toList())
    }

    /** Sets the fill color for each bar. Colors repeat when fewer colors than values are supplied. */
    public fun barColors(colors: List<Color>) {
        pointColors(colors)
    }

    public fun pointLabels(vararg labels: String) {
        mutablePointLabels.clear()
        mutablePointLabels.addAll(labels)
    }

    public fun fill(color: Color, opacity: Float = fillOpacity) {
        fillColor = color
        fillOpacity = opacity.coerceIn(0f, 1f)
    }

    public fun smooth(enabled: Boolean = true) {
        curve = if (enabled) ChartCurve.SMOOTH else ChartCurve.STRAIGHT
    }

    public fun points(visible: Boolean = true, radius: Float = pointRadius) {
        showPoints = visible
        pointRadius = radius.coerceAtLeast(0f)
    }

    public fun valueLabels(visible: Boolean = true, color: Color? = valueLabelColor) {
        showValues = visible
        valueLabelColor = color
    }

    internal fun replaceDataValues(values: List<Float?>) {
        mutableValues.clear()
        mutableValues.addAll(values)
    }
}

/** Complete chart description configured by the Kotlin DSL. */
@KuiklyChartDsl
public class ChartSpec internal constructor(
    public var defaultSeriesType: ChartSeriesType = ChartSeriesType.LINE,
) {
    private val mutableLabels: MutableList<String> = mutableListOf()
    private val mutableSeries: MutableList<ChartSeries> = mutableListOf()

    public val labels: List<String>
        get() = mutableLabels.toList()

    public val series: List<ChartSeries>
        get() = mutableSeries.toList()

    internal val dataLabels: List<String>
        get() = mutableLabels

    internal val dataSeries: List<ChartSeries>
        get() = mutableSeries

    internal val mutableDataSeries: MutableList<ChartSeries>
        get() = mutableSeries

    public var title: String = ""
    public var subtitle: String = ""
    public var emptyText: String = "No data"
    public val theme: ChartTheme = ChartTheme()
    public val axes: ChartAxisConfig = ChartAxisConfig()
    public val grid: ChartGridConfig = ChartGridConfig()
    public val legend: ChartLegendConfig = ChartLegendConfig()
    public val bars: BarChartConfig = BarChartConfig()
    public val pie: PieChartConfig = PieChartConfig()
    public val tooltip: ChartTooltipConfig = ChartTooltipConfig()
    public val interaction: ChartInteractionConfig = ChartInteractionConfig()
    public val animation: ChartAnimationConfig = ChartAnimationConfig()

    public fun labels(vararg labels: String) {
        labels(labels.toList())
    }

    public fun labels(labels: List<String>) {
        mutableLabels.clear()
        mutableLabels.addAll(labels)
    }

    public fun series(
        name: String,
        vararg values: Float,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = addSeries(defaultSeriesType, name, values.map { it }, init)

    public fun series(
        name: String,
        values: List<Float?>,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = addSeries(defaultSeriesType, name, values, init)

    public fun line(
        name: String,
        vararg values: Float,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = addSeries(ChartSeriesType.LINE, name, values.map { it }, init)

    public fun line(
        name: String,
        values: List<Float?>,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = addSeries(ChartSeriesType.LINE, name, values, init)

    public fun area(
        name: String,
        vararg values: Float,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = addSeries(ChartSeriesType.AREA, name, values.map { it }, init)

    public fun area(
        name: String,
        values: List<Float?>,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = addSeries(ChartSeriesType.AREA, name, values, init)

    public fun bars(
        name: String,
        vararg values: Float,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = addSeries(ChartSeriesType.BAR, name, values.map { it }, init)

    public fun bars(
        name: String,
        values: List<Float?>,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = addSeries(ChartSeriesType.BAR, name, values, init)

    public fun pie(
        name: String,
        vararg entries: PieEntry,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries = pie(name, entries.toList(), init)

    public fun pie(
        name: String,
        entries: List<PieEntry>,
        init: ChartSeries.() -> Unit = {},
    ): ChartSeries {
        val chartSeries = addSeries(
            ChartSeriesType.PIE,
            name,
            entries.map { it.value },
        ) {
            pointLabels(*entries.map { it.label }.toTypedArray())
            pointColors(entries.mapIndexed { index, entry ->
                entry.color ?: ChartPalette.colors[index % ChartPalette.colors.size]
            })
            init()
        }
        return chartSeries
    }

    public fun theme(init: ChartTheme.() -> Unit) {
        theme.apply(init)
    }

    public fun axes(init: ChartAxisConfig.() -> Unit) {
        axes.apply(init)
    }

    public fun grid(init: ChartGridConfig.() -> Unit) {
        grid.apply(init)
    }

    public fun legend(init: ChartLegendConfig.() -> Unit) {
        legend.apply(init)
    }

    public fun bars(init: BarChartConfig.() -> Unit) {
        bars.apply(init)
    }

    public fun pie(init: PieChartConfig.() -> Unit) {
        pie.apply(init)
    }

    public fun tooltip(init: ChartTooltipConfig.() -> Unit) {
        tooltip.apply(init)
    }

    public fun interaction(init: ChartInteractionConfig.() -> Unit) {
        interaction.apply(init)
    }

    /** Configures runtime old-data to new-data transitions. */
    public fun animation(init: ChartAnimationConfig.() -> Unit) {
        animation.apply(init)
    }

    public fun clearSeries() {
        mutableSeries.clear()
    }

    internal fun dataCount(): Int {
        val labelCount = mutableLabels.size
        val seriesCount = mutableSeries.maxOfOrNull { it.dataValues.size } ?: 0
        return maxOf(labelCount, seriesCount)
    }

    internal fun categoryLabel(index: Int): String {
        return mutableLabels.getOrNull(index) ?: (index + 1).toString()
    }

    private fun addSeries(
        type: ChartSeriesType,
        name: String,
        values: List<Float?>,
        init: ChartSeries.() -> Unit,
    ): ChartSeries {
        val defaultColor = ChartPalette.colors[mutableSeries.size % ChartPalette.colors.size]
        return ChartSeries(type, name, values, defaultColor).apply(init).also(mutableSeries::add)
    }
}
