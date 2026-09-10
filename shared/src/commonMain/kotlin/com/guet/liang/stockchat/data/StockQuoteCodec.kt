package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// StockQuote 与 JSON 互转：收藏持久化与页面路由参数共用同一份字段约定。

internal fun StockQuote.toJson(): JSONObject =
    JSONObject().apply {
        put("name", name)
        put("symbol", symbol)
        put("marketLabel", marketLabel)
        put("price", price)
        put("change", change)
        put("changePercent", changePercent)
        put("updatedAt", updatedAt)
        put("isPositive", isPositive)
        put("trendPoints", JSONArray().apply { trendPoints.forEach { point -> put(point.toDouble()) } })
        put("summary", summary)
        put("aiInsight", aiInsight)
    }

/** Returns null when the payload lacks the name or symbol that identify a quote. */
internal fun JSONObject.toStockQuoteOrNull(): StockQuote? {
    val name = optString("name").trim()
    val symbol = optString("symbol").trim()
    if (name.isBlank() || symbol.isBlank()) {
        return null
    }
    val points =
        optJSONArray("trendPoints")?.let { array ->
            buildList { repeat(array.length()) { index -> add(array.optDouble(index, 0.0).toFloat()) } }
        }.orEmpty()
    return StockQuote(
        name = name,
        symbol = symbol,
        marketLabel = optString("marketLabel"),
        price = optString("price"),
        change = optString("change"),
        changePercent = optString("changePercent"),
        updatedAt = optString("updatedAt"),
        isPositive = optBoolean("isPositive", false),
        trendPoints = points,
        summary = optString("summary"),
        aiInsight = optString("aiInsight"),
    )
}
