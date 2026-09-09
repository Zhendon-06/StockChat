@file:Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList", "UnusedParameter", "MagicNumber")
package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.base.streamChatCompletion
import com.guet.liang.stockchat.base.BridgeModule
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MarketDataResult
import com.guet.liang.stockchat.model.TencentMarketSnapshot
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// OpenAI 兼容聊天数据源：流式/非流式问答与错误处理。

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class AliyunStockChatDataSource(
    private val networkModule: NetworkModule,
    private val config: AliyunApiConfig,
    private val bridgeModule: BridgeModule? = null,
    private val useNativeStreaming: Boolean = false,
) : StockChatDataSource {
    private val marketDataService = TencentMarketDataService(networkModule)
    private val intentRecognitionService = LlmIntentRecognitionService(config, ::request)
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
        intentRecognitionService.classify(question, history, model) { result ->
            when (result) {
                is IntentRecognitionResult.Failure -> callback(ChatAnswer.Failure(result.message))
                is IntentRecognitionResult.Success -> {
                    val plan = SecuritiesQueryRouter.route(result.classification)
                    if (plan != null) {
                        securitiesResolver.resolve(question, history, model, plan.searchEntities) { resolution ->
                            when (resolution) {
                                is SecuritiesResolutionResult.Failure -> callback(ChatAnswer.Failure(resolution.message))
                                is SecuritiesResolutionResult.Success -> answerMarketQuery(
                                    question, history, model,
                                    plan.copy(targets = resolution.targets, notices = plan.notices + resolution.notices),
                                    callback,
                                )
                            }
                        }
                    } else {
                        answerWithAi(question, history, emptyList(), model, emptyList(), null, attempt, callback)
                    }
                }
            }
        }
    }

    private fun answerMarketQuery(
        question: String,
        history: List<ChatHistoryItem>,
        model: String,
        plan: SecuritiesQueryPlan,
        callback: (ChatAnswer) -> Unit,
    ) {
        if (plan.targets.isEmpty()) {
            if (plan.needsAi && plan.notices.isNotEmpty()) {
                answerWithAi(question, history, emptyList(), model, emptyList(), plan, 0, callback)
            } else {
                callback(ChatAnswer.Success(marketAnswerBlocks(plan, emptyList(), aiUnavailable = false)))
            }
            return
        }
        marketDataService.load(plan) { result ->
            when (result) {
                is MarketDataResult.Success -> {
                    val resolvedPlan = plan.copy(notices = plan.notices + result.notices)
                    if (plan.needsAi && config.apiKey.isNotBlank()) {
                        answerWithAi(
                            question = question,
                            history = history,
                            images = emptyList(),
                            model = model,
                            snapshots = result.snapshots,
                            plan = resolvedPlan,
                            attempt = 0,
                            callback = callback,
                        )
                    } else {
                        callback(
                            ChatAnswer.Success(
                                marketAnswerBlocks(
                                    plan = resolvedPlan,
                                    snapshots = result.snapshots,
                                    aiUnavailable = plan.needsAi,
                                )
                            )
                        )
                    }
                }
                MarketDataResult.Empty -> callback(
                    ChatAnswer.Failure("AI 已识别标的，但行情服务暂未返回数据，请稍后重新生成。" +
                        plan.notices.joinToString(prefix = "\n", separator = "\n"))
                )
                is MarketDataResult.Failure -> callback(
                    ChatAnswer.Failure((listOf(result.message) + plan.notices).joinToString("\n"))
                )
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
                callback(ChatAnswer.Failure(MISSING_API_KEY_MESSAGE))
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
        val questionWithMarketContext = listOf(
            question,
            if (snapshots.isEmpty()) "" else marketContext(snapshots),
            plan?.notices?.joinToString("\n").orEmpty(),
        ).filter(String::isNotBlank).joinToString("\n\n")
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
            put("stream", useNativeStreaming && config.supportsStreaming && bridgeModule != null)
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
                callback(ChatAnswer.Success(answerBlocks(directContent, snapshots, plan)))
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
            // 降级网络请求已收完 SSE，合并后只更新一次；逐片回放会在
            // 鸿蒙 UI 线程上重复解析、布局整段 Markdown，导致长回答卡死。
            val content = streamDeltas.joinToString("").trim()
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
            callback(ChatAnswer.Success(answerBlocks(content, snapshots, plan)))
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
                            callback(ChatAnswer.Success(answerBlocks(content, snapshots, plan)))
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
        plan: SecuritiesQueryPlan?,
    ): List<AnswerBlock> {
        val text = listOf(content.trim(), plan?.notices?.joinToString("\n").orEmpty())
            .filter(String::isNotBlank).joinToString("\n\n")
        return buildList {
            add(
                AnswerBlock.Markdown(
                    source = text,
                    fallbackText = text,
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
        val headline = if (snapshots.isEmpty()) "" else when (plan.intent) {
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
        val notices = plan.notices.joinToString("\n")
        val markdown = "StockChat Demo 信息。$headline$aiNotice\n\n$notices\n\n数据来源：腾讯证券公开行情接口；" +
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
                "未提供新闻、公告或基本面证据时，不得臆测涨跌原因。" +
                "不得声称掌握未提供的实时行情，不得编造价格、事实或确定性收益，不得使用‘稳赚’‘必涨’等绝对表述；不确定时要明确说明。" +
                "涉及行情或投资判断时应注明是 StockChat Demo 信息，并给出观察依据和主要风险。" +
                "每次涉及投资判断的回答结尾必须写明：‘以上观点仅供参考，不构成投资建议；投资决策由用户结合自身情况自主作出并承担相应风险。’"
    }
}
