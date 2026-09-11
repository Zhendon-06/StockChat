package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.base.BridgeModule
import com.guet.liang.stockchat.base.streamChatCompletion
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatRole
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Builds provider-compatible chat request payloads without owning transport state. */
internal object ChatCompletionRequestBuilder {
    fun build(
        systemPrompt: String,
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        useAliyunExtensions: Boolean,
        stream: Boolean,
    ): JSONObject {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            history.forEach { item ->
                put(JSONObject().apply {
                    put("role", if (item.role == ChatRole.USER) "user" else "assistant")
                    put("content", item.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userContent(question, images))
            })
        }
        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (useAliyunExtensions) {
                put("thinking", JSONObject().apply { put("type", "disabled") })
                put("max_completion_tokens", COMPLETION_TOKEN_BUDGET)
            } else {
                put("max_tokens", COMPLETION_TOKEN_BUDGET)
            }
            put("stream", stream)
        }
    }

    private fun userContent(question: String, images: List<String>): Any {
        if (images.isEmpty()) return question
        return JSONArray().apply {
            images.forEach { imageUrl ->
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply { put("url", imageUrl) })
                })
            }
            put(JSONObject().apply {
                put("type", "text")
                put("text", question)
            })
        }
    }

    private const val COMPLETION_TOKEN_BUDGET = 1024
}

/** Executes OpenAI-compatible chat requests over HTTP or the native streaming bridge. */
internal class StockChatCompletionClient(
    private val networkModule: NetworkModule,
    private val config: AliyunApiConfig,
    private val bridgeModule: BridgeModule?,
    private val useNativeStreaming: Boolean,
    private val systemPrompt: String,
) {
    fun answer(
        question: String,
        history: List<ChatHistoryItem>,
        images: List<String>,
        model: String,
        callback: (ChatAnswer) -> Unit,
    ) {
        val streaming = useNativeStreaming && config.supportsStreaming && bridgeModule != null
        val body = ChatCompletionRequestBuilder.build(
            systemPrompt = systemPrompt,
            question = question,
            history = history,
            images = images,
            model = model,
            useAliyunExtensions = config.useAliyunExtensions,
            stream = streaming,
        )
        if (streaming) {
            stream(body, callback)
        } else {
            request(body) { response, error -> handleResponse(response, error, callback) }
        }
    }

    fun request(body: JSONObject, callback: (JSONObject?, String?) -> Unit) {
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${config.apiKey}")
        }
        networkModule.httpRequest(
            url = "${config.baseUrl.trimEnd('/')}/chat/completions",
            isPost = true,
            param = body,
            headers = headers,
            timeout = REQUEST_TIMEOUT_SECONDS,
        ) { data, success, errorMessage, response ->
            val statusCode = response.statusCode
            if (!success || (statusCode != null && statusCode !in 200..299)) {
                callback(
                    null,
                    data.apiErrorMessage()
                        ?: errorMessage.apiErrorMessage()
                        ?: errorMessage.ifBlank { "${config.providerDisplayName} 请求失败，请稍后重试。" },
                )
            } else {
                callback(data, null)
            }
        }
    }

    private fun stream(body: JSONObject, callback: (ChatAnswer) -> Unit) {
        val streamUrl = "${config.baseUrl.trimEnd('/')}/chat/completions"
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            put("Authorization", "Bearer ${config.apiKey}")
        }
        val fallback = { request(body) { response, error -> handleResponse(response, error, callback) } }
        bridgeModule?.streamChatCompletion(
            apiKey = config.apiKey,
            url = streamUrl,
            requestBody = body,
            headers = headers,
            providerDisplayName = config.providerDisplayName,
            responseCallbackFn = StreamSession(config.providerDisplayName, callback, fallback)::onPayload,
        )
    }

    private fun handleResponse(response: JSONObject?, error: String?, callback: (ChatAnswer) -> Unit) {
        if (error != null) {
            callback(ChatAnswer.Failure(error))
            return
        }
        val directContent = response?.assistantContent().orEmpty()
        if (directContent.isNotEmpty()) {
            callback(ChatAnswer.Success(answerBlocks(directContent)))
            return
        }
        val content = response?.streamDeltas().orEmpty().joinToString("").trim()
        callback(if (content.isEmpty()) ChatAnswer.Failure(emptyAnswerMessage()) else ChatAnswer.Success(answerBlocks(content)))
    }

    private fun answerBlocks(content: String): List<AnswerBlock> {
        val text = content.trim()
        return listOf(AnswerBlock.Markdown(source = text, fallbackText = text))
    }

    private fun emptyAnswerMessage(): String = "${config.providerDisplayName} 没有返回可展示的回答，请稍后重试。"

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 60
    }
}

/** Tracks one native stream and emits terminal answers exactly once. */
private class StreamSession(
    private val providerDisplayName: String,
    private val callback: (ChatAnswer) -> Unit,
    private val fallback: () -> Unit,
) {
    private var content = ""
    private var terminal = false

    fun onPayload(payload: JSONObject?) {
        if (terminal) return
        if (payload?.optInt("success", 0) != 1) {
            handleFailure(payload)
            return
        }
        when (payload.optString("event")) {
            "delta" -> append(payload.optString("content"))
            "end" -> complete()
        }
    }

    private fun handleFailure(payload: JSONObject?) {
        if (payload?.optString("errorCode") == "STREAM_UNAVAILABLE") {
            terminal = true
            fallback()
            return
        }
        terminal = true
        val defaultMessage = "$providerDisplayName 请求失败，请稍后重试。"
        callback(ChatAnswer.Failure(payload?.optString("errorMessage")?.ifBlank { defaultMessage } ?: defaultMessage))
    }

    private fun append(delta: String) {
        if (delta.isNotEmpty()) {
            content += delta
            callback(ChatAnswer.Streaming(content))
        }
    }

    private fun complete() {
        terminal = true
        val text = content.trim()
        callback(
            if (text.isEmpty()) {
                ChatAnswer.Failure("$providerDisplayName 没有返回可展示的回答，请稍后重试。")
            } else {
                ChatAnswer.Success(listOf(AnswerBlock.Markdown(text, text)))
            }
        )
    }
}
