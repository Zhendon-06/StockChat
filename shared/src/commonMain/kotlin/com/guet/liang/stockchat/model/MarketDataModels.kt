package com.guet.liang.stockchat.model

import com.guet.liang.kuiklychart.finance.FinancialAxisLabel
import com.guet.liang.kuiklychart.finance.FinancialPoint

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class TencentMarketSnapshot(
    val providerSymbol: String,
    val quote: StockQuote,
    val previousClose: String,
    val open: String,
    val high: String,
    val low: String,
    val volume: String,
    val volumeUnit: String,
    val amount: String,
    val amountUnit: String,
    val turnoverRate: String,
    val priceEarningsRatio: String,
    val amplitude: String,
    val dailyCandles: List<TencentHistoricalCandle> = emptyList(),
    val orderBook: List<MarketOrderLevel> = emptyList(),
    val totalMarketValue: String = "",
    val floatMarketValue: String = "",
    val priceBookRatio: String = "",
    val volumeRatio: String = "",
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class TencentHistoricalCandle(
    val date: String,
    val open: Float,
    val close: Float,
    val high: Float,
    val low: Float,
    val volume: Float,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class MarketDataResult {
    data class Success(
        val snapshots: List<TencentMarketSnapshot>,
        val notices: List<String> = emptyList(),
    ) : MarketDataResult()
    data object Empty : MarketDataResult()
    data class Failure(val message: String) : MarketDataResult()
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class TencentHistoricalPoint(
    val date: String,
    val close: Float,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class HistoricalPointsResult {
    data class Success(
        val providerSymbol: String,
        val points: List<TencentHistoricalPoint>,
    ) : HistoricalPointsResult()

    data object Empty : HistoricalPointsResult()
    data class Failure(val message: String) : HistoricalPointsResult()
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class MarketOrderLevel(val side: String, val level: Int, val price: Float, val volume: Float)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class MarketPeriod(val label: String, val key: String) {
    INTRADAY("分时", "minute"), FIVE_DAYS("五日", "five"), DAY("日K", "day"), WEEK("周K", "week"), MONTH("月K", "month");
    val isIntraday: Boolean get() = this == INTRADAY || this == FIVE_DAYS
}
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class MarketChartData(
    val points: List<FinancialPoint>,
    val previousClose: Float?,
    val sessionSlots: Float = 240f,
    val labels: List<FinancialAxisLabel> = emptyList(),
    val adjustment: String = "",
)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class MarketChartResult {
    data class Content(val data: MarketChartData) : MarketChartResult()
    data class Error(val message: String) : MarketChartResult()
}
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class CapitalFlowPoint(
    val date: String,
    val main: Float,
    val small: Float,
    val medium: Float,
    val large: Float,
    val superLarge: Float,
)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class CapitalFlowResult {
    data class Content(val points: List<CapitalFlowPoint>, val isDemo: Boolean = false) : CapitalFlowResult()
    data class Error(val message: String) : CapitalFlowResult()
}
