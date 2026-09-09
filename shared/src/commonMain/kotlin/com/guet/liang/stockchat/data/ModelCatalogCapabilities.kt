package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ModelOption
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Reads provider metadata and model-name capability hints. */
internal object ModelCatalogCapabilities {
    fun inferCapabilities(model: JSONObject, id: String): Set<ModelCapability> {
        val normalizedId = id.lowercase()
        val metadata = capabilityMetadata(model)
        val explicitVisionSupport = explicitBooleanValue(model, VISION_BOOLEAN_KEYS)
            ?: metadata.visionSupport
        val explicitStreamingSupport = explicitBooleanValue(model, STREAMING_BOOLEAN_KEYS)
            ?: metadata.streamingSupport
        return buildSet {
            add(ModelCapability.CHAT)
            if (explicitStreamingSupport != false) add(ModelCapability.STREAMING)
            if (VISION_MARKERS.any(normalizedId::contains)) add(ModelCapability.VISION)
            if (REASONING_MARKERS.any(normalizedId::contains)) add(ModelCapability.REASONING)
            if (VOICE_MARKERS.any(normalizedId::contains)) add(ModelCapability.VOICE)
            addAll(metadata.detectedCapabilities)
            if (explicitVisionSupport == true) add(ModelCapability.VISION)
            if (explicitVisionSupport == false) remove(ModelCapability.VISION)
            if (explicitStreamingSupport == true) add(ModelCapability.STREAMING)
            if (explicitStreamingSupport == false) remove(ModelCapability.STREAMING)
        }
    }

    private fun capabilityMetadata(model: JSONObject): CapabilityMetadata {
        val detectedCapabilities = mutableSetOf<ModelCapability>()
        var visionSupport: Boolean? = null
        var streamingSupport: Boolean? = null
        for (key in CAPABILITY_METADATA_KEYS) {
            val metadata = parseCapabilityMetadata(model.opt(key))
            detectedCapabilities += metadata.detectedCapabilities
            visionSupport = mergeExplicitSupport(visionSupport, metadata.visionSupport)
            streamingSupport = mergeExplicitSupport(streamingSupport, metadata.streamingSupport)
        }
        return CapabilityMetadata(
            detectedCapabilities = detectedCapabilities,
            visionSupport = visionSupport,
            streamingSupport = streamingSupport,
        )
    }

    private fun parseCapabilityMetadata(value: Any?): CapabilityMetadata {
        return when (value) {
            is JSONArray -> {
                var result = CapabilityMetadata()
                for (index in 0 until value.length()) {
                    result = result.merge(parseCapabilityMetadata(value.opt(index)))
                }
                result
            }

            is JSONObject -> parseCapabilityObject(value)

            is String -> parseCapabilityString(value)
            else -> CapabilityMetadata()
        }
    }

    private fun parseCapabilityObject(value: JSONObject): CapabilityMetadata {
    var result = CapabilityMetadata()
    val keys = value.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val rawValue = value.opt(key)
        val keyCapability = capabilityForToken(key)
        val booleanValue = parseBooleanValue(rawValue)
        if (keyCapability != null && booleanValue != null) {
            result = result.withSupport(keyCapability, booleanValue)
            if (booleanValue) {
                result = result.withCapability(keyCapability)
            }
        }
        result = result.merge(parseCapabilityMetadata(rawValue))
    }
    return result
    }

    private fun parseCapabilityString(value: String): CapabilityMetadata {
        val normalized = value.trim()
        val decoded = when {
            normalized.startsWith("[") && normalized.endsWith("]") -> runCatching { JSONArray(normalized) }.getOrNull()
            normalized.startsWith("{") && normalized.endsWith("}") -> runCatching { JSONObject(normalized) }.getOrNull()
            else -> null
        }
        return decoded?.let(::parseCapabilityMetadata) ?: CapabilityMetadata(
            detectedCapabilities = normalized.split(Regex("[,;|/+\\s-]+")).mapNotNull(::capabilityForToken).toSet(),
        )
    }

    private fun capabilityForToken(value: String): ModelCapability? = modelCapabilityForToken(value)

    private fun parseBooleanValue(value: Any?): Boolean? {
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

    private fun explicitBooleanValue(model: JSONObject, keys: Iterable<String>): Boolean? {
        var result: Boolean? = null
        for (key in keys) {
            if (!model.has(key)) continue
            parseBooleanValue(model.opt(key))?.let { value ->
                result = mergeExplicitSupport(result, value)
            }
        }
        return result
    }

    private fun mergeExplicitSupport(current: Boolean?, incoming: Boolean?): Boolean? {
        return when {
            incoming == null -> current
            current == false -> false
            incoming == false -> false
            else -> true
        }
    }

    private data class CapabilityMetadata(
        val detectedCapabilities: Set<ModelCapability> = emptySet(),
        val visionSupport: Boolean? = null,
        val streamingSupport: Boolean? = null,
    ) {
        fun merge(other: CapabilityMetadata): CapabilityMetadata {
            return CapabilityMetadata(
                detectedCapabilities = detectedCapabilities + other.detectedCapabilities,
                visionSupport = mergeExplicitSupport(visionSupport, other.visionSupport),
                streamingSupport = mergeExplicitSupport(streamingSupport, other.streamingSupport),
            )
        }

        fun withCapability(capability: ModelCapability): CapabilityMetadata {
            return copy(detectedCapabilities = detectedCapabilities + capability)
        }

        fun withSupport(capability: ModelCapability, supported: Boolean): CapabilityMetadata {
            return when (capability) {
                ModelCapability.VISION -> copy(visionSupport = mergeExplicitSupport(visionSupport, supported))
                ModelCapability.STREAMING ->
                    copy(streamingSupport = mergeExplicitSupport(streamingSupport, supported))
                else -> this
            }
        }
    }

    private val VISION_MARKERS = setOf("vision", "-vl", "_vl", "gpt-4o", "gemini", "claude-3")
    private val REASONING_MARKERS = setOf("reason", "thinking", "deepseek-r1", "-r1", "o1", "o3", "o4")
    private val VOICE_MARKERS = setOf("audio", "voice", "tts", "asr", "realtime")
    private val CAPABILITY_METADATA_KEYS = listOf(
        "capabilities",
        "modalities",
        "input_modalities",
        "inputModalities",
        "output_modalities",
        "outputModalities",
        "supported_modalities",
        "supportedModalities",
        "supported_capabilities",
        "supportedCapabilities",
        "features",
        "architecture",
    )
    private val VISION_BOOLEAN_KEYS = listOf(
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
    private val STREAMING_BOOLEAN_KEYS = listOf(
        "streaming",
        "supports_streaming",
        "supportsStreaming",
        "streaming_supported",
        "streamingSupported",
    )

}
