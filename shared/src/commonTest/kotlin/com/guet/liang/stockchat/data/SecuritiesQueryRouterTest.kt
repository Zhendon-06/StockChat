package com.guet.liang.stockchat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecuritiesQueryRouterTest {
    @Test
    fun everyResearchedCompanyGoesToTencentSearchWithoutModelSuppliedCodes() {
        val classification = assertNotNull(LlmIntentResponseParser.parse(mixedCompanyIntent))
        val plan = assertNotNull(SecuritiesQueryRouter.route(classification))
        assertTrue(plan.targets.isEmpty())
        assertEquals(listOf("腾讯控股", "米哈游"), plan.searchEntities.map { it.value })
        assertEquals(SecuritiesIntent.COMPARE, plan.intent)
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun preservesLlmSelectedEntitiesWithoutCatalogOrFourCardLimit() {
        val classification = assertNotNull(LlmIntentResponseParser.parse(mixedCompanyIntent)).copy(
            entities = (1..6).map {
                IntentEntity("模型返回标的$it", ListingStatus.LISTED, "")
            },
            queryIntent = SecuritiesIntent.ANALYSIS,
            needsTrend = true,
            needsIntraday = true,
            needsAi = true,
        )
        val plan = assertNotNull(SecuritiesQueryRouter.route(classification))
        assertEquals(classification.entities, plan.searchEntities)
        assertTrue(plan.targets.isEmpty())
        assertTrue(plan.needsTrend)
        assertTrue(plan.needsIntraday)
        assertTrue(plan.needsAi)
    }

    @Test
    fun uncertainCompaniesStillReachTencentSearchInsteadOfBeingFilteredLocally() {
        val classification = assertNotNull(LlmIntentResponseParser.parse(mixedCompanyIntent)).copy(
            entities = listOf(
                IntentEntity("不确定的企业", ListingStatus.UNKNOWN, "请提供完整名称"),
                IntentEntity("其他市场企业", ListingStatus.LISTED, "当前不支持该市场"),
            ),
        )
        val plan = assertNotNull(SecuritiesQueryRouter.route(classification))
        assertTrue(plan.targets.isEmpty())
        assertEquals(classification.entities, plan.searchEntities)
        assertTrue(plan.notices.isEmpty())
    }

    @Test
    fun emptyLlmEntitiesStayEmptyInsteadOfRecoveringFromUserText() {
        val classification = assertNotNull(LlmIntentResponseParser.parse(mixedCompanyIntent))
            .copy(entities = emptyList())
        val plan = assertNotNull(SecuritiesQueryRouter.route(classification))
        assertTrue(plan.targets.isEmpty())
        assertTrue(plan.notices.single().contains("补充"))
    }

    @Test
    fun educationAndGeneralIntentNeverGenerateCardsEvenIfLlmIncludesEntities() {
        val classification = assertNotNull(LlmIntentResponseParser.parse(mixedCompanyIntent))
        assertNull(SecuritiesQueryRouter.route(classification.copy(kind = IntentKind.INVESTMENT_EDUCATION)))
        assertNull(SecuritiesQueryRouter.route(classification.copy(kind = IntentKind.GENERAL)))
    }
}
