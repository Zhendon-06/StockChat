package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderKind
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 模型配置页模型列表：目录状态、模型卡片与能力标签。

internal fun ModelConfigurationPage.ModelList(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        vif({ ctx.modelListLoading || ctx.modelListVisible || ctx.modelListError.isNotBlank() }) {
            View {
                attr {
                    width((ctx.pagerData.pageViewWidth - 40f.settingsDp()).coerceAtLeast(1f))
                    alignSelfCenter()
                    marginTop(20f.settingsDp())
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text("模型列表")
                        fontSize(14f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().textTertiary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text(
                            when {
                                ctx.modelListLoading -> "获取中"
                                ctx.modelListVisible -> "${ctx.availableModels.size} 个模型"
                                ctx.modelListError.isNotBlank() -> "获取失败"
                                else -> "待获取"
                            }
                        )
                        fontSize(12f.settingsDp())
                        color(ctx.palette().textSecondary)
                    }
                }
            }
            vif({ ctx.selectedProvider().kind == ModelProviderKind.DEFAULT }) {
                Text {
                    attr {
                        width((ctx.pagerData.pageViewWidth - 40f.settingsDp()).coerceAtLeast(1f))
                        alignSelfCenter()
                        text("内置千问三档模型，直接点击模型即可使用")
                        fontSize(12f.settingsDp())
                        color(ctx.palette().textSecondary)
                        marginTop(6f.settingsDp())
                    }
                }
            }
            vif({ ctx.modelListLoading }) {
                ctx.ModelCatalogStatus(this, "正在从 Provider 获取可用模型…", false)
            }
            vif({ !ctx.modelListLoading && ctx.modelListError.isNotBlank() }) {
                ctx.ModelCatalogStatus(this, ctx.modelListError, true)
            }
            vif({ ctx.modelListVisible && ctx.availableModels.isEmpty() }) {
                ctx.ModelCatalogStatus(this, "该 Provider 暂未返回可用模型。", true)
            }
            vif({ ctx.modelListVisible && ctx.availableModels.isNotEmpty() }) {
                ctx.availableModels.forEach { model -> ctx.ModelCard(this, model) }
            }
        }
    }
}

internal fun ModelConfigurationPage.ModelCatalogStatus(
    container: ViewContainer<*, *>,
    message: String,
    isError: Boolean,
) {
    val ctx = this
    with(container) {
        SettingsCard(
            width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
            palette = ctx::palette,
            marginTop = 10f.settingsDp(),
        ) {
            View {
                attr {
                    minHeight(58f.settingsDp())
                    padding(
                        top = 14f.settingsDp(),
                        left = 16f.settingsDp(),
                        right = 16f.settingsDp(),
                        bottom = 14f.settingsDp(),
                    )
                }
                Text {
                    attr {
                        text(message)
                        fontSize(12f.settingsDp())
                        lineHeight(18f.settingsDp())
                        color(
                            if (isError) {
                                ctx.palette().warning
                            } else {
                                ctx.palette().textSecondary
                            }
                        )
                    }
                }
                if (isError) {
                    View {
                        attr {
                            height(30f.settingsDp())
                            alignSelfFlexStart()
                            padding(left = 10f.settingsDp(), right = 10f.settingsDp())
                            borderRadius(15f.settingsDp())
                            backgroundColor(ctx.palette().accentSoft)
                            allCenter()
                            marginTop(8f.settingsDp())
                        }
                        event { click { ctx.loadModels(force = true) } }
                        Text {
                            attr {
                                text("重试")
                                fontSize(11f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ModelConfigurationPage.ModelCard(container: ViewContainer<*, *>, model: ModelOption) {
    val ctx = this
    with(container) {
        SettingsCard(
            width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
            palette = ctx::palette,
            marginTop = 10f.settingsDp(),
        ) {
            View {
                attr {
                    minHeight(84f.settingsDp())
                    padding(
                        top = 14f.settingsDp(),
                        left = 16f.settingsDp(),
                        right = 14f.settingsDp(),
                        bottom = 14f.settingsDp(),
                    )
                    flexDirectionRow()
                    alignItemsCenter()
                    backgroundColor(
                        if (ctx.selectedModelId == model.id) {
                            ctx.palette().accentSoft
                        } else {
                            ctx.palette().surface
                        }
                    )
                }
                event { click { ctx.chooseModel(model.id) } }
                ctx.ModelDescription(this, model)
                View {
                    attr {
                        size(26f.settingsDp(), 26f.settingsDp())
                        borderRadius(13f.settingsDp())
                        border(
                            Border(
                                1.5f.settingsDp(),
                                BorderStyle.SOLID,
                                if (ctx.selectedModelId == model.id) {
                                    ctx.palette().accent
                                } else {
                                    ctx.palette().divider
                                },
                            )
                        )
                        backgroundColor(
                            if (ctx.selectedModelId == model.id) {
                                ctx.palette().accent
                            } else {
                                ctx.palette().surface
                            }
                        )
                        allCenter()
                    }
                    Text {
                        attr {
                            text(if (ctx.selectedModelId == model.id) "✓" else "")
                            fontSize(13f.settingsDp())
                            fontWeightBold()
                            color(Color.WHITE)
                        }
                    }
                }
            }
        }
    }
}

internal fun ModelConfigurationPage.CapabilityChip(container: ViewContainer<*, *>, label: String) {
    val ctx = this
    with(container) {
        View {
            attr {
                height(20f.settingsDp())
                borderRadius(6f.settingsDp())
                padding(left = 7f.settingsDp(), right = 7f.settingsDp())
                marginRight(6f.settingsDp())
                backgroundColor(ctx.palette().surfaceMuted)
                allCenter()
            }
            Text {
                attr {
                    text(label)
                    fontSize(9f.settingsDp())
                    fontWeightMedium()
                    color(ctx.palette().textSecondary)
                }
            }
        }
    }
}

private fun ModelConfigurationPage.ModelDescription(
    container: ViewContainer<*, *>,
    model: ModelOption,
) {
    val ctx = this
    with(container) {
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text(model.displayName)
                    fontSize(15f.settingsDp())
                    fontWeightBold()
                    color(ctx.palette().textPrimary)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(model.id)
                    fontSize(11f.settingsDp())
                    color(ctx.palette().textSecondary)
                    marginTop(4f.settingsDp())
                    lines(1)
                }
            }
            View {
                attr {
                    height(22f.settingsDp())
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(7f.settingsDp())
                }
                val capabilityLabels = buildList {
                    add(
                        if (ModelCapability.VISION in model.capabilities) {
                            "视觉理解"
                        } else {
                            "仅文本"
                        }
                    )
                    add(
                        if (ModelCapability.STREAMING in model.capabilities) {
                            "流式输出"
                        } else {
                            "非流式"
                        }
                    )
                    model.capabilities
                        .filterNot {
                            it == ModelCapability.CHAT ||
                                it == ModelCapability.VISION ||
                                it == ModelCapability.STREAMING
                        }
                        .forEach { capability -> add(capability.displayName) }
                }
                capabilityLabels.forEach { label -> ctx.CapabilityChip(this, label) }
            }
        }
    }
}
