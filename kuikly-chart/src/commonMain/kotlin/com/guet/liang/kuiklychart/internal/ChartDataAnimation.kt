package com.guet.liang.kuiklychart.internal

import com.guet.liang.kuiklychart.api.ChartAnimationEasing
import com.guet.liang.kuiklychart.api.ChartPalette
import com.guet.liang.kuiklychart.api.ChartSeries
import com.guet.liang.kuiklychart.api.ChartSeriesType
import com.guet.liang.kuiklychart.api.ChartSpec
import com.tencent.kuikly.core.base.Color
import kotlin.math.max

internal data class ChartSeriesDataSnapshot(
    val type: ChartSeriesType,
    val name: String,
    val values: List<Float?>,
    val series: ChartSeries,
    val appearance: ChartSeriesAppearanceSnapshot,
)

internal data class ChartSeriesAppearanceSnapshot(
    val color: Color,
    val fillColor: Color?,
    val fillOpacity: Float,
    val pointColors: List<Color>,
    val valueLabelColor: Color?,
) {
    fun applyFaded(
        series: ChartSeries,
        opacityMultiplier: Float,
        pointCount: Int,
    ) {
        val normalizedOpacity = opacityMultiplier.coerceIn(0f, 1f)
        series.color = color.withOpacityMultiplier(normalizedOpacity)
        series.fillColor = fillColor
        series.fillOpacity = fillOpacity * normalizedOpacity
        val renderedPointColors = if (series.type == ChartSeriesType.PIE && pointColors.isEmpty()) {
            List(pointCount) { index -> ChartPalette.colors[index % ChartPalette.colors.size] }
        } else {
            pointColors
        }
        series.pointColors(renderedPointColors.map { it.withOpacityMultiplier(normalizedOpacity) })
        series.valueLabelColor = valueLabelColor?.withOpacityMultiplier(normalizedOpacity)
    }

    fun restore(series: ChartSeries) {
        series.color = color
        series.fillColor = fillColor
        series.fillOpacity = fillOpacity
        series.pointColors(pointColors)
        series.valueLabelColor = valueLabelColor
    }

    companion object {
        fun capture(series: ChartSeries): ChartSeriesAppearanceSnapshot {
            return ChartSeriesAppearanceSnapshot(
                series.color,
                series.fillColor,
                series.fillOpacity,
                series.pointColors,
                series.valueLabelColor,
            )
        }
    }
}

internal data class ChartDataSnapshot(
    val series: List<ChartSeriesDataSnapshot>,
) {
    companion object {
        fun capture(spec: ChartSpec): ChartDataSnapshot {
            return ChartDataSnapshot(
                spec.dataSeries.map { chartSeries ->
                    ChartSeriesDataSnapshot(
                        chartSeries.type,
                        chartSeries.name,
                        chartSeries.dataValues.toList(),
                        chartSeries,
                        ChartSeriesAppearanceSnapshot.capture(chartSeries),
                    )
                },
            )
        }
    }
}

internal class ChartDataTransition private constructor(
    private val targetSeries: MutableList<ChartSeries>,
    private val seriesTransitions: List<SeriesTransition>,
    private val exitingSeriesTransitions: List<ExitingSeriesTransition>,
) {
    val hasChanges: Boolean
        get() = seriesTransitions.any(SeriesTransition::hasChanges) ||
            exitingSeriesTransitions.any(ExitingSeriesTransition::hasChanges)

    fun apply(progress: Float) {
        val normalizedProgress = progress.coerceIn(0f, 1f)
        if (normalizedProgress >= 1f) {
            seriesTransitions.forEach(SeriesTransition::finish)
            exitingSeriesTransitions.forEach { transition -> transition.finish(targetSeries) }
            return
        }
        exitingSeriesTransitions.forEach { transition -> transition.attach(targetSeries) }
        seriesTransitions.forEach { transition -> transition.apply(normalizedProgress) }
        exitingSeriesTransitions.forEach { transition -> transition.apply(normalizedProgress) }
    }

    companion object {
        fun create(start: ChartDataSnapshot, target: ChartSpec): ChartDataTransition {
            val usedStartSeries = mutableSetOf<Int>()
            val transitions = target.dataSeries.mapIndexed { targetIndex, targetSeries ->
                val matchingStartIndex = findMatchingStartSeries(
                    start,
                    targetSeries,
                    targetIndex,
                    usedStartSeries,
                )
                if (matchingStartIndex >= 0) {
                    usedStartSeries.add(matchingStartIndex)
                }
                SeriesTransition(
                    targetSeries,
                    start.series.getOrNull(matchingStartIndex)?.values.orEmpty(),
                    targetSeries.dataValues.toList(),
                )
            }
            val exitingTransitions = start.series.mapIndexedNotNull { startIndex, startSeries ->
                if (startIndex in usedStartSeries) {
                    null
                } else {
                    ExitingSeriesTransition(
                        startIndex,
                        startSeries.series,
                        startSeries.values,
                        startSeries.series.dataValues.toList(),
                        startSeries.appearance,
                        ChartSeriesAppearanceSnapshot.capture(startSeries.series),
                    )
                }
            }
            return ChartDataTransition(target.mutableDataSeries, transitions, exitingTransitions)
        }

        private fun findMatchingStartSeries(
            start: ChartDataSnapshot,
            targetSeries: ChartSeries,
            targetIndex: Int,
            usedStartSeries: Set<Int>,
        ): Int {
            val namedSeriesIndex = start.series.indices.firstOrNull { startIndex ->
                val startSeries = start.series[startIndex]
                startIndex !in usedStartSeries &&
                    startSeries.type == targetSeries.type &&
                    startSeries.name == targetSeries.name
            }
            if (namedSeriesIndex != null) {
                return namedSeriesIndex
            }
            val positionalSeries = start.series.getOrNull(targetIndex)
            if (targetIndex !in usedStartSeries && positionalSeries?.type == targetSeries.type) {
                return targetIndex
            }
            return -1
        }
    }

    private class SeriesTransition(
        private val targetSeries: ChartSeries,
        private val startValues: List<Float?>,
        private val targetValues: List<Float?>,
    ) {
        private val frameValueCount: Int = max(startValues.size, targetValues.size)
        private val frameStartValues: List<Float?> = List(frameValueCount) { index ->
            normalizedStartValue(index, startValues, targetValues)
        }
        private val frameTargetValues: List<Float?> = List(frameValueCount) { index ->
            normalizedTargetValue(index, frameStartValues, targetValues)
        }

        val hasChanges: Boolean = !valuesEqual(startValues, targetValues)

        fun apply(progress: Float) {
            targetSeries.replaceDataValues(
                List(frameValueCount) { index ->
                    interpolateValue(frameStartValues[index], frameTargetValues[index], progress)
                },
            )
        }

        fun finish() {
            targetSeries.replaceDataValues(targetValues)
        }

        companion object {
            private fun normalizedStartValue(
                index: Int,
                startValues: List<Float?>,
                targetValues: List<Float?>,
            ): Float? {
                val startValue = startValues.getOrNull(index)
                if (startValue?.isFinite() == true) {
                    return startValue
                }
                return if (targetValues.getOrNull(index)?.isFinite() == true) 0f else startValue
            }

            private fun normalizedTargetValue(
                index: Int,
                frameStartValues: List<Float?>,
                targetValues: List<Float?>,
            ): Float? {
                val targetValue = targetValues.getOrNull(index)
                if (targetValue?.isFinite() == true) {
                    return targetValue
                }
                return if (frameStartValues[index]?.isFinite() == true) 0f else targetValue
            }
        }
    }

    private class ExitingSeriesTransition(
        private val originalIndex: Int,
        private val series: ChartSeries,
        startValues: List<Float?>,
        private val restoreValues: List<Float?>,
        private val startAppearance: ChartSeriesAppearanceSnapshot,
        private val restoreAppearance: ChartSeriesAppearanceSnapshot,
    ) {
        private val valueTransition = SeriesTransition(series, startValues, emptyList())
        private val pointCount: Int = startValues.size
        private var attached: Boolean = false

        val hasChanges: Boolean = startValues.any { value -> value?.isFinite() == true }

        fun attach(targetSeries: MutableList<ChartSeries>) {
            if (!attached && targetSeries.none { candidate -> candidate === series }) {
                targetSeries.add(originalIndex.coerceIn(0, targetSeries.size), series)
                attached = true
            }
        }

        fun apply(progress: Float) {
            valueTransition.apply(progress)
            startAppearance.applyFaded(series, 1f - progress, pointCount)
        }

        fun finish(targetSeries: MutableList<ChartSeries>) {
            if (attached) {
                val seriesIndex = targetSeries.indexOfFirst { candidate -> candidate === series }
                if (seriesIndex >= 0) {
                    targetSeries.removeAt(seriesIndex)
                }
                attached = false
            }
            series.replaceDataValues(restoreValues)
            restoreAppearance.restore(series)
        }
    }
}

private fun interpolateValue(startValue: Float?, targetValue: Float?, progress: Float): Float? {
    if (startValue?.isFinite() == true && targetValue?.isFinite() == true) {
        return (
            startValue.toDouble() +
                (targetValue.toDouble() - startValue.toDouble()) * progress.toDouble()
            ).toFloat()
    }
    return if (progress <= 0f) startValue else targetValue
}

private fun valuesEqual(first: List<Float?>, second: List<Float?>): Boolean {
    if (first.size != second.size) {
        return false
    }
    return first.indices.all { index ->
        val firstValue = first[index]
        val secondValue = second[index]
        firstValue == secondValue ||
            (firstValue?.isNaN() == true && secondValue?.isNaN() == true)
    }
}

private fun Color.withOpacityMultiplier(multiplier: Float): Color {
    val alpha = ((hexColor ushr 24) and 0xFFL).toFloat() / 255f
    return opacity(alpha * multiplier.coerceIn(0f, 1f))
}

internal fun ChartAnimationEasing.transform(progress: Float): Float {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    return when (this) {
        ChartAnimationEasing.LINEAR -> normalizedProgress
        ChartAnimationEasing.EASE_IN -> normalizedProgress * normalizedProgress
        ChartAnimationEasing.EASE_OUT -> {
            val inverseProgress = 1f - normalizedProgress
            1f - inverseProgress * inverseProgress
        }
        ChartAnimationEasing.EASE_IN_OUT -> {
            normalizedProgress * normalizedProgress * (3f - 2f * normalizedProgress)
        }
    }
}
