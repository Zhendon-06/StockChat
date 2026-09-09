package com.guet.liang.kuiklychart.api

/** Series construction shared by chart specifications without coupling it to axes or interaction styling. */
public open class ChartSeriesCollection internal constructor(public var defaultSeriesType: ChartSeriesType) {
    protected val mutableSeries: MutableList<ChartSeries> = mutableListOf()

    public val series: List<ChartSeries>
    get() = mutableSeries.toList()

    internal val dataSeries: List<ChartSeries>
    get() = mutableSeries

    internal val mutableDataSeries: MutableList<ChartSeries>
    get() = mutableSeries

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
            pointLabels(entries.map { it.label })
            pointColors(entries.mapIndexed { index, entry ->
                    entry.color ?: ChartPalette.colors[index % ChartPalette.colors.size]
            })
            init()
        }
        return chartSeries
    }

    public fun clearSeries() {
        mutableSeries.clear()
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
