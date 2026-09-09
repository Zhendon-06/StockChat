package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.tencent.kuikly.core.module.RouterModule

// 模型配置页草稿逻辑：选择/切换服务商、编辑字段、保存与放弃。

internal fun ModelConfigurationPage.selectProvider(providerId: String) {
    val provider = configuration.providers.firstOrNull { it.id == providerId } ?: return
    selectedProviderId = provider.id
    providerName = provider.displayName
    baseUrl = provider.baseUrl
    apiKey = provider.apiKey
    selectedModelId = provider.selectedModelId
    keyVisible = false
    resetModelCatalog()
    if (provider.models.isNotEmpty()) {
        availableModels = provider.models
        modelListVisible = true
    }
}

internal fun ModelConfigurationPage.selectedProvider(): ModelProviderConfig {
    return configuration.providers.firstOrNull { it.id == selectedProviderId }
        ?: configuration.providers.first()
}

internal fun ModelConfigurationPage.saveProvider() {
    val updated = currentDraftProvider() ?: return
    StockChatSettingsStore.repository.saveModelProvider(updated)
    StockChatSettingsStore.repository.selectModel(updated.id, updated.selectedModelId)
    reloadConfiguration(updated.id)
    bridgeModule.toast("模型配置已保存")
}

internal fun ModelConfigurationPage.chooseModel(modelId: String) {
    selectedModelId = modelId
    val updated = currentDraftProvider() ?: return
    StockChatSettingsStore.repository.saveModelProvider(updated)
    StockChatSettingsStore.repository.selectModel(updated.id, modelId)
    reloadConfiguration(updated.id)
}

internal fun ModelConfigurationPage.switchProvider(providerId: String) {
    if (providerId == selectedProviderId) {
        return
    }
    if (selectedProvider().kind != ModelProviderKind.DEFAULT) {
        val currentDraft = currentDraftProvider() ?: return
        StockChatSettingsStore.repository.saveModelProvider(currentDraft)
    }
    configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
    val provider = configuration.providers.firstOrNull { it.id == providerId } ?: return
    StockChatSettingsStore.repository.selectModel(provider.id, provider.selectedModelId)
    configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
    selectProvider(configuration.activeProviderId)
}

internal fun ModelConfigurationPage.updateApiKey(value: String) {
    if (apiKey == value) {
        return
    }
    apiKey = value
    resetModelCatalog()
}

internal fun ModelConfigurationPage.updateBaseUrl(value: String) {
    if (baseUrl == value) {
        return
    }
    baseUrl = value
    resetModelCatalog()
}

internal fun ModelConfigurationPage.currentDraftProvider(): ModelProviderConfig? {
    val normalizedName = providerName.trim()
    if (normalizedName.isEmpty()) {
        bridgeModule.toast("请输入 Provider 名称")
        return null
    }
    val normalizedBaseUrl = baseUrl.trim()
    if (normalizedBaseUrl.isEmpty()) {
        if (selectedProvider().kind == ModelProviderKind.DEFAULT) {
            return selectedProvider().copy(
                displayName = normalizedName.ifBlank { selectedProvider().kind.displayName },
                selectedModelId = selectedModelId,
            )
        }
        bridgeModule.toast("请输入 Base URL")
        return null
    }
    return selectedProvider().copy(
        displayName = normalizedName,
        baseUrl = normalizedBaseUrl,
        apiKey = apiKey.trim(),
        selectedModelId = selectedModelId,
        isEnabled = true,
    )
}

internal fun ModelConfigurationPage.reloadConfiguration(providerId: String) {
    configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
    val provider = configuration.providers.firstOrNull { it.id == providerId }
    if (provider == null) {
        selectProvider(providerId)
        return
    }
    selectedProviderId = provider.id
    providerName = provider.displayName
    baseUrl = provider.baseUrl
    apiKey = provider.apiKey
    selectedModelId = provider.selectedModelId
    keyVisible = false
}

internal fun ModelConfigurationPage.hasUnsavedChanges(): Boolean {
    val persistedProvider = configuration.providers.firstOrNull { it.id == selectedProviderId }
        ?: return false
    val draftProvider = persistedProvider.copy(
        displayName = providerName.trim().ifBlank { persistedProvider.kind.displayName },
        baseUrl = baseUrl.trim().trimEnd('/'),
        apiKey = apiKey.trim(),
        selectedModelId = selectedModelId,
    )
    return draftProvider != persistedProvider
}

internal fun ModelConfigurationPage.saveAndClose() {
    saveProvider()
    if (!hasUnsavedChanges()) {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }
}

internal fun ModelConfigurationPage.discardAndClose() {
    unsavedDialogOpen = false
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
}
