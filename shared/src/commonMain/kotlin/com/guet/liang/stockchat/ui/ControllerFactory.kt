package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.savedAppearanceSnapshot
import com.guet.liang.stockchat.model.SettingsSnapshot

/** Reads appearance through the settings boundary without creating network or history services. */
internal fun savedSettingsSnapshot(): SettingsSnapshot = savedAppearanceSnapshot()
