package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.MarketDataResult
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentHistoricalPoint
import com.guet.liang.stockchat.model.TencentMarketSnapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StockDetailControllerTest {
    private class Favorites : StockDetailFavoriteRepository {
        private var active = false

        override fun contains(quote: StockQuote): Boolean = active

        override fun toggle(quote: StockQuote): Boolean {
            active = !active
            return active
        }
    }

    private class Shares : StockDetailShareRepository {
        override fun share(quote: StockQuote, callback: (ShareResult) -> Unit) = callback(ShareResult.Success)
    }

    private class Market(private val result: MarketDataResult) : StockDetailMarketRepository {
        override fun load(symbol: String, callback: (MarketDataResult) -> Unit) = callback(result)

        override fun loadHistoricalPoints(symbol: String, count: Int, callback: (List<TencentHistoricalPoint>?, String?) -> Unit) =
            callback(emptyList(), null)
    }

    private class DeferredMarket : StockDetailMarketRepository {
        val requests = mutableListOf<(MarketDataResult) -> Unit>()

        override fun load(symbol: String, callback: (MarketDataResult) -> Unit) {
            requests += callback
        }

        override fun loadHistoricalPoints(
            symbol: String,
            count: Int,
            callback: (List<TencentHistoricalPoint>?, String?) -> Unit,
        ) = callback(emptyList(), null)
    }

    private class Prediction(private val result: StockPredictionResult) : StockDetailPredictionRepository {
        override fun predict(
            symbol: String,
            quote: StockQuote,
            callback: (StockPredictionResult, List<StockPredictionHistoryPoint>) -> Unit,
        ) = callback(result, emptyList())
    }

    @Test
    fun successfulMarketLoadPublishesContent() {
        val controller =
            StockDetailController(
                Market(MarketDataResult.Success(listOf(snapshot()))),
                Prediction(StockPredictionResult.Unavailable("n/a")),
            )
        controller.load("sh600000")
        assertIs<StockDetailControllerState.Content>(controller.marketState)
    }

    @Test
    fun emptyMarketLoadPublishesEmpty() {
        val controller = StockDetailController(Market(MarketDataResult.Empty), Prediction(StockPredictionResult.Unavailable("n/a")))
        controller.load("bad")
        assertIs<StockDetailControllerState.Empty>(controller.marketState)
    }

    @Test
    fun failedMarketLoadPublishesError() {
        val controller =
            StockDetailController(Market(MarketDataResult.Failure("offline")), Prediction(StockPredictionResult.Unavailable("n/a")))
        controller.load("sh600000")
        assertIs<StockDetailControllerState.Error>(controller.marketState)
    }

    @Test
    fun staleMarketResponseCannotReplaceTheMostRecentLoad() {
        val market = DeferredMarket()
        val controller = StockDetailController(market, Prediction(StockPredictionResult.Unavailable("n/a")))

        controller.load("sh600000")
        controller.load("sz000001")
        market.requests[0](MarketDataResult.Success(listOf(snapshot())))

        assertIs<StockDetailControllerState.Loading>(controller.marketState)
        market.requests[1](MarketDataResult.Failure("offline"))
        assertIs<StockDetailControllerState.Error>(controller.marketState)
    }

    @Test
    fun invalidPredictionResponsePublishesError() {
        val controller = StockDetailController(Market(MarketDataResult.Empty), Prediction(StockPredictionResult.Failure("invalid")))
        controller.requestPrediction("sh600000", snapshot().quote)
        assertIs<StockDetailPredictionControllerState.Error>(controller.predictionState)
    }

    @Test
    fun favoriteToggleUsesInjectedRepository() {
        val favorites = Favorites()
        val controller =
            StockDetailController(
                Market(MarketDataResult.Empty),
                Prediction(StockPredictionResult.Unavailable("n/a")),
                favoriteRepository = favorites,
            )
        val quote = snapshot().quote
        assertFalse(controller.isFavorite(quote))
        assertTrue(controller.toggleFavorite(quote))
        assertTrue(controller.isFavorite(quote))
    }

    @Test
    fun shareDelegatesToInjectedRepository() {
        var result: ShareResult? = null
        val controller =
            StockDetailController(
                Market(MarketDataResult.Empty),
                Prediction(StockPredictionResult.Unavailable("n/a")),
                shareRepository = Shares(),
            )
        controller.shareSnapshot(snapshot().quote) { result = it }
        assertIs<ShareResult.Success>(result)
    }

    private fun snapshot() =
        TencentMarketSnapshot(
            providerSymbol = "sh600000",
            quote = StockQuote("浦发银行", "600000", "沪A", "10", "+1", "+1%", "now", true, emptyList(), "", ""),
            previousClose = "9",
            open = "9",
            high = "10",
            low = "9",
            volume = "1",
            volumeUnit = "手",
            amount = "1",
            amountUnit = "万",
            turnoverRate = "",
            priceEarningsRatio = "",
            amplitude = "",
        )
}
