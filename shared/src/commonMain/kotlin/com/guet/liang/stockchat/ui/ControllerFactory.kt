package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.SettingsController
import com.guet.liang.stockchat.controller.SettingsCatalogRepository
import com.guet.liang.stockchat.controller.SettingsHistoryRepository
import com.guet.liang.stockchat.model.SettingsSnapshot
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ModelCatalogService
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.pager.Pager

/** Creates settings dependencies at the UI assembly boundary. */
internal fun Pager.settingsController(): SettingsController {
    val network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    val history = ChatHistoryDatabase.repository()
    val catalog = ModelCatalogService(network)
    return SettingsController(
        settings = StockChatSettingsStore.repository,
        history = object : SettingsHistoryRepository {
            override fun archivedSessions() = history.loadArchivedSessions()
            override fun restoreSession(sessionId: String) = history.restoreSession(sessionId)
        },
        catalog = SettingsCatalogRepository(catalog::load),
    )
}

/** Reads appearance through the settings boundary without creating network or history services. */
internal fun savedSettingsSnapshot(): SettingsSnapshot =
    SettingsController(StockChatSettingsStore.repository).snapshot()
