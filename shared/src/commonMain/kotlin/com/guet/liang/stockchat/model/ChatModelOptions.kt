package com.guet.liang.stockchat.model

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
