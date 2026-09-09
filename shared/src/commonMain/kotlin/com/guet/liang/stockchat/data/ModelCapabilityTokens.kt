package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ModelCapability

internal fun modelCapabilityForToken(value: String): ModelCapability? {
    val token = value.trim().lowercase().replace('-', '_')
    return CAPABILITY_TOKEN_RULES.firstOrNull { rule ->
        token in rule.tokens || rule.fragments.any(token::contains)
    }?.capability
}

/** Provider capability aliases are evaluated in the same precedence order as the original parser. */
private data class CapabilityTokenRule(val capability: ModelCapability, val tokens: Set<String>, val fragments: List<String>)

private val CAPABILITY_TOKEN_RULES = listOf(
    CapabilityTokenRule(
        ModelCapability.VISION,
        setOf(
            "vision", "visual", "image", "images", "image_input", "image_inputs",
            "multimodal", "multimodal_input", "video", "video_input",
        ),
        listOf("vision", "image_input", "multimodal"),
    ),
    CapabilityTokenRule(ModelCapability.REASONING, setOf("reasoning", "think", "thinking"), listOf("reason", "think")),
    CapabilityTokenRule(
        ModelCapability.VOICE, setOf("voice", "audio", "speech", "tts", "asr", "realtime"), listOf("audio", "speech", "voice"),
    ),
    CapabilityTokenRule(
        ModelCapability.STREAMING,
        setOf("stream", "streaming", "sse", "stream_output", "streaming_output", "streamable"), listOf("stream"),
    ),
    CapabilityTokenRule(ModelCapability.CHAT, setOf("chat", "text_input", "text_output", "text"), emptyList()),
)
