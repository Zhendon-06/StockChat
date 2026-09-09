package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.data.DEFAULT_CHAT_BASE_URL
import com.guet.liang.stockchat.data.HistoricalPointsResult
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.StockPredictionService
import com.guet.liang.stockchat.data.predictionLog
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.StockPredictionConfig
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionInput
import com.guet.liang.stockchat.model.StockPredictionResult
import com.guet.liang.stockchat.model.ModelProviderKind
import com.tencent.kuikly.core.module.NetworkModule

// 详情页 AI 预测请求：服务商解析、请求发起与日志。

private fun String.logSafe(): String {
    return replace(Regex("\\s+"), " ").trim().take(240)
}

internal fun StockDetailPage.requestPrediction(quote: StockQuote) {
    if (predictionState is PredictionUiState.Loading) {
        predictionLog("ui_request_ignored reason=already_loading symbol=$symbol")
        return
    }
    predictionToken += 1
    val currentPredictionToken = predictionToken
    updatePredictionState(PredictionUiState.Loading)
    chartShowingPrediction = false
    selectedChartPointIndex = -1
    predictionLog(
        "ui_request_started symbol=$symbol quoteName=${quote.name} " +
            "quotePrice=${quote.price} quoteUpdatedAt=${quote.updatedAt}"
    )

    val configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
    val provider = configuration.providers.firstOrNull { candidate ->
        candidate.id == configuration.activeProviderId
    }
    val usesDashScope = provider == null || provider.kind == ModelProviderKind.DEFAULT ||
        provider.kind == ModelProviderKind.ALIYUN
    val routeApiKey = pageData.params.optString("qwenApiKey").trim()
    val apiKey = when {
        provider == null -> routeApiKey
        !provider.isEnabled -> ""
        provider.apiKey.isNotBlank() -> provider.apiKey.trim()
        usesDashScope -> routeApiKey
        else -> ""
    }
    val model = provider?.selectedModelId?.trim().orEmpty()
        .ifBlank { provider?.models?.firstOrNull()?.id?.trim().orEmpty() }
    val config = StockPredictionConfig(
        apiKey = apiKey,
        baseUrl = provider?.baseUrl?.trim()?.takeIf(String::isNotBlank)
            ?: if (usesDashScope) DEFAULT_CHAT_BASE_URL else "",
        model = model,
        providerDisplayName = provider?.displayName?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "AI 模型",
        useAliyunExtensions = usesDashScope,
    )
    predictionLog(
        "ui_config providerId=${provider?.id ?: "none"} " +
            "provider=${config.providerDisplayName} kind=${provider?.kind ?: "none"} " +
            "enabled=${provider?.isEnabled ?: true} keyPresent=${config.apiKey.isNotBlank()} " +
            "baseUrl=${config.baseUrl} model=${config.model} " +
            "aliyunExtensions=${config.useAliyunExtensions}"
    )

    if (config.apiKey.isBlank()) {
        predictionLog("ui_request_rejected reason=missing_api_key")
        if (currentPredictionToken == predictionToken) {
            updatePredictionState(PredictionUiState.Unavailable(
                "当前 Provider 没有可用 API Key，请先在模型配置页面填写后重试。",
            ))
        }
        return
    }
    if (config.model.isBlank()) {
        predictionLog("ui_request_rejected reason=missing_model")
        if (currentPredictionToken == predictionToken) {
            updatePredictionState(PredictionUiState.Unavailable(
                "当前 Provider 没有可用模型，请先选择模型后重试。",
            ))
        }
        return
    }

    marketDataService.loadHistoricalPoints(
        symbol = symbol,
        count = PREDICTION_HISTORY_COUNT,
    ) history@{ historyResult ->
        if (currentPredictionToken != predictionToken) {
            return@history
        }
        when (historyResult) {
            HistoricalPointsResult.Empty -> {
                predictionLog(
                    "history_empty symbol=$symbol requestedCount=$PREDICTION_HISTORY_COUNT"
                )
                updatePredictionState(PredictionUiState.Unavailable(
                    "腾讯行情没有返回足够的历史收盘数据，未生成预测曲线。",
                ))
            }
            is HistoricalPointsResult.Failure -> {
                predictionLog(
                    "history_failed symbol=$symbol message=${historyResult.message.logSafe()}"
                )
                updatePredictionState(PredictionUiState.Error(historyResult.message))
            }
            is HistoricalPointsResult.Success -> {
                predictionLog(
                    "history_loaded symbol=$symbol count=${historyResult.points.size} " +
                        "first=${historyResult.points.firstOrNull()?.date ?: "none"} " +
                        "last=${historyResult.points.lastOrNull()?.date ?: "none"}"
                )
                val history = historyResult.points.map { point ->
                    StockPredictionHistoryPoint(
                        timestamp = point.date,
                        close = point.close,
                    )
                }
                val input = StockPredictionInput(
                    quote = quote,
                    history = history,
                    forecastHorizon = StockPredictionInput.DEFAULT_STOCK_PREDICTION_HORIZON,
                    sourceUpdatedAt = quote.updatedAt,
                )
                predictionLog(
                    "prediction_input_ready symbol=${input.quote.symbol} " +
                        "historyCount=${input.history.size} horizon=${input.forecastHorizon} " +
                        "sourceUpdatedAt=${input.sourceUpdatedAt}"
                )
                try {
                    StockPredictionService(
                        networkModule = acquireModule(NetworkModule.MODULE_NAME),
                        config = config,
                    ).predict(input) prediction@{ predictionResult ->
                        if (currentPredictionToken != predictionToken) {
                            return@prediction
                        }
                        when (predictionResult) {
                            is StockPredictionResult.Success -> {
                                predictionLog(
                                    "prediction_success symbol=$symbol " +
                                        "points=${predictionResult.prediction.forecastPoints.size} " +
                                        "direction=${predictionResult.prediction.direction} " +
                                        "confidence=${predictionResult.prediction.confidence}"
                                )
                                updatePredictionState(PredictionUiState.Content(
                                    prediction = predictionResult.prediction,
                                    history = history,
                                ))
                                chartShowingPrediction = true
                            }
                            is StockPredictionResult.Unavailable -> {
                                predictionLog(
                                    "prediction_unavailable symbol=$symbol " +
                                        "message=${predictionResult.message.logSafe()}"
                                )
                                updatePredictionState(PredictionUiState.Unavailable(
                                    predictionResult.message,
                                ))
                            }
                            is StockPredictionResult.Failure -> {
                                predictionLog(
                                    "prediction_failed symbol=$symbol status=${predictionResult.statusCode ?: "unknown"} " +
                                        "message=${predictionResult.message.logSafe()}"
                                )
                                updatePredictionState(PredictionUiState.Error(
                                    predictionResult.message,
                                ))
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    predictionLog(
                        "prediction_exception symbol=$symbol " +
                            "type=${throwable::class.simpleName ?: "unknown"}"
                    )
                    updatePredictionState(PredictionUiState.Error(
                        "AI 预测请求失败，请稍后重试；未生成预测曲线。",
                    ))
                }
            }
        }
    }
}
