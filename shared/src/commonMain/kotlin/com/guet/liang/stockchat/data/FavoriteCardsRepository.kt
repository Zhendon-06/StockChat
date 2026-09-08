package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal object FavoriteCardsStore {
    private const val STORAGE_KEY = "stock_chat_favorite_cards_v1"

    private var sharedPreferencesModule: SharedPreferencesModule? = null
    private var favorites: List<StockQuote> = emptyList()
    private var loaded = false

    fun initialize(module: SharedPreferencesModule) {
        if (loaded && sharedPreferencesModule === module) {
            return
        }
        sharedPreferencesModule = module
        favorites = read(module)
        loaded = true
    }

    fun all(): List<StockQuote> = favorites

    fun contains(quote: StockQuote): Boolean {
        val quoteKey = quoteKey(quote)
        return favorites.any { favorite -> quoteKey(favorite) == quoteKey }
    }

    fun toggle(quote: StockQuote): Boolean {
        val quoteKey = quoteKey(quote)
        val isAlreadyFavorite = favorites.any { favorite -> quoteKey(favorite) == quoteKey }
        favorites = if (isAlreadyFavorite) {
            favorites.filterNot { favorite -> quoteKey(favorite) == quoteKey }
        } else {
            listOf(quote) + favorites
        }
        persist()
        return !isAlreadyFavorite
    }

    private fun quoteKey(quote: StockQuote): String {
        return providerSymbolForQuote(quote)
            ?: "${quote.marketLabel.trim().lowercase()}:${quote.symbol.trim().uppercase()}"
    }

    private fun read(module: SharedPreferencesModule): List<StockQuote> {
        val serialized = runCatching { module.getString(STORAGE_KEY) }.getOrNull()?.trim()
        if (serialized.isNullOrBlank() || serialized == "null") {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(serialized)
            buildList {
                repeat(array.length()) { index ->
                    array.optJSONObject(index)?.toStockQuote()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.toStockQuote(): StockQuote? {
        val name = optString("name").trim()
        val symbol = optString("symbol").trim()
        if (name.isBlank() || symbol.isBlank()) {
            return null
        }
        val points = optJSONArray("trendPoints")?.let { array ->
            buildList {
                repeat(array.length()) { index ->
                    add(array.optDouble(index, 0.0).toFloat())
                }
            }
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

    private fun persist() {
        val module = sharedPreferencesModule ?: return
        runCatching {
            module.setString(
                STORAGE_KEY,
                JSONArray().apply {
                    favorites.forEach { quote -> put(quote.toJson()) }
                }.toString(),
            )
        }
    }

    private fun StockQuote.toJson(): JSONObject {
        return JSONObject().apply {
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
    }
}
