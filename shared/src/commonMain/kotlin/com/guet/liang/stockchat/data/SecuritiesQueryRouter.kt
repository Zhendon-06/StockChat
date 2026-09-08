package com.guet.liang.stockchat.data

internal enum class SecuritiesIntent { QUOTE, TREND, COMPARE, ANALYSIS }

internal data class SecurityTarget(
    val providerSymbol: String,
    val displayName: String = "",
)

internal data class SecuritiesQueryPlan(
    val intent: SecuritiesIntent,
    val targets: List<SecurityTarget>,
    val needsTrend: Boolean,
    val needsIntraday: Boolean,
    val needsAi: Boolean,
    val notices: List<String> = emptyList(),
    val searchEntities: List<IntentEntity> = emptyList(),
)

/** Maps an already parsed LLM response to tool arguments; never matches the user's text. */
internal object SecuritiesQueryRouter {
    fun route(classification: IntentClassification): SecuritiesQueryPlan? {
        if (classification.kind != IntentKind.MARKET_DATA) return null
        return SecuritiesQueryPlan(
            intent = classification.queryIntent,
            targets = emptyList(),
            searchEntities = classification.entities.distinctBy(IntentEntity::value),
            needsTrend = classification.needsTrend,
            needsIntraday = classification.needsIntraday,
            needsAi = classification.needsAi,
            notices = if (classification.entities.isEmpty()) {
                listOf("AI 联网搜索尚未确定具体标的，请补充查询条件。")
            } else emptyList(),
        )
    }
}
