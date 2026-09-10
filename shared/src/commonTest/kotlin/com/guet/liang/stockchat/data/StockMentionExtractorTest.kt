@file:Suppress("MagicNumber", "LongParameterList", "MaxLineLength")
package com.guet.liang.stockchat.data

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockMentionExtractorTest {
    @Test
    fun aliyunProviderForcesWebResearchAndSendsOnlyTheCurrentQuestion() {
        var completion: ((JSONObject?, String?) -> Unit)? = null
        var result: StockMentionResult? = null
        var requestBody: JSONObject? = null
        val service = LlmStockMentionExtractor(AliyunApiConfig(apiKey = "test-only")) { body, callback ->
            requestBody = body
            completion = callback
        }
        service.extract("查一下原神开发商和微信开发商", "selected-vision-model", forcedWebSearch = true) { result = it }
        assertNull(result)
        val body = assertNotNull(requestBody)
        assertEquals("qwen-plus", body.optString("model"))
        assertTrue(body.optBoolean("enable_search"))
        assertTrue(assertNotNull(body.optJSONObject("search_options")).optBoolean("forced_search"))
        assertFalse(body.optBoolean("stream"))
        val messages = assertNotNull(body.optJSONArray("messages"))
        assertEquals(2, messages.length())
        assertEquals("system", assertNotNull(messages.optJSONObject(0)).optString("role"))
        assertEquals("查一下原神开发商和微信开发商", assertNotNull(messages.optJSONObject(1)).optString("content"))
        assertNotNull(completion).invoke(response(mixedCompanyMentions), null)
        val success = assertIs<StockMentionResult.Success>(result)
        assertEquals(listOf("腾讯控股", "米哈游"), success.extraction.entities.map { it.value })
        assertEquals(SecuritiesIntent.COMPARE, success.extraction.queryIntent)
    }

    @Test
    fun fastModeSkipsWebSearchButKeepsTheTextResearchModel() {
        var requestBody: JSONObject? = null
        val service = LlmStockMentionExtractor(AliyunApiConfig(apiKey = "test-only")) { body, _ -> requestBody = body }
        service.extract("茅台和宁德时代", "qwen3-vl-flash", forcedWebSearch = false) { }
        val body = assertNotNull(requestBody)
        assertEquals("qwen-plus", body.optString("model"))
        assertFalse(body.has("enable_search"))
        assertFalse(body.has("search_options"))
        assertFalse(body.optBoolean("enable_thinking", true))
    }

    @Test
    fun symbolHintIsNormalizedAndInvalidHintsAreDropped() {
        val parsed = assertNotNull(StockMentionResponseParser.parse(hintedMentions))
        assertEquals("sh600519", parsed.entities[0].symbolHint)
        assertEquals("", parsed.entities[1].symbolHint)
        assertEquals("", assertNotNull(StockMentionResponseParser.parse(mixedCompanyMentions)).entities[0].symbolHint)
    }

    @Test
    fun otherProvidersUseTheSelectedModelWithoutClaimingWebSearch() {
        var requestBody: JSONObject? = null
        val service = LlmStockMentionExtractor(
            AliyunApiConfig(apiKey = "test-only", useAliyunExtensions = false, chatModel = "fallback"),
        ) { body, _ -> requestBody = body }
        service.extract("腾讯", "deepseek-chat", forcedWebSearch = true) { }
        val body = assertNotNull(requestBody)
        assertEquals("deepseek-chat", body.optString("model"))
        assertFalse(body.has("enable_search"))
        assertFalse(body.has("search_options"))
        assertEquals(2, assertNotNull(body.optJSONArray("messages")).length())
    }

    @Test
    fun requestFailureAndMalformedOutputNeverFallBackToLocalMatching() {
        for (content in listOf<String?>(null, "MARKET_DATA 腾讯 hk00700", "{}")) {
            var result: StockMentionResult? = null
            val service = LlmStockMentionExtractor(AliyunApiConfig(apiKey = "test-only")) { _, callback ->
                callback(content?.let(::response), if (content == null) "请求超时" else null)
            }
            service.extract("贵州茅台600519行情", "model", forcedWebSearch = false) { result = it }
            assertIs<StockMentionResult.Failure>(result)
        }
    }

    @Test
    fun missingKeyOrEmptyInputDoesNotSendRequests() {
        for ((key, question) in listOf("" to "贵州茅台", "test-only" to "  ")) {
            var calls = 0
            var result: StockMentionResult? = null
            val service = LlmStockMentionExtractor(AliyunApiConfig(apiKey = key)) { _, _ -> calls++ }
            service.extract(question, "model", forcedWebSearch = false) { result = it }
            assertEquals(0, calls)
            assertIs<StockMentionResult.Failure>(result)
        }
    }

    @Test
    fun rejectsInvalidSchema() {
        assertNull(StockMentionResponseParser.parse(mixedCompanyMentions.replace("\"needsTrend\":false,", "")))
        assertNull(StockMentionResponseParser.parse(mixedCompanyMentions.replace("COMPARE", "GUESS")))
        assertNull(StockMentionResponseParser.parse(mixedCompanyMentions.replace("\"listingStatus\":\"LISTED\",", "")))
    }

    @Test
    fun acceptsFencedJsonEmptyEntitiesAndKeepsUnlistedEntitySeparate() {
        val parsed = assertNotNull(StockMentionResponseParser.parse("```json\n$mixedCompanyMentions\n```"))
        assertEquals(ListingStatus.UNLISTED, parsed.entities.last().listingStatus)
        val general = assertNotNull(
            StockMentionResponseParser.parse("""{"queryIntent":"QUOTE","needsTrend":false,"needsIntraday":false,"entities":[]}"""),
        )
        assertTrue(general.entities.isEmpty())
    }

    private fun response(content: String): JSONObject = JSONObject().apply {
        put("choices", JSONArray().apply {
            put(JSONObject().apply {
                put("message", JSONObject().apply { put("content", content) })
            })
        })
    }
}

internal val hintedMentions = """
    {"queryIntent":"QUOTE","needsTrend":false,"needsIntraday":false,
     "entities":[
       {"value":"贵州茅台","providerSymbol":"SH600519","listingStatus":"LISTED","note":""},
       {"value":"宁德时代","providerSymbol":"not-a-code","listingStatus":"LISTED","note":""}
     ]}
""".trimIndent()

internal val mixedCompanyMentions = """
    {"queryIntent":"COMPARE","needsTrend":false,"needsIntraday":false,
     "entities":[
       {"value":"腾讯控股","listingStatus":"LISTED","note":""},
       {"value":"米哈游","listingStatus":"UNLISTED","note":"没有公开交易股票行情"}
     ]}
""".trimIndent()
