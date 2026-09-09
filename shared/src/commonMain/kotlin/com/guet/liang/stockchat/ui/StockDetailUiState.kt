package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.data.TencentMarketSnapshot
import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint

// 详情页 UI 状态模型：页签、解读焦点、行情与预测加载态。

internal enum class DetailInsightFocus(
    val label: String,
) {
    TREND("走势解读"),
    PREDICTION("预测依据"),
    RISK("风险提醒"),
}

internal enum class DetailTab(
    val label: String,
) {
    MARKET("行情展示"),
    PREDICTION("AI 走势"),
}

internal fun scaledFontSize(baseSize: Float): Float = baseSize * StockChatTheme.fontScale

internal sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Content(val snapshot: TencentMarketSnapshot) : DetailUiState()
    data object Empty : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

internal sealed class PredictionUiState {
    data object NotRequested : PredictionUiState()
    data object Loading : PredictionUiState()
    data class Content(
        val prediction: StockPrediction,
        val history: List<StockPredictionHistoryPoint>,
    ) : PredictionUiState()
    data class Unavailable(val message: String) : PredictionUiState()
    data class Error(val message: String) : PredictionUiState()
}
