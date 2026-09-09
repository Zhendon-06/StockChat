@file:Suppress("MagicNumber", "LongParameterList", "MaxLineLength")
package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntentRecognitionTest {
    @Test
    fun forcesWebResearchBeforeReturningCompanyNames() {
        var completion: ((JSONObject?, String?) -> Unit)? = null
        var result: IntentRecognitionResult? = null
        var requestBody: JSONObject? = null
        val service = LlmIntentRecognitionService(AliyunApiConfig(apiKey = "test-only")) { body, callback ->
            requestBody = body
            completion = callback
        }
        service.classify("查一下原神开发商和微信开发商", emptyList(), "selected-vision-model") { result = it }
        assertNull(result)
        val body = assertNotNull(requestBody)
        assertEquals("qwen-plus", body.optString("model"))
        assertTrue(body.optBoolean("enable_search"))
        assertTrue(assertNotNull(body.optJSONObject("search_options")).optBoolean("forced_search"))
        assertFalse(body.optBoolean("stream"))
        assertNotNull(completion).invoke(response(mixedCompanyIntent), null)
        val success = assertIs<IntentRecognitionResult.Success>(result)
        assertEquals(listOf("腾讯控股", "米哈游"), success.classification.entities.map { it.value })
    }

    @Test
    fun unsupportedProviderDoesNotPretendToHaveSearchedTheWeb() {
        var called = false
        var result: IntentRecognitionResult? = null
        val service = LlmIntentRecognitionService(
            AliyunApiConfig(apiKey = "test-only", useAliyunExtensions = false),
        ) { _, _ -> called = true }
        service.classify("腾讯", emptyList(), "model") { result = it }
        assertFalse(called)
        assertIs<IntentRecognitionResult.Failure>(result)
    }

    @Test
    fun passesConversationAndCurrentQuestionOnceForPronounResolution() {
        var sentMessages: JSONArray? = null
        val history = listOf(
            ChatHistoryItem(ChatRole.USER, "腾讯和网易"),
            ChatHistoryItem(ChatRole.ASSISTANT, "[行情标的:hk00700|腾讯控股]"),
            ChatHistoryItem(ChatRole.USER, "只看前者的分时"),
        )
        val service = LlmIntentRecognitionService(AliyunApiConfig(apiKey = "test-only")) { body, _ ->
            sentMessages = body.optJSONArray("messages")
        }
        service.classify("只看前者的分时", history, "model") { }
        val messages = assertNotNull(sentMessages)
        assertEquals(4, messages.length())
        assertEquals("腾讯和网易", assertNotNull(messages.optJSONObject(1)).optString("content"))
        assertEquals(history[1].content, assertNotNull(messages.optJSONObject(2)).optString("content"))
        assertEquals("只看前者的分时", assertNotNull(messages.optJSONObject(3)).optString("content"))
    }

    @Test
    fun requestFailureAndMalformedOutputNeverFallBackToLocalMatching() {
        for (content in listOf<String?>(null, "MARKET_DATA 腾讯 hk00700", "{}")) {
            var result: IntentRecognitionResult? = null
            val service = LlmIntentRecognitionService(AliyunApiConfig(apiKey = "test-only")) { _, callback ->
                callback(content?.let(::response), if (content == null) "请求超时" else null)
            }
            service.classify("贵州茅台600519行情", emptyList(), "model") { result = it }
            assertIs<IntentRecognitionResult.Failure>(result)
        }
    }

    @Test
    fun missingKeyOrEmptyInputDoesNotEmitMockCardsOrSendRequests() {
        for ((key, question) in listOf("" to "贵州茅台", "test-only" to "  ")) {
            var calls = 0
            var result: IntentRecognitionResult? = null
            val service = LlmIntentRecognitionService(AliyunApiConfig(apiKey = key)) { _, _ -> calls++ }
            service.classify(question, emptyList(), "model") { result = it }
            assertEquals(0, calls)
            assertIs<IntentRecognitionResult.Failure>(result)
        }
    }

    @Test
    fun rejectsInvalidResearchSchema() {
        assertNull(LlmIntentResponseParser.parse(mixedCompanyIntent.replace("\"needsAi\":false,", "")))
        assertNull(LlmIntentResponseParser.parse(mixedCompanyIntent.replace("MARKET_DATA", "MARKET_GUESS")))
    }

    @Test
    fun acceptsFencedJsonAndKeepsUnlistedEntitySeparate() {
        val parsed = assertNotNull(LlmIntentResponseParser.parse("```json\n$mixedCompanyIntent\n```"))
        assertEquals(ListingStatus.UNLISTED, parsed.entities.last().listingStatus)
    }

    private fun response(content: String): JSONObject = JSONObject().apply {
        put("choices", JSONArray().apply {
            put(JSONObject().apply {
                put("message", JSONObject().apply { put("content", content) })
            })
        })
    }
}

internal val mixedCompanyIntent = """
    {"intent":"MARKET_DATA","queryIntent":"COMPARE","needsTrend":false,"needsIntraday":false,"needsAi":false,
     "entities":[
       {"value":"腾讯控股","listingStatus":"LISTED","note":""},
       {"value":"米哈游","listingStatus":"UNLISTED","note":"没有公开交易股票行情"}
     ]}
""".trimIndent()
