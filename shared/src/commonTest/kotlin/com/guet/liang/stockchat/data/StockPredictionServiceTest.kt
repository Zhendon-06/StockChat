package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionInput
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockPredictionServiceTest {
    @Test
    fun parsesStrictJsonFromCodeFence() {
        val prediction = StockPredictionResponseParser.parse(
            content = """
                ```json
                {
                  "forecastPoints": [
                    {"timestamp":"2026-09-01","predictedPrice":101.5,"lowerBound":99.0,"upperBound":104.0},
                    {"timestamp":"2026-09-02","predictedPrice":102.25,"lowerBound":99.5,"upperBound":105.0}
                  ],
                  "horizon": 2,
                  "direction": "偏多",
                  "confidence": 0.72,
                  "rationale": "短期均线保持向上",
                  "generatedAt": "2026-08-31T16:00:00Z",
                  "sourceUpdatedAt": "2026-08-31 15:00:00",
                  "historyPointCount": 20
                }
                ```
            """.trimIndent(),
            modelName = "demo-model",
            sourceUpdatedAt = "2026-08-31 15:00:00",
            expectedHorizon = 2,
            expectedHistoryPointCount = 20,
        )

        val result = assertNotNull(prediction)
        assertEquals("偏多", result.direction)
        assertEquals(0.72f, result.confidence)
        assertEquals(101.5f, result.forecastPoints.first().predictedPrice)
        assertEquals(104f, result.forecastPoints.first().upperBound)
        assertEquals("demo-model", result.modelName)
    }

    @Test
    fun parsesJsonAfterQwenThinkingAndNormalizesSourceTimestamp() {
        val prediction = StockPredictionResponseParser.parse(
            content = """
                <think>先检查历史数据，再输出结构化结果。</think>
                {"forecastPoints":[
                  {"timestamp":"2026-09-01","predictedPrice":101},
                  {"timestamp":"2026-09-02","predictedPrice":102}],
                 "horizon":2,"direction":"中性","confidence":0.5,"rationale":"区间震荡",
                 "generatedAt":"2026-08-31T16:00:00Z",
                 "sourceUpdatedAt":"2026-08-31 15:00:00","historyPointCount":5}
            """.trimIndent(),
            modelName = "model",
            sourceUpdatedAt = "腾讯行情 · 2026-08-31 15:00:00",
            expectedHorizon = 2,
            expectedHistoryPointCount = 5,
        )

        val result = assertNotNull(prediction)
        assertEquals("腾讯行情 · 2026-08-31 15:00:00", result.sourceUpdatedAt)
    }

    @Test
    fun acceptsSourceTimestampWithExplicitTimezone() {
        val prediction = StockPredictionResponseParser.parse(
            content = """
                {"forecastPoints":[
                  {"timestamp":"2026-09-01","predictedPrice":101},
                  {"timestamp":"2026-09-02","predictedPrice":102}],
                 "horizon":2,"direction":"中性","confidence":0.5,"rationale":"区间震荡",
                 "generatedAt":"2026-08-31T16:00:00+08:00",
                 "sourceUpdatedAt":"2026-08-31T15:00:00+08:00","historyPointCount":5}
            """.trimIndent(),
            modelName = "model",
            sourceUpdatedAt = "腾讯行情 · 2026-08-31 15:00:00",
            expectedHorizon = 2,
            expectedHistoryPointCount = 5,
        )

        assertNotNull(prediction)
    }

    @Test
    fun parsesJsonWithSurroundingTextAndFormattedNumbers() {
        val prediction = StockPredictionResponseParser.parse(
            content = """
                预测结果如下：
                ```json
                {"forecastPoints":[
                  {"timestamp":"2026-09-01","predictedPrice":"1,234.56","lowerBound":"1,200","upperBound":"1,300"},
                  {"timestamp":"2026-09-02","predictedPrice":"1,240.00"}],
                 "horizon":2,"direction":"中性","confidence":"70%","rationale":"区间震荡",
                 "generatedAt":"2026-08-31T16:00:00Z",
                 "sourceUpdatedAt":"2026-08-31 15:00:00","historyPointCount":5}
                ```
                以上为演示信息。
            """.trimIndent(),
            modelName = "model",
            sourceUpdatedAt = "2026-08-31 15:00:00",
            expectedHorizon = 2,
            expectedHistoryPointCount = 5,
        )

        val result = assertNotNull(prediction)
        assertEquals(1234.56f, result.forecastPoints.first().predictedPrice)
        assertEquals(1200f, result.forecastPoints.first().lowerBound)
        assertEquals(0.7f, result.confidence)
    }

    @Test
    fun rejectsMissingPointsAndNonFinitePrices() {
        val missingPoint = """
            {"forecastPoints":[{"timestamp":"2026-09-01","predictedPrice":101}],
             "direction":"中性","confidence":0.5,"rationale":"x"}
        """.trimIndent()
        assertNull(
            StockPredictionResponseParser.parse(
                missingPoint,
                modelName = "model",
                sourceUpdatedAt = "source",
            )
        )

        val nonFinite = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
              {"timestamp":"2026-09-02","predictedPrice":"NaN"}],
             "direction":"中性","confidence":0.5,"rationale":"x"}
        """.trimIndent()
        assertNull(
            StockPredictionResponseParser.parse(
                nonFinite,
                modelName = "model",
                sourceUpdatedAt = "source",
            )
        )
    }

    @Test
    fun rejectsUnorderedPointsAndInvalidIntervals() {
        val unordered = """
            {"forecastPoints":[
              {"timestamp":"2026-09-02","predictedPrice":102},
              {"timestamp":"2026-09-01","predictedPrice":101}],
             "direction":"中性","confidence":0.5,"rationale":"x"}
        """.trimIndent()
        assertNull(StockPredictionResponseParser.parse(unordered, "model", "source"))

        val invalidInterval = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101,"lowerBound":102},
              {"timestamp":"2026-09-02","predictedPrice":102}],
             "direction":"中性","confidence":0.5,"rationale":"x"}
        """.trimIndent()
        assertNull(StockPredictionResponseParser.parse(invalidInterval, "model", "source"))
    }

    @Test
    fun rejectsNonJsonProseAndMalformedCodeFence() {
        assertNull(
            StockPredictionResponseParser.parse(
                "模型判断短期偏多，预计价格上涨。",
                modelName = "model",
                sourceUpdatedAt = "source",
            )
        )
        assertNull(
            StockPredictionResponseParser.parse(
                "```json\n{\"forecastPoints\": []}\n",
                modelName = "model",
                sourceUpdatedAt = "source",
            )
        )
    }

    @Test
    fun rejectsPointCountTimestampAndHorizonMismatches() {
        val duplicateTimestamps = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
              {"timestamp":"2026-09-01","predictedPrice":102}],
             "horizon":2,"direction":"中性","confidence":0.5,"rationale":"x"}
        """.trimIndent()
        assertNull(StockPredictionResponseParser.parse(duplicateTimestamps, "model", "source"))

        val horizonMismatch = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
              {"timestamp":"2026-09-02","predictedPrice":102}],
             "horizon":3,"direction":"中性","confidence":0.5,"rationale":"x"}
        """.trimIndent()
        assertNull(StockPredictionResponseParser.parse(horizonMismatch, "model", "source"))

        val tooManyPoints = buildString {
            append("{\"forecastPoints\":[")
            repeat(33) { index ->
                if (index > 0) append(',')
                append(
                    "{\"timestamp\":\"2026-10-${(index + 1).toString().padStart(2, '0')}\"," +
                        "\"predictedPrice\":101}",
                )
            }
            append("],\"horizon\":33,\"direction\":\"中性\",\"confidence\":0.5,\"rationale\":\"x\"}")
        }
        assertNull(StockPredictionResponseParser.parse(tooManyPoints, "model", "source"))
    }

    @Test
    fun normalizesModelHistoryCountToActualInputCount() {
        val invalidConfidence = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
              {"timestamp":"2026-09-02","predictedPrice":102}],
             "horizon":2,"direction":"中性","confidence":1.2,"rationale":"x"}
        """.trimIndent()
        assertNull(StockPredictionResponseParser.parse(invalidConfidence, "model", "source"))

        val wrongHistoryCount = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
             {"timestamp":"2026-09-02","predictedPrice":102}],
             "horizon":2,"direction":"中性","confidence":0.5,"rationale":"x",
             "generatedAt":"2026-08-31T16:00:00Z",
             "sourceUpdatedAt":"source","historyPointCount":4}
        """.trimIndent()
        val prediction = assertNotNull(
            StockPredictionResponseParser.parse(
                wrongHistoryCount,
                modelName = "model",
                sourceUpdatedAt = "source",
                expectedHorizon = 2,
                expectedHistoryPointCount = 5,
            )
        )
        assertEquals(5, prediction.historyPointCount)
    }

    @Test
    fun rejectsPredictionForDifferentSourceTimestamp() {
        val response = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
              {"timestamp":"2026-09-02","predictedPrice":102}],
             "horizon":2,"direction":"中性","confidence":0.5,"rationale":"x",
             "generatedAt":"2026-08-31T16:00:00Z",
             "sourceUpdatedAt":"other-source"}
        """.trimIndent()
        assertNull(
            StockPredictionResponseParser.parse(
                response,
                modelName = "model",
                sourceUpdatedAt = "source",
                expectedHorizon = 2,
            )
        )
    }

    @Test
    fun rejectsExplicitlyInvalidHorizonAndHistoryCount() {
        val invalidHorizon = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
              {"timestamp":"2026-09-02","predictedPrice":102}],
             "horizon":"unknown","direction":"中性","confidence":0.5,"rationale":"x"}
        """.trimIndent()
        assertNull(StockPredictionResponseParser.parse(invalidHorizon, "model", "source"))

        val invalidHistoryCount = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
              {"timestamp":"2026-09-02","predictedPrice":102}],
             "horizon":2,"historyPointCount":"unknown",
             "direction":"中性","confidence":0.5,"rationale":"x"}
        """.trimIndent()
        assertNull(
            StockPredictionResponseParser.parse(
                invalidHistoryCount,
                modelName = "model",
                sourceUpdatedAt = "source",
                expectedHorizon = 2,
                expectedHistoryPointCount = 5,
            )
        )
    }

    @Test
    fun rejectsMissingProvenanceAndMalformedTimestamps() {
        val missingSourceTimestamp = """
            {"forecastPoints":[
              {"timestamp":"2026-09-01","predictedPrice":101},
              {"timestamp":"2026-09-02","predictedPrice":102}],
             "horizon":2,"direction":"中性","confidence":0.5,"rationale":"x",
             "generatedAt":"2026-08-31T16:00:00Z","historyPointCount":5}
        """.trimIndent()
        assertNull(
            StockPredictionResponseParser.parse(
                missingSourceTimestamp,
                modelName = "model",
                sourceUpdatedAt = "source",
                expectedHorizon = 2,
                expectedHistoryPointCount = 5,
            )
        )

        val malformedTimestamp = """
            {"forecastPoints":[
              {"timestamp":"tomorrow","predictedPrice":101},
              {"timestamp":"2026-09-02","predictedPrice":102}],
             "horizon":2,"direction":"中性","confidence":0.5,"rationale":"x",
             "generatedAt":"not-a-time","sourceUpdatedAt":"source","historyPointCount":5}
        """.trimIndent()
        assertNull(
            StockPredictionResponseParser.parse(
                malformedTimestamp,
                modelName = "model",
                sourceUpdatedAt = "source",
                expectedHorizon = 2,
                expectedHistoryPointCount = 5,
            )
        )
    }

    @Test
    fun acceptsOpenAiContentAliasesAndBuildsCompleteContext() {
        val response = JSONObject().apply {
            put(
                "choices",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put(
                                "message",
                                JSONObject().apply {
                                    put(
                                        "content",
                                        """
                                            {"predictions":[
                                              {"date":"2026-09-01","price":101},
                                              {"date":"2026-09-02","price":102}],
                                             "period":2,"trend":"中性","confidencePercent":60,
                                             "reason":"区间震荡","generatedAt":"2026-09-04T12:00:00Z",
                                             "sourceUpdatedAt":"source",
                                             "history_point_count":5}
                                        """.trimIndent(),
                                    )
                                },
                            )
                        }
                    )
                },
            )
        }
        val content = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        val prediction = StockPredictionResponseParser.parse(
            content,
            modelName = "model",
            sourceUpdatedAt = "source",
            expectedHorizon = 2,
            expectedHistoryPointCount = 5,
        )
        assertNotNull(prediction)
        assertEquals(0.6f, prediction.confidence)

        val input = sampleInput()
        val request = StockPredictionRequestBuilder.build(input, "model")
        assertEquals("model", request.optString("model"))
        val messages = assertNotNull(request.optJSONArray("messages"))
        val userContent = messages.optJSONObject(1)?.optString("content").orEmpty()
        assertTrue("2026-08-01" in userContent)
        assertTrue("\"close\"" in userContent && "100" in userContent)
        assertTrue("forecastHorizon" in userContent)
    }

    @Test
    fun buildsDashScopeRequestWithQwenCompatibleExtensions() {
        val request = StockPredictionRequestBuilder.build(
            input = sampleInput(),
            modelName = "qwen3-vl-flash",
            useAliyunExtensions = true,
        )

        assertEquals("disabled", request.optJSONObject("thinking")?.optString("type"))
        assertEquals(1600, request.optInt("max_completion_tokens"))
        assertTrue(!request.has("max_tokens"))
        assertEquals("json_object", request.optJSONObject("response_format")?.optString("type"))
    }

    @Test
    fun buildsGenericOpenAiRequestWithoutAliyunOnlyFields() {
        val request = StockPredictionRequestBuilder.build(
            input = sampleInput(),
            modelName = "gpt-compatible-model",
            useAliyunExtensions = false,
        )

        assertEquals(1600, request.optInt("max_tokens"))
        assertTrue(!request.has("thinking"))
        assertTrue(!request.has("max_completion_tokens"))
        assertTrue(!request.has("response_format"))
    }

    private fun sampleInput(): StockPredictionInput {
        val history = (1..5).map { index ->
            StockPredictionHistoryPoint(
                timestamp = "2026-08-0$index",
                close = (99 + index).toFloat(),
            )
        }
        return StockPredictionInput(
            quote = StockQuote(
                name = "测试标的",
                symbol = "600000",
                marketLabel = "沪市",
                price = "104.00",
                change = "+1.00",
                changePercent = "+0.97%",
                updatedAt = "source",
                isPositive = true,
                trendPoints = history.map(StockPredictionHistoryPoint::close),
                summary = "",
                aiInsight = "",
            ),
            history = history,
            forecastHorizon = 2,
            sourceUpdatedAt = "source",
        )
    }
}
