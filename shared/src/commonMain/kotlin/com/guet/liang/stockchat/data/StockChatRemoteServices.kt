package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.SpeechRecognitionResult
import com.guet.liang.stockchat.model.SpeechSynthesisResult
import com.guet.liang.stockchat.base.BridgeModule
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal data class AliyunApiConfig(
    val apiKey: String,
    val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    val chatModel: String = "",
    val visionModel: String = "",
    val embeddingModel: String = "text-embedding-v4",
    val providerDisplayName: String = "阿里云百炼",
    val useAliyunExtensions: Boolean = true,
    val supportsVision: Boolean = true,
    val supportsStreaming: Boolean = true,
)

internal data class MimoVoiceApiConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.xiaomimimo.com/v1",
    val asrModel: String = "mimo-v2.5-asr",
    val ttsModel: String = "mimo-v2.5-tts",
    val ttsVoice: String = "mimo_default",
)

internal class AliyunStockChatDataSource(
    private val networkModule: NetworkModule,
    private val config: AliyunApiConfig,
    private val bridgeModule: BridgeModule? = null,
    private val useNativeStreaming: Boolean = false,
) : StockChatDataSource {
    private val marketDataService = TencentMarketDataService(networkModule)
    private val intentRecognitionService = if (config.useAliyunExtensions) {
        DashScopeIntentRecognitionService(networkModule, config)
    } else {
        null
    }
    private val localIntentRecognizer = EmbeddingFirstIntentRecognizer()

    override fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    ) {
        if (images.isNotEmpty()) {
            if (!config.supportsVision) {
                callback(ChatAnswer.Failure(visionUnsupportedMessage(model)))
                return
            }
            answerWithAi(
                question = question,
                history = history,
                images = images,
                model = model,
                snapshots = emptyList(),
                plan = null,
                attempt = attempt,
                callback = callback,
            )
            return
        }
        val recognitionService = intentRecognitionService
        if (recognitionService == null) {
            answerClassifiedQuestion(
                question,
                history,
                model,
                attempt,
                localIntentRecognizer.localClassification(question, history),
                callback,
            )
            return
        }
        recognitionService.classify(question, history) { classification ->
            answerClassifiedQuestion(
                question,
                history,
                model,
                attempt,
                classification,
                callback,
            )
        }
    }

    private fun answerClassifiedQuestion(
        question: String,
        history: List<ChatHistoryItem>,
        model: String,
        attempt: Int,
        classification: IntentClassification,
        callback: (ChatAnswer) -> Unit,
    ) {
        val marketPlan = if (classification.kind == IntentKind.MARKET_DATA) {
            SecuritiesQueryRouter.route(question, history)
                ?: SecuritiesQueryRouter.route(
                    question = question,
                    history = history,
                    assumeMarketIntent = true,
                )
        } else {
            null
        }
        if (marketPlan != null) {
            answerMarketQuery(question, history, model, marketPlan, callback)
        } else {
            answerWithAi(
                question = question,
                history = history,
                images = emptyList(),
                model = model,
                snapshots = emptyList(),
                plan = null,
                attempt = attempt,
                callback = callback,
            )
        }
    }

    private fun answerMarketQuery(
        question: String,
        history: List<ChatHistoryItem>,
        model: String,
        plan: SecuritiesQueryPlan,
        callback: (ChatAnswer) -> Unit,
    ) {
        marketDataService.load(plan) { result ->
            when (result) {
                is MarketDataResult.Success -> {
                    if (plan.needsAi && config.apiKey.isNotBlank()) {
                        answerWithAi(
                            question = question,
                            history = history,
                            images = emptyList(),
                            model = model,
                            snapshots = result.snapshots,
                            plan = plan,
                            attempt = 0,
                            callback = callback,
                        )
                    } else {
                        callback(
                            ChatAnswer.Success(
                                marketAnswerBlocks(
                                    plan = plan,
                                    snapshots = result.snapshots,
                                    aiUnavailable = plan.needsAi,
                                )
                            )
                        )
                    }
                }
                MarketDataResult.Empty -> {
                    if (plan.targets.isEmpty()) {
                        answerWithAi(
                            question = question,
                            history = history,
                            images = emptyList(),
                            model = model,
                            snapshots = emptyList(),
                            plan = null,
                            attempt = 0,
                            callback = callback,
                        )
                    } else {
                        callback(
                            ChatAnswer.Failure(
                                "未找到对应证券，请尝试输入完整名称、六位代码或带交易所的代码。"
                            )
                        )
                    }
                }
                is MarketDataResult.Failure -> callback(ChatAnswer.Failure(result.message))
            }
        }
    }

    private fun answerWithAi(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        snapshots: List<TencentMarketSnapshot>,
        plan: SecuritiesQueryPlan?,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    ) {
        if (config.apiKey.isBlank()) {
            if (snapshots.isNotEmpty() && plan != null) {
                callback(
                    ChatAnswer.Success(
                        marketAnswerBlocks(plan, snapshots, aiUnavailable = true)
                    )
                )
            } else {
                MockStockChatDataSource.answer(question, history, images, model, attempt, callback)
            }
            return
        }

        val normalizedHistory = if (
            history.lastOrNull()?.role == ChatRole.USER &&
            history.lastOrNull()?.content?.trim() == question.trim()
        ) {
            history.dropLast(1)
        } else {
            history
        }
        val questionWithMarketContext = if (snapshots.isEmpty()) {
            question
        } else {
            "$question\n\n${marketContext(snapshots)}"
        }
        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
            )
            normalizedHistory.forEach { item ->
                put(
                    JSONObject().apply {
                        put("role", if (item.role == ChatRole.USER) "user" else "assistant")
                        put("content", item.content)
                    }
                )
            }
            put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "content",
                        if (images.isEmpty()) {
                            questionWithMarketContext
                        } else {
                            JSONArray().apply {
                                images.forEach { imageUrl ->
                                    put(
                                        JSONObject().apply {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                JSONObject().apply { put("url", imageUrl) },
                                            )
                                        }
                                    )
                                }
                                put(
                                    JSONObject().apply {
                                        put("type", "text")
                                        put("text", questionWithMarketContext)
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
        val requestBody = JSONObject().apply {
            put(
                "model",
                if (images.isEmpty()) model.ifBlank { config.chatModel } else config.visionModel,
            )
            put("messages", messages)
            if (config.useAliyunExtensions) {
                put("thinking", JSONObject().apply { put("type", "disabled") })
                put("max_completion_tokens", 1024)
            } else {
                put("max_tokens", 1024)
            }
            put("stream", config.supportsStreaming)
        }
        if (useNativeStreaming && config.supportsStreaming && bridgeModule != null) {
            streamWithNativeBridge(
                requestBody = requestBody,
                plan = plan,
                snapshots = snapshots,
                callback = callback,
            )
        } else {
            request(requestBody) { response, error ->
                handleCompletedResponse(response, error, plan, snapshots, callback)
            }
        }
    }

    private fun handleCompletedResponse(
        response: JSONObject?,
        error: String?,
        plan: SecuritiesQueryPlan?,
        snapshots: List<TencentMarketSnapshot>,
        callback: (ChatAnswer) -> Unit,
    ) {
            if (error != null) {
                callback(aiFailureOrMarketFallback(error, plan, snapshots))
                return
            }
            val directContent = response?.assistantContent().orEmpty()
            if (directContent.isNotEmpty()) {
                callback(ChatAnswer.Success(answerBlocks(directContent, snapshots)))
                return
            }
            val streamDeltas = response?.streamDeltas().orEmpty()
            if (streamDeltas.isEmpty()) {
                callback(
                    aiFailureOrMarketFallback(
                        "${config.providerDisplayName} 没有返回可展示的回答，请稍后重试。",
                        plan,
                        snapshots,
                    )
                )
                return
            }
            var streamedContent = ""
            streamDeltas.forEach { delta ->
                streamedContent += delta
                callback(ChatAnswer.Streaming(streamedContent))
            }
            val content = streamedContent.trim()
            if (content.isEmpty()) {
                callback(
                    aiFailureOrMarketFallback(
                        "${config.providerDisplayName} 没有返回可展示的回答，请稍后重试。",
                        plan,
                        snapshots,
                    )
                )
                return
            }
            callback(ChatAnswer.Success(answerBlocks(content, snapshots)))
    }

    private fun streamWithNativeBridge(
        requestBody: JSONObject,
        plan: SecuritiesQueryPlan?,
        snapshots: List<TencentMarketSnapshot>,
        callback: (ChatAnswer) -> Unit,
    ) {
        var streamedContent = ""
        var networkFallbackStarted = false
        var terminalEventReceived = false
        val streamUrl = "${config.baseUrl.trimEnd('/')}/chat/completions"
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${config.apiKey}")
        }
        bridgeModule?.streamChatCompletion(
            apiKey = config.apiKey,
            url = streamUrl,
            requestBody = requestBody,
            headers = headers,
            providerDisplayName = config.providerDisplayName,
            responseCallbackFn = { payload ->
                if (terminalEventReceived) {
                    return@streamChatCompletion
                }
                val success = payload?.optInt("success", 0) == 1
                if (!success) {
                    if (payload?.optString("errorCode") == "STREAM_UNAVAILABLE") {
                        if (!networkFallbackStarted) {
                            networkFallbackStarted = true
                            terminalEventReceived = true
                            request(requestBody) { response, error ->
                                handleCompletedResponse(response, error, plan, snapshots, callback)
                            }
                        }
                        return@streamChatCompletion
                    }
                    terminalEventReceived = true
                    callback(
                        aiFailureOrMarketFallback(
                            payload?.optString("errorMessage")?.ifBlank {
                                "${config.providerDisplayName} 请求失败，请稍后重试。"
                            } ?: "${config.providerDisplayName} 请求失败，请稍后重试。",
                            plan,
                            snapshots,
                        )
                    )
                    return@streamChatCompletion
                }
                when (payload?.optString("event")) {
                    "delta" -> {
                        val delta = payload.optString("content")
                        if (delta.isNotEmpty() && !terminalEventReceived) {
                            streamedContent += delta
                            callback(ChatAnswer.Streaming(streamedContent))
                        }
                    }
                    "end" -> {
                        terminalEventReceived = true
                        val content = streamedContent.trim()
                        if (content.isEmpty()) {
                            callback(
                                aiFailureOrMarketFallback(
                                    "${config.providerDisplayName} 没有返回可展示的回答，请稍后重试。",
                                    plan,
                                    snapshots,
                                )
                            )
                        } else {
                            callback(ChatAnswer.Success(answerBlocks(content, snapshots)))
                        }
                    }
                }
            },
        )
    }

    private fun request(
        body: JSONObject,
        callback: (JSONObject?, String?) -> Unit,
    ) {
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${config.apiKey}")
        }
        networkModule.httpRequest(
            url = "${config.baseUrl.trimEnd('/')}/chat/completions",
            isPost = true,
            param = body,
            headers = headers,
            timeout = 60,
        ) { data, success, errorMessage, response ->
            val statusCode = response.statusCode
            if (!success || (statusCode != null && statusCode !in 200..299)) {
                callback(
                    null,
                    data.apiErrorMessage()
                        ?: errorMessage.apiErrorMessage()
                        ?: errorMessage.ifBlank {
                            "${config.providerDisplayName} 请求失败，请稍后重试。"
                        },
                )
            } else {
                callback(data, null)
            }
        }
    }

    private fun answerBlocks(
        content: String,
        snapshots: List<TencentMarketSnapshot>,
    ): List<AnswerBlock> {
        return buildList {
            add(
                AnswerBlock.Markdown(
                    source = content.trim(),
                    fallbackText = content.trim(),
                )
            )
            snapshots.forEach { snapshot ->
                add(AnswerBlock.MarketQuote(snapshot.quote))
            }
        }
    }

    private fun marketAnswerBlocks(
        plan: SecuritiesQueryPlan,
        snapshots: List<TencentMarketSnapshot>,
        aiUnavailable: Boolean,
    ): List<AnswerBlock> {
        val names = snapshots.joinToString("、") { snapshot ->
            "${snapshot.quote.name}（${snapshot.quote.symbol}）"
        }
        val headline = when (plan.intent) {
            SecuritiesIntent.QUOTE -> "已获取 $names 的最新行情快照。"
            SecuritiesIntent.TREND -> "已获取 $names 的最新行情与走势数据。"
            SecuritiesIntent.COMPARE -> "已获取 $names 的同期行情，可通过卡片对比价格与涨跌幅。"
            SecuritiesIntent.ANALYSIS -> "已获取 $names 的最新行情。"
        }
        val aiNotice = if (aiUnavailable) {
            "\n\nAI 深度解读当前不可用，先展示可核验的行情数据。"
        } else {
            ""
        }
        val markdown = "StockChat Demo 信息。$headline$aiNotice\n\n数据来源：腾讯证券公开行情接口；" +
            "行情时间以卡片标注为准。仅供参考，不构成投资建议。"
        return buildList {
            add(AnswerBlock.Markdown(markdown, markdown))
            snapshots.forEach { snapshot ->
                add(AnswerBlock.MarketQuote(snapshot.quote))
            }
        }
    }

    private fun marketContext(snapshots: List<TencentMarketSnapshot>): String {
        val lines = snapshots.joinToString("\n") { snapshot ->
            val quote = snapshot.quote
            val trend = quote.trendPoints.takeLast(10).joinToString(",")
            "- ${quote.name}（${quote.symbol}，${snapshot.providerSymbol}）：" +
                "现价 ${quote.price}，涨跌 ${quote.change}（${quote.changePercent}），" +
                "昨收 ${snapshot.previousClose}，今开 ${snapshot.open}，最高 ${snapshot.high}，" +
                "最低 ${snapshot.low}，成交量 ${snapshot.volume} ${snapshot.volumeUnit}，" +
                "成交额 ${snapshot.amount} ${snapshot.amountUnit}，" +
                "换手率 ${snapshot.turnoverRate}%，市盈率 ${snapshot.priceEarningsRatio}，" +
                "振幅 ${snapshot.amplitude}%，最近走势点（从旧到新）[$trend]，${quote.updatedAt}"
        }
        return "以下是本次请求刚获取的腾讯证券行情工具数据，实时数字只能引用这些字段：\n$lines"
    }

    private fun aiFailureOrMarketFallback(
        message: String,
        plan: SecuritiesQueryPlan?,
        snapshots: List<TencentMarketSnapshot>,
    ): ChatAnswer {
        return if (plan != null && snapshots.isNotEmpty()) {
            ChatAnswer.Success(marketAnswerBlocks(plan, snapshots, aiUnavailable = true))
        } else {
            ChatAnswer.Failure(message)
        }
    }

    private fun visionUnsupportedMessage(model: String): String {
        val modelName = model.ifBlank { config.chatModel }
        return "${config.providerDisplayName} 的当前模型 $modelName 不支持图片理解，" +
            "请切换到带“视觉理解”能力的模型后重试。"
    }

    companion object {
        const val MISSING_API_KEY_MESSAGE =
            "尚未配置模型服务 API Key，请在模型配置页面填写。"

        private const val SYSTEM_PROMPT =
            "你是 StockMate，一名以股票研究和投资教育为特色的中文 AI 助手。" +
                "请用简洁 Markdown 回答行情、投资入门、金融知识，也可以正常回答其他通用问题；" +
                "不要因为问题没有公司名称或证券代码而拒绝回答。" +
                "回答中新增具体股票或指数时，必须同时给出可核验的交易所代码（如 sh600519）；" +
                "无法确认代码时应明确说明，不得把它当作确定标的推荐。" +
                "当用户消息附带腾讯证券行情工具数据时，实时数字只能引用该数据并注明数据时间；" +
                "未提供新闻、公告或基本面证据时，不得臆测涨跌原因。" +
                "不得声称掌握未提供的实时行情，不得编造价格或确定性收益；不确定时要明确说明。" +
                "涉及行情或投资判断时应注明是 StockChat Demo 信息，给出观察依据、主要风险，" +
                "并以‘仅供参考，不构成投资建议’结尾。"
    }
}

internal class MimoSpeechRecognitionService(
    private val networkModule: NetworkModule,
    private val config: MimoVoiceApiConfig,
) {
    val isConfigured: Boolean
        get() = config.apiKey.isNotBlank()

    fun transcribe(
        audioBase64: String,
        mimeType: String,
        callback: (SpeechRecognitionResult) -> Unit,
    ) {
        if (config.apiKey.isBlank()) {
            callback(SpeechRecognitionResult.Failure(MIMO_VOICE_MISSING_API_KEY_MESSAGE))
            return
        }
        if (audioBase64.isBlank()) {
            callback(SpeechRecognitionResult.Failure("没有录到有效语音，请重试。"))
            return
        }

        val audioContent = JSONObject().apply {
            put("type", "input_audio")
            put(
                "input_audio",
                JSONObject().apply {
                    put("data", "data:${mimeType.ifBlank { "audio/wav" }};base64,$audioBase64")
                }
            )
        }
        val requestBody = JSONObject().apply {
            put("model", config.asrModel)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply { put(audioContent) })
                        }
                    )
                }
            )
            put("asr_options", JSONObject().apply { put("language", "auto") })
            put("stream", false)
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("api-key", config.apiKey)
        }
        networkModule.httpRequest(
            url = "${config.baseUrl.trimEnd('/')}/chat/completions",
            isPost = true,
            param = requestBody,
            headers = headers,
            timeout = 90,
        ) { data, success, errorMessage, response ->
            val statusCode = response.statusCode
            if (!success || (statusCode != null && statusCode !in 200..299)) {
                callback(
                    SpeechRecognitionResult.Failure(
                        data.apiErrorMessage()
                            ?: errorMessage.apiErrorMessage()
                            ?: errorMessage.ifBlank { "MiMo 语音识别失败，请稍后重试。" }
                    )
                )
                return@httpRequest
            }
            val text = data.assistantContent().orEmpty().trim()
            if (text.isEmpty()) {
                callback(SpeechRecognitionResult.Failure("MiMo 未识别出文字，请靠近麦克风后重试。"))
            } else {
                callback(SpeechRecognitionResult.Success(text))
            }
        }
    }
}

internal class MimoSpeechSynthesisService(
    private val networkModule: NetworkModule,
    private val config: MimoVoiceApiConfig,
    private val bridgeModule: BridgeModule? = null,
    private val useNativeStreaming: Boolean = false,
) {
    val isConfigured: Boolean
        get() = config.apiKey.isNotBlank()

    fun synthesize(
        text: String,
        callback: (SpeechSynthesisResult) -> Unit,
    ) {
        val normalizedText = text.trim()
        if (config.apiKey.isBlank()) {
            callback(SpeechSynthesisResult.Failure(MIMO_VOICE_MISSING_API_KEY_MESSAGE))
            return
        }
        if (normalizedText.isEmpty()) {
            callback(SpeechSynthesisResult.Failure("没有可朗读的文本。"))
            return
        }

        val requestBody = JSONObject().apply {
            put("model", config.ttsModel)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "请用自然、清晰、沉稳的中文播报语气朗读。")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "assistant")
                            put("content", normalizedText)
                        }
                    )
                }
            )
            put(
                "audio",
                JSONObject().apply {
                    put("format", "wav")
                    put("voice", config.ttsVoice)
                }
            )
            put("stream", false)
        }
        if (useNativeStreaming && bridgeModule != null) {
            requestBody.put(
                "audio",
                JSONObject().apply {
                    put("format", "pcm16")
                    put("voice", config.ttsVoice)
                }
            )
            requestBody.put("stream", true)
            bridgeModule.streamSpeechSynthesis(
                apiKey = config.apiKey,
                url = "${config.baseUrl.trimEnd('/')}/chat/completions",
                requestBody = requestBody,
            ) { payload ->
                if (payload?.optInt("success", 0) != 1) {
                    callback(
                        SpeechSynthesisResult.Failure(
                            payload?.optString("errorMessage")?.ifBlank {
                                "MiMo 语音生成失败，请稍后重试。"
                            } ?: "MiMo 语音生成失败，请稍后重试。"
                        )
                    )
                } else {
                    when (payload.optString("event")) {
                        "start" -> callback(SpeechSynthesisResult.Started)
                        "end" -> callback(SpeechSynthesisResult.Completed)
                    }
                }
            }
            return
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("api-key", config.apiKey)
        }
        networkModule.httpRequest(
            url = "${config.baseUrl.trimEnd('/')}/chat/completions",
            isPost = true,
            param = requestBody,
            headers = headers,
            timeout = 90,
        ) { data, success, errorMessage, response ->
            val statusCode = response.statusCode
            if (!success || (statusCode != null && statusCode !in 200..299)) {
                callback(
                    SpeechSynthesisResult.Failure(
                        data.apiErrorMessage()
                            ?: errorMessage.apiErrorMessage()
                            ?: errorMessage.ifBlank { "MiMo 语音生成失败，请稍后重试。" }
                    )
                )
                return@httpRequest
            }
            val audioBase64 = data.assistantAudioData().orEmpty().trim()
            if (audioBase64.isEmpty()) {
                callback(SpeechSynthesisResult.Failure("MiMo 没有返回可播放的语音。"))
            } else {
                callback(
                    SpeechSynthesisResult.Success(
                        audioBase64 = audioBase64,
                        mimeType = "audio/wav",
                    )
                )
            }
        }
    }
}

internal const val MIMO_VOICE_MISSING_API_KEY_MESSAGE =
    "尚未配置 MiMo 语音 API Key，请在项目 local.properties 的 MIMO_VOICE_API_KEY= 后填写。"

private fun JSONObject.assistantContent(): String? {
    val choice = optJSONArray("choices")?.optJSONObject(0)
    return listOf(
        choice?.optJSONObject("message")?.opt("content"),
        choice?.optJSONObject("delta")?.opt("content"),
        optJSONObject("output")
            ?.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.opt("content"),
        optJSONObject("output")
            ?.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.opt("content"),
        opt("content"),
    )
        .asSequence()
        .map(::contentText)
        .firstOrNull(String::isNotBlank)
}

private fun JSONObject.assistantAudioData(): String? {
    return optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optJSONObject("audio")
        ?.optString("data")
}

private fun JSONObject.streamDeltas(): List<String> {
    val rawData = optString("data").orEmpty()
    if (rawData.isBlank()) {
        return emptyList()
    }
    return rawData
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("data:") }
        .mapNotNull { line ->
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") {
                return@mapNotNull null
            }
            runCatching {
                JSONObject(payload).streamPayloadContent().takeIf(String::isNotEmpty)
            }.getOrNull()
        }
        .toList()
}

private fun JSONObject.streamPayloadContent(): String {
    val choice = optJSONArray("choices")?.optJSONObject(0)
    return listOf(
        choice?.optJSONObject("delta")?.opt("content"),
        choice?.optJSONObject("message")?.opt("content"),
        optJSONObject("output")
            ?.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.opt("content"),
        optJSONObject("output")
            ?.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.opt("content"),
        opt("content"),
    )
        .asSequence()
        .map(::contentText)
        .firstOrNull(String::isNotBlank)
        .orEmpty()
}

private fun contentText(value: Any?): String {
    return when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (index in 0 until value.length()) {
                val part = contentText(value.opt(index))
                if (part.isNotEmpty()) {
                    append(part)
                }
            }
        }
        is JSONObject -> listOf(value.opt("text"), value.opt("content"))
            .asSequence()
            .map(::contentText)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
        else -> ""
    }
}

private fun JSONObject.apiErrorMessage(): String? {
    val message = optJSONObject("error")?.optString("message").orEmpty().trim()
    return message.ifEmpty { null }
}

private fun String.apiErrorMessage(): String? {
    if (isBlank()) {
        return null
    }
    return runCatching { JSONObject(this).apiErrorMessage() }.getOrNull()
}
