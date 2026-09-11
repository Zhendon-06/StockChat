package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.base.StockChatLog
import com.guet.liang.stockchat.base.toUserMessage
import com.guet.liang.stockchat.data.SettingsRepository
import com.guet.liang.stockchat.model.AnswerMode
import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.ChatSessionSummary
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.SettingsSnapshot
import com.guet.liang.stockchat.model.ShareContent
import com.guet.liang.stockchat.model.SharedChatRecord
import com.guet.liang.stockchat.model.TableStyleSettings
import com.guet.liang.stockchat.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** Catalog boundary that can be provided by a network service or a fake. */
internal fun interface SettingsCatalogRepository {
    fun load(baseUrl: String, apiKey: String, callback: (ModelCatalogResult) -> Unit)
}

/** Archived conversation operations used by the settings screens. */
internal interface SettingsHistoryRepository {
    fun archivedSessions(): List<ChatSessionSummary>

    fun restoreSession(sessionId: String): Boolean
}

/** Coordinates persisted settings and optional catalog/history services for settings pages. */
internal class SettingsController(
    private val settings: SettingsRepository,
    internal val history: SettingsHistoryRepository? = null,
    private val catalog: SettingsCatalogRepository? = null,
) {
    // Kotlin/JS 会把属性 getter 与同名函数都编译成 `snapshot`，显式改名避免冲突
    @kotlin.js.JsName("snapshotFlow")
    val snapshot: StateFlow<SettingsSnapshot>
        get() = settings.snapshot

    fun snapshot(): SettingsSnapshot = settings.loadSnapshot()

    fun setThemeMode(value: ThemeMode) = settings.setThemeMode(value)

    fun setFontSize(value: FontSizeSettings) = settings.setFontSize(value)

    fun setTableStyle(value: TableStyleSettings) = settings.setTableStyle(value)

    fun setChatBackground(value: ChatBackgroundSettings) = settings.setChatBackground(value)

    fun saveProvider(value: ModelProviderConfig) = settings.saveModelProvider(value)

    fun setAnswerMode(value: AnswerMode) = settings.setAnswerMode(value)

    fun deleteProvider(id: String): Boolean = settings.deleteModelProvider(id)

    fun selectModel(providerId: String, modelId: String): Boolean =
        settings.selectModel(providerId, modelId)

    fun fetchCatalog(providerId: String, callback: (ModelCatalogResult) -> Unit) {
        val provider = snapshot().modelConfiguration.providers.firstOrNull { it.id == providerId }
        if (provider == null) {
            callback(ModelCatalogResult.Failure("未找到模型服务商。"))
            return
        }
        fetchCatalog(provider, callback)
    }

    fun fetchCatalog(provider: ModelProviderConfig, callback: (ModelCatalogResult) -> Unit) {
        val service =
            catalog
                ?: run {
                    callback(ModelCatalogResult.Failure("模型目录服务不可用。"))
                    return
                }
        try {
            service.load(provider.baseUrl.trim().trimEnd('/'), provider.apiKey.trim(), callback)
        } catch (exception: RuntimeException) {
            StockChatLog.w("SettingsController", "fetch models failed", exception)
            callback(ModelCatalogResult.Failure(exception.toUserMessage("模型列表请求失败，请稍后重试。")))
        }
    }

    fun sharedChats(): List<SharedChatRecord> = snapshot().sharedChats

    fun deleteSharedChat(id: String): Boolean = settings.deleteSharedChat(id)

    fun recordSharedChat(
        sessionId: String,
        question: String,
        content: ShareContent,
        destinationLabel: String = "系统分享",
    ): SharedChatRecord = settings.recordSharedChat(sessionId, question, content, destinationLabel)
}

internal fun SettingsController.archivedSessions(): List<ChatSessionSummary> =
    history?.archivedSessions().orEmpty()

internal fun SettingsController.restoreSession(sessionId: String): Boolean =
    history?.restoreSession(sessionId) == true
