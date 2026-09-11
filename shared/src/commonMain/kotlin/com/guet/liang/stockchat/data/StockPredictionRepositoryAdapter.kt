package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.base.StockChatLog
import com.guet.liang.stockchat.base.toUserMessage
import com.guet.liang.stockchat.controller.StockDetailMarketRepository
import com.guet.liang.stockchat.controller.StockDetailPredictionRepository
import com.guet.liang.stockchat.model.StockPredictionConfig
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionInput
import com.guet.liang.stockchat.model.StockPredictionResult
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.module.NetworkModule

/** Resolves the active provider and real history before making a replaceable prediction request. */
internal class StockPredictionRepositoryAdapter(
    private val settings: SettingsRepository,
    private val marketRepository: StockDetailMarketRepository,
    private val predictInput: (StockPredictionConfig, StockPredictionInput, (StockPredictionResult) -> Unit) -> Unit,
    private val routeApiKey: String = "",
    private val routeBaseUrl: String = "",
) : StockDetailPredictionRepository {
    constructor(
        settings: SettingsRepository,
        network: NetworkModule,
        market: StockDetailMarketRepository,
        routeApiKey: String,
        routeBaseUrl: String = "",
    ) : this(
        settings,
        market,
        { config, input, callback -> StockPredictionService(network, config).predict(input, callback) },
        routeApiKey,
        routeBaseUrl,
    )

    override fun predict(symbol: String, quote: StockQuote, callback: (StockPredictionResult, List<StockPredictionHistoryPoint>) -> Unit) {
        val config = detailPredictionConfig(settings.loadSnapshot().modelConfiguration, routeApiKey, routeBaseUrl)
        val unavailable =
            when {
                config.apiKey.isBlank() -> "当前 Provider 没有可用 API Key，请先在模型配置页面填写后重试。"
                config.model.isBlank() -> "当前 Provider 没有可用模型，请先选择模型后重试。"
                else -> null
            }
        if (unavailable != null) {
            callback(StockPredictionResult.Unavailable(unavailable), emptyList())
            return
        }
        marketRepository.loadHistoricalPoints(symbol, PREDICTION_HISTORY_COUNT) { points, error ->
            val history = points.orEmpty().map { StockPredictionHistoryPoint(it.date, it.close) }
            when {
                error != null -> callback(StockPredictionResult.Failure(error), emptyList())
                history.isEmpty() -> callback(StockPredictionResult.Unavailable("腾讯行情没有返回足够的历史收盘数据，未生成预测曲线。"), emptyList())
                else -> predictWithHistory(config, StockPredictionInput(quote, history), callback)
            }
        }
    }

    private fun predictWithHistory(
        config: StockPredictionConfig,
        input: StockPredictionInput,
        callback: (StockPredictionResult, List<StockPredictionHistoryPoint>) -> Unit,
    ) {
        try {
            predictInput(config, input) { callback(it, input.history) }
        } catch (exception: RuntimeException) {
            StockChatLog.w("StockPredictionRepository", "prediction request failed", exception)
            callback(StockPredictionResult.Failure(exception.toUserMessage("AI 预测请求失败，请稍后重试；未生成预测曲线。")), emptyList())
        }
    }

    companion object {
        private const val PREDICTION_HISTORY_COUNT = 120
    }
}
