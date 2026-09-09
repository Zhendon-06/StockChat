package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.HistoricalPointsResult
import com.guet.liang.stockchat.model.MarketDataResult
import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.StockPredictionResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentHistoricalPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StockDetailRepositoryAdaptersTest {
    @Test
    fun marketAdapterPreservesSymbolsAndHistoryFailures() {
        var requestedSymbol = ""
        val market =
            TencentStockDetailMarketRepository(
                { symbol, callback ->
                    requestedSymbol = symbol
                    callback(MarketDataResult.Empty)
                },
                { _, _, callback -> callback(HistoricalPointsResult.Failure("offline")) },
            )
        market.load("sh600000") { assertIs<MarketDataResult.Empty>(it) }
        assertEquals("sh600000", requestedSymbol)
        market.loadHistoricalPoints("sh600000", 1) { points, error ->
            assertEquals(null, points)
            assertEquals("offline", error)
        }
    }

    @Test
    fun predictionUsesRealHistoryAndReportsAsynchronousPredictorExceptions() {
        val market =
            TencentStockDetailMarketRepository(
                { _, callback -> callback(MarketDataResult.Empty) },
                { symbol, _, callback -> callback(HistoricalPointsResult.Success(symbol, listOf(TencentHistoricalPoint("date", 1f)))) },
            )
        val settings = InMemorySettingsRepository(initialModelConfiguration = ModelConfiguration(provider.id, listOf(provider)))
        val repository =
            StockPredictionRepositoryAdapter(
                settings,
                market,
                { config, input, _ ->
                    assertEquals("model", config.model)
                    assertEquals("date", input.history.single().timestamp)
                    error("provider failed")
                },
            )
        repository.predict("sh600000", quote) { result, history ->
            assertIs<StockPredictionResult.Failure>(result)
            assertEquals(emptyList(), history)
        }
    }

    @Test
    fun disabledProviderCannotUseRouteCredentialFallback() {
        val config = detailPredictionConfig(ModelConfiguration(provider.id, listOf(provider.copy(isEnabled = false))), "route-test")
        assertEquals("", config.apiKey)
    }

    companion object {
        private val provider =
            ModelProviderConfig(
                "provider",
                ModelProviderKind.ALIYUN,
                "Demo",
                "https://example.com",
                "test-key",
                listOf(ModelOption("model", "Model", "")),
                "model",
            )
        private val quote = StockQuote("名称", "600000", "沪A", "1", "+1", "+1%", "time", true, emptyList(), "", "")
    }
}
