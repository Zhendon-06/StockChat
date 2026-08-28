package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecuritiesQueryRouterTest {
    @Test
    fun routesKnownSecurityQuoteWithoutAi() {
        val plan = assertNotNull(SecuritiesQueryRouter.route("贵州茅台现在多少钱？"))

        assertEquals(SecuritiesIntent.QUOTE, plan.intent)
        assertEquals(listOf("sh600519"), plan.targets.map(SecurityTarget::providerSymbol))
        assertFalse(plan.needsAi)
    }

    @Test
    fun normalizesSpacesInKnownIndexName() {
        val plan = assertNotNull(SecuritiesQueryRouter.route("看看沪深 300 指数"))

        assertEquals("sh000300", plan.targets.single().providerSymbol)
    }

    @Test
    fun routesTrendAndProviderSymbol() {
        val plan = assertNotNull(SecuritiesQueryRouter.route("sh600519 今天分时走势"))

        assertEquals(SecuritiesIntent.TREND, plan.intent)
        assertEquals("sh600519", plan.targets.single().providerSymbol)
        assertTrue(plan.needsTrend)
        assertTrue(plan.needsIntraday)
    }

    @Test
    fun routesComparisonWithTwoTargets() {
        val plan = assertNotNull(SecuritiesQueryRouter.route("茅台和宁德时代对比一下"))

        assertEquals(SecuritiesIntent.COMPARE, plan.intent)
        assertEquals(setOf("sh600519", "sz300750"), plan.targets.map { it.providerSymbol }.toSet())
    }

    @Test
    fun routesTwoPlainCodes() {
        val plan = assertNotNull(SecuritiesQueryRouter.route("600519和300750涨跌对比"))

        assertEquals(setOf("sh600519", "sz300750"), plan.targets.map { it.providerSymbol }.toSet())
    }

    @Test
    fun keepsGeneralMarketKnowledgeOutOfQuoteFlow() {
        assertNull(SecuritiesQueryRouter.route("市盈率是什么？"))
        assertNull(SecuritiesQueryRouter.route("K线怎么看？"))
    }

    @Test
    fun extractsUnknownSecurityNameForSearch() {
        val plan = assertNotNull(SecuritiesQueryRouter.route("帮我查一下格力电器的股价"))

        assertEquals(emptyList(), plan.targets)
        assertEquals(listOf("格力电器"), plan.unresolvedTerms)
    }

    @Test
    fun followsUniqueRecentMarketTarget() {
        val history = listOf(
            ChatHistoryItem(
                role = ChatRole.ASSISTANT,
                content = "[行情标的:sh600519|贵州茅台] 腾讯行情 · 2026-08-28 16:15:00",
            )
        )

        val plan = assertNotNull(SecuritiesQueryRouter.route("它现在多少钱？", history))

        assertEquals("sh600519", plan.targets.single().providerSymbol)
    }
}
