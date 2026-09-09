package com.guet.liang.stockchat.data

// 远端服务配置：阿里云兼容接口与 MiMo 语音接口。

/** 阿里云百炼 OpenAI 兼容接口地址，同时是默认服务商与预测请求的兜底 baseUrl。 */
internal const val DEFAULT_CHAT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

internal data class AliyunApiConfig(
    val apiKey: String,
    val baseUrl: String = DEFAULT_CHAT_BASE_URL,
    val chatModel: String = "",
    val visionModel: String = "",
    val webSearchModel: String = "qwen-plus",
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
