package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.StockChatShareContentBuilder
import com.guet.liang.stockchat.controller.StockDetailFavoriteRepository
import com.guet.liang.stockchat.controller.StockDetailMarketRepository
import com.guet.liang.stockchat.controller.StockDetailShareRepository
import com.guet.liang.stockchat.model.HistoricalPointsResult
import com.guet.liang.stockchat.model.MarketDataResult
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TencentHistoricalPoint

/** Converts Tencent detail and historical responses at the controller boundary. */
internal class TencentStockDetailMarketRepository(
    private val loadDetail: (String, (MarketDataResult) -> Unit) -> Unit,
    private val loadHistory: (String, Int, (HistoricalPointsResult) -> Unit) -> Unit,
) : StockDetailMarketRepository {
    constructor(service: TencentMarketDataService) : this(service::loadDetail, service::loadHistoricalPoints)

    override fun load(symbol: String, callback: (MarketDataResult) -> Unit) = loadDetail(symbol, callback)

    override fun loadHistoricalPoints(symbol: String, count: Int, callback: (List<TencentHistoricalPoint>?, String?) -> Unit) {
        loadHistory(symbol, count) { result ->
            when (result) {
                is HistoricalPointsResult.Success -> callback(result.points, null)
                HistoricalPointsResult.Empty -> callback(emptyList(), null)
                is HistoricalPointsResult.Failure -> callback(null, result.message)
            }
        }
    }
}

/** Shares the existing favorites storage with the detail controller. */
internal class FavoriteCardsRepositoryAdapter : StockDetailFavoriteRepository {
    override fun contains(quote: StockQuote): Boolean = FavoriteCardsStore.contains(quote)

    override fun toggle(quote: StockQuote): Boolean = FavoriteCardsStore.toggle(quote)
}

/** Records shares and rolls back a failed native dispatch. */
internal class StockDetailShareRepositoryAdapter(private val settings: SettingsRepository, private val shareModule: ShareModule) :
    StockDetailShareRepository {
    override fun share(quote: StockQuote, callback: (ShareResult) -> Unit) {
        val content = StockChatShareContentBuilder.fromQuote(quote)
        val record =
            settings.recordSharedChat(
                sessionId = "stock-detail-${quote.symbol}",
                question = "${quote.name}（${quote.symbol}）行情详情",
                content = content,
            )
        shareModule.share(content) { result ->
            if (result is ShareResult.Failure) settings.deleteSharedChat(record.id)
            callback(result)
        }
    }
}
