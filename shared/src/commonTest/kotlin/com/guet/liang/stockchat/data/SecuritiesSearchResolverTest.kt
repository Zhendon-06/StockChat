package com.guet.liang.stockchat.data

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecuritiesSearchResolverTest {
    private val tencent = IntentEntity("腾讯控股", ListingStatus.LISTED, "微信开发商")
    private val mihoyo = IntentEntity("米哈游", ListingStatus.UNLISTED, "原神开发商，未上市")
    private val matches = listOf(
        TencentSearchMatch("hk80700", "80700", "腾讯控股R", "GP"),
        TencentSearchMatch("hk00700", "00700", "腾讯控股", "GP"),
    )

    @Test
    fun researchNamesAreSearchedAndSecondCandidateChosenByLlm() {
        val searched = mutableListOf<String>()
        var selected = false
        var result: SecuritiesResolutionResult? = null
        val resolver = SecuritiesSearchResolver(
            search = { name, callback ->
                searched += name
                callback(SecuritySearchResult.Success(if (name == tencent.value) matches else emptyList()))
            },
            select = { question, _, _, candidates, callback ->
                assertEquals("原神和微信的开发商", question)
                assertEquals(listOf("腾讯控股", "米哈游"), searched)
                assertEquals(matches, candidates.single().matches)
                selected = true
                callback(LlmSecurityCandidateSelector.parseSelection(
                    """{"selections":[{"entityIndex":0,"providerSymbol":"hk00700","note":"主要港币柜台"}]}""",
                    candidates,
                ))
            },
        )
        resolver.resolve("原神和微信的开发商", emptyList(), "model", listOf(tencent, mihoyo)) { result = it }
        assertTrue(selected)
        val success = assertIs<SecuritiesResolutionResult.Success>(result)
        assertEquals(listOf(SecurityTarget("hk00700", "腾讯控股")), success.targets)
        assertTrue(success.notices.single().contains("米哈游"))
    }

    @Test
    fun selectorReceivesAllTencentCandidatesAndUsesCurrentModel() {
        var body: JSONObject? = null
        val selector = LlmSecurityCandidateSelector(AliyunApiConfig(apiKey = "test-only")) { request, _ -> body = request }
        selector.select("只看港币柜台", emptyList(), "selected-model", listOf(SecuritySearchCandidates(tencent, matches))) { }
        val request = assertNotNull(body)
        assertEquals("selected-model", request.optString("model"))
        val content = assertNotNull(request.optJSONArray("messages")?.optJSONObject(1)).optString("content")
        assertTrue(content.contains("hk80700"))
        assertTrue(content.contains("hk00700"))
        assertTrue(content.contains("只看港币柜台"))
    }

    @Test
    fun rejectsInventedCodeAndMissingOrDuplicateSelections() {
        val candidates = listOf(SecuritySearchCandidates(tencent, matches))
        for (json in listOf(
            """{"selections":[{"entityIndex":0,"providerSymbol":"sh600519","note":""}]}""",
            """{"selections":[]}""",
            """{"selections":[{"entityIndex":1,"providerSymbol":"hk00700","note":""}]}""",
        )) assertIs<SecuritiesResolutionResult.Failure>(LlmSecurityCandidateSelector.parseSelection(json, candidates))
    }

    @Test
    fun irrelevantCandidatesNeverProduceCardsWhenLlmDeclinesThem() {
        val result = LlmSecurityCandidateSelector.parseSelection(
            """{"selections":[{"entityIndex":0,"providerSymbol":"","note":"未上市企业，搜索结果无关"}]}""",
            listOf(SecuritySearchCandidates(mihoyo, matches)),
        )
        val success = assertIs<SecuritiesResolutionResult.Success>(result)
        assertTrue(success.targets.isEmpty())
        assertTrue(success.notices.single().contains("无关"))
    }

    @Test
    fun validatedSymbolHintSkipsTheSelectorAndUnhintedCompaniesStillUseIt() {
        val hintedTencent = tencent.copy(symbolHint = "hk00700")
        val moutai = IntentEntity("贵州茅台", ListingStatus.LISTED, "")
        val moutaiMatches = listOf(TencentSearchMatch("sh600519", "600519", "贵州茅台", "GP"))
        var selectedCandidates: List<SecuritySearchCandidates>? = null
        var result: SecuritiesResolutionResult? = null
        val resolver = SecuritiesSearchResolver(
            search = { name, callback ->
                callback(SecuritySearchResult.Success(if (name == moutai.value) moutaiMatches else matches))
            },
            select = { _, _, _, candidates, callback ->
                selectedCandidates = candidates
                callback(LlmSecurityCandidateSelector.parseSelection(
                    """{"selections":[{"entityIndex":0,"providerSymbol":"sh600519","note":""}]}""",
                    candidates,
                ))
            },
        )
        resolver.resolve("腾讯和茅台", emptyList(), "model", listOf(hintedTencent, moutai)) { result = it }
        assertEquals(listOf(moutai), assertNotNull(selectedCandidates).map { it.entity })
        val success = assertIs<SecuritiesResolutionResult.Success>(result)
        assertEquals(listOf("hk00700", "sh600519"), success.targets.map { it.providerSymbol })
        assertEquals("腾讯控股", success.targets.first().displayName)
    }

    @Test
    fun hintOutsideTencentResultsFallsBackToTheSelector() {
        var selected = false
        val resolver = SecuritiesSearchResolver(
            search = { _, callback -> callback(SecuritySearchResult.Success(matches)) },
            select = { _, _, _, candidates, callback ->
                selected = true
                callback(SecuritiesResolutionResult.Success(listOf(SecurityTarget("hk00700", "腾讯控股")), emptyList()))
            },
        )
        var result: SecuritiesResolutionResult? = null
        resolver.resolve("腾讯", emptyList(), "model", listOf(tencent.copy(symbolHint = "sh600519"))) { result = it }
        assertTrue(selected)
        assertEquals("hk00700", assertIs<SecuritiesResolutionResult.Success>(result).targets.single().providerSymbol)
    }

    @Test
    fun allHintedCompaniesResolveWithoutAnyModelCall() {
        var result: SecuritiesResolutionResult? = null
        val resolver = SecuritiesSearchResolver(
            search = { _, callback -> callback(SecuritySearchResult.Success(matches)) },
            select = { _, _, _, _, _ -> error("selector must not run") },
        )
        resolver.resolve("腾讯", emptyList(), "model", listOf(tencent.copy(symbolHint = "hk00700"))) { result = it }
        assertEquals("hk00700", assertIs<SecuritiesResolutionResult.Success>(result).targets.single().providerSymbol)
    }

    @Test
    fun searchesRunConcurrentlyAndResultsKeepEntityOrder() {
        val pendingSearches = mutableMapOf<String, (SecuritySearchResult) -> Unit>()
        var result: SecuritiesResolutionResult? = null
        val resolver = SecuritiesSearchResolver(
            search = { name, callback -> pendingSearches[name] = callback },
            select = { _, _, _, _, _ -> error("hints resolve everything") },
        )
        val moutai = IntentEntity("贵州茅台", ListingStatus.LISTED, "", symbolHint = "sh600519")
        resolver.resolve("腾讯和茅台", emptyList(), "model", listOf(tencent.copy(symbolHint = "hk00700"), moutai)) { result = it }
        assertEquals(setOf("腾讯控股", "贵州茅台"), pendingSearches.keys)
        assertNull(result)
        pendingSearches.getValue("贵州茅台")(SecuritySearchResult.Success(listOf(TencentSearchMatch("sh600519", "600519", "贵州茅台", "GP"))))
        assertNull(result)
        pendingSearches.getValue("腾讯控股")(SecuritySearchResult.Success(matches))
        assertEquals(listOf("hk00700", "sh600519"), assertIs<SecuritiesResolutionResult.Success>(result).targets.map { it.providerSymbol })
    }

    @Test
    fun oneSearchFailurePreservesOtherCompanies() {
        var result: SecuritiesResolutionResult? = null
        val resolver = SecuritiesSearchResolver(
            search = { name, callback ->
                callback(if (name == mihoyo.value) SecuritySearchResult.Failure("搜索超时")
                else SecuritySearchResult.Success(matches))
            },
            select = { _, _, _, _, callback ->
                callback(SecuritiesResolutionResult.Success(listOf(SecurityTarget("hk00700", "腾讯控股")), emptyList()))
            },
        )
        resolver.resolve("腾讯和米哈游", emptyList(), "model", listOf(mihoyo, tencent)) { result = it }
        val success = assertIs<SecuritiesResolutionResult.Success>(result)
        assertEquals("hk00700", success.targets.single().providerSymbol)
        assertTrue(success.notices.single().contains("搜索超时"))
    }

    @Test
    fun totalSearchOutageIsRetryableInsteadOfReportedAsNoMatchingCompanies() {
        var result: SecuritiesResolutionResult? = null
        val resolver = SecuritiesSearchResolver(
            search = { _, callback -> callback(SecuritySearchResult.Failure("搜索超时")) },
            select = { _, _, _, _, _ -> error("Must not select without Tencent results") },
        )
        resolver.resolve("腾讯", emptyList(), "model", listOf(tencent)) { result = it }
        assertIs<SecuritiesResolutionResult.Failure>(result)
    }

    @Test
    fun selectionFailureNeverUsesFirstTencentMatch() {
        var result: SecuritiesResolutionResult? = null
        val resolver = SecuritiesSearchResolver(
            search = { _, callback -> callback(SecuritySearchResult.Success(matches)) },
            select = { _, _, _, _, callback -> callback(SecuritiesResolutionResult.Failure("AI 超时")) },
        )
        resolver.resolve("腾讯", emptyList(), "model", listOf(tencent)) { result = it }
        assertIs<SecuritiesResolutionResult.Failure>(result)
    }
}
