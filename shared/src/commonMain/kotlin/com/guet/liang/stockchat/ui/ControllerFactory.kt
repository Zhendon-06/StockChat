package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.SettingsController
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.SettingsSnapshot

/** Reads appearance through the settings boundary without creating network or history services. */
internal fun savedSettingsSnapshot(): SettingsSnapshot =
    SettingsController(StockChatSettingsStore.repository).snapshot()
