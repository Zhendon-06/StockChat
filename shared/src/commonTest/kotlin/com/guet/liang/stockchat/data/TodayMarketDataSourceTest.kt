package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.TodayMarketResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TodayMarketDataSourceTest {
    @Test
    fun fallsBackToClearlyLabeledDemoSnapshot() {
        val requestedPlans = mutableListOf<SecuritiesQueryPlan>()
        val dataSource = TencentTodayMarketDataSource { plan, callback ->
            requestedPlans += plan
            callback(MarketDataResult.Failure("offline"))
        }
        var receivedResult: TodayMarketResult? = null

        dataSource.load { result -> receivedResult = result }

        assertEquals(2, requestedPlans.size)
        val symbolBatches = requestedPlans.map { plan ->
            plan.targets.map(SecurityTarget::providerSymbol)
        }
        assertTrue(
            symbolBatches.contains(
                listOf("sh000001", "sz399001", "sz399006", "sh000300")
            ),
            "应包含指数批次，实际: $symbolBatches",
        )
        assertTrue(
            symbolBatches.any { it.contains("sh600519") && it.size == 10 },
            "应包含 10 只样本股批次，实际: $symbolBatches",
        )
        assertTrue(requestedPlans.all { !it.needsIntraday })

        val snapshot = assertIs<TodayMarketResult.Success>(receivedResult).snapshot
        assertEquals(4, snapshot.indices.size)
        assertEquals(4, snapshot.advancingCount + snapshot.decliningCount + snapshot.unchangedCount)
        assertEquals(10, snapshot.sampleStocks.size)
        assertEquals(5, snapshot.sectors.size)
        // 样本股按涨跌降序
        val percents = snapshot.sampleStocks.map {
            it.changePercent.replace("%", "").replace("+", "").toDouble()
        }
        assertEquals(percents.sortedDescending(), percents)
        assertTrue(snapshot.isDemo)
        assertTrue(snapshot.sourceLabel.contains("非实时"))
        assertTrue(snapshot.summary.contains("本地演示数据"))
        assertTrue(snapshot.disclaimer.contains("不构成投资建议"))
    }

    @Test
    fun mergesSuccessfulBatchesWithoutDemoLabel() {
        val dataSource = TencentTodayMarketDataSource { plan, callback ->
            callback(
                MarketDataResult.Success(
                    plan.targets.map { target ->
                        TencentMarketSnapshot(
                            providerSymbol = target.providerSymbol,
                            quote = com.guet.liang.stockchat.model.StockQuote(
                                name = target.displayName,
                                symbol = target.providerSymbol.drop(2),
                                marketLabel = "测试",
                                price = "100.00",
                                change = "+1.00",
                                changePercent = "+1.00%",
                                updatedAt = "2026-09-01 15:00",
                                isPositive = true,
                                trendPoints = listOf(99f, 100f),
                                summary = "",
                                aiInsight = "",
                            ),
                            previousClose = "99.00",
                            open = "99.20",
                            high = "100.50",
                            low = "98.80",
                            volume = "100",
                            volumeUnit = "万手",
                            amount = "10",
                            amountUnit = "亿元",
                            turnoverRate = "1.0%",
                            priceEarningsRatio = "20.0",
                            amplitude = "1.7%",
                        )
                    }
                )
            )
        }
        var receivedResult: TodayMarketResult? = null

        dataSource.load { result -> receivedResult = result }

        val snapshot = assertIs<TodayMarketResult.Success>(receivedResult).snapshot
        assertEquals(false, snapshot.isDemo)
        assertEquals("腾讯证券公开行情", snapshot.sourceLabel)
        assertEquals(4, snapshot.indices.size)
        assertEquals(10, snapshot.sampleStocks.size)
        assertTrue(snapshot.sectors.all { it.changeLabel == "+1.00%" })
    }
}
