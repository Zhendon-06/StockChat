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

internal fun parseCapabilityValue(value: Any?): PersistedCapabilityMetadata {
    return when (value) {
        is JSONArray -> {
            var result = PersistedCapabilityMetadata()
            for (index in 0 until value.length()) {
                result = mergeCapabilityMetadata(result, parseCapabilityValue(value.opt(index)))
            }
            result
        }

        is JSONObject -> {
            var result = PersistedCapabilityMetadata()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val rawValue = value.opt(key)
                val keyCapability = capabilityForToken(key)
                val booleanValue = parseBooleanValue(rawValue)
                if (keyCapability != null && booleanValue != null) {
                    result = mergeCapabilityMetadata(
                        result,
                        PersistedCapabilityMetadata(
                            capabilities = if (booleanValue) setOf(keyCapability) else emptySet(),
                            visionSupport = booleanSupportFor(keyCapability, booleanValue, ModelCapability.VISION),
                            streamingSupport = booleanSupportFor(
                                keyCapability,
                                booleanValue,
                                ModelCapability.STREAMING,
                            ),
                        ),
                    )
                }
                result = mergeCapabilityMetadata(result, parseCapabilityValue(rawValue))
            }
            result
        }

        is String -> parseCapabilityString(value)
        else -> PersistedCapabilityMetadata()
    }
}

internal fun parseCapabilityString(value: String): PersistedCapabilityMetadata {
    val normalized = value.trim()
    val decoded = when {
        normalized.startsWith("[") && normalized.endsWith("]") -> runCatching { JSONArray(normalized) }.getOrNull()
        normalized.startsWith("{") && normalized.endsWith("}") -> runCatching { JSONObject(normalized) }.getOrNull()
        else -> null
    }
    return decoded?.let(::parseCapabilityValue) ?: PersistedCapabilityMetadata(
        capabilities = normalized.split(Regex("[,;|/+\\s-]+")).mapNotNull(::capabilityForToken).toSet(),
    )
}

internal fun capabilityForToken(value: String): ModelCapability? =
    modelCapabilityForToken(value) ?: enumValueOrNull<ModelCapability>(value.trim().uppercase())

internal fun parseBooleanValue(value: Any?): Boolean? {
    return when (value) {
        is Boolean -> value
        is Number -> value.toDouble().let { number ->
            when {
                number.isNaN() -> null
                number == 0.0 -> false
                else -> true
            }
        }
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes", "y", "on", "supported", "enabled" -> true
            "false", "0", "no", "n", "off", "unsupported", "disabled" -> false
            else -> null
        }
        else -> null
    }
}

internal fun explicitBooleanValue(model: JSONObject, keys: Iterable<String>): Boolean? {
    var result: Boolean? = null
    for (key in keys) {
        if (!model.has(key)) continue
        parseBooleanValue(model.opt(key))?.let { value ->
            result = mergeExplicitSupport(result, value)
        }
    }
    return result
}

internal fun booleanSupportFor(
    capability: ModelCapability,
    value: Boolean,
    target: ModelCapability,
): Boolean? {
    return value.takeIf { capability == target }
}

internal fun mergeCapabilityMetadata(
    first: PersistedCapabilityMetadata,
    second: PersistedCapabilityMetadata,
): PersistedCapabilityMetadata {
    return PersistedCapabilityMetadata(
        capabilities = first.capabilities + second.capabilities,
        visionSupport = mergeExplicitSupport(first.visionSupport, second.visionSupport),
        streamingSupport = mergeExplicitSupport(first.streamingSupport, second.streamingSupport),
    )
}

internal fun mergeExplicitSupport(current: Boolean?, incoming: Boolean?): Boolean? {
    return when {
        incoming == null -> current
        current == false -> false
        incoming == false -> false
        else -> true
    }
}

/** Encoded provider capability flags retained by settings persistence. */
internal data class PersistedCapabilityMetadata(
    val capabilities: Set<ModelCapability> = emptySet(),
    val visionSupport: Boolean? = null,
    val streamingSupport: Boolean? = null,
)

internal val PERSISTED_VISION_BOOLEAN_KEYS = listOf(
    "vision",
    "supports_vision",
    "supportsVision",
    "vision_supported",
    "visionSupported",
    "image_input",
    "imageInput",
    "supports_image_input",
    "supportsImageInput",
    "multimodal",
    "supports_multimodal",
    "supportsMultimodal",
)

internal val PERSISTED_STREAMING_BOOLEAN_KEYS = listOf(
    "streamingSupported",
    "streaming_supported",
    "supportsStreaming",
    "supports_streaming",
    "streaming",
)
