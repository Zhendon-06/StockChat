package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskMapCalculatorTest {
    private companion object {
        const val EXPECTED_COUNT = 3
    }

    @Test
    fun summarizesDirectionAndLargestMove() {
        val snapshot = buildRiskMapSnapshot(
            listOf(
                quote("甲", "+2.10%", true),
                quote("乙", "-3.40%", false),
                quote("丙", "0.00%", false),
            )
        )

        assertEquals(EXPECTED_COUNT, snapshot.total)
        assertEquals(1, snapshot.rising)
        assertEquals(1, snapshot.falling)
        assertEquals(1, snapshot.unchanged)
        assertEquals("乙", snapshot.largestMove?.name)
        assertTrue(snapshot.concentrationLabel == "涨跌分布分散")
    }

    @Test
    fun emptyFavoritesProduceGuidance() {
        val snapshot = buildRiskMapSnapshot(emptyList())

        assertEquals(0, snapshot.total)
        assertEquals("暂无自选数据", snapshot.concentrationLabel)
        assertTrue(snapshot.headline.contains("收藏"))
    }

    private fun quote(name: String, changePercent: String, positive: Boolean) = StockQuote(
        name = name,
        symbol = name,
        marketLabel = "测试",
        price = "10.00",
        change = changePercent,
        changePercent = changePercent,
        updatedAt = "now",
        isPositive = positive,
        trendPoints = listOf(1f, 2f),
        summary = "",
        aiInsight = "",
    )
}
