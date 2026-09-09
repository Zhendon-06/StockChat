package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionResult
import com.tencent.kuikly.core.log.KLog

// 预测链路日志辅助。

private const val STOCK_PREDICTION_LOG_TAG = "StockPrediction"

internal fun predictionLog(message: String) {
    runCatching { KLog.i(STOCK_PREDICTION_LOG_TAG, message) }
        .onFailure { println("[$STOCK_PREDICTION_LOG_TAG] $message") }
}

internal fun predictionError(message: String) {
    runCatching { KLog.e(STOCK_PREDICTION_LOG_TAG, message) }
        .onFailure { println("[$STOCK_PREDICTION_LOG_TAG] $message") }
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
