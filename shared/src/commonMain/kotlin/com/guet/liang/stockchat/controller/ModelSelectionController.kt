package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.base.StockChatLog
import com.guet.liang.stockchat.base.toUserMessage
import com.guet.liang.stockchat.data.AliyunApiConfig
import com.guet.liang.stockchat.data.DEFAULT_CHAT_BASE_URL
import com.guet.liang.stockchat.data.ContextWindowManager
import com.guet.liang.stockchat.data.SettingsRepository
import com.guet.liang.stockchat.data.StockChatDataSource
import com.guet.liang.stockchat.model.AnswerMode
import com.guet.liang.stockchat.model.ChatModelOption
import com.guet.liang.stockchat.model.DEFAULT_CHAT_MODEL_ICON_ASSET
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.providerIconAsset
import com.guet.liang.stockchat.model.toChatModelOptions

/** Saved model selection and current catalog status presented by the drawer. */
internal data class ModelSelectionState(
    val providerId: String = "",
    val modelId: String = "",
    val options: List<ChatModelOption> = emptyList(),
    val label: String = "选择模型",
    val icon: String = DEFAULT_CHAT_MODEL_ICON_ASSET,
    val loading: Boolean = false,
    val error: String = "",
    val answerMode: AnswerMode = AnswerMode.FAST,
) {
    /** Option matching the saved model id, else the first option, else a placeholder for an empty catalog. */
    val selectedModel: ChatModelOption
        get() =
            options.firstOrNull { it.id == modelId }
                ?: options.firstOrNull()
                ?: ChatModelOption(
                    id = modelId,
                    displayName = "未选择模型",
                    description = "当前 Provider 暂无可用模型",
                    badge = "",
                    multiplier = "",
                    iconAsset = DEFAULT_CHAT_MODEL_ICON_ASSET,
                )
}

/** Rebuilds the chat source and invalidates catalog results when provider credentials change. */
internal class ModelSelectionController(
    private val settings: SettingsRepository,
    private val catalog: SettingsCatalogRepository,
    private val sourceFactory: (AliyunApiConfig) -> StockChatDataSource,
    private val scheduleTimeout: (Int, () -> Unit) -> Unit,
    private val routeApiKey: String = "",
    private val routeBaseUrl: String = "",
    private val onChanged: (ModelSelectionState) -> Unit = {},
) {
    var state = ModelSelectionState()
        private set

    lateinit var dataSource: StockChatDataSource
        private set

    private var token = 0
    private var lastFetch = ""
    private var inFlight = ""

    fun configureChatProvider() {
        val provider = activeProvider()
        if (state.providerId != provider?.id.orEmpty()) invalidate()
        val options = provider?.toChatModelOptions(state.modelId).orEmpty()
        val selected = provider?.selectedModelId?.takeIf { id -> options.any { it.id == id } } ?: options.firstOrNull()?.id.orEmpty()
        val capabilities = options.firstOrNull { it.id == selected }?.capabilities.orEmpty()
        val contextWindowTokens = ContextWindowManager.parseContextWindow(options.firstOrNull { it.id == selected }?.multiplier.orEmpty())
        val answerMode = settings.loadSnapshot().modelConfiguration.answerMode
        dataSource = sourceFactory(providerConfig(provider, selected, capabilities, contextWindowTokens, answerMode))
        publish(
            state.copy(
                providerId = provider?.id.orEmpty(),
                modelId = selected,
                options = options,
                answerMode = answerMode,
                label =
                    when (provider?.kind) {
                        ModelProviderKind.CUSTOM -> provider.displayName
                        null -> "选择模型"
                        else -> provider.kind.displayName
                    },
                icon = provider?.kind?.let(::providerIconAsset) ?: DEFAULT_CHAT_MODEL_ICON_ASSET,
            )
        )
    }

    fun selectModel(modelId: String) {
        if (state.options.none { it.id == modelId }) return
        publish(state.copy(modelId = modelId))
        if (state.providerId.isNotBlank()) {
            settings.selectModel(state.providerId, modelId)
            configureChatProvider()
        }
    }

    /** Persists the answering strategy and rebuilds the source so the next turn uses it. */
    fun selectAnswerMode(mode: AnswerMode) {
        settings.setAnswerMode(mode)
        configureChatProvider()
    }

    fun fetchDrawerModels(force: Boolean = false) {
        publish(state.copy(error = ""))
        val provider = activeProvider()
        if (provider == null || !canFetch(provider)) {
            invalidate()
            return
        }
        val fingerprint = fingerprint(provider)
        if (state.loading && inFlight == fingerprint) return
        if (state.loading) invalidate()
        if (!force && lastFetch == fingerprint) return
        val requestToken = ++token
        lastFetch = fingerprint
        inFlight = fingerprint
        publish(state.copy(loading = true))
        scheduleTimeout(CATALOG_TIMEOUT_MS) { if (requestToken == token && state.loading) fail("模型列表请求超时，请检查网络后重试。") }
        runCatching {
                catalog.load(provider.baseUrl.trim().trimEnd('/'), requestKey(provider)) { result ->
                    completeCatalog(requestToken, provider, fingerprint, result)
                }
            }
            .onFailure { throwable ->
                StockChatLog.w("ModelSelectionController", "fetch models failed", throwable)
                if (requestToken == token && state.loading) fail(throwable.toUserMessage("模型列表请求失败，请稍后重试。"))
            }
    }

    fun retryDrawerModels() = fetchDrawerModels(force = true)

    fun invalidate() {
        token += 1
        lastFetch = ""
        inFlight = ""
        publish(state.copy(loading = false, error = ""))
    }

    private fun completeCatalog(requestToken: Int, requested: ModelProviderConfig, fingerprint: String, result: ModelCatalogResult) {
        if (requestToken != token) return
        val latest = activeProvider()
        if (latest == null || latest.id != requested.id || fingerprint(latest) != fingerprint) {
            invalidate()
            return
        }
        inFlight = ""
        publish(state.copy(loading = false))
        when (result) {
            is ModelCatalogResult.Failure -> fail(result.message)
            is ModelCatalogResult.Success -> {
                settings.saveModelProvider(
                    latest.copy(
                        models = result.models,
                        selectedModelId =
                            result.models.firstOrNull { it.id == latest.selectedModelId }?.id ?: result.models.firstOrNull()?.id.orEmpty(),
                    )
                )
                configureChatProvider()
            }
        }
    }

    private fun activeProvider(): ModelProviderConfig? {
        val configuration = settings.loadSnapshot().modelConfiguration
        return configuration.providers.firstOrNull { it.id == configuration.activeProviderId }
    }

    private fun canFetch(provider: ModelProviderConfig): Boolean =
        provider.kind != ModelProviderKind.DEFAULT && requestKey(provider).isNotBlank() && provider.baseUrl.isNotBlank()

    private fun requestKey(provider: ModelProviderConfig): String =
        provider.apiKey.ifBlank { if (provider.kind == ModelProviderKind.ALIYUN) routeApiKey.trim() else "" }

    private fun fingerprint(provider: ModelProviderConfig): String =
        "${provider.id}|${provider.baseUrl.trim().trimEnd('/')}|${requestKey(provider)}"

    private fun providerConfig(
        provider: ModelProviderConfig?,
        selected: String,
        capabilities: Set<ModelCapability>,
        contextWindowTokens: Int,
        answerMode: AnswerMode,
    ): AliyunApiConfig {
        val dashScope = provider == null || provider.kind in setOf(ModelProviderKind.DEFAULT, ModelProviderKind.ALIYUN)
        val key =
            when {
                provider == null -> routeApiKey.trim()
                !provider.isEnabled -> ""
                provider.apiKey.isNotBlank() -> provider.apiKey
                dashScope -> routeApiKey.trim()
                else -> ""
            }
        return AliyunApiConfig(
            apiKey = key,
            baseUrl = routeBaseUrl.trim().takeIf { dashScope && it.isNotBlank() }
                ?: provider?.baseUrl?.takeIf(String::isNotBlank)
                ?: DEFAULT_CHAT_BASE_URL,
            chatModel = selected,
            visionModel = selected,
            providerDisplayName = provider?.displayName ?: "选择模型",
            useAliyunExtensions = dashScope,
            supportsVision = ModelCapability.VISION in capabilities,
            supportsStreaming = ModelCapability.STREAMING in capabilities,
            contextWindowTokens = contextWindowTokens,
            answerMode = answerMode,
        )
    }

    private fun fail(message: String) {
        invalidate()
        publish(state.copy(error = message))
    }

    private fun publish(next: ModelSelectionState) {
        state = next
        onChanged(next)
    }

    companion object {
        private const val CATALOG_TIMEOUT_MS = 35_000
    }
}
