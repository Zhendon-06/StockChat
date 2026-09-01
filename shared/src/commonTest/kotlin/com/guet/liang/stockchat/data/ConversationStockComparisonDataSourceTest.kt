package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ConversationStockDataSource
import com.guet.liang.stockchat.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConversationStockComparisonDataSourceTest {
    @Test
    fun refreshesAllDistinctSymbolsAndReturnsPartialResult() {
        val comparison = ConversationStockComparisonGenerator.generate(
            title = "刷新",
            messages = listOf(
                ChatMessage(
                    id = "question",
                    role = ChatRole.USER,
                    blocks = listOf(
                        AnswerBlock.Markdown(
                            source = "比较贵州茅台和宁德时代",
                            fallbackText = "",
                        )
                    ),
                )
            ),
        )
        val requestedSymbols = mutableListOf<String>()
        val dataSource = ConversationStockComparisonDataSource { providerSymbol, callback ->
            requestedSymbols += providerSymbol
            if (providerSymbol == "sh600519") {
                callback(MarketDataResult.Success(listOf(marketSnapshot())))
            } else {
                callback(MarketDataResult.Failure("演示失败"))
            }
        }
        var callbackResult: ConversationStockComparisonRefreshResult? = null

        dataSource.refresh(comparison) { callbackResult = it }

        val result = assertNotNull(callbackResult)
        assertEquals(listOf("sh600519", "sz300750"), requestedSymbols)
        assertEquals(2, result.requestedCount)
        assertEquals(1, result.refreshedCount)
        assertEquals(1, result.unavailableCount)
        assertEquals(listOf("sz300750"), result.unavailableProviderSymbols)
        assertTrue(result.isPartial)
        assertTrue(result.message.contains("1/2"))
        assertEquals(
            ConversationStockDataSource.FRESH_MARKET,
            result.snapshot.rows.single { it.providerSymbol == "sh600519" }.dataSource,
        )
        assertEquals(
            ConversationStockDataSource.MENTION_ONLY,
            result.snapshot.rows.single { it.providerSymbol == "sz300750" }.dataSource,
        )
    }

    private fun marketSnapshot(): TencentMarketSnapshot {
        return TencentMarketSnapshot(
            providerSymbol = "sh600519",
            quote = StockQuote(
                name = "贵州茅台",
                symbol = "600519",
                marketLabel = "沪市 · 腾讯行情",
                price = "1,500.00",
                change = "+10.00",
                changePercent = "+0.67%",
                updatedAt = "腾讯行情 · 2026-08-31 15:00:00",
                isPositive = true,
                trendPoints = listOf(1490f, 1500f),
                summary = "演示行情",
                aiInsight = "演示解读",
            ),
            previousClose = "1,490.00",
            open = "1,498.00",
            high = "1,505.00",
            low = "1,488.00",
            volume = "12000",
            volumeUnit = "手",
            amount = "180000",
            amountUnit = "万元",
            turnoverRate = "0.55",
            priceEarningsRatio = "18.20",
            amplitude = "1.14",
        )
    }
}
