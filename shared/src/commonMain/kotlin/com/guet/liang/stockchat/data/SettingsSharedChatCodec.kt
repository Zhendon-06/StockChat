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

internal fun SharedChatRecord.toJson(): JSONObject {
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

internal fun JSONArray?.toSharedChatRecords(): List<SharedChatRecord> {
    if (this == null) {
        return emptyList()
    }
    return buildList {
        repeat(length()) { index ->
            optJSONObject(index)?.toSharedChatRecord()?.let(::add)
        }
    }
}

internal fun JSONObject.toSharedChatRecord(): SharedChatRecord? {
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
