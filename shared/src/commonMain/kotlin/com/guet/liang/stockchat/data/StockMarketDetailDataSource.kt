package com.guet.liang.stockchat.data

import com.guet.liang.kuiklychart.finance.FinancialAxisLabel
import com.guet.liang.kuiklychart.finance.FinancialChartMath
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.stockchat.model.CapitalFlowPoint
import com.guet.liang.stockchat.model.CapitalFlowResult
import com.guet.liang.stockchat.model.MarketChartData
import com.guet.liang.stockchat.model.MarketChartResult
import com.guet.liang.stockchat.model.MarketPeriod
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** These exchange codes identify indices, whose point values are not tradable share prices. */
internal fun isMarketIndex(symbol: String): Boolean =
    symbol.lowercase().let { it.startsWith("sh000") || it.startsWith("sz399") }

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal interface StockMarketDetailDataSource {
    fun loadChart(symbol: String, period: MarketPeriod, previousClose: Float?, callback: (MarketChartResult) -> Unit)
    fun loadCapitalFlow(symbol: String, callback: (CapitalFlowResult) -> Unit)
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class RemoteStockMarketDetailDataSource(private val network: NetworkModule) : StockMarketDetailDataSource {
    override fun loadChart(symbol: String, period: MarketPeriod, previousClose: Float?, callback: (MarketChartResult) -> Unit) {
        val url = when (period) {
            MarketPeriod.INTRADAY -> "https://web.ifzq.gtimg.cn/appstock/app/minute/query"
            MarketPeriod.FIVE_DAYS -> "https://web.ifzq.gtimg.cn/appstock/app/day/query"
            else -> "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
        }
        val params = JSONObject().apply {
            if (period.isIntraday) put("code", symbol) else put("param", "$symbol,${period.key},,,240,qfq")
        }
        network.requestGet(url, params) { data, success, _, response ->
            if (!success || response.statusCode?.let { it !in 200..299 } == true) {
                callback(MarketChartResult.Error("${period.label}数据加载失败，点击重试"))
            } else {
                val chart = StockMarketDetailParser.chart(data, symbol, period, previousClose)
                callback(MarketChartResult.Content(chart))
            }
        }
    }

    override fun loadCapitalFlow(symbol: String, callback: (CapitalFlowResult) -> Unit) {
        if (!(symbol.startsWith("sh6") || symbol.startsWith("sz0") || symbol.startsWith("sz3"))) {
            callback(CapitalFlowResult.Error("该市场暂未提供个股资金流向数据")); return
        }
        val params = JSONObject().apply {
            put("secid", "${if (symbol.startsWith("sh")) 1 else 0}.${symbol.drop(2)}")
            put("lmt", "20"); put("klt", "101")
            put("fields1", "f1,f2,f3,f7"); put("fields2", "f51,f52,f53,f54,f55,f56")
        }
        network.requestGet("https://push2his.eastmoney.com/api/qt/stock/fflow/daykline/get", params) { data, success, _, response ->
            if (!success || response.statusCode?.let { it !in 200..299 } == true) {
                callback(CapitalFlowResult.Error("资金数据暂不可用，点击重试"))
            } else callback(CapitalFlowResult.Content(StockMarketDetailParser.capitalFlow(data)))
        }
    }
}
