package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.SpeechRecognitionResult
import com.guet.liang.stockchat.model.StockDetailResult
import com.guet.liang.stockchat.base.BridgeModule
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal data class MimoApiConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.xiaomimimo.com/v1",
    val chatModel: String = "mimo-v2.5",
    val asrModel: String = "mimo-v2.5-asr",
)

internal class MimoStockChatDataSource(
    private val networkModule: NetworkModule,
    private val config: MimoApiConfig,
    private val bridgeModule: BridgeModule? = null,
    private val useNativeStreaming: Boolean = false,
) : StockChatDataSource {

    override fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        attempt: Int,
        callback: (ChatAnswer) -> Unit,
    ) {
        if (config.apiKey.isBlank()) {
            callback(ChatAnswer.Failure(MISSING_API_KEY_MESSAGE))
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
                    put("content", question)
                }
            )
        }
        val requestBody = JSONObject().apply {
            put("model", config.chatModel)
            put("messages", messages)
            put("thinking", JSONObject().apply { put("type", "disabled") })
            put("max_completion_tokens", 1024)
            put("stream", true)
        }
        if (useNativeStreaming && bridgeModule != null) {
            streamWithNativeBridge(requestBody, question, callback)
        } else {
            request(requestBody) { response, error ->
                handleCompletedResponse(response, error, question, callback)
            }
        }
    }

    private fun handleCompletedResponse(
        response: JSONObject?,
        error: String?,
        question: String,
        callback: (ChatAnswer) -> Unit,
    ) {
            if (error != null) {
                callback(ChatAnswer.Failure(error))
                return
            }
            val directContent = response?.assistantContent().orEmpty()
            if (directContent.isNotEmpty()) {
                callback(ChatAnswer.Success(answerBlocks(directContent, question)))
                return
            }
            val streamDeltas = response?.streamDeltas().orEmpty()
            if (streamDeltas.isEmpty()) {
                callback(ChatAnswer.Failure("MiMo 没有返回可展示的回答，请稍后重试。"))
                return
            }
            var streamedContent = ""
            streamDeltas.forEach { delta ->
                streamedContent += delta
                callback(ChatAnswer.Streaming(streamedContent))
            }
            val content = streamedContent.trim()
            if (content.isEmpty()) {
                callback(ChatAnswer.Failure("MiMo 没有返回可展示的回答，请稍后重试。"))
                return
            }
            callback(ChatAnswer.Success(answerBlocks(content, question)))
    }

    private fun streamWithNativeBridge(
        requestBody: JSONObject,
        question: String,
        callback: (ChatAnswer) -> Unit,
    ) {
        var streamedContent = ""
        bridgeModule?.streamChatCompletion(config.apiKey, requestBody) { payload ->
            val success = payload?.optInt("success", 0) == 1
            if (!success) {
                callback(
                    ChatAnswer.Failure(
                        payload?.optString("errorMessage")?.ifBlank {
                            "MiMo 请求失败，请稍后重试。"
                        } ?: "MiMo 请求失败，请稍后重试."
                    )
                )
                return@streamChatCompletion
            }
            when (payload?.optString("event")) {
                "delta" -> {
                    val delta = payload.optString("content")
                    if (delta.isNotEmpty()) {
                        streamedContent += delta
                        callback(ChatAnswer.Streaming(streamedContent))
                    }
                }
                "end" -> {
                    val content = streamedContent.trim()
                    if (content.isEmpty()) {
                        callback(ChatAnswer.Failure("MiMo 没有返回可展示的回答，请稍后重试。"))
                    } else {
                        callback(ChatAnswer.Success(answerBlocks(content, question)))
                    }
                }
            }
        }
    }

    override fun stockDetail(symbol: String): StockDetailResult {
        return MockStockChatDataSource.stockDetail(symbol)
    }

    private fun request(
        body: JSONObject,
        callback: (JSONObject?, String?) -> Unit,
    ) {
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("api-key", config.apiKey)
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
                        ?: errorMessage.ifBlank { "MiMo 请求失败，请稍后重试。" },
                )
            } else {
                callback(data, null)
            }
        }
    }

    private fun answerBlocks(content: String, question: String): List<AnswerBlock> {
        val blocks = mutableListOf<AnswerBlock>(
            AnswerBlock.Markdown(
                source = content.trim(),
                fallbackText = content.trim(),
            )
        )
        MockStockChatDataSource.quoteForQuestion(question)?.let { quote ->
            blocks += AnswerBlock.MarketQuote(quote)
        }
        return blocks
    }

    companion object {
        const val MISSING_API_KEY_MESSAGE =
            "尚未配置 MiMo API Key，请在项目 local.properties 的 MIMO_API_KEY= 后填写。"

        private const val SYSTEM_PROMPT =
            "你是 StockMate，一名中文股票研究助手。请用简洁 Markdown 回答股票、指数和市场问题。" +
                "不得声称掌握未提供的实时行情，不得编造价格或确定性收益；不确定时要明确说明。" +
                "回答应给出观察依据、主要风险，并以‘仅供参考，不构成投资建议’结尾。"
    }
}

internal class MimoSpeechRecognitionService(
    private val networkModule: NetworkModule,
    private val config: MimoApiConfig,
) {
    fun transcribe(
        audioBase64: String,
        mimeType: String,
        callback: (SpeechRecognitionResult) -> Unit,
    ) {
        if (config.apiKey.isBlank()) {
            callback(SpeechRecognitionResult.Failure(MimoStockChatDataSource.MISSING_API_KEY_MESSAGE))
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

private fun JSONObject.assistantContent(): String? {
    return optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optString("content")
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
                JSONObject(payload)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    ?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }
        .toList()
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
