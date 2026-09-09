package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.base.StockChatLog
import com.guet.liang.stockchat.base.toUserMessage
import com.guet.liang.stockchat.model.MarketDataResult
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentHistoricalPoint
import com.guet.liang.stockchat.model.TencentMarketSnapshot

/** Data boundary used by the detail controller; UI and tests can provide platform-specific adapters. */
internal interface StockDetailMarketRepository {
    fun load(symbol: String, callback: (MarketDataResult) -> Unit)

    fun loadHistoricalPoints(symbol: String, count: Int, callback: (List<TencentHistoricalPoint>?, String?) -> Unit)
}

/** Prediction boundary kept separate from market loading so either service can be replaced independently. */
internal interface StockDetailPredictionRepository {
    fun predict(symbol: String, quote: StockQuote, callback: (StockPredictionResult, List<StockPredictionHistoryPoint>) -> Unit)
}

/** Favorite membership stored independently from market loading. */
internal interface StockDetailFavoriteRepository {
    fun contains(quote: StockQuote): Boolean

    fun toggle(quote: StockQuote): Boolean
}

/** Persists and dispatches a share, rolling back failed native delivery. */
internal interface StockDetailShareRepository {
    fun share(quote: StockQuote, callback: (ShareResult) -> Unit)
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class StockDetailControllerState {
    data object Loading : StockDetailControllerState()

    data class Content(val snapshot: TencentMarketSnapshot) : StockDetailControllerState()

    data object Empty : StockDetailControllerState()

    data class Error(val message: String) : StockDetailControllerState()
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class StockDetailPredictionControllerState {
    data object NotRequested : StockDetailPredictionControllerState()

    data object Loading : StockDetailPredictionControllerState()

    data class Content(val prediction: StockPrediction, val history: List<StockPredictionHistoryPoint> = emptyList()) :
        StockDetailPredictionControllerState()

    data class Unavailable(val message: String) : StockDetailPredictionControllerState()

    data class Error(val message: String) : StockDetailPredictionControllerState()
}

/** Coordinates detail-page requests while keeping request invalidation and error mapping out of Kuikly DSL. */
internal class StockDetailController(
    private val marketRepository: StockDetailMarketRepository,
    private val predictionRepository: StockDetailPredictionRepository,
    private val favoriteRepository: StockDetailFavoriteRepository? = null,
    private val shareRepository: StockDetailShareRepository? = null,
    private val onMarketStateChanged: (StockDetailControllerState) -> Unit = {},
    private val onPredictionStateChanged: (StockDetailPredictionControllerState) -> Unit = {},
) {
    var marketState: StockDetailControllerState = StockDetailControllerState.Loading
        private set

    var predictionState: StockDetailPredictionControllerState = StockDetailPredictionControllerState.NotRequested
        private set

    private var requestToken = 0

    fun load(symbol: String) {
        val token = ++requestToken
        publishPrediction(StockDetailPredictionControllerState.NotRequested)
        publishMarket(StockDetailControllerState.Loading)
        marketRepository.load(symbol) { result ->
            if (token != requestToken) return@load
            publishMarket(
                when (result) {
                    is MarketDataResult.Success ->
                        result.snapshots.firstOrNull()?.let(StockDetailControllerState::Content) ?: StockDetailControllerState.Empty
                    MarketDataResult.Empty -> StockDetailControllerState.Empty
                    is MarketDataResult.Failure -> StockDetailControllerState.Error(result.message)
                }
            )
        }
    }

    fun requestPrediction(symbol: String, quote: StockQuote) {
        if (predictionState is StockDetailPredictionControllerState.Loading) return
        val token = requestToken
        publishPrediction(StockDetailPredictionControllerState.Loading)
        runCatching {
                predictionRepository.predict(symbol, quote) { result, history ->
                    if (token != requestToken) return@predict
                    publishPrediction(
                        when (result) {
                            is StockPredictionResult.Success -> StockDetailPredictionControllerState.Content(result.prediction, history)
                            is StockPredictionResult.Unavailable -> StockDetailPredictionControllerState.Unavailable(result.message)
                            is StockPredictionResult.Failure -> StockDetailPredictionControllerState.Error(result.message)
                        }
                    )
                }
            }
            .onFailure { throwable ->
                StockChatLog.w("StockDetailController", "prediction request failed", throwable)
                publishPrediction(StockDetailPredictionControllerState.Error(throwable.toUserMessage("AI 预测请求失败，请稍后重试；未生成预测曲线。")))
            }
    }

    fun isFavorite(quote: StockQuote): Boolean = favoriteRepository?.contains(quote) == true

    fun toggleFavorite(quote: StockQuote): Boolean = favoriteRepository?.toggle(quote) ?: false

    fun shareSnapshot(quote: StockQuote, callback: (ShareResult) -> Unit) {
        shareRepository?.share(quote, callback)
    }

    fun invalidate() {
        requestToken += 1
    }

    private fun publishMarket(next: StockDetailControllerState) {
        marketState = next
        onMarketStateChanged(next)
    }

    private fun publishPrediction(next: StockDetailPredictionControllerState) {
        predictionState = next
        onPredictionStateChanged(next)
    }
}
