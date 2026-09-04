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

internal interface SettingsPersistence {
    fun read(): String?

    fun write(serializedSnapshot: String)
}

internal class KuiklySharedPreferencesSettingsPersistence(
    private val sharedPreferencesModule: SharedPreferencesModule,
) : SettingsPersistence {
    override fun read(): String? {
        return runCatching { sharedPreferencesModule.getString(STORAGE_KEY) }
            .getOrNull()
            ?.trim()
            ?.takeUnless { value -> value.isEmpty() || value == "null" }
    }

    override fun write(serializedSnapshot: String) {
        runCatching {
            sharedPreferencesModule.setString(STORAGE_KEY, serializedSnapshot)
        }
    }

    private companion object {
        const val STORAGE_KEY = "stock_chat_settings_v1"
    }
}

internal data class PersistedSettingsState(
    val appearance: AppearanceSettings,
    val sharedChats: List<SharedChatRecord>,
    val modelConfiguration: ModelConfiguration,
)

internal object SettingsSnapshotJsonCodec {
    fun encode(snapshot: SettingsSnapshot): String {
        return JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("appearance", snapshot.appearance.toJson())
            put("sharedChats", snapshot.sharedChats.toJsonArray { record -> record.toJson() })
            put("modelConfiguration", snapshot.modelConfiguration.toJson())
        }.toString()
    }

    fun decode(serializedSnapshot: String): PersistedSettingsState? {
        return runCatching {
            val root = JSONObject(serializedSnapshot)
            val appearance = root.optJSONObject("appearance")
                ?.toAppearanceSettings()
                ?: MockSettingsData.appearance
            val sharedChats = if (root.has("sharedChats")) {
                root.optJSONArray("sharedChats").toSharedChatRecords()
            } else {
                emptyList()
            }
            val modelConfiguration = root.optJSONObject("modelConfiguration")
                ?.toModelConfiguration()
                ?.takeIf { configuration -> configuration.providers.isNotEmpty() }
                ?: MockSettingsData.modelConfiguration
            PersistedSettingsState(
                appearance = appearance,
                sharedChats = sharedChats,
                modelConfiguration = modelConfiguration,
            )
        }.getOrNull()
    }

    private fun AppearanceSettings.toJson(): JSONObject {
        return JSONObject().apply {
            put("themeMode", themeMode.name)
            put(
                "fontSize",
                JSONObject().apply {
                    put("followsSystem", fontSize.followsSystem)
                    put("scale", fontSize.scale.toDouble())
                },
            )
            put(
                "tableStyle",
                JSONObject().apply {
                    put("preset", tableStyle.preset.name)
                    put("showGridLines", tableStyle.showGridLines)
                    put("highlightHeader", tableStyle.highlightHeader)
                    put("customColorArgb", tableStyle.customColorArgb)
                },
            )
            put(
                "chatBackground",
                JSONObject().apply {
                    put("preset", chatBackground.preset.name)
                    chatBackground.customImageUri?.let { uri -> put("customImageUri", uri) }
                    put("blurRadius", chatBackground.blurRadius.toDouble())
                    put("maskOpacity", chatBackground.maskOpacity.toDouble())
                    put("maskBrightness", chatBackground.maskBrightness.toDouble())
                    put("chatTextSizeSp", chatBackground.chatTextSizeSp.toDouble())
                    put("chatTextColorMode", chatBackground.chatTextColorMode.name)
                },
            )
        }
    }

    private fun JSONObject.toAppearanceSettings(): AppearanceSettings {
        val defaults = MockSettingsData.appearance
        val fontJson = optJSONObject("fontSize")
        val tableJson = optJSONObject("tableStyle")
        val backgroundJson = optJSONObject("chatBackground")
        return AppearanceSettings(
            themeMode = enumValueOrDefault(optString("themeMode"), defaults.themeMode),
            fontSize = FontSizeSettings(
                followsSystem = fontJson?.optBoolean(
                    "followsSystem",
                    defaults.fontSize.followsSystem,
                ) ?: defaults.fontSize.followsSystem,
                scale = fontJson?.optDouble("scale", defaults.fontSize.scale.toDouble())
                    ?.toFloat()
                    ?: defaults.fontSize.scale,
            ),
            tableStyle = TableStyleSettings(
                preset = enumValueOrDefault(
                    tableJson?.optString("preset").orEmpty(),
                    defaults.tableStyle.preset,
                ),
                showGridLines = tableJson?.optBoolean(
                    "showGridLines",
                    defaults.tableStyle.showGridLines,
                ) ?: defaults.tableStyle.showGridLines,
                highlightHeader = tableJson?.optBoolean(
                    "highlightHeader",
                    defaults.tableStyle.highlightHeader,
                ) ?: defaults.tableStyle.highlightHeader,
                customColorArgb = tableJson?.optLong(
                    "customColorArgb",
                    defaults.tableStyle.customColorArgb,
                ) ?: defaults.tableStyle.customColorArgb,
            ),
            chatBackground = ChatBackgroundSettings(
                preset = enumValueOrDefault(
                    backgroundJson?.optString("preset").orEmpty(),
                    defaults.chatBackground.preset,
                ),
                customImageUri = backgroundJson
                    ?.optString("customImageUri")
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
                blurRadius = backgroundJson?.optDouble(
                    "blurRadius",
                    defaults.chatBackground.blurRadius.toDouble(),
                )?.toFloat() ?: defaults.chatBackground.blurRadius,
                maskOpacity = backgroundJson?.optDouble(
                    "maskOpacity",
                    defaults.chatBackground.maskOpacity.toDouble(),
                )?.toFloat() ?: defaults.chatBackground.maskOpacity,
                maskBrightness = backgroundJson?.optDouble(
                    "maskBrightness",
                    defaults.chatBackground.maskBrightness.toDouble(),
                )?.toFloat() ?: defaults.chatBackground.maskBrightness,
                chatTextSizeSp = backgroundJson?.optDouble(
                    "chatTextSizeSp",
                    defaults.chatBackground.chatTextSizeSp.toDouble(),
                )?.toFloat() ?: defaults.chatBackground.chatTextSizeSp,
                chatTextColorMode = enumValueOrDefault(
                    backgroundJson?.optString("chatTextColorMode").orEmpty(),
                    defaults.chatBackground.chatTextColorMode,
                ),
            ),
        )
    }

    private fun SharedChatRecord.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("sessionId", sessionId)
            put("question", question)
            put("sharedAtEpochMillis", sharedAtEpochMillis)
            put("destinationLabel", destinationLabel)
            put("isDemo", isDemo)
            put(
                "content",
                JSONObject().apply {
                    put("title", content.title)
                    put("text", content.text)
                    content.url?.let { url -> put("url", url) }
                },
            )
        }
    }

    private fun JSONArray?.toSharedChatRecords(): List<SharedChatRecord> {
        if (this == null) {
            return emptyList()
        }
        return buildList {
            repeat(length()) { index ->
                optJSONObject(index)?.toSharedChatRecord()?.let(::add)
            }
        }
    }

    private fun JSONObject.toSharedChatRecord(): SharedChatRecord? {
        val id = optString("id").trim()
        val contentJson = optJSONObject("content") ?: return null
        val text = contentJson.optString("text").trim()
        if (id.isBlank() || text.isBlank()) {
            return null
        }
        return SharedChatRecord(
            id = id,
            sessionId = optString("sessionId").trim(),
            question = optString("question").trim(),
            content = ShareContent(
                title = contentJson.optString("title").trim(),
                text = text,
                url = contentJson.optString("url").trim().takeIf(String::isNotEmpty),
            ),
            sharedAtEpochMillis = optLong("sharedAtEpochMillis"),
            destinationLabel = optString("destinationLabel").trim().ifBlank { "系统分享" },
            isDemo = optBoolean("isDemo"),
        )
    }

    private fun ModelConfiguration.toJson(): JSONObject {
        return JSONObject().apply {
            put("activeProviderId", activeProviderId)
            put("providers", providers.toJsonArray { provider -> provider.toJson() })
        }
    }

    private fun ModelProviderConfig.toJson(): JSONObject {
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

    private fun ModelOption.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("displayName", displayName)
            put("contextWindowLabel", contextWindowLabel)
            put("capabilities", capabilities.map(ModelCapability::name).toJsonArray())
            put("streamingSupported", ModelCapability.STREAMING in capabilities)
            put("visionSupported", ModelCapability.VISION in capabilities)
        }
    }

    private fun JSONObject.toModelConfiguration(): ModelConfiguration? {
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

    private fun JSONObject.toModelProviderConfig(): ModelProviderConfig? {
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

    private fun JSONObject.toModelOption(): ModelOption? {
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
        val streamingSupported = explicitBooleanValue(this, STREAMING_BOOLEAN_KEYS)
            ?: parsedMetadata.streamingSupport
            ?: true
        val visionSupported = explicitBooleanValue(this, VISION_BOOLEAN_KEYS)
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

    private fun parseCapabilityValue(value: Any?): PersistedCapabilityMetadata {
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

    private fun parseCapabilityString(value: String): PersistedCapabilityMetadata {
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            return PersistedCapabilityMetadata()
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            runCatching { JSONArray(normalized) }
                .getOrNull()
                ?.let { return parseCapabilityValue(it) }
        }
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            runCatching { JSONObject(normalized) }
                .getOrNull()
                ?.let { return parseCapabilityValue(it) }
        }
        val capabilities = buildSet {
            normalized
                .split(Regex("[,;|/+\\s-]+"))
                .mapNotNull(::capabilityForToken)
                .forEach(::add)
        }
        return PersistedCapabilityMetadata(capabilities = capabilities)
    }

    private fun capabilityForToken(value: String): ModelCapability? {
        val token = value.trim().lowercase().replace('-', '_')
        return when {
            token in VISION_TOKENS || token.contains("vision") ||
                token.contains("image_input") || token.contains("multimodal") ->
                ModelCapability.VISION
            token in REASONING_TOKENS || token.contains("reason") || token.contains("think") ->
                ModelCapability.REASONING
            token in VOICE_TOKENS || token.contains("audio") || token.contains("speech") ||
                token.contains("voice") -> ModelCapability.VOICE
            token in STREAMING_TOKENS || token.contains("stream") || token == "sse" ->
                ModelCapability.STREAMING
            token in CHAT_TOKENS || token == "text" -> ModelCapability.CHAT
            else -> enumValueOrNull<ModelCapability>(value.trim().uppercase())
        }
    }

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

    private fun booleanSupportFor(
        capability: ModelCapability,
        value: Boolean,
        target: ModelCapability,
    ): Boolean? {
        return value.takeIf { capability == target }
    }

    private fun mergeCapabilityMetadata(
        first: PersistedCapabilityMetadata,
        second: PersistedCapabilityMetadata,
    ): PersistedCapabilityMetadata {
        return PersistedCapabilityMetadata(
            capabilities = first.capabilities + second.capabilities,
            visionSupport = mergeExplicitSupport(first.visionSupport, second.visionSupport),
            streamingSupport = mergeExplicitSupport(first.streamingSupport, second.streamingSupport),
        )
    }

    private fun mergeExplicitSupport(current: Boolean?, incoming: Boolean?): Boolean? {
        return when {
            incoming == null -> current
            current == false -> false
            incoming == false -> false
            else -> true
        }
    }

    private data class PersistedCapabilityMetadata(
        val capabilities: Set<ModelCapability> = emptySet(),
        val visionSupport: Boolean? = null,
        val streamingSupport: Boolean? = null,
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
        "streamingSupported",
        "streaming_supported",
        "supportsStreaming",
        "supports_streaming",
        "streaming",
    )

    private val VISION_TOKENS = setOf(
        "vision",
        "visual",
        "image",
        "images",
        "image_input",
        "image_inputs",
        "multimodal",
        "multimodal_input",
        "video",
        "video_input",
    )

    private val REASONING_TOKENS = setOf("reasoning", "think", "thinking")
    private val VOICE_TOKENS = setOf("voice", "audio", "speech", "tts", "asr", "realtime")
    private val STREAMING_TOKENS = setOf(
        "stream",
        "streaming",
        "sse",
        "stream_output",
        "streaming_output",
        "streamable",
    )
    private val CHAT_TOKENS = setOf("chat", "text_input", "text_output", "text")

    private fun <T> List<T>.toJsonArray(transform: (T) -> Any?): JSONArray {
        return JSONArray().apply {
            this@toJsonArray.forEach { item -> put(transform(item)) }
        }
    }

    private fun List<String>.toJsonArray(): JSONArray {
        return JSONArray().apply {
            this@toJsonArray.forEach(::put)
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T {
        return enumValueOrNull<T>(value) ?: fallback
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? {
        return enumValues<T>().firstOrNull { candidate -> candidate.name == value }
    }

    private const val SCHEMA_VERSION = 1
}
