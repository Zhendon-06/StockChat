package com.guet.liang.stockchat.data

import com.guet.liang.kuiklychart.finance.FinancialAxisLabel
import com.guet.liang.kuiklychart.finance.FinancialChartMath
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.stockchat.model.CapitalFlowPoint
import com.guet.liang.stockchat.model.MarketChartData
import com.guet.liang.stockchat.model.MarketPeriod
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Parses candle, intraday and capital-flow payloads into shared market models. */
internal object StockMarketDetailParser {
    fun chart(response: JSONObject, symbol: String, period: MarketPeriod, previousClose: Float?): MarketChartData {
        val security = tencentSecurityData(response, symbol) ?: return MarketChartData(emptyList(), previousClose)
        return when {
            !period.isIntraday -> candleChart(security, period, previousClose)
            period == MarketPeriod.INTRADAY -> intradayChart(security, symbol, previousClose)
            else -> fiveDayChart(security, symbol, previousClose)
        }
    }

    private fun candleChart(security: JSONObject, period: MarketPeriod, previousClose: Float?): MarketChartData {
        val adjusted = security.optJSONArray("qfq${period.key}")?.takeIf { it.length() > 0 }
        val rows = adjusted ?: security.optJSONArray(period.key) ?: return MarketChartData(emptyList(), previousClose)
        val points = (0 until rows.length()).mapNotNull { index -> rows.optJSONArray(index)?.let(::candle) }
            .distinctBy { it.label }.sortedBy { it.label }
        return MarketChartData(points, previousClose, adjustment = if (adjusted != null) "前复权" else "不复权")
    }

    private fun candle(row: JSONArray): FinancialPoint? {
        val values = (1..CANDLE_VALUE_COUNT).map { row.optString(it).orEmpty().toFloatOrNull() }
        val label = row.optString(0).orEmpty().replace('/', '-')
        if (values.any { it == null } || label.isBlank()) return null
        val (open, close, high) = values.filterNotNull()
        val low = checkNotNull(values[LOW_VALUE_INDEX])
        val volume = checkNotNull(values[VOLUME_VALUE_INDEX])
        return FinancialPoint(label, open, high, low, close, volume).takeIf(FinancialChartMath::valid)
    }

    private fun intradayChart(security: JSONObject, symbol: String, previousClose: Float?): MarketChartData {
        val data = security.optJSONObject("data") ?: return MarketChartData(emptyList(), previousClose)
        val session = stockMarketSession(symbol.startsWith("hk"))
        return MarketChartData(
            minutes(data.optJSONArray("data"), data.optString("date").orEmpty(), symbol.startsWith("hk"), isMarketIndex(symbol)),
            previousClose, session.length, session.labels,
        )
    }

    private fun fiveDayChart(security: JSONObject, symbol: String, previousClose: Float?): MarketChartData {
        val days = security.optJSONArray("data") ?: return MarketChartData(emptyList(), previousClose)
        val session = stockMarketSession(symbol.startsWith("hk"))
        val ordered = (0 until days.length()).mapNotNull { days.optJSONObject(it) }
            .sortedBy { it.optString("date") }.takeLast(FIVE_DAY_COUNT)
        val points = ordered.flatMapIndexed { index, day ->
            minutes(day.optJSONArray("data"), day.optString("date").orEmpty(), symbol.startsWith("hk"), isMarketIndex(symbol)).map {
                it.copy(slot = it.slot + index * (session.length + 1f))
            }
        }
        return MarketChartData(
            points, ordered.firstOrNull()?.optString("prec")?.toFloatOrNull() ?: previousClose,
            ordered.size.coerceAtLeast(1) * (session.length + 1f), ordered.mapIndexed { index, day ->
                FinancialAxisLabel(
                    index * (session.length + 1f) + session.length / SESSION_MIDPOINT_DIVISOR,
                    marketShortDate(day.optString("date").orEmpty()),
                )
            },
        )
    }

    /** Tencent counters are cumulative; the minute parser derives incremental volume and VWAP. */
    fun minutes(rows: JSONArray?, date: String, hongKong: Boolean, isIndex: Boolean = false): List<FinancialPoint> =
        parseMarketMinutes(rows, date, hongKong, isIndex)

    fun capitalFlow(response: JSONObject): List<CapitalFlowPoint> {
        if (response.optInt("rc", -1) != 0) return emptyList()
        val rows = response.optJSONObject("data")?.optJSONArray("klines") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index -> capitalFlowPoint(rows.optString(index).orEmpty()) }
            .distinctBy { it.date }.sortedBy { it.date }.takeLast(CAPITAL_FLOW_DAYS)
    }

    private fun capitalFlowPoint(row: String): CapitalFlowPoint? {
        val values = row.split(',')
        if (values.size < CAPITAL_FLOW_FIELD_COUNT) return null
        val amounts = values.drop(1).take(CAPITAL_FLOW_AMOUNT_COUNT).map { it.toFloatOrNull()?.takeIf(Float::isFinite) }
        if (amounts.any { it == null }) return null
        val (main, small, medium) = amounts.filterNotNull()
        val large = checkNotNull(amounts[LARGE_FLOW_INDEX])
        val superLarge = checkNotNull(amounts[SUPER_LARGE_FLOW_INDEX])
        return CapitalFlowPoint(values[0], main, small, medium, large, superLarge)
    }

    private const val CANDLE_VALUE_COUNT = 5
    private const val FIVE_DAY_COUNT = 5
    private const val SESSION_MIDPOINT_DIVISOR = 2f
    private const val CAPITAL_FLOW_FIELD_COUNT = 6
    private const val CAPITAL_FLOW_AMOUNT_COUNT = 5
    private const val CAPITAL_FLOW_DAYS = 20
}

private const val LOW_VALUE_INDEX = 3
private const val VOLUME_VALUE_INDEX = 4

private const val LARGE_FLOW_INDEX = 3
private const val SUPER_LARGE_FLOW_INDEX = 4
