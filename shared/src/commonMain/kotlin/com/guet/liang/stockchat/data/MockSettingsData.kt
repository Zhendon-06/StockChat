package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AppearanceSettings
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.ShareContent
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

    val sharedChats = listOf(
        SharedChatRecord(
            id = "demo-share-moutai",
            sessionId = "demo-session-moutai",
            question = "贵州茅台最近的走势怎么样？",
            content = ShareContent(
                title = "StockChat｜贵州茅台行情解读",
                text = "贵州茅台（600519）演示行情小幅上行，短线走势仍需结合成交量和基本面观察。\n\n$STOCK_CHAT_RISK_DISCLOSURE",
            ),
            sharedAtEpochMillis = 1_788_192_000_000L,
            destinationLabel = "系统分享",
            isDemo = true,
        ),
        SharedChatRecord(
            id = "demo-share-catl",
            sessionId = "demo-session-catl",
            question = "帮我总结宁德时代的关键指标",
            content = ShareContent(
                title = "StockChat｜宁德时代指标摘要",
                text = "宁德时代（300750）演示数据包含现价、涨跌幅和走势摘要，便于快速对比。\n\n$STOCK_CHAT_RISK_DISCLOSURE",
            ),
            sharedAtEpochMillis = 1_788_109_200_000L,
            destinationLabel = "微信",
            isDemo = true,
        ),
        SharedChatRecord(
            id = "demo-share-index",
            sessionId = "demo-session-index",
            question = "今天沪深300表现如何？",
            content = ShareContent(
                title = "StockChat｜沪深300市场摘要",
                text = "沪深300（000300）演示指数温和上行，内容仅用于展示分享记录与行情卡片。\n\n$STOCK_CHAT_RISK_DISCLOSURE",
            ),
            sharedAtEpochMillis = 1_787_846_400_000L,
            destinationLabel = "复制链接",
            isDemo = true,
        ),
    )

    val modelConfiguration = ModelConfiguration(
        activeProviderId = ALIYUN_PROVIDER_ID,
        providers = listOf(
            provider(
                id = ALIYUN_PROVIDER_ID,
                kind = ModelProviderKind.ALIYUN,
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                selectedModelId = "qwen-plus",
                models = listOf(
                    model("qwen-plus", "Qwen Plus", "128K"),
                    model("qwen-max", "Qwen Max", "32K"),
                    model(
                        id = "qwen-vl-plus",
                        displayName = "Qwen VL Plus",
                        contextWindowLabel = "32K",
                        capabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION),
                    ),
                ),
            ),
            provider(
                id = DEEPSEEK_PROVIDER_ID,
                kind = ModelProviderKind.DEEPSEEK,
                baseUrl = "https://api.deepseek.com/v1",
                selectedModelId = "deepseek-chat",
                models = listOf(
                    model("deepseek-chat", "DeepSeek Chat", "64K"),
                    model(
                        id = "deepseek-reasoner",
                        displayName = "DeepSeek Reasoner",
                        contextWindowLabel = "64K",
                        capabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING),
                    ),
                ),
            ),
            provider(
                id = GLM_PROVIDER_ID,
                kind = ModelProviderKind.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                selectedModelId = "glm-4-plus",
                models = listOf(
                    model("glm-4-plus", "GLM-4 Plus", "128K"),
                    model("glm-4-air", "GLM-4 Air", "128K"),
                    model(
                        id = "glm-4v-plus",
                        displayName = "GLM-4V Plus",
                        contextWindowLabel = "16K",
                        capabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION),
                    ),
                ),
            ),
            provider(
                id = KIMI_PROVIDER_ID,
                kind = ModelProviderKind.KIMI,
                baseUrl = "https://api.moonshot.cn/v1",
                selectedModelId = "moonshot-v1-32k",
                models = listOf(
                    model("moonshot-v1-8k", "Moonshot 8K", "8K"),
                    model("moonshot-v1-32k", "Moonshot 32K", "32K"),
                    model("moonshot-v1-128k", "Moonshot 128K", "128K"),
                ),
            ),
            provider(
                id = MIMO_PROVIDER_ID,
                kind = ModelProviderKind.MIMO,
                baseUrl = "https://api.xiaomimimo.com/v1",
                selectedModelId = "mimo-v2.5",
                models = listOf(
                    model("mimo-v2.5", "MiMo V2.5", "128K"),
                    model(
                        id = "mimo-v2.5-reasoning",
                        displayName = "MiMo V2.5 Reasoning",
                        contextWindowLabel = "128K",
                        capabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING),
                    ),
                    model(
                        id = "mimo-v2.5-voice",
                        displayName = "MiMo V2.5 Voice",
                        contextWindowLabel = "32K",
                        capabilities = setOf(ModelCapability.CHAT, ModelCapability.VOICE),
                    ),
                ),
            ),
        ),
    )

    private fun provider(
        id: String,
        kind: ModelProviderKind,
        baseUrl: String,
        selectedModelId: String,
        models: List<ModelOption>,
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
        contextWindowLabel: String,
        capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT),
    ): ModelOption {
        return ModelOption(
            id = id,
            displayName = displayName,
            contextWindowLabel = contextWindowLabel,
            capabilities = capabilities,
        )
    }

    private const val ALIYUN_PROVIDER_ID = "aliyun"
    private const val DEEPSEEK_PROVIDER_ID = "deepseek"
    private const val GLM_PROVIDER_ID = "glm"
    private const val KIMI_PROVIDER_ID = "kimi"
    private const val MIMO_PROVIDER_ID = "mimo"
}
