package com.guet.liang.stockchat.model

internal data class TodayMarketSnapshot(
    val asOf: String,
    val indices: List<StockQuote>,
    val advancingCount: Int,
    val decliningCount: Int,
    val unchangedCount: Int,
    val mood: String,
    // 固定样本股按板块聚合后的当日均值，按涨跌降序；非全市场板块排名
    val sectors: List<TodayMarketSectorObservation>,
    // 固定样本股按当日涨跌降序；非全市场涨跌榜
    val sampleStocks: List<StockQuote>,
    val summary: String,
    val isDemo: Boolean,
    val sourceLabel: String,
    val disclaimer: String = TODAY_MARKET_DISCLAIMER,
)

internal data class TodayMarketSectorObservation(
    val name: String,
    val changeLabel: String,
    val isPositive: Boolean,
    val members: String,
)

internal sealed class TodayMarketResult {
    data class Success(val snapshot: TodayMarketSnapshot) : TodayMarketResult()
    data object Empty : TodayMarketResult()
    data class Failure(val message: String) : TodayMarketResult()
}

internal sealed class TodayMarketUiState {
    data object Loading : TodayMarketUiState()
    data class Content(val snapshot: TodayMarketSnapshot) : TodayMarketUiState()
    data object Empty : TodayMarketUiState()
    data class Error(val message: String) : TodayMarketUiState()
}

internal const val TODAY_MARKET_DISCLAIMER =
    "行情与 AI 结论均为演示信息，仅供参考，不构成投资建议。"
