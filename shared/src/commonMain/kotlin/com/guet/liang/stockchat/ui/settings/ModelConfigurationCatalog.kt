package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.data.ModelCatalogResult
import com.guet.liang.stockchat.data.StockChatSettingsStore

// 模型配置页模型目录拉取：请求指纹、加载与重置。

private const val MODEL_CATALOG_FALLBACK_TIMEOUT_MS = 35_000

internal fun ModelConfigurationPage.resetModelCatalog() {
    modelRequestToken += 1
    availableModels = emptyList()
    modelListVisible = false
    modelListLoading = false
    modelListError = ""
}

internal fun ModelConfigurationPage.modelRequestFingerprint(): String {
    return "${selectedProviderId}|${baseUrl.trim().trimEnd('/')}|${apiKey.trim()}"
}

internal fun ModelConfigurationPage.loadModels(force: Boolean = false) {
    val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    val normalizedApiKey = apiKey.trim()
    if (normalizedBaseUrl.isBlank()) {
        modelRequestToken += 1
        modelListLoading = false
        modelListVisible = false
        modelListError = "请先填写 Base URL。"
        bridgeModule.toast("请先填写 Base URL")
        return
    }
    if (normalizedApiKey.isBlank()) {
        modelRequestToken += 1
        modelListLoading = false
        modelListVisible = false
        modelListError = "请先填写 API Key。"
        bridgeModule.toast("请先填写 API Key")
        return
    }
    if (!modelCatalogServiceReady) {
        return
    }
    val fingerprint = modelRequestFingerprint()
    if (modelListLoading || (!force && fingerprint == lastAttemptedModelRequest)) {
        return
    }
    val providerId = selectedProviderId
    val requestToken = modelRequestToken + 1
    modelRequestToken = requestToken
    lastAttemptedModelRequest = fingerprint
    modelListLoading = true
    modelListVisible = false
    modelListError = ""
    setTimeout(MODEL_CATALOG_FALLBACK_TIMEOUT_MS) {
        if (requestToken == modelRequestToken && modelListLoading) {
            modelRequestToken += 1
            modelListLoading = false
            modelListVisible = false
            modelListError = "模型列表请求超时，请检查网络后重试。"
            lastAttemptedModelRequest = ""
        }
    }
    try {
        modelCatalogService.load(normalizedBaseUrl, normalizedApiKey) { result ->
            if (requestToken != modelRequestToken || providerId != selectedProviderId) {
                return@load
            }
            modelListLoading = false
            when (result) {
                is ModelCatalogResult.Failure -> {
                    modelListVisible = false
                    modelListError = result.message
                    lastAttemptedModelRequest = ""
                }
                is ModelCatalogResult.Success -> {
                    val models = result.models
                    availableModels = models
                    modelListVisible = true
                    modelListError = ""
                    val nextSelectedModelId = models.firstOrNull { model ->
                        model.id == selectedModelId
                    }?.id ?: models.firstOrNull()?.id.orEmpty()
                    selectedModelId = nextSelectedModelId
                    val provider = selectedProvider()
                    val updated = provider.copy(
                        displayName = providerName.trim().ifBlank { provider.kind.displayName },
                        baseUrl = normalizedBaseUrl,
                        apiKey = normalizedApiKey,
                        models = models,
                        selectedModelId = nextSelectedModelId,
                    )
                    StockChatSettingsStore.repository.saveModelProvider(updated)
                    configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
                    providerName = updated.displayName
                    baseUrl = updated.baseUrl
                    apiKey = updated.apiKey
                    bridgeModule.toast("已获取 ${models.size} 个可用模型")
                }
            }
        }
    } catch (_: Throwable) {
        if (
            requestToken == modelRequestToken &&
            providerId == selectedProviderId &&
            modelListLoading
        ) {
            modelRequestToken += 1
            modelListLoading = false
            modelListVisible = false
            modelListError = "模型列表请求失败，请稍后重试。"
            lastAttemptedModelRequest = ""
        }
    }
}
