package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ModelOption
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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
