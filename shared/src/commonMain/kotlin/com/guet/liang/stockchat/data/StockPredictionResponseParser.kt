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
        val payload = response.optJSONObject("prediction")
            ?: response.optJSONObject("forecast")
            ?: response
        val points = findArray(payload, FORECAST_ARRAY_KEYS)
            ?.let(::parsePoints)
            ?: return null
        if (points.size !in MIN_POINTS..MAX_POINTS ||
            (expectedHorizon > 0 && points.size != expectedHorizon) ||
            !timestampsInOrder(points.map(StockPredictionPoint::timestamp))
        ) {
            return null
        }

        if (!hasAny(payload, HORIZON_KEYS)) return null
        val horizon = readInteger(payload, HORIZON_KEYS) ?: return null
        if (horizon != points.size || horizon !in MIN_POINTS..MAX_POINTS ||
            (expectedHorizon > 0 && horizon != expectedHorizon)
        ) {
            return null
        }
        val direction = readString(payload, DIRECTION_KEYS) ?: return null
        val confidence = readConfidence(payload) ?: return null
        val rationale = readString(payload, RATIONALE_KEYS) ?: return null
        val generatedAt = readString(payload, GENERATED_AT_KEYS) ?: return null
        if (!isSupportedTimestamp(generatedAt)) return null
        val returnedSourceTime = readString(payload, SOURCE_UPDATED_AT_KEYS) ?: return null
        val expectedSourceTime = sourceUpdatedAt.trim()
        if (
            expectedSourceTime.isBlank() ||
            !predictionTimestampsMatch(returnedSourceTime, expectedSourceTime)
        ) {
            return null
        }
        val effectiveSourceTime = expectedSourceTime
        if (!hasAny(payload, HISTORY_COUNT_KEYS)) return null
        val historyPointCount = readInteger(payload, HISTORY_COUNT_KEYS) ?: return null
        if (historyPointCount <= 0) {
            return null
        }
        val effectiveHistoryPointCount = if (expectedHistoryPointCount > 0) {
            if (historyPointCount != expectedHistoryPointCount) {
                predictionLog(
                    "history_count_adjusted returned=$historyPointCount " +
                        "expected=$expectedHistoryPointCount"
                )
            }
            expectedHistoryPointCount
        } else {
            historyPointCount
        }
        val effectiveModelName = modelName.trim().ifBlank {
            readString(payload, MODEL_NAME_KEYS).orEmpty()
        }.ifBlank { "未指定模型" }
        return StockPrediction(
            forecastPoints = points,
            horizon = horizon,
            direction = direction,
            confidence = confidence,
            rationale = rationale,
            modelName = effectiveModelName,
            generatedAt = generatedAt,
            sourceUpdatedAt = effectiveSourceTime,
            historyPointCount = effectiveHistoryPointCount,
            conclusions = parseChartConclusions(payload.optJSONArray("conclusions")),
        )
    }

    private fun parsePoints(array: JSONArray): List<StockPredictionPoint> {
        if (array.length() > MAX_POINTS) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val point = array.optJSONObject(index) ?: return emptyList()
                val timestamp = readString(point, TIMESTAMP_KEYS) ?: return emptyList()
                val predictedPrice = readPrice(point, PRICE_KEYS) ?: return emptyList()
                val lower = readOptionalPrice(point, LOWER_BOUND_KEYS)
                val upper = readOptionalPrice(point, UPPER_BOUND_KEYS)
                if (lower.isInvalid || upper.isInvalid) return emptyList()
                if (lower.value != null && lower.value > predictedPrice) return emptyList()
                if (upper.value != null && upper.value < predictedPrice) return emptyList()
                if (lower.value != null && upper.value != null && lower.value > upper.value) {
                    return emptyList()
                }
                add(
                    StockPredictionPoint(
                        timestamp = timestamp,
                        predictedPrice = predictedPrice,
                        lowerBound = lower.value,
                        upperBound = upper.value,
                    )
                )
            }
        }
    }

    private fun readConfidence(payload: JSONObject): Float? {
        val fraction = readNumber(payload, CONFIDENCE_KEYS)
        if (fraction != null && fraction.isFinite() && fraction in 0.0..1.0) {
            return fraction.toFloat()
        }
        val percentage = readNumber(payload, CONFIDENCE_PERCENT_KEYS)
            ?: findRaw(payload, CONFIDENCE_KEYS).toPercentNumericDouble()
        if (percentage != null && percentage.isFinite() && percentage in 0.0..100.0) {
            return (percentage / 100.0).toFloat()
        }
        return null
    }

    private fun readPrice(payload: JSONObject, keys: List<String>): Float? {
        val number = readNumber(payload, keys) ?: return null
        if (!number.isFinite() || number <= 0.0 || number > MAX_ABSOLUTE_PRICE) return null
        val result = number.toFloat()
        return result.takeIf { it.isFinite() && it > 0f }
    }

    private fun readOptionalPrice(payload: JSONObject, keys: List<String>): OptionalPrice {
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

    private fun readInteger(payload: JSONObject, keys: List<String>): Int? {
        val number = readNumber(payload, keys) ?: return null
        if (!number.isFinite() || number <= 0.0 || number % 1.0 != 0.0) return null
        return number.toInt().takeIf { it > 0 }
    }

    private fun readString(payload: JSONObject, keys: List<String>): String? {
        val value = findRaw(payload, keys).toText()
        return value.trim().takeIf(String::isNotBlank)
    }

    private fun readNumber(payload: JSONObject, keys: List<String>): Double? {
        return findRaw(payload, keys).toNumericDouble()
    }

    private fun findArray(payload: JSONObject, keys: List<String>): JSONArray? {
        keys.forEach { key ->
            payload.optJSONArray(key)?.let { return it }
            val raw = payload.opt(key)
            if (raw is String) {
                runCatching { JSONArray(raw.trim()) }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun findRaw(payload: JSONObject, keys: List<String>): Any? {
        keys.forEach { key ->
            payload.opt(key)?.let { return it }
        }
        return null
    }

    private fun hasAny(payload: JSONObject, keys: List<String>): Boolean {
        return keys.any(payload::has)
    }

    private data class OptionalPrice(
        val value: Float?,
        val isInvalid: Boolean,
    )

    private const val MIN_POINTS = 2
    private const val MAX_POINTS = 32
    private const val MAX_ABSOLUTE_PRICE = 1.0e12
    private val FORECAST_ARRAY_KEYS = listOf(
        "forecastPoints", "forecast_points", "predictions", "predictionPoints", "points", "forecast",
    )
    private val TIMESTAMP_KEYS = listOf(
        "timestamp", "time", "datetime", "date", "targetDate", "forecastDate",
    )
    private val PRICE_KEYS = listOf(
        "predictedPrice", "predicted_price", "price", "value", "close",
    )
    private val LOWER_BOUND_KEYS = listOf(
        "lowerBound", "lower_bound", "lower", "low", "confidenceLower", "p10",
    )
    private val UPPER_BOUND_KEYS = listOf(
        "upperBound", "upper_bound", "upper", "high", "confidenceUpper", "p90",
    )
    private val HORIZON_KEYS = listOf("horizon", "forecastHorizon", "forecast_horizon", "period")
    private val DIRECTION_KEYS = listOf("direction", "trend", "outlook", "signal")
    private val CONFIDENCE_KEYS = listOf("confidence", "confidenceScore", "confidence_score")
    private val CONFIDENCE_PERCENT_KEYS = listOf("confidencePercent", "confidence_percent")
    private val RATIONALE_KEYS = listOf("rationale", "reason", "basis", "explanation")
    private val GENERATED_AT_KEYS = listOf("generatedAt", "generated_at", "createdAt", "created_at")
    private val SOURCE_UPDATED_AT_KEYS = listOf(
        "sourceUpdatedAt", "source_updated_at", "dataAsOf", "data_as_of",
    )
    private val HISTORY_COUNT_KEYS = listOf(
        "historyPointCount", "history_point_count", "inputPointCount", "input_point_count",
    )
    private val MODEL_NAME_KEYS = listOf("modelName", "model_name", "model")
}

private fun normalizeJsonContent(content: String): String? {
    val trimmed = content
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .trim()
    if (trimmed.isBlank()) return null
    val fencedStart = trimmed.indexOf("```")
    if (fencedStart >= 0) {
        val bodyStart = trimmed.indexOf('\n', fencedStart)
        val fencedEnd = if (bodyStart >= 0) trimmed.indexOf("```", bodyStart + 1) else -1
        if (bodyStart >= 0 && fencedEnd > bodyStart) {
            extractJsonObject(trimmed.substring(bodyStart + 1, fencedEnd))?.let { return it }
        }
    }
    return extractJsonObject(trimmed)
}

private fun extractJsonObject(value: String): String? {
    val start = value.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until value.length) {
        val character = value[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == '"') {
                inString = false
            }
            continue
        }
        when (character) {
            '"' -> inString = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return value.substring(start, index + 1).trim()
                }
            }
        }
    }
    return null
}

internal fun extractAssistantContent(response: JSONObject): String? {
    val choice = response.optJSONArray("choices")?.optJSONObject(0)
    return listOf(
        choice?.optJSONObject("message")?.opt("content"),
        choice?.optJSONObject("delta")?.opt("content"),
        response.optJSONObject("output")?.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.opt("content"),
        response.optJSONObject("output")?.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("delta")?.opt("content"),
        response.opt("output_text"),
        response.opt("content"),
    ).asSequence().map(::contentText).firstOrNull(String::isNotBlank)
}

private fun contentText(value: Any?): String {
    return when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (index in 0 until value.length()) append(contentText(value.opt(index)))
        }
        is JSONObject -> listOf(value.opt("text"), value.opt("content"), value.opt("value"))
            .asSequence().map(::contentText).firstOrNull(String::isNotBlank).orEmpty()
        else -> ""
    }
}

private fun Any?.toText(): String {
    return when (this) {
        is String -> this
        null -> ""
        else -> toString()
    }
}

private fun Any?.toNumericDouble(): Double? {
    return when (this) {
        is Number -> toDouble()
        is String -> trim().replace(",", "").toDoubleOrNull()
        else -> toString().replace(",", "").toDoubleOrNull()
    }?.takeIf(Double::isFinite)
}

private fun Any?.toPercentNumericDouble(): Double? {
    val text = toText().trim()
    if (!text.endsWith('%')) return null
    return text.dropLast(1).replace(",", "").toDoubleOrNull()
        ?.takeIf(Double::isFinite)
}

internal fun Float?.isValidPrice(): Boolean {
    return this != null && isFinite() && this > 0f
}

internal fun String.toPredictionPrice(): Float? {
    return replace(",", "").trim().toFloatOrNull()
}
