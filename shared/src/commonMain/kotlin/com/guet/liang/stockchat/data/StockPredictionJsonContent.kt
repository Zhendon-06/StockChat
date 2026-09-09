package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionPoint
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal fun normalizeJsonContent(content: String): String? {
    val trimmed = content
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .trim()
    if (trimmed.isBlank()) return null
    val fencedStart = trimmed.indexOf("```")
    if (fencedStart >= 0) {
        val bodyStart = trimmed.indexOf('\n', fencedStart)
        val fencedEnd = if (bodyStart >= 0) trimmed.indexOf("```", bodyStart + 1) else -1
        if (bodyStart >= 0 && fencedEnd > bodyStart) {
            extractJsonObject(trimmed.substring(bodyStart + 1, fencedEnd))?.let { return it }
        }
    }
    return extractJsonObject(trimmed)
}

private fun extractJsonObject(value: String): String? {
    val start = value.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until value.length) {
        val character = value[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == '"') {
                inString = false
            }
            continue
        }
        when (character) {
            '"' -> inString = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return value.substring(start, index + 1).trim()
                }
            }
        }
    }
    return null
}

internal fun extractAssistantContent(response: JSONObject): String? {
    val choice = response.optJSONArray("choices")?.optJSONObject(0)
    return listOf(
        choice?.optJSONObject("message")?.opt("content"),
        choice?.optJSONObject("delta")?.opt("content"),
        response.optJSONObject("output")?.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.opt("content"),
        response.optJSONObject("output")?.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("delta")?.opt("content"),
        response.opt("output_text"),
        response.opt("content"),
    ).asSequence().map(::contentText).firstOrNull(String::isNotBlank)
}

private fun contentText(value: Any?): String {
    return when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (index in 0 until value.length()) append(contentText(value.opt(index)))
        }
        is JSONObject -> listOf(value.opt("text"), value.opt("content"), value.opt("value"))
            .asSequence().map(::contentText).firstOrNull(String::isNotBlank).orEmpty()
        else -> ""
    }
}

internal fun Any?.toText(): String {
    return when (this) {
        is String -> this
        null -> ""
        else -> toString()
    }
}

internal fun Any?.toNumericDouble(): Double? {
    return when (this) {
        is Number -> toDouble()
        is String -> trim().replace(",", "").toDoubleOrNull()
        else -> toString().replace(",", "").toDoubleOrNull()
    }?.takeIf(Double::isFinite)
}

internal fun Any?.toPercentNumericDouble(): Double? {
    val text = toText().trim()
    if (!text.endsWith('%')) return null
    return text.dropLast(1).replace(",", "").toDoubleOrNull()
        ?.takeIf(Double::isFinite)
}

internal fun Float?.isValidPrice(): Boolean {
    return this != null && isFinite() && this > 0f
}

internal fun String.toPredictionPrice(): Float? {
    return replace(",", "").trim().toFloatOrNull()
}
