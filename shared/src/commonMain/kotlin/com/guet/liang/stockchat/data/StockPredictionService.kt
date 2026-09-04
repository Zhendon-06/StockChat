package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionConfig
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionInput
import com.guet.liang.stockchat.model.StockPredictionPoint
import com.guet.liang.stockchat.model.StockPredictionResult
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

private const val STOCK_PREDICTION_LOG_TAG = "StockPrediction"

/**
 * Calls an OpenAI-compatible model for a structured, conditional price forecast.
 *
 * This service intentionally has no local prediction fallback. If the model cannot be
 * contacted or its response cannot be verified, the caller receives an unavailable/failure
 * result and must not draw a forecast curve.
 */
internal class StockPredictionService(
    private val networkModule: NetworkModule,
    private val config: StockPredictionConfig,
) {
    fun predict(
        input: StockPredictionInput,
        callback: (StockPredictionResult) -> Unit,
    ) {
        var finished = false
        fun finish(result: StockPredictionResult) {
            if (finished) return
            finished = true
            callback(result)
        }

        when (val validation = validateInput(input)) {
            null -> Unit
            else -> {
                predictionLog(
                    "input_rejected provider=${config.providerDisplayName} " +
                        "reason=${predictionResultMessage(validation)}"
                )
                finish(validation)
                return
            }
        }

        val request = StockPredictionRequestBuilder.build(
            input = input,
            modelName = config.model,
            useAliyunExtensions = config.useAliyunExtensions,
        )
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Accept", "application/json")
            put("Authorization", "Bearer ${config.apiKey.trim()}")
        }
        val endpoint = completionEndpoint(config.baseUrl)
        predictionLog(
            "request_started provider=${config.providerDisplayName} model=${config.model} " +
                "endpoint=$endpoint aliyunExtensions=${config.useAliyunExtensions} " +
                "historyCount=${input.history.size} horizon=${input.forecastHorizon} " +
                "sourceUpdatedAt=${input.sourceUpdatedAt} " +
                "firstHistory=${input.history.firstOrNull()?.timestamp ?: "none"} " +
                "lastHistory=${input.history.lastOrNull()?.timestamp ?: "none"}"
        )
        try {
            networkModule.httpRequest(
                url = endpoint,
                isPost = true,
                param = request,
                headers = headers,
                timeout = config.requestTimeoutSeconds.coerceAtLeast(1),
            ) { responseData, success, errorMessage, response ->
                val statusCode = response.statusCode
                predictionLog(
                    "response_received provider=${config.providerDisplayName} " +
                        "success=$success status=${statusCode ?: "unknown"} " +
                        "bodyLength=${responseData.toString().length} " +
                        "keys=${responseData.keySet().joinToString(",")}"
                )
                if (!success || (statusCode != null && statusCode !in 200..299)) {
                    predictionError(
                        "http_failed provider=${config.providerDisplayName} " +
                            "status=${statusCode ?: "unknown"}"
                    )
                    finish(
                        StockPredictionResult.Failure(
                            message = sanitizePredictionError(
                                errorMessage.ifBlank {
                                    responseData.optJSONObject("error")
                                        ?.optString("message")
                                        .orEmpty()
                                },
                                config.apiKey,
                                config.providerDisplayName,
                            ),
                            statusCode = statusCode,
                        )
                    )
                    return@httpRequest
                }

                val content = extractAssistantContent(responseData)
                predictionLog(
                    "assistant_content provider=${config.providerDisplayName} " +
                        "length=${content?.length ?: 0} preview=${contentPreview(content)}"
                )
                if (content.isNullOrBlank()) {
                    predictionError(
                        "empty_content provider=${config.providerDisplayName} " +
                            "status=${statusCode ?: "unknown"}"
                    )
                    finish(
                        StockPredictionResult.Failure(
                            "${config.providerDisplayName} 未返回可解析的预测结果。",
                            statusCode,
                        )
                    )
                    return@httpRequest
                }
                val prediction = StockPredictionResponseParser.parse(
                    content = content,
                    modelName = config.model,
                    sourceUpdatedAt = input.sourceUpdatedAt,
                    expectedHorizon = input.forecastHorizon,
                    expectedHistoryPointCount = input.history.size,
                )
                val rejectionReason = predictionPlausibilityFailure(prediction, input)
                if (prediction == null || rejectionReason != null) {
                    predictionError(
                        "response_rejected provider=${config.providerDisplayName} " +
                            "reason=${rejectionReason ?: "parse_failed"} contentLength=${content.length} " +
                            "expectedHorizon=${input.forecastHorizon} historyCount=${input.history.size}"
                    )
                    finish(
                        StockPredictionResult.Failure(
                            "${config.providerDisplayName} 返回的预测格式或数值未通过校验，未生成预测曲线。",
                            statusCode,
                        )
                    )
                } else {
                    predictionLog(
                        "response_accepted provider=${config.providerDisplayName} " +
                            "points=${prediction.forecastPoints.size} " +
                            "direction=${prediction.direction} " +
                            "confidence=${prediction.confidence}"
                    )
                    finish(StockPredictionResult.Success(prediction))
                }
            }
        } catch (throwable: Throwable) {
            predictionError(
                "request_exception provider=${config.providerDisplayName} " +
                    "type=${throwable::class.simpleName ?: "unknown"}"
            )
            finish(
                StockPredictionResult.Failure(
                    "${config.providerDisplayName} 预测请求失败，请稍后重试。",
                )
            )
        }
    }

    private fun validateInput(input: StockPredictionInput): StockPredictionResult? {
        val providerName = config.providerDisplayName.ifBlank { "AI 模型" }
        if (config.apiKey.isBlank()) {
            return StockPredictionResult.Unavailable(
                "尚未配置 AI 预测模型 API Key，请在模型配置页面填写后重试。",
            )
        }
        if (config.baseUrl.isBlank() || config.model.isBlank()) {
            return StockPredictionResult.Unavailable(
                "AI 预测模型配置不完整，请检查服务地址和模型名称。",
            )
        }
        if (input.history.size < MIN_HISTORY_POINTS) {
            return StockPredictionResult.Unavailable(
                "${providerName} 预测需要至少 $MIN_HISTORY_POINTS 个按时间排序的历史行情点。",
            )
        }
        if (input.forecastHorizon !in MIN_FORECAST_POINTS..MAX_FORECAST_POINTS) {
            return StockPredictionResult.Unavailable(
                "预测周期必须在 $MIN_FORECAST_POINTS 至 $MAX_FORECAST_POINTS 个交易点之间。",
            )
        }
        if (input.sourceUpdatedAt.isBlank() || input.sourceUpdatedAt.isUnknownMarketTimestamp()) {
            return StockPredictionResult.Unavailable("行情时间缺失，无法确认预测所依据的数据版本。")
        }
        if (input.history.any { it.timestamp.isBlank() || !it.close.isValidPrice() }) {
            return StockPredictionResult.Unavailable("历史行情包含无效时间或价格，未生成预测曲线。")
        }
        if (!timestampsInOrder(input.history.map(StockPredictionHistoryPoint::timestamp))) {
            return StockPredictionResult.Unavailable("历史行情时间未按从旧到新排列，未生成预测曲线。")
        }
        if (!input.quote.price.toPredictionPrice().isValidPrice()) {
            return StockPredictionResult.Unavailable("当前行情价格无效，未生成预测曲线。")
        }
        return null
    }

    private fun predictionPlausibilityFailure(
        prediction: StockPrediction?,
        input: StockPredictionInput,
    ): String? {
        if (prediction == null) return "parse_failed"
        if (prediction.forecastPoints.size != input.forecastHorizon) {
            return "forecast_count=${prediction.forecastPoints.size}"
        }
        if (prediction.historyPointCount != input.history.size) {
            return "history_count=${prediction.historyPointCount}"
        }
        if (prediction.sourceUpdatedAt != input.sourceUpdatedAt) {
            return "source_timestamp_mismatch"
        }
        if (prediction.confidence !in 0f..1f || !prediction.confidence.isFinite()) {
            return "confidence=${prediction.confidence}"
        }
        if (prediction.direction.isBlank()) return "direction_blank"
        if (prediction.rationale.isBlank()) return "rationale_blank"
        if (!isSupportedTimestamp(prediction.generatedAt)) return "generated_at_invalid"
        if (!timestampsInOrder(prediction.forecastPoints.map(StockPredictionPoint::timestamp))) {
            return "forecast_timestamps_invalid_or_unordered"
        }
        if (!timestampIsAfter(
                prediction.forecastPoints.first().timestamp,
                input.history.last().timestamp,
            )
        ) {
            return "forecast_starts_before_history"
        }
        val latest = input.history.last().close.toDouble()
        if (!latest.isFinite() || latest <= 0.0) return "latest_history_price_invalid"
        val lowerPrice = latest * MIN_FORECAST_PRICE_RATIO
        val upperPrice = latest * MAX_FORECAST_PRICE_RATIO
        val invalidPointIndex = prediction.forecastPoints.withIndex().firstOrNull { (_, point) ->
            !(point.predictedPrice.isValidPrice() &&
                point.predictedPrice.toDouble() in lowerPrice..upperPrice &&
                (point.lowerBound == null || (
                    point.lowerBound.isValidPrice() &&
                        point.lowerBound <= point.predictedPrice
                    )) &&
                (point.upperBound == null || (
                    point.upperBound.isValidPrice() &&
                        point.upperBound >= point.predictedPrice
                    )) &&
                (point.lowerBound == null || point.upperBound == null ||
                    point.lowerBound <= point.upperBound))
        }?.index
        if (invalidPointIndex != null) return "forecast_point_invalid[index=$invalidPointIndex]"
        return null
    }

    private fun completionEndpoint(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }

    companion object {
        private const val MIN_HISTORY_POINTS = 5
        private const val MIN_FORECAST_POINTS = 2
        private const val MAX_FORECAST_POINTS = 32
        private const val MIN_FORECAST_PRICE_RATIO = 0.01
        private const val MAX_FORECAST_PRICE_RATIO = 100.0
    }
}

private fun predictionLog(message: String) {
    runCatching { KLog.i(STOCK_PREDICTION_LOG_TAG, message) }
        .onFailure { println("[$STOCK_PREDICTION_LOG_TAG] $message") }
}

private fun predictionError(message: String) {
    runCatching { KLog.e(STOCK_PREDICTION_LOG_TAG, message) }
        .onFailure { println("[$STOCK_PREDICTION_LOG_TAG] $message") }
}

private fun predictionResultMessage(result: StockPredictionResult): String {
    return when (result) {
        is StockPredictionResult.Unavailable -> result.message
        is StockPredictionResult.Failure -> result.message
        is StockPredictionResult.Success -> "success"
    }.replace(Regex("\\s+"), " ").take(240)
}

private fun contentPreview(content: String?): String {
    val compact = content.orEmpty()
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.length <= 1600) return compact
    return compact.take(800) + " … " + compact.takeLast(800)
}

/** Builds the request shared by all OpenAI-compatible prediction providers. */
internal object StockPredictionRequestBuilder {
    fun build(
        input: StockPredictionInput,
        modelName: String,
        useAliyunExtensions: Boolean = false,
    ): JSONObject {
        val history = JSONArray().apply {
            input.history.forEach { point ->
                put(
                    JSONObject().apply {
                        put("timestamp", point.timestamp)
                        put("close", point.close.toDouble())
                    }
                )
            }
        }
        val context = JSONObject().apply {
            put("name", input.quote.name)
            put("symbol", input.quote.symbol)
            put("market", input.quote.marketLabel)
            put("currentPrice", input.quote.price)
            put("change", input.quote.change)
            put("changePercent", input.quote.changePercent)
            put("sourceUpdatedAt", input.sourceUpdatedAt)
            put("history", history)
            put("forecastHorizon", input.forecastHorizon)
        }
        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", STOCK_PREDICTION_SYSTEM_PROMPT)
                }
            )
            put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "content",
                        "请仅依据下面 JSON 中的行情数据生成条件性预测，不要补造缺失数据：\n" +
                            context.toString(),
                    )
                }
            )
        }
        return JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            put("temperature", 0)
            if (useAliyunExtensions) {
                put("thinking", JSONObject().apply { put("type", "disabled") })
                put("max_completion_tokens", 1600)
            } else {
                put("max_tokens", 1600)
            }
            put("stream", false)
            if (useAliyunExtensions) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }
    }

    private const val STOCK_PREDICTION_SYSTEM_PROMPT =
        "你是一个负责金融时间序列分析的 AI 模型。" +
            "你只能根据用户提供的、按时间从旧到新排列的真实行情点做条件性预测；" +
            "不能声称确定盈利，不能把预测当作投资建议。" +
            "必须只返回一个严格 JSON 对象，不要 Markdown、代码围栏或额外解释。" +
            "JSON 必须包含 forecastPoints 数组（恰好按要求的 horizon 个点），" +
            "每个点包含 timestamp、predictedPrice，并可包含 lowerBound、upperBound；" +
            "还必须包含 horizon、direction、confidence（0 到 1）、rationale、generatedAt、sourceUpdatedAt、historyPointCount。" +
            "所有 forecastPoints.timestamp 与 generatedAt 必须使用 YYYY-MM-DD 或 ISO 8601 日期时间格式，不能使用‘明天’等相对日期；" +
            "sourceUpdatedAt 必须原样回显输入 JSON 中的 sourceUpdatedAt，historyPointCount 必须等于输入样本数；" +
            "timestamp 必须是预测目标时间且按从旧到新排列；价格必须为正数且有限；" +
            "direction 请使用看多、偏多、中性、偏空或谨慎观望等清晰表述。"
}

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

private fun predictionTimestampsMatch(first: String, second: String): Boolean {
    val firstTrimmed = first.trim()
    val secondTrimmed = second.trim()
    if (firstTrimmed == secondTrimmed) return true
    val firstComparable = comparablePredictionTimestamp(firstTrimmed) ?: return false
    val secondComparable = comparablePredictionTimestamp(secondTrimmed) ?: return false
    if (firstComparable == secondComparable) return true
    return stripPredictionTimestampZone(firstComparable) ==
        stripPredictionTimestampZone(secondComparable)
}

private fun stripPredictionTimestampZone(value: String): String {
    return value.removeSuffix("Z")
        .replace(Regex("[+-]\\d{2}:?\\d{2}$"), "")
}

private fun comparablePredictionTimestamp(value: String): String? {
    parsePredictionTimestamp(value)?.let { return it.comparableValue }
    return TIMESTAMP_TOKEN_PATTERN.findAll(value)
        .mapNotNull { match -> parsePredictionTimestamp(match.value)?.comparableValue }
        .firstOrNull()
}

private fun extractAssistantContent(response: JSONObject): String? {
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

private fun Float?.isValidPrice(): Boolean {
    return this != null && isFinite() && this > 0f
}

private fun String.toPredictionPrice(): Float? {
    return replace(",", "").trim().toFloatOrNull()
}

private fun String.isUnknownMarketTimestamp(): Boolean {
    return contains("时间未知", ignoreCase = true) ||
        contains("unknown", ignoreCase = true)
}

private fun timestampsInOrder(timestamps: List<String>): Boolean {
    if (timestamps.isEmpty() || timestamps.any(String::isBlank) ||
        timestamps.distinct().size != timestamps.size
    ) {
        return false
    }
    return timestamps.zipWithNext().all { (previous, current) ->
        comparePredictionTimestamps(previous, current)?.let { it < 0 } == true
    }
}

private fun timestampIsAfter(candidate: String, reference: String): Boolean {
    return comparePredictionTimestamps(reference, candidate)?.let { it < 0 } == true
}

private fun isSupportedTimestamp(value: String): Boolean {
    return parsePredictionTimestamp(value) != null
}

private enum class PredictionTimestampKind {
    NUMERIC,
    CALENDAR,
}

private data class ParsedPredictionTimestamp(
    val kind: PredictionTimestampKind,
    val comparableValue: String,
)

private val NUMERIC_TIMESTAMP_PATTERN = Regex("^[0-9]{8,17}$")
private val TIMESTAMP_TOKEN_PATTERN = Regex(
    "(?<!\\d)(?:\\d{4}[-/]\\d{2}[-/]\\d{2}(?:[T\\s]\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:?\\d{2})?)?|\\d{8,17})(?!\\d)"
)
private val CALENDAR_TIMESTAMP_PATTERN = Regex(
    """^(\d{4})[-/](\d{2})[-/](\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:?\d{2})?)?$""",
)

private fun parsePredictionTimestamp(value: String): ParsedPredictionTimestamp? {
    val trimmed = value.trim()
    if (trimmed.matches(NUMERIC_TIMESTAMP_PATTERN)) {
        val numeric = trimmed.toLongOrNull() ?: return null
        if (numeric <= 0L) return null
        return ParsedPredictionTimestamp(
            kind = PredictionTimestampKind.NUMERIC,
            comparableValue = numeric.toString(),
        )
    }
    val match = CALENDAR_TIMESTAMP_PATTERN.matchEntire(trimmed) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (year <= 0 || month !in 1..12 || day !in 1..daysInMonth(year, month)) {
        return null
    }
    val hourText = match.groupValues[4]
    val minuteText = match.groupValues[5]
    val secondText = match.groupValues[6]
    val fractionText = match.groupValues[7]
    val zoneText = match.groupValues[8]
    if (hourText.isBlank() != minuteText.isBlank() ||
        (secondText.isNotBlank() && minuteText.isBlank()) ||
        (fractionText.isNotBlank() && secondText.isBlank())
    ) {
        return null
    }
    if (hourText.isNotBlank()) {
        val hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: return null
        val second = secondText.ifBlank { "00" }.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    } else if (zoneText.isNotBlank()) {
        return null
    }
    if (zoneText.isNotBlank() && zoneText != "Z") {
        val zoneDigits = zoneText.removePrefix("+").removePrefix("-").replace(":", "")
        val zoneHour = zoneDigits.take(2).toIntOrNull() ?: return null
        val zoneMinute = zoneDigits.drop(2).toIntOrNull() ?: return null
        if (zoneHour !in 0..23 || zoneMinute !in 0..59) return null
    }
    val normalizedDate = "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
    val normalizedTime = if (hourText.isBlank()) {
        "00:00:00"
    } else {
        val normalizedFraction = fractionText.takeIf(String::isNotBlank)
            ?.padEnd(9, '0')
            ?.let { ".$it" }
            .orEmpty()
        "${hourText}:${minuteText}:${secondText.ifBlank { "00" }}$normalizedFraction"
    }
    val normalizedZone = when {
        zoneText.isBlank() -> ""
        zoneText == "Z" -> "Z"
        else -> zoneText.replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
    }
    return ParsedPredictionTimestamp(
        kind = PredictionTimestampKind.CALENDAR,
        comparableValue = "$normalizedDate $normalizedTime$normalizedZone",
    )
}

private fun comparePredictionTimestamps(first: String, second: String): Int? {
    val parsedFirst = parsePredictionTimestamp(first) ?: return null
    val parsedSecond = parsePredictionTimestamp(second) ?: return null
    if (parsedFirst.kind != parsedSecond.kind) return null
    return when (parsedFirst.kind) {
        PredictionTimestampKind.NUMERIC -> {
            val firstNumber = parsedFirst.comparableValue.toLongOrNull() ?: return null
            val secondNumber = parsedSecond.comparableValue.toLongOrNull() ?: return null
            firstNumber.compareTo(secondNumber)
        }
        PredictionTimestampKind.CALENDAR ->
            parsedFirst.comparableValue.compareTo(parsedSecond.comparableValue)
    }
}

private fun daysInMonth(year: Int, month: Int): Int {
    return when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

private fun sanitizePredictionError(
    rawMessage: String,
    apiKey: String,
    providerName: String,
): String {
    val message = rawMessage.trim()
    if (message.isBlank()) return "$providerName 预测请求失败，请稍后重试。"
    var sanitized = message
    val normalizedKey = apiKey.trim()
    if (normalizedKey.isNotEmpty()) {
        sanitized = sanitized.replace(
            Regex(Regex.escape(normalizedKey), RegexOption.IGNORE_CASE),
            "[REDACTED]",
        )
    }
    sanitized = sanitized
        .replace(
            Regex("(?i)authorization\\s*[:=]\\s*[\\\"']?bearer\\s+[^\\s,;}\\\"']+"),
            "Authorization: Bearer [REDACTED]",
        )
        .replace(Regex("(?i)bearer\\s+[^\\s,;}]+"), "Bearer [REDACTED]")
    return if (Regex("(?i)(api[\\s_-]*key|authorization|bearer|token|secret|credential|密钥|凭据)")
        .containsMatchIn(sanitized)
    ) {
        "$providerName 预测请求失败，请检查 API Key、服务地址或模型配置。"
    } else {
        sanitized.take(240)
    }
}
