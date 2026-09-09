package com.guet.liang.stockchat.data

internal fun signedMarketValue(rawValue: String, numericChange: Double): String = when {
    rawValue.isEmpty() -> if (numericChange >= 0.0) "+0.00" else "0.00"
    numericChange > 0.0 && !rawValue.startsWith("+") -> "+$rawValue"
    else -> rawValue
}

internal fun tencentMarketLabel(providerSymbol: String, isIndex: Boolean): String {
    val marketName = when {
        providerSymbol.startsWith("sh") -> "沪市"
        providerSymbol.startsWith("sz") -> "深市"
        providerSymbol.startsWith("bj") -> "北交所"
        providerSymbol.startsWith("hk") -> "港股"
        else -> "证券"
    }
    return if (isIndex) "${marketName}指数 · 腾讯行情" else "$marketName · 腾讯行情"
}

internal fun formatTencentTimestamp(timestamp: String): String {
    if (timestamp.length >= FORMATTED_TIMESTAMP_LENGTH &&
        timestamp[YEAR_END] in setOf('/', '-') && timestamp[FORMATTED_MONTH_END] in setOf('/', '-')) {
        return timestamp.take(FORMATTED_TIMESTAMP_LENGTH).replace('/', '-')
    }
    if (timestamp.length < COMPACT_TIMESTAMP_LENGTH) return timestamp.ifBlank { "时间未知" }
    val year = timestamp.take(YEAR_END)
    val pairs = timestamp.drop(YEAR_END).take(COMPACT_TIMESTAMP_LENGTH - YEAR_END).chunked(TIMESTAMP_FIELD_WIDTH)
    val (month, day, hour) = pairs
    val minute = pairs[MINUTE_PAIR_INDEX]
    val second = pairs[SECOND_PAIR_INDEX]
    return "$year-$month-$day $hour:$minute:$second"
}

internal fun parseTencentSearch(rawResponse: String): List<TencentSearchMatch> {
    val payload = rawResponse.substringAfter("=\"", "").substringBeforeLast("\"", "")
    return if (payload.isEmpty()) emptyList() else payload.split('^').mapNotNull(::tencentSearchItem)
}

private fun tencentSearchItem(item: String): TencentSearchMatch? {
    val fields = item.split('~')
    val market = fields.getOrNull(0)?.lowercase().orEmpty()
    val code = fields.getOrNull(1).orEmpty()
    val name = UNICODE_ESCAPE.replace(fields.getOrNull(SEARCH_NAME_FIELD).orEmpty()) {
        it.groupValues[1].toInt(HEX_RADIX).toChar().toString()
    }
    val validCode = when (market) {
        "hk" -> code.length == HONG_KONG_CODE_LENGTH
        "sh", "sz", "bj" -> code.length == MAINLAND_CODE_LENGTH
        else -> false
    }
    return if (!validCode || name.isBlank()) null else {
        TencentSearchMatch(market + code, code, name, fields.getOrNull(SEARCH_TYPE_FIELD).orEmpty())
    }
}

private const val FORMATTED_TIMESTAMP_LENGTH = 19
private const val COMPACT_TIMESTAMP_LENGTH = 14
private const val YEAR_END = 4
private const val FORMATTED_MONTH_END = 7
private const val TIMESTAMP_FIELD_WIDTH = 2
private const val SEARCH_NAME_FIELD = 2
private const val SEARCH_TYPE_FIELD = 4
private const val HONG_KONG_CODE_LENGTH = 5
private const val MAINLAND_CODE_LENGTH = 6
private const val HEX_RADIX = 16
private val UNICODE_ESCAPE = Regex("\\\\u([0-9a-fA-F]{4})")

private const val MINUTE_PAIR_INDEX = 3
private const val SECOND_PAIR_INDEX = 4
