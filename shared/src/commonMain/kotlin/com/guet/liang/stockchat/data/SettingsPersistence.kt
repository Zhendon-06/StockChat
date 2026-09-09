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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal interface SettingsPersistence {
    fun read(): String?

    fun write(serializedSnapshot: String)
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class PersistedSettingsState(
    val appearance: AppearanceSettings,
    val sharedChats: List<SharedChatRecord>,
    val modelConfiguration: ModelConfiguration,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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

    private const val SCHEMA_VERSION = 1
}
