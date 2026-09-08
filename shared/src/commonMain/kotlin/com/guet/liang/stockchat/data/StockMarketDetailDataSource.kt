package com.guet.liang.stockchat.data

import com.guet.liang.kuiklychart.finance.FinancialAxisLabel
import com.guet.liang.kuiklychart.finance.FinancialChartMath
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** These exchange codes identify indices, whose point values are not tradable share prices. */
internal fun isMarketIndex(symbol: String): Boolean =
    symbol.lowercase().let { it.startsWith("sh000") || it.startsWith("sz399") }

internal data class MarketOrderLevel(val side: String, val level: Int, val price: Float, val volume: Float)
internal enum class MarketPeriod(val label: String, val key: String) {
    INTRADAY("分时", "minute"), FIVE_DAYS("五日", "five"), DAY("日K", "day"), WEEK("周K", "week"), MONTH("月K", "month");
    val isIntraday: Boolean get() = this == INTRADAY || this == FIVE_DAYS
}
internal data class MarketChartData(
    val points: List<FinancialPoint>,
    val previousClose: Float?,
    val sessionSlots: Float = 240f,
    val labels: List<FinancialAxisLabel> = emptyList(),
    val adjustment: String = "",
)
internal sealed class MarketChartResult {
    data class Content(val data: MarketChartData) : MarketChartResult()
    data class Error(val message: String) : MarketChartResult()
}
internal data class CapitalFlowPoint(val date: String, val main: Float, val small: Float, val medium: Float, val large: Float, val superLarge: Float)
internal sealed class CapitalFlowResult {
    data class Content(val points: List<CapitalFlowPoint>, val isDemo: Boolean = false) : CapitalFlowResult()
    data class Error(val message: String) : CapitalFlowResult()
}

internal interface StockMarketDetailDataSource {
    fun loadChart(symbol: String, period: MarketPeriod, previousClose: Float?, callback: (MarketChartResult) -> Unit)
    fun loadCapitalFlow(symbol: String, callback: (CapitalFlowResult) -> Unit)
}

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

internal object StockMarketDetailParser {
    fun chart(response: JSONObject, symbol: String, period: MarketPeriod, previousClose: Float?): MarketChartData {
        val empty = MarketChartData(emptyList(), previousClose)
        if (response.optInt("code", -1) != 0) return empty
        val security = response.optJSONObject("data")?.optJSONObject(symbol) ?: return empty
        if (!period.isIntraday) {
            val adjusted = security.optJSONArray("qfq${period.key}")?.takeIf { it.length() > 0 }
            val rows = adjusted ?: security.optJSONArray(period.key) ?: return empty
            val points = buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONArray(i) ?: continue
                    val values = (1..5).map { row.optString(it).orEmpty().toFloatOrNull() }
                    if (values.any { it == null }) continue
                    val label = row.optString(0).orEmpty().replace('/', '-')
                    if (label.isBlank()) continue
                    val point = FinancialPoint(label, values[0]!!, values[2]!!, values[3]!!, values[1]!!, values[4])
                    if (FinancialChartMath.valid(point)) add(point)
                }
            }.distinctBy { it.label }.sortedBy { it.label }
            return MarketChartData(points, previousClose, adjustment = if (adjusted != null) "前复权" else "不复权")
        }
        val hongKong = symbol.startsWith("hk")
        val sessionLength = if (hongKong) 330f else 240f
        if (period == MarketPeriod.INTRADAY) {
            val data = security.optJSONObject("data") ?: return empty
            val date = data.optString("date").orEmpty()
            return MarketChartData(
                minutes(data.optJSONArray("data"), date, hongKong, isMarketIndex(symbol)), previousClose, sessionLength,
                listOf(FinancialAxisLabel(0f, "09:30"), FinancialAxisLabel(if (hongKong) 150f else 120f,
                    if (hongKong) "12:00/13:00" else "11:30/13:00"), FinancialAxisLabel(sessionLength, if (hongKong) "16:00" else "15:00")),
            )
        }
        val days = security.optJSONArray("data") ?: return empty
        val ordered = (0 until days.length()).mapNotNull { days.optJSONObject(it) }.sortedBy { it.optString("date") }.takeLast(5)
        val points = ordered.flatMapIndexed { index, day ->
            minutes(day.optJSONArray("data"), day.optString("date").orEmpty(), hongKong, isMarketIndex(symbol)).map {
                it.copy(slot = it.slot + index * (sessionLength + 1f))
            }
        }
        return MarketChartData(points, ordered.firstOrNull()?.optString("prec")?.toFloatOrNull() ?: previousClose,
            ordered.size.coerceAtLeast(1) * (sessionLength + 1f), ordered.mapIndexed { index, day ->
                FinancialAxisLabel(index * (sessionLength + 1f) + sessionLength / 2f, shortDate(day.optString("date").orEmpty()))
            })
    }

    /** Tencent minute volume/amount are cumulative; derive incremental volume and true VWAP. */
    fun minutes(rows: JSONArray?, date: String, hongKong: Boolean, isIndex: Boolean = false): List<FinancialPoint> {
        if (rows == null) return emptyList()
        var lastVolume = 0f
        return buildList {
            val ordered = (0 until rows.length()).map { rows.optString(it).orEmpty().trim().split(Regex("\\s+")) }
                .filter { it.size >= 2 }.distinctBy { it[0] }.sortedBy { it[0] }
            for (row in ordered) {
                val time = row[0]
                if (time.length != 4) continue
                val hour = time.take(2).toIntOrNull() ?: continue
                val minute = time.takeLast(2).toIntOrNull() ?: continue
                if (minute !in 0..59) continue
                val total = hour * 60 + minute
                val morningEnd = if (hongKong) 720 else 690
                val close = if (hongKong) 960 else 900
                val slot = when (total) {
                    in 570..morningEnd -> total - 570
                    in 780..close -> morningEnd - 570 + total - 780
                    else -> continue
                }
                val price = row[1].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: continue
                val cumulative = row.getOrNull(2)?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
                val amount = row.getOrNull(3)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                // A missing/reset cumulative counter is unknown, never a negative or giant synthetic bar.
                val volume = cumulative?.takeIf { it >= lastVolume }?.minus(lastVolume)
                if (cumulative != null) lastVolume = cumulative
                val average = if (!isIndex && cumulative != null && cumulative > 0 && amount != null) {
                    (amount / cumulative / if (hongKong) 1 else 100).toFloat().takeIf { it.isFinite() && it > 0 }
                } else null
                add(FinancialPoint("${shortDate(date)} ${time.take(2)}:${time.takeLast(2)}".trim(), price, price, price, price, volume, average, slot.toFloat()))
            }
        }
    }

    fun capitalFlow(response: JSONObject): List<CapitalFlowPoint> {
        if (response.optInt("rc", -1) != 0) return emptyList()
        val rows = response.optJSONObject("data")?.optJSONArray("klines") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val values = rows.optString(index).orEmpty().split(',')
            if (values.size < 6) return@mapNotNull null
            val amounts = values.drop(1).take(5).map { it.toFloatOrNull()?.takeIf(Float::isFinite) }
            if (amounts.any { it == null }) null else CapitalFlowPoint(values[0], amounts[0]!!, amounts[1]!!, amounts[2]!!, amounts[3]!!, amounts[4]!!)
        }.distinctBy { it.date }.sortedBy { it.date }.takeLast(20)
    }

    private fun shortDate(date: String): String = when {
        date.length == 8 -> "${date.substring(4, 6)}-${date.substring(6, 8)}"
        date.length >= 10 -> date.substring(5, 10)
        else -> date
    }
}
