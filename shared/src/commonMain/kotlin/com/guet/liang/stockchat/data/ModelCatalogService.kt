package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelOption
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Result of loading the models exposed by an OpenAI-compatible endpoint. */
internal sealed class ModelCatalogResult {
    data class Success(val models: List<ModelOption>) : ModelCatalogResult()
    data class Failure(val message: String, val statusCode: Int? = null) : ModelCatalogResult()
}

private const val GENERIC_MODEL_CATALOG_ERROR = "模型列表请求失败，请检查 API Key 和服务地址。"

private val API_KEY_FIELD_PATTERN = Regex(
    "(?i)(?:api[\\s_-]*key|x-api-key|access[\\s_-]*token|refresh[\\s_-]*token|token)\\s*[:=]\\s*[\\\"']?[^\\s,;}\\\"']+",
)
private val AUTHORIZATION_FIELD_PATTERN = Regex(
    "(?i)authorization\\s*[:=]\\s*[\\\"']?bearer\\s+[^\\s,;}\\\"']+",
)
private val BEARER_PATTERN = Regex("(?i)bearer\\s+[^\\s,;}]+")
private val CREDENTIAL_ERROR_MARKER = Regex(
    "(?i)(api[\\s_-]*key|x-api-key|authorization|bearer|access[\\s_-]*token|refresh[\\s_-]*token|secret|credential|密钥|凭据|\\bkey\\b)",
)

internal fun sanitizeModelCatalogError(errorMessage: String, apiKey: String): String {
    val message = errorMessage.trim()
    if (message.isBlank()) {
        return ""
    }

    var sanitized = message
    val normalizedApiKey = apiKey.trim()
    if (normalizedApiKey.isNotEmpty()) {
        sanitized = sanitized.replace(
            Regex(Regex.escape(normalizedApiKey), RegexOption.IGNORE_CASE),
            "[REDACTED]",
        )
    }
    sanitized = sanitized
        .replace(API_KEY_FIELD_PATTERN, "API Key: [REDACTED]")
        .replace(AUTHORIZATION_FIELD_PATTERN, "Authorization: Bearer [REDACTED]")
        .replace(BEARER_PATTERN, "Bearer [REDACTED]")

    return if (CREDENTIAL_ERROR_MARKER.containsMatchIn(sanitized)) {
        GENERIC_MODEL_CATALOG_ERROR
    } else {
        sanitized
    }
}

internal class ModelCatalogService(
    private val networkModule: NetworkModule,
) {
    fun load(
        baseUrl: String,
        apiKey: String,
        callback: (ModelCatalogResult) -> Unit,
    ) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedApiKey = apiKey.trim()
        if (normalizedBaseUrl.isBlank()) {
            callback(ModelCatalogResult.Failure("请先填写模型服务地址。"))
            return
        }
        if (normalizedApiKey.isBlank()) {
            callback(ModelCatalogResult.Failure("请先填写 API Key。"))
            return
        }

        val headers = JSONObject().apply {
            put("Authorization", "Bearer $normalizedApiKey")
            put("Accept", "application/json")
        }
        networkModule.httpRequest(
            url = "$normalizedBaseUrl/models",
            isPost = false,
            param = JSONObject(),
            headers = headers,
            timeout = REQUEST_TIMEOUT_SECONDS,
        ) { data, success, errorMessage, response ->
            val statusCode = response.statusCode
            if (!success || (statusCode != null && statusCode !in 200..299)) {
                callback(
                    ModelCatalogResult.Failure(
                        sanitizeModelCatalogError(errorMessage, normalizedApiKey)
                            .ifBlank { GENERIC_MODEL_CATALOG_ERROR },
                        statusCode,
                    )
                )
                return@httpRequest
            }
            val hasModelsArray = data.optJSONArray("data") != null || data.optJSONArray("models") != null
            if (!hasModelsArray) {
                callback(ModelCatalogResult.Failure("模型服务返回的数据格式无效。", statusCode))
                return@httpRequest
            }
            callback(ModelCatalogResult.Success(ModelCatalogResponseParser.parseModels(data)))
        }
    }

    companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 30
    }
}

/** Parser for the OpenAI-compatible `{ "data": [{ "id": "..." }] }` response. */
internal object ModelCatalogResponseParser {
    fun parse(response: JSONObject): List<ModelOption> = parseModels(response)

    fun parseModels(response: JSONObject): List<ModelOption> {
        val models = response.optJSONArray("data")
            ?: response.optJSONArray("models")
            ?: return emptyList()
        return buildList {
            for (index in 0 until models.length()) {
                val model = models.optJSONObject(index) ?: continue
                val id = model.optString("id").orEmpty().trim()
                if (id.isBlank()) continue
                add(
                    ModelOption(
                        id = id,
                        displayName = listOf("name", "display_name", "displayName")
                            .firstNotNullOfOrNull { key ->
                                model.optString(key).orEmpty().trim().takeIf(String::isNotBlank)
                            }
                            ?: displayNameFor(id),
                        contextWindowLabel = contextWindowLabel(model, id),
                        capabilities = inferCapabilities(model, id),
                    )
                )
            }
        }.distinctBy(ModelOption::id)
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
            return if (contextLength >= 1024 * 1024) {
                "${contextLength / (1024 * 1024)}M"
            } else if (contextLength >= 1024) {
                "${contextLength / 1024}K"
            } else {
                contextLength.toString()
            }
        }
        return inferContextWindow(id)
    }

    private fun inferCapabilities(model: JSONObject, id: String): Set<ModelCapability> {
        val normalizedId = id.lowercase()
        return buildSet {
            add(ModelCapability.CHAT)
            if (VISION_MARKERS.any(normalizedId::contains)) add(ModelCapability.VISION)
            if (REASONING_MARKERS.any(normalizedId::contains)) add(ModelCapability.REASONING)
            if (VOICE_MARKERS.any(normalizedId::contains)) add(ModelCapability.VOICE)
            listOf("capabilities", "modalities").forEach { key ->
                val explicitCapabilities = model.optJSONArray(key) ?: return@forEach
                for (index in 0 until explicitCapabilities.length()) {
                    when (explicitCapabilities.optString(index).orEmpty().lowercase()) {
                        "vision", "image", "images", "multimodal" -> add(ModelCapability.VISION)
                        "reasoning", "think", "thinking" -> add(ModelCapability.REASONING)
                        "voice", "audio", "speech" -> add(ModelCapability.VOICE)
                        "chat", "text", "text_input", "text_output" -> add(ModelCapability.CHAT)
                    }
                }
            }
        }
    }

    private val CONTEXT_PATTERN = Regex("(\\d+(?:\\.\\d+)?)(k|m)(?:b)?(?:[-_]?(?:context|ctx))?")
    private val VISION_MARKERS = setOf("vision", "-vl", "_vl", "gpt-4o", "gemini", "claude-3")
    private val REASONING_MARKERS = setOf("reason", "thinking", "deepseek-r1", "-r1", "o1", "o3", "o4")
    private val VOICE_MARKERS = setOf("audio", "voice", "tts", "asr", "realtime")
}
