package com.guet.liang.stockchat.controller

import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.stockchat.data.StockMarketDetailDataSource
import com.guet.liang.stockchat.model.CapitalFlowResult
import com.guet.liang.stockchat.model.MarketChartData
import com.guet.liang.stockchat.model.MarketChartResult
import com.guet.liang.stockchat.model.MarketPeriod
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentMarketSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketPanelControllerTest {
    @Test
    fun periodSwitchIgnoresThePreviousRequest() {
        val harness = Harness()
        harness.controller.loadChart(MarketPeriod.DAY)
        harness.controller.loadChart(MarketPeriod.WEEK)
        harness.source.chartRequests.first().invoke(MarketChartResult.Error("stale"))
        assertNull(harness.chartResult)
        harness.source.chartRequests.last().invoke(harness.content)
        assertEquals(MarketPeriod.WEEK, harness.period)
        assertEquals(harness.content, harness.chartResult)
    }

    @Test
    fun revisitingACompletedPeriodUsesItsCache() {
        val harness = Harness()
        harness.controller.loadChart(MarketPeriod.DAY)
        harness.source.chartRequests.single().invoke(harness.content)
        harness.controller.loadChart(MarketPeriod.DAY)
        assertEquals(1, harness.source.chartRequests.size)
        assertEquals(harness.content, harness.chartResult)
    }

    @Test
    fun disposalPreventsLateResponsesFromUpdatingTheView() {
        val harness = Harness()
        harness.controller.loadChart(MarketPeriod.DAY)
        harness.controller.loadCapital()
        harness.controller.dispose()
        harness.source.chartRequests.single().invoke(harness.content)
        harness.source.capitalRequests.single().invoke(CapitalFlowResult.Error("late"))
        assertNull(harness.chartResult)
        assertNull(harness.capitalResult)
    }

    @Test
    fun demoSelectionCannotBeOverwrittenByAnInflightCapitalRequest() {
        val harness = Harness()
        harness.controller.loadCapital()
        harness.controller.showCapitalDemo()
        val demo = assertIs<CapitalFlowResult.Content>(harness.capitalResult)
        assertTrue(demo.isDemo)
        harness.source.capitalRequests.single().invoke(CapitalFlowResult.Error("stale"))
        assertEquals(demo, harness.capitalResult)
    }

    private class Harness {
        val source = FakeSource()
        var period: MarketPeriod? = null
        var chartResult: MarketChartResult? = null
        var capitalResult: CapitalFlowResult? = null
        val content = MarketChartResult.Content(
            MarketChartData(listOf(FinancialPoint("2026-09-09", 1f, 1f, 1f, 1f)), 1f),
        )
        val controller = MarketPanelController(
            source, snapshot(),
            onChartChanged = { requested, result -> period = requested; chartResult = result },
            onCapitalChanged = { result, _ -> capitalResult = result },
        )

        private fun snapshot() = TencentMarketSnapshot(
            providerSymbol = "sh600000",
            quote = StockQuote("浦发银行", "600000", "沪A", "10", "+1", "+1%", "now", true, emptyList(), "", ""),
            previousClose = "9", open = "9", high = "10", low = "9", volume = "1", volumeUnit = "手",
            amount = "1", amountUnit = "万", turnoverRate = "", priceEarningsRatio = "", amplitude = "",
        )
    }

    private class FakeSource : StockMarketDetailDataSource {
        val chartRequests = mutableListOf<(MarketChartResult) -> Unit>()
        val capitalRequests = mutableListOf<(CapitalFlowResult) -> Unit>()

        override fun loadChart(symbol: String, period: MarketPeriod, previousClose: Float?, callback: (MarketChartResult) -> Unit) {
            chartRequests.add(callback)
        }

        override fun loadCapitalFlow(symbol: String, callback: (CapitalFlowResult) -> Unit) {
            capitalRequests.add(callback)
        }
    }
}
