package com.guet.liang.stockchat.data

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class SecuritiesIntent { QUOTE, TREND, COMPARE, ANALYSIS }

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class SecurityTarget(
    val providerSymbol: String,
    val displayName: String = "",
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class SecuritiesQueryPlan(
    val intent: SecuritiesIntent,
    val targets: List<SecurityTarget>,
    val needsTrend: Boolean,
    val needsIntraday: Boolean,
    val notices: List<String> = emptyList(),
    val searchEntities: List<IntentEntity> = emptyList(),
)

/** Maps an already parsed LLM response to tool arguments; never matches the user's text. */
internal object SecuritiesQueryRouter {
    /** Returns null when the model found no security to look up, so no market request is made. */
    fun route(extraction: StockMentionExtraction): SecuritiesQueryPlan? {
        val entities = extraction.entities.distinctBy(IntentEntity::value)
        if (entities.isEmpty()) return null
        return SecuritiesQueryPlan(
            intent = extraction.queryIntent,
            targets = emptyList(),
            searchEntities = entities,
            needsTrend = extraction.needsTrend,
            needsIntraday = extraction.needsIntraday,
        )
    }
}
