package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntentRecognitionTest {
    @Test
    fun classifiesHighConfidenceMarketVectorAndExtractsSecurity() {
        val classification = EmbeddingFirstIntentRecognizer().classifyEmbeddingVectors(
            question = "贵州茅台现在多少钱？",
            questionVector = listOf(1f, 0f),
            prototypeVectors = mapOf(
                IntentKind.MARKET_DATA to listOf(1f, 0f),
                IntentKind.INVESTMENT_EDUCATION to listOf(0f, 1f),
                IntentKind.GENERAL to listOf(-1f, 0f),
            ),
        )

        assertEquals(IntentKind.MARKET_DATA, classification.kind)
        assertEquals(IntentSource.EMBEDDING, classification.source)
        assertEquals(1f, classification.confidence)
        assertEquals("sh600519", classification.entities.single().providerSymbol)
    }

    @Test
    fun classifiesHighConfidenceGeneralQuestion() {
        val classification = EmbeddingFirstIntentRecognizer().classifyEmbeddingVectors(
            question = "帮我写一封请假邮件",
            questionVector = listOf(0f, 1f),
            prototypeVectors = mapOf(
                IntentKind.MARKET_DATA to listOf(1f, 0f),
                IntentKind.INVESTMENT_EDUCATION to listOf(-1f, 0f),
                IntentKind.GENERAL to listOf(0f, 1f),
            ),
        )

        assertEquals(IntentKind.GENERAL, classification.kind)
        assertEquals(IntentSource.EMBEDDING, classification.source)
        assertEquals(1f, classification.confidence)
        assertTrue(classification.entities.isEmpty())
    }

    @Test
    fun companyMentionDoesNotOverrideConfidentGeneralIntent() {
        val classification = EmbeddingFirstIntentRecognizer().classifyEmbeddingVectors(
            question = "帮我写一段贵州茅台的品牌介绍",
            questionVector = listOf(0f, 1f),
            prototypeVectors = mapOf(
                IntentKind.MARKET_DATA to listOf(1f, 0f),
                IntentKind.INVESTMENT_EDUCATION to listOf(-1f, 0f),
                IntentKind.GENERAL to listOf(0f, 1f),
            ),
        )

        assertEquals(IntentKind.GENERAL, classification.kind)
        assertEquals("sh600519", classification.entities.single().providerSymbol)
    }

    @Test
    fun explicitCodeKeepsMarketIntentWhenEmbeddingIsAmbiguous() {
        val classification = EmbeddingFirstIntentRecognizer().classifyEmbeddingVectors(
            question = "600519怎么样？",
            questionVector = listOf(1f, 0f),
            prototypeVectors = mapOf(
                IntentKind.MARKET_DATA to listOf(1f, 0f),
                IntentKind.INVESTMENT_EDUCATION to listOf(1f, 0.01f),
                IntentKind.GENERAL to listOf(0f, 1f),
            ),
        )

        assertEquals(IntentKind.MARKET_DATA, classification.kind)
        assertEquals("sh600519", classification.entities.single().providerSymbol)
    }

    @Test
    fun invokesLlmFallbackWhenEmbeddingConfidenceIsBelowThreshold() {
        val history = listOf(ChatHistoryItem(ChatRole.USER, "我们聊点别的"))
        var fallbackQuestion = ""
        var fallbackHistory = emptyList<ChatHistoryItem>()
        val fallback = object : IntentLlmFallback {
            override fun classify(
                question: String,
                history: List<ChatHistoryItem>,
                callback: (IntentClassification?) -> Unit,
            ) {
                fallbackQuestion = question
                fallbackHistory = history
                callback(
                    IntentClassification(
                        kind = IntentKind.GENERAL,
                        confidence = 0.91f,
                        source = IntentSource.NONE,
                    )
                )
            }
        }
        val recognizer = EmbeddingFirstIntentRecognizer(
            embeddingProvider = TextEmbeddingProvider { text ->
                when (text) {
                    IntentPrototypeCatalog.anchors.getValue(IntentKind.MARKET_DATA) ->
                        listOf(1f, 0f)
                    IntentPrototypeCatalog.anchors.getValue(IntentKind.INVESTMENT_EDUCATION) ->
                        listOf(0f, 1f)
                    IntentPrototypeCatalog.anchors.getValue(IntentKind.GENERAL) ->
                        listOf(-1f, 0f)
                    else -> listOf(0f, -1f)
                }
            },
            fallback = fallback,
        )
        var result: IntentClassification? = null

        recognizer.classify("解释一下量子纠缠", history) { result = it }

        assertEquals("解释一下量子纠缠", fallbackQuestion)
        assertEquals(history, fallbackHistory)
        assertEquals(IntentKind.GENERAL, result?.kind)
        assertEquals(IntentSource.LLM, result?.source)
        assertEquals(0.91f, result?.confidence)
    }

    @Test
    fun rejectsEmbeddingWhenTopCandidatesHaveInsufficientMargin() {
        val classification = EmbeddingFirstIntentRecognizer().classifyEmbeddingVectors(
            question = "这件事你怎么看？",
            questionVector = listOf(1f, 0f),
            prototypeVectors = mapOf(
                IntentKind.MARKET_DATA to listOf(1f, 0f),
                IntentKind.INVESTMENT_EDUCATION to listOf(0f, 1f),
                IntentKind.GENERAL to listOf(1f, 0.01f),
            ),
        )

        assertEquals(IntentKind.UNKNOWN, classification.kind)
        assertEquals(IntentSource.EMBEDDING, classification.source)
        assertTrue(classification.confidence > 0.99f)
    }

    @Test
    fun classifiesInvestmentLearningQuestionsWithLocalRules() {
        val recognizer = EmbeddingFirstIntentRecognizer()

        val buyingStocks = recognizer.localClassification("新手如何买股票？")
        val priceEarnings = recognizer.localClassification("市盈率是什么意思？")

        assertEquals(IntentKind.INVESTMENT_EDUCATION, buyingStocks.kind)
        assertEquals(IntentSource.LOCAL_RULE, buyingStocks.source)
        assertEquals(IntentKind.INVESTMENT_EDUCATION, priceEarnings.kind)
        assertEquals(IntentSource.LOCAL_RULE, priceEarnings.source)
    }

    @Test
    fun classifiesUnrelatedQuestionAsGeneralWithLocalRules() {
        val classification = EmbeddingFirstIntentRecognizer()
            .localClassification("周末去上海怎么玩？")

        assertEquals(IntentKind.GENERAL, classification.kind)
        assertEquals(IntentSource.LOCAL_RULE, classification.source)
        assertTrue(classification.entities.isEmpty())
    }

    @Test
    fun returnsUnknownForBlankQuestion() {
        val classification = EmbeddingFirstIntentRecognizer().classifyEmbedding("  \n ")

        assertEquals(IntentKind.UNKNOWN, classification.kind)
        assertEquals(IntentSource.NONE, classification.source)
        assertEquals(0f, classification.confidence)
    }
}
