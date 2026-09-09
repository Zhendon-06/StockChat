package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.maxTextLengthLegacy
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 模型配置页服务商编辑区：介绍、服务商选择与表单字段。

internal fun ModelConfigurationPage.Introduction(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                width((ctx.pagerData.pageViewWidth - 40f.settingsDp()).coerceAtLeast(1f))
                alignSelfCenter()
                padding(top = 10f.settingsDp(), bottom = 2f.settingsDp())
            }
            Text {
                attr {
                    text("Provider 配置")
                    fontSize(14f.settingsDp())
                    fontWeightBold()
                    color(ctx.palette().textTertiary)
                }
            }
            Text {
                attr {
                    text("默认服务无需配置，也可切换并维护五个模型服务商。")
                    fontSize(13f.settingsDp())
                    lineHeight(20f.settingsDp())
                    color(ctx.palette().textSecondary)
                    marginTop(7f.settingsDp())
                }
            }
        }
    }
}

internal fun ModelConfigurationPage.ProviderSelector(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        Scroller {
            attr {
                height(100f.settingsDp())
                flexDirectionRow()
                padding(left = 16f.settingsDp(), right = 6f.settingsDp())
                marginTop(10f.settingsDp())
                showScrollerIndicator(false)
                bouncesEnable(true)
            }
            ctx.configuration.providers.forEach { provider -> ctx.ProviderChoice(this, provider) }
        }
    }
}

internal fun ModelConfigurationPage.ProviderEditor(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        SettingsCard(
            width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
            palette = ctx::palette,
            marginTop = 6f.settingsDp(),
        ) {
            View {
                attr {
                    padding(
                        top = 18f.settingsDp(),
                        left = 18f.settingsDp(),
                        right = 18f.settingsDp(),
                        bottom = 18f.settingsDp(),
                    )
                }
                ctx.ProviderIdentity(this)
                ctx.BuiltInProviderFields(this)
                ctx.CustomProviderFields(this)
                ctx.ProviderKeyEditor(this)
                Text {
                    attr {
                        text(
                            if (ctx.selectedProvider().kind == ModelProviderKind.DEFAULT) {
                                "内置模型仅供演示，不构成投资建议。"
                            } else {
                                "未填写 API Key 时不会发起该 Provider 的鉴权请求。"
                            }
                        )
                        fontSize(11f.settingsDp())
                        color(ctx.palette().textTertiary)
                        marginTop(8f.settingsDp())
                    }
                }
                ctx.ProviderActions(this)
            }
        }
    }
}

internal fun ModelConfigurationPage.ProviderField(
    container: ViewContainer<*, *>,
    label: String,
    value: () -> String,
    placeholder: String,
    onChanged: (String) -> Unit,
    readOnly: Boolean = false,
) {
    val ctx = this
    with(container) {
        View {
            attr { marginTop(15f.settingsDp()) }
            Text {
                attr {
                    text(label)
                    fontSize(11f.settingsDp())
                    color(ctx.palette().textTertiary)
                    marginLeft(4f.settingsDp())
                }
            }
            Input {
                attr {
                    height(48f.settingsDp())
                    marginTop(6f.settingsDp())
                    borderRadius(13f.settingsDp())
                    backgroundColor(ctx.palette().surfaceMuted)
                    border(Border(1f.settingsDp(), BorderStyle.SOLID, ctx.palette().divider))
                    textAlignLeft()
                    text(value())
                    fontSize(14f.settingsDp())
                    color(ctx.palette().textPrimary)
                    tintColor(ctx.palette().accent)
                    placeholder(placeholder)
                    placeholderColor(ctx.palette().textTertiary)
                    maxTextLengthLegacy(240)
                    touchEnable(!readOnly)
                }
                event { textDidChange(isSyncEdit = true) { onChanged(it.text) } }
            }
            vif({ readOnly }) {
                Text {
                    attr {
                        text("🔒")
                        fontSize(12f.settingsDp())
                        absolutePosition(right = 14f.settingsDp(), bottom = 16f.settingsDp())
                    }
                }
            }
        }
    }
}

private fun ModelConfigurationPage.ProviderChoice(
    container: ViewContainer<*, *>,
    provider: ModelProviderConfig,
) {
    val ctx = this
    with(container) {
        View {
            attr {
                val selected = ctx.selectedProviderId == provider.id
                width(118f.settingsDp())
                height(88f.settingsDp())
                borderRadius(18f.settingsDp())
                padding(
                    top = 12f.settingsDp(),
                    left = 12f.settingsDp(),
                    right = 12f.settingsDp(),
                    bottom = 10f.settingsDp(),
                )
                marginRight(10f.settingsDp())
                backgroundColor(if (selected) ctx.palette().accentSoft else ctx.palette().surface)
                border(
                    Border(
                        if (selected) 1.5f.settingsDp() else 1f.settingsDp(),
                        BorderStyle.SOLID,
                        if (selected) ctx.palette().accent else ctx.palette().divider,
                    )
                )
            }
            event { click { ctx.switchProvider(provider.id) } }
            View {
                attr {
                    size(32f.settingsDp(), 32f.settingsDp())
                    borderRadius(10f.settingsDp())
                    backgroundColor(Color.WHITE)
                    allCenter()
                }
                Image {
                    attr {
                        size(27f.settingsDp(), 27f.settingsDp())
                        resizeContain()
                        src(ImageUri.commonAssets(ctx.providerAsset(provider.kind)))
                    }
                }
            }
            Text {
                attr {
                    text(provider.kind.displayName)
                    fontSize(12f.settingsDp())
                    fontWeightBold()
                    color(ctx.palette().textPrimary)
                    marginTop(8f.settingsDp())
                    lines(1)
                }
            }
            vif({ ctx.selectedProviderId == provider.id }) {
                View {
                    attr {
                        absolutePosition(top = 9f.settingsDp(), right = 9f.settingsDp())
                        size(18f.settingsDp(), 18f.settingsDp())
                        borderRadius(9f.settingsDp())
                        backgroundColor(ctx.palette().accent)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("✓")
                            fontSize(10f.settingsDp())
                            fontWeightBold()
                            color(Color.WHITE)
                        }
                    }
                }
            }
        }
    }
}

private fun ModelConfigurationPage.ProviderIdentity(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                height(44f.settingsDp())
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    size(38f.settingsDp(), 38f.settingsDp())
                    borderRadius(12f.settingsDp())
                    backgroundColor(Color.WHITE)
                    allCenter()
                }
                Image {
                    attr {
                        size(32f.settingsDp(), 32f.settingsDp())
                        resizeContain()
                        // 必须在 attr 闭包内读取 observable，
                        // 否则切换 Provider 时图标不会跟随刷新
                        src(ImageUri.commonAssets(ctx.providerAsset(ctx.selectedProvider().kind)))
                    }
                }
            }
            Text {
                attr {
                    text(ctx.selectedProvider().kind.displayName)
                    fontSize(17f.settingsDp())
                    fontWeightBold()
                    color(ctx.palette().textPrimary)
                    marginLeft(11f.settingsDp())
                    flex(1f)
                    lines(1)
                }
            }
        }
    }
}

private fun ModelConfigurationPage.BuiltInProviderFields(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        vif({ ctx.selectedProvider().kind == ModelProviderKind.DEFAULT }) {
            ctx.ProviderField(
                this,
                label = "Provider 名称",
                value = { ctx.providerName },
                placeholder = "服务商名称",
                onChanged = { ctx.providerName = it },
                readOnly = true,
            )
            Text {
                attr {
                    text("内置千问服务，使用构建时配置的 API Key，直接选择下方模型即可使用。")
                    fontSize(12f.settingsDp())
                    lineHeight(18f.settingsDp())
                    color(ctx.palette().textSecondary)
                    marginTop(14f.settingsDp())
                }
            }
        }
    }
}

private fun ModelConfigurationPage.CustomProviderFields(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        vif({ ctx.selectedProvider().kind != ModelProviderKind.DEFAULT }) {
            ctx.ProviderField(
                this,
                label = "Provider 名称",
                value = { ctx.providerName },
                placeholder = "服务商名称",
                onChanged = { ctx.providerName = it },
            )
            ctx.ProviderField(
                this,
                label = "Base URL",
                value = { ctx.baseUrl },
                placeholder = "https://api.example.com/v1",
                onChanged = { ctx.updateBaseUrl(it) },
            )
        }
    }
}

private fun ModelConfigurationPage.ProviderKeyEditor(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        vif({ ctx.selectedProvider().kind != ModelProviderKind.DEFAULT }) {
            View {
                attr { marginTop(15f.settingsDp()) }
                Text {
                    attr {
                        text("API Key")
                        fontSize(11f.settingsDp())
                        color(ctx.palette().textTertiary)
                        marginLeft(4f.settingsDp())
                    }
                }
                View {
                    attr {
                        height(48f.settingsDp())
                        marginTop(6f.settingsDp())
                        borderRadius(13f.settingsDp())
                        backgroundColor(ctx.palette().surfaceMuted)
                        border(Border(1f.settingsDp(), BorderStyle.SOLID, ctx.palette().divider))
                        flexDirectionRow()
                        alignItemsCenter()
                        overflow(true)
                    }
                    vif({ !ctx.keyVisible }) { ctx.ProviderKeyInput(this, password = true) }
                    vif({ ctx.keyVisible }) { ctx.ProviderKeyInput(this, password = false) }
                    View {
                        attr {
                            width(48f.settingsDp())
                            height(48f.settingsDp())
                            allCenter()
                        }
                        event { click { ctx.keyVisible = !ctx.keyVisible } }
                        Text {
                            attr {
                                text(if (ctx.keyVisible) "隐藏" else "查看")
                                fontSize(11f.settingsDp())
                                fontWeightMedium()
                                color(ctx.palette().textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ModelConfigurationPage.ProviderActions(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        vif({ ctx.selectedProvider().kind != ModelProviderKind.DEFAULT }) {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(14f.settingsDp())
                }
                View {
                    val loadInProgress = ctx.modelListLoading
                    attr {
                        height(44f.settingsDp())
                        width(116f.settingsDp())
                        borderRadius(22f.settingsDp())
                        backgroundColor(
                            if (loadInProgress) {
                                ctx.palette().surfaceMuted
                            } else {
                                ctx.palette().accentSoft
                            }
                        )
                        allCenter()
                        touchEnable(!loadInProgress)
                    }
                    event { click { ctx.loadModels(force = true) } }
                    Text {
                        attr {
                            text(if (loadInProgress) "获取中…" else "获取可用模型")
                            fontSize(12f.settingsDp())
                            fontWeightBold()
                            color(
                                if (loadInProgress) {
                                    ctx.palette().textTertiary
                                } else {
                                    ctx.palette().accent
                                }
                            )
                            lines(1)
                        }
                    }
                }
                View {
                    attr {
                        height(44f.settingsDp())
                        flex(1f)
                        borderRadius(22f.settingsDp())
                        backgroundColor(ctx.palette().accent)
                        allCenter()
                        marginLeft(10f.settingsDp())
                    }
                    event { click { ctx.saveProvider() } }
                    Text {
                        attr {
                            text("保存并设为当前 Provider")
                            fontSize(13f.settingsDp())
                            fontWeightBold()
                            color(Color.WHITE)
                            lines(1)
                        }
                    }
                }
            }
        }
    }
}

private fun ModelConfigurationPage.ProviderKeyInput(
    container: ViewContainer<*, *>,
    password: Boolean,
) {
    val ctx = this
    with(container) {
        Input {
            attr {
                flex(1f)
                height(48f.settingsDp())
                marginLeft(13f.settingsDp())
                marginRight(8f.settingsDp())
                text(ctx.apiKey)
                fontSize(14f.settingsDp())
                color(ctx.palette().textPrimary)
                tintColor(ctx.palette().accent)
                placeholder("未填写时使用本地 Mock 数据")
                placeholderColor(ctx.palette().textTertiary)
                if (password) keyboardTypePassword()
                maxTextLengthLegacy(256)
            }
            event {
                textDidChange(isSyncEdit = true) { ctx.updateApiKey(it.text) }
                inputReturn { ctx.loadModels(force = true) }
            }
        }
    }
}
