package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.bridgeModule

internal fun ModelConfigurationPage.resetModelCatalog() {
    modelController.resetCatalog()
}

internal fun ModelConfigurationPage.loadModels(force: Boolean = false) {
    modelController.loadModels(::providerDraft, force)?.let(bridgeModule::toast)
}
