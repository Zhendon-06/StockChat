package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.StockPredictionConfig

/** Disabled providers never fall back to route credentials. */
internal fun detailPredictionConfig(
    configuration: ModelConfiguration,
    routeApiKey: String,
    routeBaseUrl: String = "",
): StockPredictionConfig {
    val provider = configuration.providers.firstOrNull { it.id == configuration.activeProviderId }
    val dashScope = provider == null || provider.kind in setOf(ModelProviderKind.DEFAULT, ModelProviderKind.ALIYUN)
    val key =
        when {
            provider == null -> routeApiKey.trim()
            !provider.isEnabled -> ""
            provider.apiKey.isNotBlank() -> provider.apiKey.trim()
            dashScope -> routeApiKey.trim()
            else -> ""
        }
    return StockPredictionConfig(
        apiKey = key,
        baseUrl = routeBaseUrl.trim().takeIf { dashScope && it.isNotBlank() }
            ?: provider?.baseUrl?.trim().orEmpty().ifBlank { if (dashScope) DEFAULT_CHAT_BASE_URL else "" },
        model = provider?.selectedModelId?.trim().orEmpty().ifBlank { provider?.models?.firstOrNull()?.id?.trim().orEmpty() },
        providerDisplayName = provider?.displayName?.trim().orEmpty().ifBlank { "AI 模型" },
        useAliyunExtensions = dashScope,
    )
}
