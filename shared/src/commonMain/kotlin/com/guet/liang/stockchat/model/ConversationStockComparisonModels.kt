package com.guet.liang.stockchat.model

internal enum class ConversationStockDataSource(
    val label: String,
) {
    MENTION_ONLY("会话提及"),
    CONVERSATION_QUOTE("会话行情"),
    FRESH_MARKET("最新行情"),
}

internal data class ConversationStockComparisonRow(
    val providerSymbol: String,
    val name: String,
    val symbol: String,
    val marketLabel: String = "",
    val price: String = "",
    val change: String = "",
    val changePercent: String = "",
    val previousClose: String = "",
    val open: String = "",
    val high: String = "",
    val low: String = "",
    val volume: String = "",
    val volumeUnit: String = "",
    val amount: String = "",
    val amountUnit: String = "",
    val turnoverRate: String = "",
    val priceEarningsRatio: String = "",
    val amplitude: String = "",
    val updatedAt: String = "",
    val trendPoints: List<Float> = emptyList(),
    val summary: String = "",
    val aiInsight: String = "",
    val mentionedByUser: Boolean = false,
    val generatedByAi: Boolean = false,
    val dataSource: ConversationStockDataSource = ConversationStockDataSource.MENTION_ONLY,
    val relatedMessageIds: List<String> = emptyList(),
) {
    val displayName: String
        get() = name.ifBlank { symbol.ifBlank { providerSymbol } }

    val hasQuote: Boolean
        get() = price.isNotBlank() || changePercent.isNotBlank()

    val sourceDescription: String
        get() = buildList {
            if (mentionedByUser) add("用户提及")
            if (generatedByAi) add("AI 生成")
        }.joinToString(" + ").ifBlank { "会话识别" }
}

internal data class ConversationStockComparisonSnapshot(
    val title: String,
    val sourceMessageCount: Int,
    val rows: List<ConversationStockComparisonRow>,
    val disclaimer: String = DEFAULT_STOCK_COMPARISON_DISCLAIMER,
) {
    val providerSymbols: List<String>
        get() = rows.map(ConversationStockComparisonRow::providerSymbol)
            .filter(String::isNotBlank)
            .distinct()
}

internal const val DEFAULT_STOCK_COMPARISON_DISCLAIMER =
    "行情与 AI 结论均为演示信息，仅供参考，不构成投资建议。"
