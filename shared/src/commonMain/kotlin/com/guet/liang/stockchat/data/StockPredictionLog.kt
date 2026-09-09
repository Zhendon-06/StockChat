@file:Suppress("MagicNumber")
package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionResult
import com.guet.liang.stockchat.base.StockChatLog

// 预测链路日志辅助。

private const val STOCK_PREDICTION_LOG_TAG = "StockPrediction"

internal fun predictionLog(message: String) {
    StockChatLog.d(STOCK_PREDICTION_LOG_TAG, message)
}

internal fun predictionError(message: String) {
    StockChatLog.w(STOCK_PREDICTION_LOG_TAG, message)
}

internal fun predictionResultMessage(result: StockPredictionResult): String {
    return when (result) {
        is StockPredictionResult.Unavailable -> result.message
        is StockPredictionResult.Failure -> result.message
        is StockPredictionResult.Success -> "success"
    }.replace(Regex("\\s+"), " ").take(240)
}

internal fun contentPreview(content: String?): String {
    val compact = content.orEmpty()
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.length <= 1600) return compact
    return compact.take(800) + " … " + compact.takeLast(800)
}
