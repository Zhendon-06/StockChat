package com.guet.liang.kuiklychart.finance

import com.tencent.kuikly.core.base.Color
import kotlin.math.abs
import kotlin.math.roundToLong

/** One exchange period. Volume is incremental, in the unit supplied by the caller. */
public data class FinancialPoint(
    val label: String,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Float? = null,
    val average: Float? = null,
/** Exchange-session coordinate, allowing an unfinished session to leave future space empty. */
    val slot: Float = 0f,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
public data class FinancialAxisLabel(val slot: Float, val text: String)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
public enum class FinancialChartMode { INTRADAY, CANDLES, CLOSE_LINE }

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
public data class FinancialInterval(val lower: Float, val upper: Float)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
public class FinancialChartSpec {
    public var points: List<FinancialPoint> = emptyList()
    public var mode: FinancialChartMode = FinancialChartMode.CANDLES
    public var valueLabel: String = "价格"
    public var intradayDescription: String = "价格走势"
    public var showVolume: Boolean = true
    public var forecastStartIndex: Int? = null
    public var forecastIntervals: Map<Int, FinancialInterval> = emptyMap()
    /** Reveal the forecast from left to right; zero disables the animation. */
    public var forecastRevealDurationMillis: Int = 0
    public var forecastColor: Color = Color(0xFF8D65D8L)
    public var previousClose: Float? = null
    public var sessionSlots: Float = 240f
    public var sessionLabels: List<FinancialAxisLabel> = listOf(
        FinancialAxisLabel(0f, "09:30"), FinancialAxisLabel(120f, "11:30/13:00"),
        FinancialAxisLabel(240f, "15:00"),
    )
    public var volumeUnit: String = "手"
    public var visibleCount: Int = 60
    public var movingAveragePeriods: List<Int> = listOf(5, 10, 20)
    public var backgroundColor: Color = Color.WHITE
    public var textColor: Color = Color(0xFF222833L)
    public var mutedColor: Color = Color(0xFF89919DL)
    public var gridColor: Color = Color(0xFFECEEF2L)
    public var riseColor: Color = Color(0xFFE93449L)
    public var fallColor: Color = Color(0xFF139D80L)
    public var evidenceColor: Color = Color(0xFF6E56CFL)
    public var lineColor: Color = Color(0xFF287BF3L)
    public var averageColor: Color = Color(0xFFEBAB45L)
    public var averageColors: List<Color> = listOf(Color(0xFFE65BA3L), Color(0xFFEBAB45L), Color(0xFF50A9E9L))
}

/** Pure finance calculations shared by all renderers and platforms. */
public object FinancialChartMath {
    /** Invalid model bounds must never affect the axis or produce a filled band. */
    public fun validInterval(interval: FinancialInterval, close: Float): Boolean =
    interval.lower.isFinite() && interval.upper.isFinite() && close.isFinite() &&
    interval.lower > 0f && interval.lower <= close && interval.upper >= close

    public fun valid(point: FinancialPoint): Boolean =
    listOf(point.open, point.high, point.low, point.close, point.slot).all(Float::isFinite) &&
    point.low > 0f && point.high >= maxOf(point.open, point.close) &&
    point.low <= minOf(point.open, point.close) &&
    (point.volume == null || point.volume.isFinite() && point.volume >= 0f)

    /** Warm-up uses history before the visible viewport; incomplete windows stay absent. */
    public fun movingAverage(points: List<FinancialPoint>, period: Int): List<Float?> {
        if (period <= 0) return List(points.size) { null }
        var sum = 0.0
        return points.mapIndexed { index, point ->
            sum += point.close
            if (index >= period) sum -= points[index - period].close
            if (index + 1 >= period) (sum / period).toFloat() else null
        }
    }

    public fun priceRange(points: List<FinancialPoint>, previousClose: Float?, intraday: Boolean): Pair<Float, Float> {
        if (points.isEmpty()) return 0f to 1f
        val low = points.minOf { minOf(it.low, it.average?.takeIf { v -> v.isFinite() && v > 0f } ?: it.low) }
        val high = points.maxOf { maxOf(it.high, it.average?.takeIf { v -> v.isFinite() && v > 0f } ?: it.high) }
        if (intraday && previousClose?.takeIf { it.isFinite() && it > 0f } != null) {
            val radius = maxOf(abs(high - previousClose), abs(low - previousClose), previousClose * 0.005f) * 1.05f
            return previousClose - radius to previousClose + radius
        }
        val padding = maxOf((high - low) * 0.12f, high * 0.005f)
        return low - padding to high + padding
    }
}

public fun financialNumber(value: Float, decimals: Int = 2): String {
    if (!value.isFinite()) return "--"
    val places = decimals.coerceIn(0, 4)
    val factor = when (places) {
        0 -> 1L
        1 -> 10L
        2 -> 100L
        3 -> 1000L
        else -> 10000L
    }
    val scaled = (abs(value).toDouble() * factor).roundToLong()
    return (if (value < 0 && scaled != 0L) "-" else "") + (scaled / factor).toString() +
    if (places == 0) "" else "." + (scaled % factor).toString().padStart(places, '0')
}

public fun financialVolume(value: Float): String = when {
    abs(value) >= 100000000f -> financialNumber(value / 100000000f) + "亿"
    abs(value) >= 10000f -> financialNumber(value / 10000f) + "万"
    else -> financialNumber(value, 0)
}
