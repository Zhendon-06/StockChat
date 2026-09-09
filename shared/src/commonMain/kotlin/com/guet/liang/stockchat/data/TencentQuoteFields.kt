package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.MarketOrderLevel
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray

/** Names the positional fields in Tencent's quote response. */
internal class TencentQuoteFields(private val data: JSONArray, private val symbol: String) {
    val name = field(QuoteField.NAME)
    val code = field(QuoteField.CODE)
    val price = field(QuoteField.PRICE)
    val previousClose = field(QuoteField.PREVIOUS_CLOSE)
    val open = field(QuoteField.OPEN)
    val volume = field(QuoteField.VOLUME)
    val high = field(QuoteField.HIGH)
    val low = field(QuoteField.LOW)
    val amount = field(QuoteField.AMOUNT)
    val turnoverRate = field(QuoteField.TURNOVER_RATE)
    val priceEarningsRatio = field(QuoteField.PRICE_EARNINGS_RATIO)
    val amplitude = field(QuoteField.AMPLITUDE)
    val totalMarketValue = data.optString(QuoteField.TOTAL_MARKET_VALUE.index).orEmpty()
    val floatMarketValue = data.optString(QuoteField.FLOAT_MARKET_VALUE.index).orEmpty()
    val priceBookRatio = data.optString(QuoteField.PRICE_BOOK_RATIO.index).orEmpty()
    val volumeRatio = data.optString(QuoteField.VOLUME_RATIO.index).orEmpty()
    val volumeUnit = if (symbol.startsWith("hk")) "股" else "手"
    val amountUnit = if (symbol.startsWith("hk")) "港元" else "万元"
    val validIdentity: Boolean get() = name.isNotEmpty() && code.isNotEmpty() && price.isNotEmpty()

    fun quote(trendPoints: List<Float>): StockQuote {
        val rawChange = field(QuoteField.CHANGE)
        val numericChange = rawChange.toDoubleOrNull() ?: 0.0
        val change = signedMarketValue(rawChange, numericChange)
        val changePercent = signedMarketValue(field(QuoteField.CHANGE_PERCENT), numericChange) + "%"
        val movement = when {
            numericChange > 0.0 -> "上涨"
            numericChange < 0.0 -> "下跌"
            else -> "持平"
        }
        return StockQuote(
            name = name,
            symbol = code,
            marketLabel = tencentMarketLabel(symbol, isMarketIndex(symbol)),
            price = price,
            change = change,
            changePercent = changePercent,
            updatedAt = "腾讯行情 · ${formatTencentTimestamp(field(QuoteField.UPDATED_AT))}",
            isPositive = numericChange >= 0.0,
            trendPoints = trendPoints.ifEmpty { listOfNotNull(price.toFloatOrNull()) },
            summary = summary(),
            aiInsight = "最新行情快照显示该标的当前${movement}${changePercent}。请结合基本面、估值和风险承受能力综合判断。",
        )
    }

    fun orderBook(): List<MarketOrderLevel> {
        if (isMarketIndex(symbol) || !(symbol.startsWith("sh") || symbol.startsWith("sz"))) return emptyList()
        return buildList {
            for (level in 1..ORDER_BOOK_LEVELS) {
                for ((side, offset) in ORDER_BOOK_SIDES) {
                    orderLevel(side, offset, level)?.let(::add)
                }
            }
        }
    }

    private fun orderLevel(side: String, offset: Int, level: Int): MarketOrderLevel? {
        val index = offset + (level - 1) * ORDER_BOOK_FIELD_COUNT
        val priceValue = data.optString(index).orEmpty().toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }
        val size = data.optString(index + 1).orEmpty().toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 }
        return if (priceValue != null && size != null) MarketOrderLevel(side, level, priceValue, size) else null
    }

    private fun summary(): String = listOf(
        Triple("昨收", previousClose, ""), Triple("今开", open, ""),
        Triple("最高", high, ""), Triple("最低", low, ""),
        Triple("成交量", volume, " $volumeUnit"), Triple("成交额", amount, " $amountUnit"),
    ).filter { it.second.isNotEmpty() }.joinToString("，") { (label, value, unit) -> "$label $value$unit" }

    private fun field(field: QuoteField): String = data.optString(field.index).orEmpty().trim()

    private companion object {
        const val ORDER_BOOK_LEVELS = 5
        const val ORDER_BOOK_FIELD_COUNT = 2
        val ORDER_BOOK_SIDES = listOf("买" to 9, "卖" to 19)
    }
}

/** Tencent's documented quote array positions are data-schema identifiers. */
private enum class QuoteField(val index: Int) {
    NAME(1), CODE(2), PRICE(3), PREVIOUS_CLOSE(4), OPEN(5), VOLUME(6), UPDATED_AT(30),
    CHANGE(31), CHANGE_PERCENT(32), HIGH(33), LOW(34), AMOUNT(37), TURNOVER_RATE(38),
    PRICE_EARNINGS_RATIO(39), AMPLITUDE(43), FLOAT_MARKET_VALUE(44), TOTAL_MARKET_VALUE(45),
    PRICE_BOOK_RATIO(46), VOLUME_RATIO(49),
}
