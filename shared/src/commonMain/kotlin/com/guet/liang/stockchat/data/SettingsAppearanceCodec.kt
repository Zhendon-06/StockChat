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

internal fun AppearanceSettings.toJson(): JSONObject {
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

internal fun JSONObject.toAppearanceSettings(): AppearanceSettings {
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
