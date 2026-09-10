@file:Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList", "UnusedParameter", "MagicNumber")
package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.base.streamChatCompletion
import com.guet.liang.stockchat.base.BridgeModule
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.AnswerMode
import com.guet.liang.stockchat.model.MarketDataResult
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// OpenAI 兼容聊天数据源，两条请求分支：
//  1. 聊天分支：携带会话上下文（问 A + 答 B + 问 D），支持流式与响应缓存，负责回答正文。
//  2. 标的分支：只发送当前这条消息（问 D），单次无上下文，返回结构化标的后查腾讯行情生成卡片。
// AnswerMode.FAST 两条分支并行，正文先出、卡片随后附上；AnswerMode.PRECISE 先跑标的分支（百炼强制联网），
// 再把实时行情注入聊天提示词，正文可引用实时数字。两种模式都由 ParallelAnswerJoin 合并输出。

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class AliyunStockChatDataSource(
    private val networkModule: NetworkModule,
    private val config: AliyunApiConfig,
    private val bridgeModule: BridgeModule? = null,
    private val useNativeStreaming: Boolean = true,
) : StockChatDataSource {
    private val marketDataService = TencentMarketDataService(networkModule)
    private val stockMentionExtractor = LlmStockMentionExtractor(config, ::request)
    private val securitySearch = TencentSecuritySearchService(networkModule)
    private val securitySelector = LlmSecurityCandidateSelector(config, ::request)
    private val securitiesResolver = SecuritiesSearchResolver(securitySearch::search, securitySelector::select)

    override fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    ) {
        if (images.isNotEmpty() && !config.supportsVision) {
            callback(ChatAnswer.Failure(visionUnsupportedMessage(model)))
            return
        }
        if (config.apiKey.isBlank()) {
            callback(ChatAnswer.Failure(MISSING_API_KEY_MESSAGE))
            return
        }
        val normalizedHistory = if (
            history.lastOrNull()?.role == ChatRole.USER &&
            history.lastOrNull()?.content?.trim() == question.trim()
        ) history.dropLast(1) else history
        val selectedModel = if (images.isEmpty()) model.ifBlank { config.chatModel } else config.visionModel
        val turn = ChatTurn(question, normalizedHistory, images, model, selectedModel)
        when {
            images.isNotEmpty() -> answerWithContext(turn, MarketBranchOutcome.NONE, callback)
            config.answerMode == AnswerMode.PRECISE -> answerPrecise(turn, callback)
            else -> answerFast(turn, callback)
        }
    }

    /** One user turn after history normalisation; shared by both answering modes. */
    private data class ChatTurn(
        val question: String,
        val history: List<ChatHistoryItem>,
        val images: List<String>,
        val model: String,
        val selectedModel: String,
    )

    /**
     * FAST: chat and stock branches start together. The text streams at once, cards attach as soon
     * as Tencent answers, and the model never sees live numbers.
     */
    private fun answerFast(turn: ChatTurn, callback: (ChatAnswer) -> Unit) {
        val contextHistory = ContextWindowManager.trim(turn.history, turn.question, config.contextWindowTokens)
        // The cache key covers the whole conversation context, so a repeated turn with the same
        // history replays both the chat text and the quote cards without touching the network.
        val cacheKey = aiResponseCacheKey(config, turn.selectedModel, turn.question, contextHistory, turn.images)
        AiResponseCache.get(cacheKey)?.let { cachedBlocks ->
            replayCachedAnswer(cachedBlocks, callback)
            return
        }
        val join = ParallelAnswerJoin(callback) { blocks -> AiResponseCache.put(cacheKey, blocks) }
        answerWithAi(turn.question, contextHistory, turn.images, turn.selectedModel, join::onChat)
        startMarketBranch(turn.question, turn.model, forcedWebSearch = false, join::onMarket)
    }

    /**
     * PRECISE: the stock branch runs first (with web research on DashScope), then the chat request
     * receives the live quotes in its prompt so the text can cite real numbers.
     */
    private fun answerPrecise(turn: ChatTurn, callback: (ChatAnswer) -> Unit) {
        startMarketBranch(turn.question, turn.model, forcedWebSearch = true) { outcome ->
            answerWithContext(turn, outcome, callback)
        }
    }

    /** Chat request whose prompt already contains whatever the stock branch found. */
    private fun answerWithContext(turn: ChatTurn, outcome: MarketBranchOutcome, callback: (ChatAnswer) -> Unit) {
        val questionWithMarketContext = listOf(
            turn.question,
            if (outcome.snapshots.isEmpty()) "" else marketContextPrompt(outcome.snapshots),
            outcome.notices.joinToString("\n"),
        ).filter(String::isNotBlank).joinToString("\n\n")
        val contextHistory = ContextWindowManager.trim(turn.history, questionWithMarketContext, config.contextWindowTokens)
        val cacheKey = aiResponseCacheKey(config, turn.selectedModel, questionWithMarketContext, contextHistory, turn.images)
        AiResponseCache.get(cacheKey)?.let { cachedBlocks ->
            replayCachedAnswer(cachedBlocks, callback)
            return
        }
        val join = ParallelAnswerJoin(callback) { blocks -> AiResponseCache.put(cacheKey, blocks) }
        // Cards are already known, so they ride along with the very first streamed delta.
        join.onMarket(outcome)
        answerWithAi(questionWithMarketContext, contextHistory, turn.images, turn.selectedModel, join::onChat)
    }

    /**
     * Stock branch: extract → Tencent search → (LLM confirmation only for unresolved names) → quotes.
     * Only the current question is sent; conversation history never reaches this branch.
     */
    private fun startMarketBranch(
        question: String,
        model: String,
        forcedWebSearch: Boolean,
        onOutcome: (MarketBranchOutcome) -> Unit,
    ) {
        stockMentionExtractor.extract(question, model, forcedWebSearch) { result ->
            when (result) {
                is StockMentionResult.Failure -> onOutcome(
                    MarketBranchOutcome(notices = listOf("行情卡片暂不可用：${result.message}"))
                )
                is StockMentionResult.Success -> {
                    val plan = SecuritiesQueryRouter.route(result.extraction)
                    if (plan == null) {
                        onOutcome(MarketBranchOutcome.NONE)
                        return@extract
                    }
                    securitiesResolver.resolve(question, emptyList(), model, plan.searchEntities) { resolution ->
                        when (resolution) {
                            is SecuritiesResolutionResult.Failure -> onOutcome(
                                MarketBranchOutcome(plan = plan, notices = plan.notices + resolution.message)
                            )
                            is SecuritiesResolutionResult.Success -> loadMarketData(
                                plan.copy(targets = resolution.targets, notices = plan.notices + resolution.notices),
                                onOutcome,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadMarketData(plan: SecuritiesQueryPlan, onOutcome: (MarketBranchOutcome) -> Unit) {
        if (plan.targets.isEmpty()) {
            onOutcome(MarketBranchOutcome(plan = plan, notices = plan.notices))
            return
        }
        marketDataService.load(plan) { result ->
            onOutcome(
                when (result) {
                    is MarketDataResult.Success -> MarketBranchOutcome(plan, result.snapshots, plan.notices + result.notices)
                    MarketDataResult.Empty -> MarketBranchOutcome(
                        plan = plan,
                        notices = plan.notices + "AI 已识别标的，但行情服务暂未返回数据，请稍后重新生成。",
                    )
                    is MarketDataResult.Failure -> MarketBranchOutcome(plan = plan, notices = plan.notices + result.message)
                }
            )
        }
    }

    /** Chat branch: the only request that carries conversation history. */
    private fun answerWithAi(
        question: String,
        contextHistory: List<ChatHistoryItem>,
        images: List<String>,
        selectedModel: String,
        callback: (ChatAnswer) -> Unit,
    ) {
        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
            )
            contextHistory.forEach { item ->
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
                            question
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
                                        put("text", question)
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
        val streaming = useNativeStreaming && config.supportsStreaming && bridgeModule != null
        val requestBody = JSONObject().apply {
            put("model", selectedModel)
            put("messages", messages)
            if (config.useAliyunExtensions) {
                put("thinking", JSONObject().apply { put("type", "disabled") })
                put("max_completion_tokens", 1024)
            } else {
                put("max_tokens", 1024)
            }
            put("stream", streaming)
        }
        if (streaming) {
            streamWithNativeBridge(requestBody, callback)
        } else {
            request(requestBody) { response, error -> handleCompletedResponse(response, error, callback) }
        }
    }

    private fun handleCompletedResponse(
        response: JSONObject?,
        error: String?,
        callback: (ChatAnswer) -> Unit,
    ) {
        if (error != null) {
            callback(ChatAnswer.Failure(error))
            return
        }
        val directContent = response?.assistantContent().orEmpty()
        if (directContent.isNotEmpty()) {
            callback(ChatAnswer.Success(chatAnswerBlocks(directContent)))
            return
        }
        // 降级网络请求已收完 SSE，合并后只更新一次；逐片回放会在
        // 鸿蒙 UI 线程上重复解析、布局整段 Markdown，导致长回答卡死。
        val content = response?.streamDeltas().orEmpty().joinToString("").trim()
        if (content.isEmpty()) {
            callback(ChatAnswer.Failure(emptyAnswerMessage()))
        } else {
            callback(ChatAnswer.Success(chatAnswerBlocks(content)))
        }
    }

    private fun streamWithNativeBridge(
        requestBody: JSONObject,
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
                                handleCompletedResponse(response, error, callback)
                            }
                        }
                        return@streamChatCompletion
                    }
                    terminalEventReceived = true
                    val fallbackMessage = "${config.providerDisplayName} 请求失败，请稍后重试。"
                    callback(ChatAnswer.Failure(payload?.optString("errorMessage")?.ifBlank { fallbackMessage } ?: fallbackMessage))
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
                            callback(ChatAnswer.Failure(emptyAnswerMessage()))
                        } else {
                            callback(ChatAnswer.Success(chatAnswerBlocks(content)))
                        }
                    }
                }
            },
        )
    }

    private fun replayCachedAnswer(blocks: List<AnswerBlock>, callback: (ChatAnswer) -> Unit) {
        val markdown = blocks.filterIsInstance<AnswerBlock.Markdown>().firstOrNull()?.source.orEmpty()
        val cards = blocks.filterIsInstance<AnswerBlock.MarketQuote>()
        if (config.supportsStreaming && markdown.isNotBlank()) {
            val step = 48
            var end = step
            while (end < markdown.length) {
                callback(ChatAnswer.Streaming(markdown.substring(0, end), cards))
                end += step
            }
        }
        callback(ChatAnswer.Success(blocks, fromCache = true))
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

    private fun chatAnswerBlocks(content: String): List<AnswerBlock> {
        val text = content.trim()
        return listOf(AnswerBlock.Markdown(source = text, fallbackText = text))
    }

    private fun emptyAnswerMessage(): String = "${config.providerDisplayName} 没有返回可展示的回答，请稍后重试。"

    private fun visionUnsupportedMessage(model: String): String {
        val modelName = model.ifBlank { config.chatModel }
        return "${config.providerDisplayName} 的当前模型 $modelName 不支持图片理解，" +
            "请切换到带“视觉理解”能力的模型后重试。"
    }

    companion object {
        const val MISSING_API_KEY_MESSAGE =
            "尚未配置模型服务 API Key，请在模型配置页面填写。"

        private const val SYSTEM_PROMPT =
            "你是 StockMate，一名面向股票资讯、市场研究和投资决策辅助的中文 AI 助手。" +
                "请用简洁、结构清晰的 Markdown 回答行情、个股、指数、组合、投资入门和金融知识，也可以回答其他通用问题；" +
                "不要因为问题没有公司名称或证券代码而拒绝回答。" +
                "对于涉及个股、指数、组合或市场走势判断的问题，必须明确给出投资意见和方向性观点，至少使用‘看多/偏多/中性/偏空/谨慎观望’或‘关注/持有/减仓/回避’等清晰表述；" +
                "不能只复述行情、罗列风险，或只说‘无法提供投资建议’，也不能用免责声明代替观点。" +
                "观点必须说明适用时间周期、关键依据、可能推翻判断的条件、主要风险和下一步观察点；" +
                "信息不足时，先指出缺失信息和不确定性，再基于现有事实给出条件化观点或中性倾向，不得拒绝给出任何判断。" +
                "回答中新增具体股票或指数时，必须同时给出可核验的交易所代码（如 sh600519）；" +
                "无法确认代码时应明确标记待确认，不得把未经核验的标的写成确定推荐。" +
                "当用户消息附带腾讯证券行情工具数据时，实时数字只能引用该数据并注明数据时间；" +
                "未附带时，系统会另行获取用户提到标的的实时行情并以卡片展示在回答下方，" +
                "此时你没有实时行情数据，不得编造或猜测当前价格与涨跌，涉及实时数字时请引导用户查看行情卡片。" +
                "历史消息中形如“[行情标的:代码|名称] 时间，现价 …”的内容是此前卡片记录的行情，引用时必须注明其时间。" +
                "未提供新闻、公告或基本面证据时，不得臆测涨跌原因。" +
                "不得声称掌握未提供的实时行情，不得编造价格、事实或确定性收益，不得使用‘稳赚’‘必涨’等绝对表述；不确定时要明确说明。" +
                "涉及行情或投资判断时应注明是 StockChat Demo 信息，并给出观察依据和主要风险。" +
                "每次涉及投资判断的回答结尾必须写明：‘以上观点仅供参考，不构成投资建议；投资决策由用户结合自身情况自主作出并承担相应风险。’"
    }
}
