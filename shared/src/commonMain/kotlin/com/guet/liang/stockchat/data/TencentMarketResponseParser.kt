package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.TencentHistoricalPoint
import com.guet.liang.stockchat.model.TencentMarketSnapshot
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Decodes Tencent responses while preserving empty and malformed-response fallbacks. */
internal object TencentMarketResponseParser {
    fun parseSnapshot(response: JSONObject, providerSymbol: String): TencentMarketSnapshot? {
        val securityData = tencentSecurityData(response, providerSymbol) ?: return null
        val quoteData = securityData.optJSONObject("qt")?.optJSONArray(providerSymbol) ?: return null
        val fields = TencentQuoteFields(quoteData, providerSymbol)
        if (!fields.validIdentity) return null
        return TencentMarketSnapshot(
            providerSymbol = providerSymbol,
            quote = fields.quote(tencentKlinePoints(securityData)),
            previousClose = fields.previousClose,
            open = fields.open,
            high = fields.high,
            low = fields.low,
            volume = fields.volume,
            volumeUnit = fields.volumeUnit,
            amount = fields.amount,
            amountUnit = fields.amountUnit,
            turnoverRate = fields.turnoverRate,
            priceEarningsRatio = fields.priceEarningsRatio,
            amplitude = fields.amplitude,
            dailyCandles = tencentKlineCandles(securityData),
            orderBook = fields.orderBook(),
            totalMarketValue = fields.totalMarketValue,
            floatMarketValue = fields.floatMarketValue,
            priceBookRatio = fields.priceBookRatio,
            volumeRatio = fields.volumeRatio,
        )
    }

    fun parseMinutePoints(response: JSONObject, providerSymbol: String): List<Float> {
        val minuteData = tencentSecurityData(response, providerSymbol)
            ?.optJSONObject("data")?.optJSONArray("data") ?: return emptyList()
        val points = (0 until minuteData.length()).mapNotNull { index ->
            minuteData.optString(index).orEmpty().trim().split(Regex("\\s+")).getOrNull(1)?.toFloatOrNull()
        }
        return sampleMarketPoints(points, MAX_CHART_POINTS)
    }

    fun parseHistoricalPoints(
        response: JSONObject,
        providerSymbol: String,
        maxCount: Int = DEFAULT_HISTORICAL_POINT_COUNT,
    ): List<TencentHistoricalPoint> {
        if (maxCount <= 0) return emptyList()
        val securityData = tencentSecurityData(response, providerSymbol) ?: return emptyList()
        return sampleMarketPoints(tencentHistoricalPoints(securityData), maxCount)
    }

    fun parseSearch(rawResponse: String): List<TencentSearchMatch> = parseTencentSearch(rawResponse)

    private const val MAX_CHART_POINTS = 80
}

internal fun tencentSecurityData(response: JSONObject, symbol: String): JSONObject? =
    if (response.optInt("code", -1) == 0) response.optJSONObject("data")?.optJSONObject(symbol) else null
