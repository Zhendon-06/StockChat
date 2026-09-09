package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ConversationStockComparisonDataSource
import com.guet.liang.stockchat.data.FavoriteCardsStore
import com.guet.liang.stockchat.data.LocalArtifactGenerator
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.pager.Pager

/** The only artifact composition boundary that creates concrete stores and network adapters. */
internal fun Pager.artifactController(
    onComparisonChanged: (ComparisonDetailUiState) -> Unit = {},
): ArtifactController {
    val market = ConversationStockComparisonDataSource(acquireModule<NetworkModule>(NetworkModule.MODULE_NAME))
    return ArtifactController(
        sessions = ChatHistoryDatabase.repository(),
        tables = ChatHistoryDatabase.artifactRepository(),
        mindMaps = ChatHistoryDatabase.mindMapArtifactRepository(),
        generator = LocalArtifactGenerator,
        market = ArtifactMarketRepository(market::refresh),
        loadFavorites = FavoriteCardsStore::all,
        onComparisonChanged = onComparisonChanged,
    )
}
