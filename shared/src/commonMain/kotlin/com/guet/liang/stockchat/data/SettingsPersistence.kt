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
            apiKey = "",
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
        val capabilitiesJson = optJSONArray("capabilities")
        val capabilities = buildSet {
            if (capabilitiesJson != null) {
                repeat(capabilitiesJson.length()) { index ->
                    enumValueOrNull<ModelCapability>(
                        capabilitiesJson.optString(index).orEmpty()
                    )?.let(::add)
                }
            }
        }.ifEmpty { setOf(ModelCapability.CHAT) }
        return ModelOption(
            id = id,
            displayName = optString("displayName").trim().ifBlank { id },
            contextWindowLabel = optString("contextWindowLabel").trim(),
            capabilities = capabilities,
        )
    }

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
