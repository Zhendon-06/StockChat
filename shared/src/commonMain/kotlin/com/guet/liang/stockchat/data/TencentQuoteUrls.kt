package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal const val MIN_HISTORICAL_POINT_COUNT = 1
internal const val MAX_HISTORICAL_POINT_COUNT = 240
internal const val DEFAULT_HISTORICAL_POINT_COUNT = 120

internal fun historicalRequestParams(providerSymbol: String, count: Int): JSONObject = JSONObject().apply {
    put("param", "$providerSymbol,day,,,$count,qfq")
}

internal fun isSuccessfulMarketResponse(success: Boolean, statusCode: Int?): Boolean =
    success && (statusCode == null || statusCode in HTTP_SUCCESS_CODES)


internal const val TENCENT_KLINE_URL = "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get"
internal const val TENCENT_MINUTE_URL = "https://web.ifzq.gtimg.cn/appstock/app/minute/query"
private val HTTP_SUCCESS_CODES = 200..299
private const val HONG_KONG_CODE_LENGTH = 5

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class TencentSearchMatch(
    val providerSymbol: String,
    val code: String,
    val name: String,
    val type: String,
)

internal fun normalizeProviderSymbol(symbol: String): String? {
    val normalized = symbol.trim().lowercase().replace(" ", "")
    val hongKongPrefix = Regex("^hk(\\d{1,5})$").matchEntire(normalized)
    val mainlandSuffix = Regex("^(\\d{6})[.]?(sh|sz|bj)$").matchEntire(normalized)
    val hongKongSuffix = Regex("^(\\d{1,5})[.]?hk$").matchEntire(normalized)
    return when {
        Regex("^(sh|sz|bj)\\d{6}$").matches(normalized) -> normalized
        hongKongPrefix != null -> "hk${hongKongPrefix.groupValues[1].padStart(HONG_KONG_CODE_LENGTH, '0')}"
        mainlandSuffix != null -> mainlandSuffix.groupValues[2] + mainlandSuffix.groupValues[1]
        hongKongSuffix != null -> "hk${hongKongSuffix.groupValues[1].padStart(HONG_KONG_CODE_LENGTH, '0')}"
        !Regex("^\\d{6}$").matches(normalized) -> null
        else -> when (normalized.first()) {
            '4', '8' -> "bj$normalized"
            '5', '6', '9' -> "sh$normalized"
            else -> "sz$normalized"
        }
    }
}

internal fun providerSymbolForQuote(quote: StockQuote): String? {
    val market = when {
        quote.marketLabel.startsWith("沪市") -> "sh"
        quote.marketLabel.startsWith("深市") -> "sz"
        quote.marketLabel.startsWith("北交所") -> "bj"
        quote.marketLabel.startsWith("港股") -> "hk"
        else -> null
    }
    return market?.let { "$it${quote.symbol}" } ?: normalizeProviderSymbol(quote.symbol)
}
