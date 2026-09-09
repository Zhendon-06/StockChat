package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.controller.ModelProviderDraft
import com.guet.liang.stockchat.controller.ModelProviderDraftResult
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
    controller.saveProvider(updated)
    controller.selectModel(updated.id, updated.selectedModelId)
    reloadConfiguration(updated.id)
    bridgeModule.toast("模型配置已保存")
}

internal fun ModelConfigurationPage.chooseModel(modelId: String) {
    selectedModelId = modelId
    val updated = currentDraftProvider() ?: return
    controller.saveProvider(updated)
    controller.selectModel(updated.id, modelId)
    reloadConfiguration(updated.id)
}

internal fun ModelConfigurationPage.switchProvider(providerId: String) {
    if (providerId == selectedProviderId) {
        return
    }
    if (selectedProvider().kind != ModelProviderKind.DEFAULT) {
        val currentDraft = currentDraftProvider() ?: return
        controller.saveProvider(currentDraft)
    }
    configuration = controller.snapshot().modelConfiguration
    val provider = configuration.providers.firstOrNull { it.id == providerId } ?: return
    controller.selectModel(provider.id, provider.selectedModelId)
    configuration = controller.snapshot().modelConfiguration
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

internal fun ModelConfigurationPage.providerDraft(): ModelProviderDraft =
    ModelProviderDraft(
        provider = selectedProvider(),
        displayName = providerName,
        baseUrl = baseUrl,
        apiKey = apiKey,
        selectedModelId = selectedModelId,
    )

internal fun ModelConfigurationPage.currentDraftProvider(): ModelProviderConfig? {
    return when (val result = modelController.validateDraft(providerDraft())) {
        is ModelProviderDraftResult.Valid -> result.provider
        is ModelProviderDraftResult.Invalid -> {
            bridgeModule.toast(result.message)
            null
        }
    }
}

internal fun ModelConfigurationPage.reloadConfiguration(providerId: String) {
    configuration = controller.snapshot().modelConfiguration
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
    return configuration.providers.any { it.id == selectedProviderId } &&
        modelController.hasUnsavedChanges(providerDraft())
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
