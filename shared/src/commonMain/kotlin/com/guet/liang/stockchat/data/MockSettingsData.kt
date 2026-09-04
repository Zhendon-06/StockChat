package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AppearanceSettings
import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.SharedChatRecord
import com.guet.liang.stockchat.model.StockTablePreviewRow

internal object MockSettingsData {
    val appearance = AppearanceSettings()

    val tablePreviewRows = listOf(
        StockTablePreviewRow(
            name = "贵州茅台",
            symbol = "600519",
            price = "1,428.60",
            changePercent = "+0.90%",
            turnover = "18.6 亿",
            isPositive = true,
        ),
        StockTablePreviewRow(
            name = "宁德时代",
            symbol = "300750",
            price = "218.35",
            changePercent = "-0.97%",
            turnover = "32.4 亿",
            isPositive = false,
        ),
        StockTablePreviewRow(
            name = "沪深300",
            symbol = "000300",
            price = "3,892.41",
            changePercent = "+0.47%",
            turnover = "2,184 亿",
            isPositive = true,
        ),
        StockTablePreviewRow(
            name = "中证500",
            symbol = "000905",
            price = "5,642.18",
            changePercent = "-0.38%",
            turnover = "1,476 亿",
            isPositive = false,
        ),
    )

    val sharedChats = emptyList<SharedChatRecord>()

    // 默认 Provider 提供内置免费模型；其他 Provider 的模型列表需由用户拉取。
    val modelConfiguration = ModelConfiguration(
        activeProviderId = DEFAULT_PROVIDER_ID,
        providers = listOf(
            provider(
                id = DEFAULT_PROVIDER_ID,
                kind = ModelProviderKind.DEFAULT,
                baseUrl = "",
                models = listOf(
                    model("stockchat-default", "股票助手", setOf(ModelCapability.CHAT)),
                    model("stockchat-analysis", "深度分析", setOf(ModelCapability.CHAT, ModelCapability.REASONING)),
                    model("stockchat-fast", "快速问答", setOf(ModelCapability.CHAT)),
                ),
                selectedModelId = "stockchat-default",
            ),
            provider(
                id = ALIYUN_PROVIDER_ID,
                kind = ModelProviderKind.ALIYUN,
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            ),
            provider(
                id = DEEPSEEK_PROVIDER_ID,
                kind = ModelProviderKind.DEEPSEEK,
                baseUrl = "https://api.deepseek.com/v1",
            ),
            provider(
                id = GLM_PROVIDER_ID,
                kind = ModelProviderKind.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            ),
            provider(
                id = KIMI_PROVIDER_ID,
                kind = ModelProviderKind.KIMI,
                baseUrl = "https://api.moonshot.cn/v1",
            ),
            provider(
                id = MIMO_PROVIDER_ID,
                kind = ModelProviderKind.MIMO,
                baseUrl = "https://api.xiaomimimo.com/v1",
            ),
        ),
    )

    private fun provider(
        id: String,
        kind: ModelProviderKind,
        baseUrl: String,
        models: List<ModelOption> = emptyList(),
        selectedModelId: String = "",
    ): ModelProviderConfig {
        return ModelProviderConfig(
            id = id,
            kind = kind,
            displayName = kind.displayName,
            baseUrl = baseUrl,
            apiKey = "",
            models = models,
            selectedModelId = selectedModelId,
        )
    }

    private fun model(
        id: String,
        displayName: String,
        capabilities: Set<com.guet.liang.stockchat.model.ModelCapability>,
    ) = ModelOption(id, displayName, "", capabilities)

    private const val ALIYUN_PROVIDER_ID = "aliyun"
    private const val DEFAULT_PROVIDER_ID = "default"
    private const val DEEPSEEK_PROVIDER_ID = "deepseek"
    private const val GLM_PROVIDER_ID = "glm"
    private const val KIMI_PROVIDER_ID = "kimi"
    private const val MIMO_PROVIDER_ID = "mimo"
}
