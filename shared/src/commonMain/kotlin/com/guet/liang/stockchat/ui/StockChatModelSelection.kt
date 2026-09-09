package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.data.AliyunApiConfig
import com.guet.liang.stockchat.data.AliyunStockChatDataSource
import com.guet.liang.stockchat.data.DEFAULT_CHAT_BASE_URL
import com.guet.liang.stockchat.data.ModelCatalogResult
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.ui.settings.MODEL_CONFIGURATION_PAGE_NAME
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// 模型选择：当前模型、模型目录拉取、聊天服务商配置与模型选项映射。

internal data class ChatModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val badge: String,
    val multiplier: String,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT),
    val iconAsset: String = "stockchat_app_icon.png",
    val isLocked: Boolean = false,
)

internal const val DEFAULT_CHAT_MODEL_ICON_ASSET = "stockchat_app_icon.png"

private const val DRAWER_MODEL_CATALOG_FALLBACK_TIMEOUT_MS = 35_000

internal fun StockChatPage.selectModel(modelId: String) {
    if (chatModelOptions.none { it.id == modelId }) {
        return
    }
    selectedModelId = modelId
    if (activeModelProviderId.isNotBlank()) {
        StockChatSettingsStore.repository.selectModel(activeModelProviderId, modelId)
        configureChatProvider()
    }
    closeModelMenu()
}

internal fun StockChatPage.selectedModel(): ChatModelOption {
    return chatModelOptions.firstOrNull { it.id == selectedModelId }
        ?: chatModelOptions.firstOrNull()
        ?: ChatModelOption(
            id = selectedModelId,
            displayName = "未选择模型",
            description = "当前 Provider 暂无可用模型",
            badge = "",
            multiplier = "",
            iconAsset = DEFAULT_CHAT_MODEL_ICON_ASSET,
        )
}

// composer 只显示当前模型提供商名称，Drawer 展示该提供商的模型列表
internal fun StockChatPage.composerModelDisplayName(): String {
    return composerModelLabel
}

internal fun StockChatPage.composerModelIconAsset(): String {
    return composerModelIcon
}

internal fun StockChatPage.configureChatProvider() {
    val configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
    val provider = configuration.providers.firstOrNull { candidate ->
        candidate.id == configuration.activeProviderId
    }
    val usesDashScope = provider == null || provider.kind == ModelProviderKind.DEFAULT ||
        provider.kind == ModelProviderKind.ALIYUN
    val nextProviderId = provider?.id.orEmpty()
    if (activeModelProviderId != nextProviderId) {
        drawerModelRequestToken += 1
        drawerModelsLoading = false
        drawerModelsError = ""
        lastDrawerModelFetch = ""
        drawerModelInFlightFingerprint = ""
    }
    // 模型选择面板只展示当前 Provider 已保存的模型列表。
    val options = provider?.toChatModelOptions(selectedModelId).orEmpty()
    chatModelOptions = options
    activeModelProviderId = nextProviderId
    selectedModelId = provider?.selectedModelId
        ?.takeIf { modelId -> chatModelOptions.any { option -> option.id == modelId } }
        ?: chatModelOptions.firstOrNull()?.id.orEmpty()
    modelMenuContentRevision += 1
    composerModelLabel = when (provider?.kind) {
        ModelProviderKind.CUSTOM -> provider.displayName
        null -> null
        else -> provider.kind.displayName
    } ?: provider?.displayName ?: "选择模型"
    composerModelIcon = provider?.kind?.let(::providerIconAsset) ?: DEFAULT_CHAT_MODEL_ICON_ASSET

    val routeApiKey = pageData.params.optString("qwenApiKey").trim()
    val providerApiKey = when {
        provider == null -> routeApiKey
        !provider.isEnabled -> ""
        provider.apiKey.isNotBlank() -> provider.apiKey
        usesDashScope -> routeApiKey
        else -> ""
    }
    val selectedModelCapabilities = selectedModel().capabilities
    val supportsStreaming = ModelCapability.STREAMING in selectedModelCapabilities
    val config = AliyunApiConfig(
        apiKey = providerApiKey,
        baseUrl = provider?.baseUrl?.takeIf(String::isNotBlank)
            ?: DEFAULT_CHAT_BASE_URL,
        chatModel = selectedModelId,
        visionModel = selectedModelId,
        providerDisplayName = provider?.displayName ?: "选择模型",
        useAliyunExtensions = usesDashScope,
        supportsVision = ModelCapability.VISION in selectedModelCapabilities,
        supportsStreaming = supportsStreaming,
    )
    val nativeStreamingEnabled = pageData.params.optInt(
        "aliyunNativeStreaming",
        pageData.params.optInt("mimoNativeStreaming"),
    ) == 1
    dataSource = AliyunStockChatDataSource(
        networkModule = networkModule,
        config = config,
        bridgeModule = bridgeModule,
        useNativeStreaming = nativeStreamingEnabled && supportsStreaming,
    )
}

/** 拉取当前 Provider 的模型列表并写回设置存储。 */

internal fun StockChatPage.fetchDrawerModels(force: Boolean = false) {
    if (!modelCatalogServiceReady) {
        return
    }
    drawerModelsError = ""
    val configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
    val provider = configuration.providers.firstOrNull { candidate ->
        candidate.id == configuration.activeProviderId
    }
    if (provider == null) {
        drawerModelRequestToken += 1
        drawerModelsLoading = false
        lastDrawerModelFetch = ""
        drawerModelInFlightFingerprint = ""
        return
    }
    if (provider.kind == ModelProviderKind.DEFAULT) {
        // 内置三档千问模型是固定配置，避免 /models 返回的完整目录覆盖面板。
        drawerModelRequestToken += 1
        drawerModelsLoading = false
        lastDrawerModelFetch = ""
        drawerModelInFlightFingerprint = ""
        return
    }
    val routeApiKey = pageData.params.optString("qwenApiKey").trim()
    val requestKey = when {
        provider.apiKey.isNotBlank() -> provider.apiKey
        provider.kind == ModelProviderKind.ALIYUN -> routeApiKey
        else -> ""
    }
    val requestBaseUrl = provider.baseUrl.trim().trimEnd('/')
    if (requestKey.isBlank() || requestBaseUrl.isBlank()) {
        // 无 Key / 无地址时不发起请求，由面板的空态提示引导去模型配置页
        drawerModelRequestToken += 1
        drawerModelsLoading = false
        lastDrawerModelFetch = ""
        drawerModelInFlightFingerprint = ""
        return
    }
    val fingerprint = "${provider.id}|$requestBaseUrl|$requestKey"
    if (drawerModelsLoading) {
        if (fingerprint == drawerModelInFlightFingerprint) {
            return
        }
        drawerModelRequestToken += 1
        drawerModelsLoading = false
        drawerModelsError = ""
        lastDrawerModelFetch = ""
        drawerModelInFlightFingerprint = ""
    }
    if (!force && fingerprint == lastDrawerModelFetch) {
        return
    }
    val providerId = provider.id
    val requestToken = drawerModelRequestToken + 1
    drawerModelRequestToken = requestToken
    lastDrawerModelFetch = fingerprint
    drawerModelInFlightFingerprint = fingerprint
    drawerModelsLoading = true
    setTimeout(DRAWER_MODEL_CATALOG_FALLBACK_TIMEOUT_MS) {
        if (requestToken == drawerModelRequestToken && drawerModelsLoading) {
            drawerModelRequestToken += 1
            drawerModelsLoading = false
            lastDrawerModelFetch = ""
            drawerModelInFlightFingerprint = ""
            drawerModelsError = "模型列表请求超时，请检查网络后重试。"
        }
    }
    try {
        modelCatalogService.load(requestBaseUrl, requestKey) { result ->
            val latestConfiguration = StockChatSettingsStore.repository.loadSnapshot()
                .modelConfiguration
            val latestProvider = latestConfiguration.providers.firstOrNull { candidate ->
                candidate.id == providerId
            }
            if (latestProvider == null) {
                if (requestToken == drawerModelRequestToken) {
                    drawerModelsLoading = false
                    lastDrawerModelFetch = ""
                    drawerModelInFlightFingerprint = ""
                }
                return@load
            }
            val latestRequestKey = when {
                latestProvider.apiKey.isNotBlank() -> latestProvider.apiKey
                latestProvider.kind == ModelProviderKind.ALIYUN -> routeApiKey
                else -> ""
            }
            val latestFingerprint =
                "${latestProvider.id}|${latestProvider.baseUrl.trim().trimEnd('/')}|$latestRequestKey"
            if (
                requestToken != drawerModelRequestToken ||
                latestConfiguration.activeProviderId != providerId ||
                latestFingerprint != fingerprint
            ) {
                if (requestToken == drawerModelRequestToken && drawerModelsLoading) {
                    drawerModelsLoading = false
                    lastDrawerModelFetch = ""
                    drawerModelInFlightFingerprint = ""
                }
                return@load
            }
            drawerModelsLoading = false
            drawerModelInFlightFingerprint = ""
            when (result) {
                is ModelCatalogResult.Failure -> {
                    // 允许下次打开面板时重试
                    lastDrawerModelFetch = ""
                    drawerModelsError = result.message
                }
                is ModelCatalogResult.Success -> {
                    val nextSelectedModelId = result.models
                        .firstOrNull { model -> model.id == latestProvider.selectedModelId }
                        ?.id
                        ?: result.models.firstOrNull()?.id.orEmpty()
                    StockChatSettingsStore.repository.saveModelProvider(
                        latestProvider.copy(
                            models = result.models,
                            selectedModelId = nextSelectedModelId,
                        ),
                    )
                    // 重新从设置构建面板选项与聊天数据源
                    configureChatProvider()
                }
            }
        }
    } catch (_: Throwable) {
        if (requestToken == drawerModelRequestToken && drawerModelsLoading) {
            drawerModelRequestToken += 1
            drawerModelsLoading = false
            lastDrawerModelFetch = ""
            drawerModelInFlightFingerprint = ""
            drawerModelsError = "模型列表请求失败，请稍后重试。"
        }
    }
}

internal fun StockChatPage.retryDrawerModels() {
    lastDrawerModelFetch = ""
    fetchDrawerModels(force = true)
}

internal fun StockChatPage.openModelConfiguration() {
    closeModelMenu()
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        MODEL_CONFIGURATION_PAGE_NAME,
        JSONObject(),
    )
}

internal fun ModelProviderConfig.toChatModelOptions(selectedModelId: String): List<ChatModelOption> {
    return models.mapIndexed { index, model ->
        val capabilityLabels = buildList {
            add(
                if (ModelCapability.VISION in model.capabilities) {
                    "视觉理解"
                } else {
                    "仅文本"
                },
            )
            add(
                if (ModelCapability.STREAMING in model.capabilities) {
                    "流式输出"
                } else {
                    "非流式"
                },
            )
            model.capabilities
                .filterNot {
                    it == ModelCapability.CHAT ||
                        it == ModelCapability.VISION ||
                        it == ModelCapability.STREAMING
                }
                .forEach { capability -> add(capability.displayName) }
        }
        ChatModelOption(
            id = model.id,
            displayName = model.displayName,
            description = "$displayName · ${capabilityLabels.joinToString(" · ")}",
            badge = when {
                model.id == selectedModelId -> "当前"
                index == 0 -> "推荐"
                else -> if (ModelCapability.VISION in model.capabilities) {
                    "视觉"
                } else {
                    "文本"
                }
            },
            multiplier = model.contextWindowLabel,
            capabilities = model.capabilities,
            iconAsset = providerIconAsset(kind),
        )
    }
}

internal fun providerIconAsset(kind: ModelProviderKind): String = when (kind) {
    ModelProviderKind.DEFAULT -> "stockchat_app_icon.png"
    ModelProviderKind.ALIYUN -> "tongyi-qianwen.png"
    ModelProviderKind.DEEPSEEK -> "deepseek.png"
    ModelProviderKind.GLM -> "glm.png"
    ModelProviderKind.KIMI -> "kimi.png"
    ModelProviderKind.MIMO -> "mimo.png"
    ModelProviderKind.CUSTOM -> "stockchat_app_icon.png"
}
