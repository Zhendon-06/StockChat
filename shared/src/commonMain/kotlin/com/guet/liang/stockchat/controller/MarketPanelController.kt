package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.data.CapitalFlowDemoData
import com.guet.liang.stockchat.data.StockMarketDetailDataSource
import com.guet.liang.stockchat.data.marketDemoConclusion
import com.guet.liang.stockchat.model.CapitalFlowResult
import com.guet.liang.stockchat.model.ChartConclusion
import com.guet.liang.stockchat.model.MarketChartData
import com.guet.liang.stockchat.model.MarketChartResult
import com.guet.liang.stockchat.model.MarketPeriod
import com.guet.liang.stockchat.model.TencentMarketSnapshot

/** Owns period caching and request generations for the market terminal. */
internal class MarketPanelController(
    private val source: StockMarketDetailDataSource,
    private val snapshot: TencentMarketSnapshot,
    private val onChartChanged: (MarketPeriod, MarketChartResult?) -> Unit,
    private val onCapitalChanged: (CapitalFlowResult?, MarketChartData?) -> Unit,
) {
    private val cache = mutableMapOf<MarketPeriod, MarketChartData>()
    private var chartGeneration = 0
    private var capitalGeneration = 0
    private var disposed = false

    val isIndex: Boolean = snapshot.providerSymbol.lowercase().let { it.startsWith("sh000") || it.startsWith("sz399") }
    val demoDistribution get() = CapitalFlowDemoData.distribution

    fun loadChart(period: MarketPeriod) {
        val generation = ++chartGeneration
        val cached = cache[period]
        if (cached != null) {
            onChartChanged(period, MarketChartResult.Content(cached))
            return
        }
        onChartChanged(period, null)
        source.loadChart(snapshot.providerSymbol, period, snapshot.previousClose.toFloatOrNull()) { result ->
            if (!disposed && generation == chartGeneration) {
                if (result is MarketChartResult.Content && result.data.points.isNotEmpty()) cache[period] = result.data
                onChartChanged(period, result)
            }
        }
    }

    fun loadCapital() {
        val generation = ++capitalGeneration
        onCapitalChanged(null, null)
        source.loadCapitalFlow(snapshot.providerSymbol) { result ->
            if (!disposed && generation == capitalGeneration) {
                onCapitalChanged(result, null)
                loadComparisonPrices(result, generation)
            }
        }
    }

    private fun loadComparisonPrices(result: CapitalFlowResult, generation: Int) {
        if (result !is CapitalFlowResult.Content || result.points.isEmpty()) return
        source.loadChart(snapshot.providerSymbol, MarketPeriod.DAY, snapshot.previousClose.toFloatOrNull()) { prices ->
            if (!disposed && generation == capitalGeneration && prices is MarketChartResult.Content) {
                onCapitalChanged(result, prices.data)
            }
        }
    }

    fun showCapitalDemo() {
        capitalGeneration++
        onCapitalChanged(CapitalFlowResult.Content(CapitalFlowDemoData.points, isDemo = true), null)
    }

    fun conclusion(period: MarketPeriod, data: MarketChartData, selectedIndex: Int?): ChartConclusion? =
        marketDemoConclusion(snapshot.quote.symbol, period, snapshot.quote.updatedAt, data.points, selectedIndex)

    fun dispose() {
        disposed = true
        chartGeneration++
        capitalGeneration++
    }
}
