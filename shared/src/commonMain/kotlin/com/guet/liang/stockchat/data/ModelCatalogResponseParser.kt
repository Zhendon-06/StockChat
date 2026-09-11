package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ModelOption
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Parser for the OpenAI-compatible `{ "data": [{ "id": "..." }] }` response. */
internal object ModelCatalogResponseParser {
    fun parse(response: JSONObject): List<ModelOption> = parseModels(response)

    fun parseModels(response: JSONObject): List<ModelOption> {
        val models = response.optJSONArray("data")
            ?: response.optJSONArray("models")
            ?: return emptyList()
        return (0 until models.length()).mapNotNull { models.optJSONObject(it)?.let(::parseModel) }.distinctBy(ModelOption::id)
    }

    private fun parseModel(model: JSONObject): ModelOption? {
        val id = model.optString("id").orEmpty().trim()
        if (id.isBlank()) return null
        val providerName = listOf("name", "display_name", "displayName").firstNotNullOfOrNull { key ->
            model.optString(key).orEmpty().trim().takeIf(String::isNotBlank)
        }
        return ModelOption(
            id = id,
            // Gateways often shorten names (for example, "Deepseek Flash")
            // while the canonical version remains in `id`. Keep both visible
            // so users can identify the exact model they are selecting.
            displayName = providerName
                ?.takeIf { name -> name.equals(id, ignoreCase = true) }
                ?: providerName?.let { name -> "$name ($id)" }
                ?: displayNameFor(id),
            contextWindowLabel = contextWindowLabel(model, id),
            capabilities = ModelCatalogCapabilities.inferCapabilities(model, id),
        )
    }

    private fun displayNameFor(id: String): String {
        return id.split('-', '_').filter(String::isNotBlank).joinToString(" ") { part ->
            part.replaceFirstChar { character -> character.uppercase() }
        }
    }

    private fun inferContextWindow(id: String): String {
        val normalizedId = id.lowercase()
        CONTEXT_PATTERN.find(normalizedId)?.let { match ->
            val value = match.groupValues[1]
            return when (match.groupValues[2]) {
                "m" -> "${value}M"
                else -> "${value}K"
            }
        }
        return when {
            normalizedId.contains("gemini") -> "1M"
            normalizedId.contains("claude") -> "200K"
            normalizedId.contains("qwen") || normalizedId.contains("glm") -> "128K"
            normalizedId.contains("deepseek") -> "64K"
            normalizedId.contains("kimi") || normalizedId.contains("moonshot") -> "128K"
            normalizedId.contains("gpt-4") -> "128K"
            else -> "未知"
        }
    }

    private fun contextWindowLabel(model: JSONObject, id: String): String {
        val contextLength = listOf(
            "context_length",
            "context_window",
            "max_context_length",
            "max_model_len",
            "num_ctx",
            "input_token_limit",
            "max_input_tokens",
        )
            .firstNotNullOfOrNull { key ->
                model.optInt(key, 0).takeIf { it > 0 }
                    ?: model.optString(key).orEmpty().trim().toIntOrNull()?.takeIf { it > 0 }
            }
        if (contextLength != null) {
            return if (contextLength >= TOKENS_PER_KIBI * TOKENS_PER_KIBI) {
                "${contextLength / (TOKENS_PER_KIBI * TOKENS_PER_KIBI)}M"
            } else if (contextLength >= TOKENS_PER_KIBI) {
                "${contextLength / TOKENS_PER_KIBI}K"
            } else {
                contextLength.toString()
            }
        }
        return inferContextWindow(id)
    }

    private val CONTEXT_PATTERN = Regex("(\\d+(?:\\.\\d+)?)(k|m)(?:b)?(?:[-_]?(?:context|ctx))?")
    private const val TOKENS_PER_KIBI = 1024
}
