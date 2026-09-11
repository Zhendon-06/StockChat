package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.data.FavoriteCardsRepositoryAdapter
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.StockDetailShareRepositoryAdapter
import com.guet.liang.stockchat.data.StockPredictionRepositoryAdapter
import com.guet.liang.stockchat.data.TencentMarketDataService
import com.guet.liang.stockchat.data.TencentStockDetailMarketRepository
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.pager.Pager

/** Assembles platform bridges once while keeping the detail page free of service implementations. */
internal fun Pager.stockDetailController(
    onMarketStateChanged: (StockDetailControllerState) -> Unit,
    onPredictionStateChanged: (StockDetailPredictionControllerState) -> Unit,
): StockDetailController {
    val network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    val market = TencentStockDetailMarketRepository(TencentMarketDataService(network))
    return StockDetailController(
        marketRepository = market,
        predictionRepository =
            StockPredictionRepositoryAdapter(
                StockChatSettingsStore.repository,
                network,
                market,
                pageData.params.optString("aiProxyToken").trim().ifBlank { pageData.params.optString("qwenApiKey") },
                pageData.params.optString("aiProxyBaseUrl"),
            ),
        favoriteRepository = FavoriteCardsRepositoryAdapter(),
        shareRepository =
            StockDetailShareRepositoryAdapter(StockChatSettingsStore.repository, acquireModule<ShareModule>(ShareModule.MODULE_NAME)),
        onMarketStateChanged = onMarketStateChanged,
        onPredictionStateChanged = onPredictionStateChanged,
    )
}
