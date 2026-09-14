package com.guet.liang.stockchat.data

import kotlin.test.Test
import kotlin.test.assertNotEquals

class AiResponseCacheKeyTest {
    @Test
    fun textOnlyFollowUpsCannotReplayAnswersWithMarketCards() {
        val config = AliyunApiConfig(apiKey = "test-only")
        val withCards = aiResponseCacheKey(config, "model", "继续分析茅台", emptyList(), emptyList())
        val withoutCards = aiResponseCacheKey(
            config, "model", "继续分析茅台", emptyList(), emptyList(), marketCardsEnabled = false,
        )

        assertNotEquals(withCards, withoutCards)
    }
}
