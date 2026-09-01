package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.tencent.kuikly.core.module.NetworkModule

internal data class ConversationStockComparisonRefreshResult(
    val snapshot: ConversationStockComparisonSnapshot,
    val requestedCount: Int,
    val refreshedCount: Int,
    val unavailableCount: Int,
    val unavailableProviderSymbols: List<String>,
    val message: String,
) {
    val isComplete: Boolean
        get() = requestedCount > 0 && unavailableCount == 0

    val isPartial: Boolean
        get() = refreshedCount > 0 && unavailableCount > 0
}

internal class ConversationStockComparisonDataSource(
    private val loadDetail: (String, (MarketDataResult) -> Unit) -> Unit,
) {
    constructor(marketDataService: TencentMarketDataService) : this(marketDataService::loadDetail)

    constructor(networkModule: NetworkModule) : this(TencentMarketDataService(networkModule))

    fun refresh(
        snapshot: ConversationStockComparisonSnapshot,
        callback: (ConversationStockComparisonRefreshResult) -> Unit,
    ) {
        val providerSymbols = snapshot.providerSymbols
        if (providerSymbols.isEmpty()) {
            callback(
                ConversationStockComparisonRefreshResult(
                    snapshot = snapshot,
                    requestedCount = 0,
                    refreshedCount = 0,
                    unavailableCount = 0,
                    unavailableProviderSymbols = emptyList(),
                    message = "会话中没有可刷新的证券代码。",
                )
            )
            return
        }

        val refreshedSnapshots = mutableListOf<TencentMarketSnapshot>()
        val unavailableProviderSymbols = mutableListOf<String>()
        fun loadAt(index: Int) {
            if (index >= providerSymbols.size) {
                val refreshedCount = refreshedSnapshots
                    .map(TencentMarketSnapshot::providerSymbol)
                    .distinct()
                    .size
                val unavailableCount = providerSymbols.size - refreshedCount
                callback(
                    ConversationStockComparisonRefreshResult(
                        snapshot = ConversationStockComparisonGenerator.overlay(
                            snapshot = snapshot,
                            marketSnapshots = refreshedSnapshots,
                        ),
                        requestedCount = providerSymbols.size,
                        refreshedCount = refreshedCount,
                        unavailableCount = unavailableCount,
                        unavailableProviderSymbols = unavailableProviderSymbols.distinct(),
                        message = refreshMessage(
                            requestedCount = providerSymbols.size,
                            refreshedCount = refreshedCount,
                            unavailableCount = unavailableCount,
                        ),
                    )
                )
                return
            }

            val providerSymbol = providerSymbols[index]
            loadDetail(providerSymbol) { result ->
                when (result) {
                    is MarketDataResult.Success -> {
                        val matchingSnapshot = result.snapshots.firstOrNull { marketSnapshot ->
                            marketSnapshot.providerSymbol.equals(providerSymbol, ignoreCase = true)
                        } ?: result.snapshots.singleOrNull()
                        if (matchingSnapshot == null) {
                            unavailableProviderSymbols += providerSymbol
                        } else {
                            refreshedSnapshots += matchingSnapshot
                        }
                    }
                    MarketDataResult.Empty -> unavailableProviderSymbols += providerSymbol
                    is MarketDataResult.Failure -> unavailableProviderSymbols += providerSymbol
                }
                loadAt(index + 1)
            }
        }
        loadAt(0)
    }

    private fun refreshMessage(
        requestedCount: Int,
        refreshedCount: Int,
        unavailableCount: Int,
    ): String {
        return when {
            unavailableCount == 0 -> "已刷新 $refreshedCount 个标的的最新行情。"
            refreshedCount == 0 -> "$requestedCount 个标的的最新行情暂不可用，已保留会话内数据。"
            else -> "已刷新 $refreshedCount/$requestedCount 个标的，" +
                "$unavailableCount 个暂不可用并保留会话内数据。"
        }
    }
}
