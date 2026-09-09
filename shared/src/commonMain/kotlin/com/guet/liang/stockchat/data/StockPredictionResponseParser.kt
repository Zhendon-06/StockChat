@file:Suppress("ReturnCount")
package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionPoint
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// 预测响应解析：JSON 提取、数值与价格解析。

/** Strict parser for the JSON content emitted by an AI completion. */
internal object StockPredictionResponseParser {
    fun parse(
        content: String,
        modelName: String,
        sourceUpdatedAt: String,
        expectedHorizon: Int = 0,
        expectedHistoryPointCount: Int = 0,
    ): StockPrediction? {
        val normalized = normalizeJsonContent(content) ?: return null
        val root = runCatching { JSONObject(normalized) }.getOrNull() ?: return null
        return parse(
            response = root,
            modelName = modelName,
            sourceUpdatedAt = sourceUpdatedAt,
            expectedHorizon = expectedHorizon,
            expectedHistoryPointCount = expectedHistoryPointCount,
        )
    }

    fun parse(
        response: JSONObject,
        modelName: String,
        sourceUpdatedAt: String,
        expectedHorizon: Int = 0,
        expectedHistoryPointCount: Int = 0,
    ): StockPrediction? {
        val payload = response.optJSONObject("prediction") ?: response.optJSONObject("forecast") ?: response
        val points = predictionForecast(payload, expectedHorizon) ?: return null
        val narrative = predictionNarrative(payload) ?: return null
        val context = predictionContext(payload, sourceUpdatedAt, expectedHistoryPointCount) ?: return null
        return StockPrediction(
            forecastPoints = points,
            horizon = points.size,
            direction = narrative.direction,
            confidence = narrative.confidence,
            rationale = narrative.rationale,
            modelName = modelName.trim().ifBlank { readString(payload, MODEL_NAME_KEYS).orEmpty() }.ifBlank { "未指定模型" },
            generatedAt = context.generatedAt,
            sourceUpdatedAt = context.sourceUpdatedAt,
            historyPointCount = context.historyPointCount,
            conclusions = parseChartConclusions(payload.optJSONArray("conclusions")),
        )
    }
}
