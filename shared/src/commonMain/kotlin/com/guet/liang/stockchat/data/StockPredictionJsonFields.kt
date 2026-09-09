package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionPoint
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal fun readConfidence(payload: JSONObject): Float? {
    val fraction = readNumber(payload, CONFIDENCE_KEYS)
    if (fraction != null && fraction.isFinite() && fraction in 0.0..1.0) {
        return fraction.toFloat()
    }
    val percentage = readNumber(payload, CONFIDENCE_PERCENT_KEYS)
        ?: findRaw(payload, CONFIDENCE_KEYS).toPercentNumericDouble()
    if (percentage != null && percentage.isFinite() && percentage in 0.0..100.0) {
        return (percentage / PERCENT_SCALE).toFloat()
    }
    return null
}

internal fun readPrice(payload: JSONObject, keys: List<String>): Float? {
    val number = readNumber(payload, keys) ?: return null
    if (!number.isFinite() || number <= 0.0 || number > MAX_ABSOLUTE_PRICE) return null
    val result = number.toFloat()
    return result.takeIf { it.isFinite() && it > 0f }
}

internal fun readOptionalPrice(payload: JSONObject, keys: List<String>): OptionalPrice {
    val raw = findRaw(payload, keys) ?: return OptionalPrice(null, false)
    val number = raw.toNumericDouble()
        ?: return OptionalPrice(null, true)
    if (!number.isFinite() || number <= 0.0 || number > MAX_ABSOLUTE_PRICE) {
        return OptionalPrice(null, true)
    }
    val value = number.toFloat()
    val valid = value.isFinite() && value > 0f
    return OptionalPrice(value.takeIf { valid }, !valid)
}

internal fun readInteger(payload: JSONObject, keys: List<String>): Int? {
    val number = readNumber(payload, keys) ?: return null
    if (!number.isFinite() || number <= 0.0 || number % 1.0 != 0.0) return null
    return number.toInt().takeIf { it > 0 }
}

internal fun readString(payload: JSONObject, keys: List<String>): String? {
    val value = findRaw(payload, keys).toText()
    return value.trim().takeIf(String::isNotBlank)
}

internal fun readNumber(payload: JSONObject, keys: List<String>): Double? {
    return findRaw(payload, keys).toNumericDouble()
}

internal fun findArray(payload: JSONObject, keys: List<String>): JSONArray? {
    keys.forEach { key ->
        payload.optJSONArray(key)?.let { return it }
        val raw = payload.opt(key)
        if (raw is String) {
            runCatching { JSONArray(raw.trim()) }.getOrNull()?.let { return it }
        }
    }
    return null
}

internal fun findRaw(payload: JSONObject, keys: List<String>): Any? {
    keys.forEach { key ->
        payload.opt(key)?.let { return it }
    }
    return null
}

internal fun hasAny(payload: JSONObject, keys: List<String>): Boolean {
    return keys.any(payload::has)
}

/** A missing bound is valid; a present malformed value is rejected. */
internal data class OptionalPrice(
    val value: Float?,
    val isInvalid: Boolean,
)

internal const val MIN_POINTS = 2
internal const val MAX_POINTS = 32
internal const val MAX_ABSOLUTE_PRICE = 1.0e12
internal val FORECAST_ARRAY_KEYS = listOf(
    "forecastPoints", "forecast_points", "predictions", "predictionPoints", "points", "forecast",
)
internal val TIMESTAMP_KEYS = listOf(
    "timestamp", "time", "datetime", "date", "targetDate", "forecastDate",
)
internal val PRICE_KEYS = listOf(
    "predictedPrice", "predicted_price", "price", "value", "close",
)
internal val LOWER_BOUND_KEYS = listOf(
    "lowerBound", "lower_bound", "lower", "low", "confidenceLower", "p10",
)
internal val UPPER_BOUND_KEYS = listOf(
    "upperBound", "upper_bound", "upper", "high", "confidenceUpper", "p90",
)
internal val HORIZON_KEYS = listOf("horizon", "forecastHorizon", "forecast_horizon", "period")
internal val DIRECTION_KEYS = listOf("direction", "trend", "outlook", "signal")
internal val CONFIDENCE_KEYS = listOf("confidence", "confidenceScore", "confidence_score")
internal val CONFIDENCE_PERCENT_KEYS = listOf("confidencePercent", "confidence_percent")
internal val RATIONALE_KEYS = listOf("rationale", "reason", "basis", "explanation")
internal val GENERATED_AT_KEYS = listOf("generatedAt", "generated_at", "createdAt", "created_at")
internal val SOURCE_UPDATED_AT_KEYS = listOf(
    "sourceUpdatedAt", "source_updated_at", "dataAsOf", "data_as_of",
)
internal val HISTORY_COUNT_KEYS = listOf(
    "historyPointCount", "history_point_count", "inputPointCount", "input_point_count",
)
internal val MODEL_NAME_KEYS = listOf("modelName", "model_name", "model")

private const val PERCENT_SCALE = 100.0
