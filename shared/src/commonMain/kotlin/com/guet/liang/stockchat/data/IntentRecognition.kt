package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.sqrt

internal enum class IntentKind {
    MARKET_DATA,
    INVESTMENT_EDUCATION,
    GENERAL,
    UNKNOWN,
}

internal enum class IntentSource {
    EMBEDDING,
    LLM,
    LOCAL_RULE,
    NONE,
}

internal data class IntentEntity(
    val kind: String,
    val value: String,
    val providerSymbol: String = "",
)

internal data class IntentClassification(
    val kind: IntentKind,
    val confidence: Float,
    val source: IntentSource,
    val entities: List<IntentEntity> = emptyList(),
)

internal fun interface TextEmbeddingProvider {
    fun embed(text: String): List<Float>
}

internal interface IntentLlmFallback {
    fun classify(
        question: String,
        history: List<ChatHistoryItem>,
        callback: (IntentClassification?) -> Unit,
    )
}

internal object IntentPrototypeCatalog {
    val kinds = listOf(
        IntentKind.MARKET_DATA,
        IntentKind.INVESTMENT_EDUCATION,
        IntentKind.GENERAL,
    )

    val anchors = mapOf(
        IntentKind.MARKET_DATA to "股票行情 现价 股价 涨跌 涨幅 跌幅 走势 趋势 K线 分时 指数 大盘 成交量",
        IntentKind.INVESTMENT_EDUCATION to "什么是市盈率 如何炒股 投资基础 选股方法 分散投资风险 新手入门 基本面 技术分析",
        IntentKind.GENERAL to "天气 编程 写邮件 翻译 旅游 生活闲聊 故事 数学 工作学习 通用问题",
    )

    fun anchorTexts(): List<String> = kinds.map { anchors.getValue(it) }
}

internal class EmbeddingFirstIntentRecognizer(
    private val embeddingProvider: TextEmbeddingProvider = LocalTextEmbeddingProvider,
    private val fallback: IntentLlmFallback? = null,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val minimumMargin: Float = DEFAULT_MINIMUM_MARGIN,
) {
    private val localPrototypeVectors: Map<IntentKind, List<Float>> by lazy {
        IntentPrototypeCatalog.kinds.associateWith { kind ->
            embeddingProvider.embed(IntentPrototypeCatalog.anchors.getValue(kind))
        }
    }

    fun classifyEmbedding(
        question: String,
        history: List<ChatHistoryItem> = emptyList(),
    ): IntentClassification {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isEmpty()) {
            return IntentClassification(IntentKind.UNKNOWN, 0f, IntentSource.NONE)
        }
        val vector = runCatching { embeddingProvider.embed(normalizedQuestion) }.getOrNull()
        if (vector.isNullOrEmpty()) {
            return localClassification(normalizedQuestion, history)
        }
        return classifyEmbeddingVectors(
            question = normalizedQuestion,
            history = history,
            questionVector = vector,
            prototypeVectors = localPrototypeVectors,
        )
    }

    fun classifyEmbeddingVectors(
        question: String,
        history: List<ChatHistoryItem> = emptyList(),
        questionVector: List<Float>,
        prototypeVectors: Map<IntentKind, List<Float>>,
    ): IntentClassification {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isEmpty() || questionVector.isEmpty()) {
            return IntentClassification(IntentKind.UNKNOWN, 0f, IntentSource.NONE)
        }
        val scores = prototypeVectors.mapValues { (_, vector) ->
            cosineSimilarity(questionVector, vector)
        }
        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull()
            ?: return localClassification(normalizedQuestion, history)
        val similarity = best.value.coerceIn(-1f, 1f)
        val margin = similarity - (ranked.getOrNull(1)?.value ?: -1f)
        val entities = entitiesFor(normalizedQuestion, history)
        val hasConfidentEmbedding = similarity >= confidenceThreshold && margin >= minimumMargin
        val hasExplicitMarketSignal = entities.isNotEmpty() &&
            containsMarketDataSignal(normalizedQuestion)
        val kind = when {
            hasConfidentEmbedding -> best.key
            hasExplicitMarketSignal -> IntentKind.MARKET_DATA
            else -> IntentKind.UNKNOWN
        }
        return IntentClassification(
            kind = kind,
            confidence = if (hasExplicitMarketSignal) {
                similarity.coerceAtLeast(0.8f)
            } else {
                similarity.coerceIn(0f, 1f)
            },
            source = IntentSource.EMBEDDING,
            entities = entities,
        )
    }

    fun classify(
        question: String,
        history: List<ChatHistoryItem> = emptyList(),
        callback: (IntentClassification) -> Unit,
    ) {
        val embedding = classifyEmbedding(question, history)
        if (embedding.kind != IntentKind.UNKNOWN) {
            callback(embedding)
            return
        }
        val llm = fallback
        if (llm == null) {
            callback(localClassification(question, history))
            return
        }
        llm.classify(question, history) { result ->
            callback(
                result?.let {
                    it.copy(
                        source = IntentSource.LLM,
                        confidence = it.confidence.coerceIn(0f, 1f),
                        entities = if (it.entities.isEmpty()) {
                            entitiesFor(question, history)
                        } else {
                            it.entities
                        },
                    )
                } ?: localClassification(question, history)
            )
        }
    }

    fun localClassification(
        question: String,
        history: List<ChatHistoryItem> = emptyList(),
    ): IntentClassification {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isEmpty()) {
            return IntentClassification(IntentKind.UNKNOWN, 0f, IntentSource.NONE)
        }
        val entities = entitiesFor(normalizedQuestion, history)
        if (entities.isNotEmpty() && containsMarketDataSignal(normalizedQuestion)) {
            return IntentClassification(IntentKind.MARKET_DATA, 0.99f, IntentSource.LOCAL_RULE, entities)
        }
        if (educationKeywords.any { normalizedQuestion.contains(it, ignoreCase = true) }) {
            return IntentClassification(
                IntentKind.INVESTMENT_EDUCATION,
                0.86f,
                IntentSource.LOCAL_RULE,
                entities,
            )
        }
        if (SecuritiesQueryRouter.route(normalizedQuestion, history) != null) {
            return IntentClassification(IntentKind.MARKET_DATA, 0.92f, IntentSource.LOCAL_RULE, entities)
        }
        return IntentClassification(IntentKind.GENERAL, 0.62f, IntentSource.LOCAL_RULE, entities)
    }

    private fun entitiesFor(
        question: String,
        history: List<ChatHistoryItem>,
    ): List<IntentEntity> {
        val plan = SecuritiesQueryRouter.route(question, history) ?: return emptyList()
        return buildList {
            plan.targets.forEach { target ->
                add(
                    IntentEntity(
                        kind = "security",
                        value = target.displayName.ifBlank { target.providerSymbol },
                        providerSymbol = target.providerSymbol,
                    )
                )
            }
            plan.unresolvedTerms.forEach { term ->
                add(IntentEntity(kind = "security_name", value = term))
            }
        }
    }

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.58f
        const val DEFAULT_MINIMUM_MARGIN = 0.04f
        private val educationKeywords = listOf(
            "炒股", "如何买股票", "怎么买股票", "股票怎么开户", "证券怎么开户", "新手",
            "股票怎么入门", "股市怎么入门", "投资怎么入门",
            "什么是", "是什么意思", "怎么计算",
            "如何理解", "选股方法", "分散投资", "投资基础", "基本面", "技术分析", "市盈率",
            "换手率", "成交量", "风险管理", "仓位管理", "止损",
        )
        private val marketDataKeywords = listOf(
            "行情", "现价", "股价", "多少钱", "涨跌", "涨幅", "跌幅", "走势", "趋势", "分时",
            "K线", "k线", "开盘", "收盘", "最高", "最低", "成交量", "成交额", "换手率", "市盈率",
            "估值", "分析", "解读", "能买吗", "值得买", "风险", "预测",
        )
        private val securityCodeRegex = Regex(
            "(?i)(?:(?:sh|sz|bj|hk)\\s*\\d{1,6}|\\d{6}|\\d{1,6}[.]?(?:sh|sz|bj|hk))"
        )

        private fun containsMarketDataSignal(question: String): Boolean {
            return securityCodeRegex.containsMatchIn(question) ||
                marketDataKeywords.any { question.contains(it, ignoreCase = true) }
        }

        private fun cosineSimilarity(left: List<Float>, right: List<Float>): Float {
            val size = minOf(left.size, right.size)
            if (size == 0) return 0f
            var dot = 0f
            var leftNorm = 0f
            var rightNorm = 0f
            for (index in 0 until size) {
                val a = left[index]
                val b = right[index]
                dot += a * b
                leftNorm += a * a
                rightNorm += b * b
            }
            val denominator = sqrt(leftNorm.toDouble() * rightNorm.toDouble()).toFloat()
            return if (denominator == 0f) 0f else (dot / denominator).coerceIn(-1f, 1f)
        }
    }
}

internal object LocalTextEmbeddingProvider : TextEmbeddingProvider {
    private const val DIMENSION = 96

    override fun embed(text: String): List<Float> {
        val vector = FloatArray(DIMENSION)
        val normalized = text.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        normalized.forEachIndexed { index, character ->
            val bucket = (character.code * 31 + index * 17).mod(DIMENSION)
            vector[bucket] += 1f
            if (index + 1 < normalized.length) {
                val next = normalized[index + 1]
                val pairBucket = (character.code * 131 + next.code * 17).mod(DIMENSION)
                vector[pairBucket] += 0.5f
            }
        }
        return vector.toList()
    }
}

internal class DashScopeIntentRecognitionService(
    private val networkModule: NetworkModule,
    private val config: AliyunApiConfig,
    private val confidenceThreshold: Float = EmbeddingFirstIntentRecognizer.DEFAULT_CONFIDENCE_THRESHOLD,
) {
    private val localRecognizer = EmbeddingFirstIntentRecognizer(
        confidenceThreshold = confidenceThreshold,
    )

    fun classify(
        question: String,
        history: List<ChatHistoryItem> = emptyList(),
        callback: (IntentClassification) -> Unit,
    ) {
        if (question.trim().isEmpty()) {
            callback(IntentClassification(IntentKind.UNKNOWN, 0f, IntentSource.NONE))
            return
        }
        if (config.apiKey.isBlank()) {
            callback(localRecognizer.localClassification(question, history))
            return
        }
        requestEmbeddings(question) { vectors ->
            if (vectors != null && vectors.size == IntentPrototypeCatalog.kinds.size + 1) {
                val prototypes = IntentPrototypeCatalog.kinds.mapIndexed { index, kind ->
                    kind to vectors[index + 1]
                }.toMap()
                val embedding = localRecognizer.classifyEmbeddingVectors(
                    question = question,
                    history = history,
                    questionVector = vectors.first(),
                    prototypeVectors = prototypes,
                )
                if (embedding.kind != IntentKind.UNKNOWN) {
                    callback(embedding)
                } else {
                    requestLlmClassification(question, history, callback)
                }
            } else {
                requestLlmClassification(question, history, callback)
            }
        }
    }

    private fun requestEmbeddings(
        question: String,
        callback: (List<List<Float>>?) -> Unit,
    ) {
        val input = JSONArray().apply {
            put(question)
            IntentPrototypeCatalog.anchorTexts().forEach(::put)
        }
        val body = JSONObject().apply {
            put("model", config.embeddingModel)
            put("input", input)
            put("encoding_format", "float")
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${config.apiKey}")
        }
        networkModule.httpRequest(
            url = "${config.baseUrl.trimEnd('/')}/embeddings",
            isPost = true,
            param = body,
            headers = headers,
            timeout = 15,
        ) { data, success, _, response ->
            if (!success || (response.statusCode != null && response.statusCode !in 200..299)) {
                callback(null)
                return@httpRequest
            }
            callback(parseEmbeddingVectors(data))
        }
    }

    private fun requestLlmClassification(
        question: String,
        history: List<ChatHistoryItem>,
        callback: (IntentClassification) -> Unit,
    ) {
        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", INTENT_CLASSIFIER_PROMPT)
                }
            )
            history.takeLast(6).forEach { item ->
                put(
                    JSONObject().apply {
                        put("role", if (item.role == ChatRole.USER) "user" else "assistant")
                        put("content", item.content)
                    }
                )
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", question)
            })
        }
        val body = JSONObject().apply {
            put("model", config.chatModel)
            put("messages", messages)
            put("temperature", 0)
            put("stream", false)
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${config.apiKey}")
        }
        networkModule.httpRequest(
            url = "${config.baseUrl.trimEnd('/')}/chat/completions",
            isPost = true,
            param = body,
            headers = headers,
            timeout = 20,
        ) { data, success, _, response ->
            if (!success || (response.statusCode != null && response.statusCode !in 200..299)) {
                callback(localRecognizer.localClassification(question, history))
                return@httpRequest
            }
            val content = data.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            val parsedKind = parseLlmKind(content)
            if (parsedKind == null) {
                callback(localRecognizer.localClassification(question, history))
            } else {
                callback(
                    IntentClassification(
                        kind = parsedKind,
                        confidence = 0.78f,
                        source = IntentSource.LLM,
                        entities = localRecognizer.localClassification(question, history).entities,
                    )
                )
            }
        }
    }

    private fun parseEmbeddingVectors(response: JSONObject): List<List<Float>>? {
        val outputEmbeddings = response.optJSONObject("output")?.optJSONArray("embeddings")
        val dataEmbeddings = response.optJSONArray("data")
        val rows = outputEmbeddings ?: dataEmbeddings ?: return null
        val vectors = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index)?.optJSONArray("embedding")
                    ?: rows.optJSONArray(index)
                    ?: return@buildList
                val vector = buildList {
                    for (itemIndex in 0 until row.length()) {
                        val value = row.optDouble(itemIndex, Double.NaN)
                        if (!value.isNaN()) add(value.toFloat())
                    }
                }
                if (vector.isNotEmpty()) add(vector)
            }
        }
        return vectors.takeIf { it.isNotEmpty() }
    }

    private fun parseLlmKind(content: String): IntentKind? {
        val normalized = content.trim().uppercase()
        val explicit = Regex("\\\"intent\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val value = if (explicit.isNotEmpty()) explicit else normalized
        return when {
            value.contains("MARKET_DATA") || value.contains("MARKET") ||
                value.contains("行情") || value.contains("股票") || value.contains("指数") ->
                IntentKind.MARKET_DATA
            value.contains("INVESTMENT_EDUCATION") || value.contains("EDUCATION") ||
                value.contains("投资") || value.contains("炒股") || value.contains("市盈率") ->
                IntentKind.INVESTMENT_EDUCATION
            value.contains("GENERAL") || value.contains("通用") || value.contains("闲聊") ->
                IntentKind.GENERAL
            else -> null
        }
    }

    companion object {
        private const val INTENT_CLASSIFIER_PROMPT =
            "将用户问题分类，只输出 JSON，不要解释：" +
                "{\"intent\":\"MARKET_DATA|INVESTMENT_EDUCATION|GENERAL\"}。" +
                "MARKET_DATA 仅表示需要调用当前行情工具获取价格、涨跌、走势、股票或指数数据；" +
                "INVESTMENT_EDUCATION 表示如何买股票、怎么炒股、开户、投资概念、风险和方法，" +
                "例如‘市盈率是什么’必须归为 INVESTMENT_EDUCATION；" +
                "GENERAL 表示其他问题。"
    }
}
