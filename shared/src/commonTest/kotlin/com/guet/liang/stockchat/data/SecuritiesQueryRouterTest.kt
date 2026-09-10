package com.guet.liang.stockchat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecuritiesQueryRouterTest {
    @Test
    fun everyExtractedCompanyGoesToTencentSearchWithoutModelSuppliedCodes() {
        val extraction = assertNotNull(StockMentionResponseParser.parse(mixedCompanyMentions))
        val plan = assertNotNull(SecuritiesQueryRouter.route(extraction))
        assertTrue(plan.targets.isEmpty())
        assertEquals(listOf("腾讯控股", "米哈游"), plan.searchEntities.map { it.value })
        assertEquals(SecuritiesIntent.COMPARE, plan.intent)
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun preservesLlmSelectedEntitiesWithoutCatalogOrFourCardLimit() {
        val extraction = assertNotNull(StockMentionResponseParser.parse(mixedCompanyMentions)).copy(
            entities = (1..6).map {
                IntentEntity("模型返回标的$it", ListingStatus.LISTED, "")
            },
            queryIntent = SecuritiesIntent.ANALYSIS,
            needsTrend = true,
            needsIntraday = true,
        )
        val plan = assertNotNull(SecuritiesQueryRouter.route(extraction))
        assertEquals(extraction.entities, plan.searchEntities)
        assertTrue(plan.targets.isEmpty())
        assertTrue(plan.needsTrend)
        assertTrue(plan.needsIntraday)
    }

    @Test
    fun uncertainCompaniesStillReachTencentSearchInsteadOfBeingFilteredLocally() {
        val extraction = assertNotNull(StockMentionResponseParser.parse(mixedCompanyMentions)).copy(
            entities = listOf(
                IntentEntity("不确定的企业", ListingStatus.UNKNOWN, "请提供完整名称"),
                IntentEntity("其他市场企业", ListingStatus.LISTED, "当前不支持该市场"),
            ),
        )
        val plan = assertNotNull(SecuritiesQueryRouter.route(extraction))
        assertTrue(plan.targets.isEmpty())
        assertEquals(extraction.entities, plan.searchEntities)
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun duplicateNamesCollapseToOneSearch() {
        val extraction = assertNotNull(StockMentionResponseParser.parse(mixedCompanyMentions)).copy(
            entities = listOf(
                IntentEntity("腾讯控股", ListingStatus.LISTED, ""),
                IntentEntity("腾讯控股", ListingStatus.LISTED, "重复"),
            ),
        )
        assertEquals(1, assertNotNull(SecuritiesQueryRouter.route(extraction)).searchEntities.size)
    }

    @Test
    fun emptyEntitiesSkipTheMarketBranchInsteadOfRecoveringFromUserText() {
        val extraction = assertNotNull(StockMentionResponseParser.parse(mixedCompanyMentions))
            .copy(entities = emptyList())
        assertNull(SecuritiesQueryRouter.route(extraction))
    }
}
