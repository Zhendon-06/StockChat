package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AppearanceSettings
import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.SettingsSnapshot
import com.guet.liang.stockchat.model.ShareContent
import com.guet.liang.stockchat.model.SharedChatRecord
import com.guet.liang.stockchat.model.TableStyleSettings
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal fun ModelConfiguration.toJson(): JSONObject {
    return JSONObject().apply {
        put("activeProviderId", activeProviderId)
        put("providers", providers.toJsonArray { provider -> provider.toJson() })
    }
}

internal fun ModelProviderConfig.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("displayName", displayName)
        put("baseUrl", baseUrl)
        put("apiKey", apiKey)
        put("selectedModelId", selectedModelId)
        put("isEnabled", isEnabled)
        put("models", models.toJsonArray { model -> model.toJson() })
    }
}

internal fun ModelOption.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("contextWindowLabel", contextWindowLabel)
        put("capabilities", capabilities.map(ModelCapability::name).toJsonArray())
        put("streamingSupported", ModelCapability.STREAMING in capabilities)
        put("visionSupported", ModelCapability.VISION in capabilities)
    }
}

internal fun JSONObject.toModelConfiguration(): ModelConfiguration? {
    val providersJson = optJSONArray("providers") ?: return null
    val providers = buildList {
        repeat(providersJson.length()) { index ->
            providersJson.optJSONObject(index)?.toModelProviderConfig()?.let(::add)
        }
    }
    if (providers.isEmpty()) {
        return null
    }
    return ModelConfiguration(
        activeProviderId = optString("activeProviderId").trim(),
        providers = providers,
    )
}

internal fun JSONObject.toModelProviderConfig(): ModelProviderConfig? {
    val id = optString("id").trim()
    val modelsJson = optJSONArray("models") ?: return null
    val models = buildList {
        repeat(modelsJson.length()) { index ->
            modelsJson.optJSONObject(index)?.toModelOption()?.let(::add)
        }
    }
    if (id.isBlank()) {
        return null
    }
    val kind = enumValueOrDefault(optString("kind"), ModelProviderKind.CUSTOM)
    return ModelProviderConfig(
        id = id,
        kind = kind,
        displayName = optString("displayName").trim().ifBlank { kind.displayName },
        baseUrl = optString("baseUrl").trim(),
        apiKey = optString("apiKey").trim(),
        models = models,
        selectedModelId = optString("selectedModelId").trim(),
        isEnabled = optBoolean("isEnabled", true),
    )
}

internal fun JSONObject.toModelOption(): ModelOption? {
    val id = optString("id").trim()
    if (id.isBlank()) {
        return null
    }
    val parsedCapabilities = parseCapabilityValue(opt("capabilities"))
    val parsedMetadata = listOf(
        parsedCapabilities,
        parseCapabilityValue(opt("modalities")),
        parseCapabilityValue(opt("input_modalities")),
        parseCapabilityValue(opt("inputModalities")),
        parseCapabilityValue(opt("output_modalities")),
        parseCapabilityValue(opt("outputModalities")),
        parseCapabilityValue(opt("supported_modalities")),
        parseCapabilityValue(opt("supportedModalities")),
        parseCapabilityValue(opt("architecture")),
    ).fold(PersistedCapabilityMetadata(), ::mergeCapabilityMetadata)
    val capabilities = parsedMetadata.capabilities.ifEmpty { setOf(ModelCapability.CHAT) }
    val streamingSupported = explicitBooleanValue(this, PERSISTED_STREAMING_BOOLEAN_KEYS)
        ?: parsedMetadata.streamingSupport
        ?: true
    val visionSupported = explicitBooleanValue(this, PERSISTED_VISION_BOOLEAN_KEYS)
        ?: parsedMetadata.visionSupport
    val normalizedCapabilities = capabilities.toMutableSet().apply {
        if (streamingSupported && ModelCapability.CHAT in this) {
            add(ModelCapability.STREAMING)
        } else if (!streamingSupported) {
            remove(ModelCapability.STREAMING)
        }
        when (visionSupported) {
            true -> add(ModelCapability.VISION)
            false -> remove(ModelCapability.VISION)
            null -> Unit
        }
    }.toSet()
    return ModelOption(
        id = id,
        displayName = optString("displayName").trim().ifBlank { id },
        contextWindowLabel = optString("contextWindowLabel").trim(),
        capabilities = normalizedCapabilities,
    )
}
