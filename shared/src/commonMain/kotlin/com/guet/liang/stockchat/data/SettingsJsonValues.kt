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

internal fun <T> List<T>.toJsonArray(transform: (T) -> Any?): JSONArray {
    return JSONArray().apply {
        this@toJsonArray.forEach { item -> put(transform(item)) }
    }
}

internal fun List<String>.toJsonArray(): JSONArray {
    return JSONArray().apply {
        this@toJsonArray.forEach(::put)
    }
}

internal inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T {
    return enumValueOrNull<T>(value) ?: fallback
}

internal inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? {
    return enumValues<T>().firstOrNull { candidate -> candidate.name == value }
}
