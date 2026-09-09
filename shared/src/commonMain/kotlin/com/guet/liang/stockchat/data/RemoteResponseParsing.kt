package com.guet.liang.stockchat.data

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// 远端响应解析：助手正文/音频提取、流式增量与错误信息。

internal fun JSONObject.assistantContent(): String? {
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

internal fun JSONObject.assistantAudioData(): String? {
    return optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optJSONObject("audio")
        ?.optString("data")
}

internal fun JSONObject.streamDeltas(): List<String> {
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

internal fun JSONObject.apiErrorMessage(): String? {
    val message = optJSONObject("error")?.optString("message").orEmpty().trim()
    return message.ifEmpty { null }
}

internal fun String.apiErrorMessage(): String? {
    if (isBlank()) {
        return null
    }
    return runCatching { JSONObject(this).apiErrorMessage() }.getOrNull()
}
