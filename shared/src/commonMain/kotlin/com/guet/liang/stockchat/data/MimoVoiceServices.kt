package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.SpeechRecognitionResult
import com.guet.liang.stockchat.model.SpeechSynthesisResult
import com.guet.liang.stockchat.base.BridgeModule
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// MiMo 语音服务：语音识别与语音合成。

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
