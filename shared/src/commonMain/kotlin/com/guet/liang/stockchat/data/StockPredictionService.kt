@file:Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "MagicNumber")
package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.base.toUserMessage

import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionConfig
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionInput
import com.guet.liang.stockchat.model.StockPredictionPoint
import com.guet.liang.stockchat.model.StockPredictionResult
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// AI 走势预测服务：请求编排、结果校验与错误归一化。

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
        } catch (exception: RuntimeException) {
            predictionError(
                "request_exception provider=${config.providerDisplayName} " +
                    "type=${exception::class.simpleName ?: "unknown"}"
            )
            finish(
                StockPredictionResult.Failure(
                    exception.toUserMessage("${config.providerDisplayName} 预测请求失败，请稍后重试。"),
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

internal fun sanitizePredictionError(
    rawMessage: String,
    apiKey: String,
    providerName: String,
): String {
    val message = rawMessage.trim()
    val normalized = message.lowercase()
    if ("arrearage" in normalized || "overdue-payment" in normalized) {
        return "$providerName 账户欠费或余额不足，请在服务商控制台处理账单，或切换可用模型后重试。"
    }
    if ("insufficient_quota" in normalized) return "$providerName 可用额度不足，请检查服务额度或切换模型后重试。"
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
