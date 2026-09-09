package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPredictionPoint
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal fun predictionForecast(payload: JSONObject, expectedHorizon: Int): List<StockPredictionPoint>? {
    val points = findArray(payload, FORECAST_ARRAY_KEYS)?.let(::predictionPoints).orEmpty()
    val horizon = readInteger(payload, HORIZON_KEYS)
    val matchesExpected = expectedHorizon <= 0 || points.size == expectedHorizon
    val validCount = points.size in MIN_POINTS..MAX_POINTS && matchesExpected
    val validHorizon = hasAny(payload, HORIZON_KEYS) && horizon == points.size
    return points.takeIf { validCount && validHorizon && timestampsInOrder(points.map(StockPredictionPoint::timestamp)) }
}

private fun predictionPoints(array: JSONArray): List<StockPredictionPoint> {
    if (array.length() > MAX_POINTS) return emptyList()
    val points = (0 until array.length()).map { index -> array.optJSONObject(index)?.let(::predictionPoint) }
    return if (points.any { it == null }) emptyList() else points.filterNotNull()
}

private fun predictionPoint(point: JSONObject): StockPredictionPoint? {
    val timestamp = readString(point, TIMESTAMP_KEYS)
    val price = readPrice(point, PRICE_KEYS)
    if (timestamp == null || price == null) return null
    val lower = readOptionalPrice(point, LOWER_BOUND_KEYS)
    val upper = readOptionalPrice(point, UPPER_BOUND_KEYS)
    val validBounds = !lower.isInvalid && !upper.isInvalid
    val withinBounds = lower.value?.let { it <= price } != false && upper.value?.let { it >= price } != false
    return if (validBounds && withinBounds) StockPredictionPoint(timestamp, price, lower.value, upper.value) else null
}

internal fun predictionNarrative(payload: JSONObject): PredictionNarrative? {
    val direction = readString(payload, DIRECTION_KEYS)
    val confidence = readConfidence(payload)
    val rationale = readString(payload, RATIONALE_KEYS)
    return if (direction != null && confidence != null && rationale != null) PredictionNarrative(direction, confidence, rationale) else null
}

internal fun predictionContext(payload: JSONObject, sourceUpdatedAt: String, expectedHistoryCount: Int): PredictionContext? {
    val generatedAt = readString(payload, GENERATED_AT_KEYS)?.takeIf(::isSupportedTimestamp)
    val expectedTime = sourceUpdatedAt.trim()
    val returnedTime = readString(payload, SOURCE_UPDATED_AT_KEYS)?.takeIf {
        expectedTime.isNotBlank() && predictionTimestampsMatch(it, expectedTime)
    }
    val historyCount = readInteger(payload, HISTORY_COUNT_KEYS)?.takeIf { it > 0 }
    if (generatedAt == null || returnedTime == null || historyCount == null) return null
    if (!hasAny(payload, HISTORY_COUNT_KEYS)) return null
    val effectiveCount = if (expectedHistoryCount > 0) {
        if (historyCount != expectedHistoryCount) {
            predictionLog("history_count_adjusted returned=$historyCount expected=$expectedHistoryCount")
        }
        expectedHistoryCount
    } else historyCount
    return PredictionContext(generatedAt, expectedTime, effectiveCount)
}

/** Validated descriptive fields of a model prediction. */
internal data class PredictionNarrative(val direction: String, val confidence: Float, val rationale: String)

/** Validated generation and source metadata of a model prediction. */
internal data class PredictionContext(val generatedAt: String, val sourceUpdatedAt: String, val historyPointCount: Int)
