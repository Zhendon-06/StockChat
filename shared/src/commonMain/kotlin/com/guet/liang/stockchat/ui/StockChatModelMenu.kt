package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.AnswerMode
import com.guet.liang.stockchat.model.ChatModelOption
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 模型选择菜单：底部弹层、模型列表、加载/错误/空态与下滑关闭手势。

private const val MODEL_MENU_DISMISS_DISTANCE = 48f

// 模型选择底部弹层：与消息菜单一样常驻挂载，遮罩淡入淡出、面板自底部滑入滑出
internal fun StockChatPage.ModelMenuOverlay(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0x7A141A18))
                opacity(if (ctx.modelMenuOpen) 1f else 0f)
                touchEnable(ctx.modelMenuOpen)
                zIndex(13)
                animation(Animation.easeOut(0.24f), ctx.modelMenuOpen)
            }
            event { click { ctx.closeModelMenu() } }
        }
        View {
            attr {
                absolutePosition(left = 0f, right = 0f, bottom = 0f)
                height(metrics.dp(466f) + pagerData.safeAreaInsets.bottom)
                borderRadius(metrics.dp(28f), metrics.dp(28f), 0f, 0f)
                backgroundColor(StockChatTheme.surface)
                padding(left = metrics.dp(20f), right = metrics.dp(20f), bottom = pagerData.safeAreaInsets.bottom + metrics.dp(12f))
                transform(Translate(0f, if (ctx.modelMenuOpen) 0f else 1f))
                touchEnable(ctx.modelMenuOpen)
                zIndex(14)
                animation(Animation.easeOut(0.28f), ctx.modelMenuOpen)
            }
            View {
                attr {
                    height(metrics.dp(32f))
                    alignSelfStretch()
                    allCenter()
                    capture(CaptureRule.pan(CaptureRuleDirection.VERTICAL))
                }
                event { pan { params -> ctx.handleModelMenuPan(params) } }
                View {
                    attr {
                        width(metrics.dp(44f))
                        height(metrics.dp(5f))
                        borderRadius(metrics.dp(3f))
                        backgroundColor(StockChatTheme.borderStrong)
                    }
                }
            }
            Text {
                attr {
                    text("选择模型")
                    fontSize(metrics.dp(22f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    textAlignCenter()
                    marginTop(metrics.dp(20f))
                    marginBottom(metrics.dp(10f))
                }
            }
            Scroller {
                attr {
                    flex(1f)
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                }
                vif({ ctx.modelMenuContentRevision % 2 == 0 }) { ctx.ModelMenuContent(this) }
                velse { ctx.ModelMenuContent(this) }
            }
        }
    }
}

internal fun StockChatPage.ModelMenuContent(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        ctx.AnswerModeSection(this)
        vif({ ctx.drawerModelsLoading }) { ctx.DrawerModelNotice(this, message = "正在从 Provider 拉取可用模型…") }
        vif({ !ctx.drawerModelsLoading && ctx.drawerModelsError.isNotBlank() }) {
            ctx.DrawerModelNotice(this, message = ctx.drawerModelsError, actionText = "重试", onAction = { ctx.retryDrawerModels() })
        }
        vif({ !ctx.drawerModelsLoading && ctx.drawerModelsError.isBlank() && ctx.chatModelOptions.isEmpty() }) {
            ctx.DrawerModelEmptyState(this)
        }
        ctx.chatModelOptions.forEach { option -> ctx.ModelMenuItem(this, option) }
        Text {
            attr {
                text("模型选择会同步到设置，并影响后续回答")
                fontSize(metrics.dp(11f))
                color(StockChatTheme.textTertiary)
                textAlignCenter()
                marginTop(metrics.dp(8f))
                marginBottom(metrics.dp(8f))
            }
        }
    }
}

// 回答模式：并行（更快）与串行联网（正文可引用实时行情）二选一，随模型选择一起持久化
internal fun StockChatPage.AnswerModeSection(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        Text {
            attr {
                text("回答模式")
                fontSize(metrics.dp(13f))
                fontWeightBold()
                color(StockChatTheme.textSecondary)
                marginTop(metrics.dp(4f))
                marginBottom(metrics.dp(6f))
                marginLeft(metrics.dp(4f))
            }
        }
        AnswerMode.values().forEach { mode -> ctx.AnswerModeItem(this, mode) }
        View {
            attr {
                height(1f)
                alignSelfStretch()
                backgroundColor(StockChatTheme.borderStrong)
                marginTop(metrics.dp(10f))
                marginBottom(metrics.dp(10f))
            }
        }
    }
}

internal fun StockChatPage.AnswerModeItem(container: ViewContainer<*, *>, mode: AnswerMode) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                borderRadius(metrics.dp(16f))
                flexDirectionRow()
                alignItemsCenter()
                padding(top = metrics.dp(10f), bottom = metrics.dp(10f), left = metrics.dp(12f), right = metrics.dp(12f))
                marginBottom(metrics.dp(6f))
                backgroundColor(if (mode == ctx.answerMode) StockChatTheme.accentSoft else StockChatTheme.surfaceSoft)
            }
            event { click { ctx.selectAnswerMode(mode) } }
            Text {
                attr {
                    text(if (mode == AnswerMode.FAST) "⚡" else "🌐")
                    fontSize(metrics.dp(20f))
                    marginRight(metrics.dp(10f))
                }
            }
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text(mode.displayName)
                        fontSize(metrics.dp(15f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(mode.description)
                        fontSize(metrics.dp(11f))
                        lineHeight(metrics.dp(16f))
                        color(StockChatTheme.textSecondary)
                        marginTop(metrics.dp(3f))
                    }
                }
            }
            Text {
                attr {
                    text("✓")
                    fontSize(metrics.dp(20f))
                    fontWeightBold()
                    color(StockChatTheme.accent)
                    marginLeft(metrics.dp(8f))
                    opacity(if (mode == ctx.answerMode) 1f else 0f)
                }
            }
        }
    }
}

internal fun StockChatPage.ModelMenuItem(container: ViewContainer<*, *>, option: ChatModelOption) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                height(metrics.dp(76f))
                borderRadius(metrics.dp(18f))
                flexDirectionRow()
                alignItemsCenter()
                padding(left = metrics.dp(12f), right = metrics.dp(12f))
                backgroundColor(
                    if (option.id == ctx.selectedModelId) {
                        StockChatTheme.accentSoft
                    } else {
                        StockChatTheme.surface
                    }
                )
            }
            event { click { ctx.selectModel(option.id) } }
            View {
                attr {
                    size(metrics.dp(40f), metrics.dp(40f))
                    borderRadius(metrics.dp(13f))
                    backgroundColor(Color.WHITE)
                    allCenter()
                }
                Image {
                    attr {
                        size(metrics.dp(30f), metrics.dp(30f))
                        resizeContain()
                        src(ImageUri.commonAssets(option.iconAsset))
                    }
                }
            }
            View {
                attr {
                    flex(1f)
                    marginLeft(metrics.dp(12f))
                }
                ctx.ModelOptionTitle(this, option)
                Text {
                    attr {
                        text(option.description)
                        fontSize(metrics.dp(12f))
                        color(StockChatTheme.textSecondary)
                        marginTop(metrics.dp(4f))
                    }
                }
            }
            Text {
                attr {
                    text("✓")
                    fontSize(metrics.dp(24f))
                    fontWeightBold()
                    color(StockChatTheme.accent)
                    opacity(if (option.id == ctx.selectedModelId) 1f else 0f)
                }
            }
        }
    }
}

// 模型面板内的状态条：拉取中 / 拉取失败（可重试）
internal fun StockChatPage.DrawerModelNotice(
    container: ViewContainer<*, *>,
    message: String,
    actionText: String = "",
    onAction: (() -> Unit)? = null,
) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                marginTop(metrics.dp(6f))
                marginBottom(metrics.dp(6f))
                borderRadius(metrics.dp(14f))
                backgroundColor(StockChatTheme.surfaceSoft)
                padding(top = metrics.dp(12f), left = metrics.dp(14f), right = metrics.dp(14f), bottom = metrics.dp(12f))
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text(message)
                    fontSize(metrics.dp(12f))
                    lineHeight(metrics.dp(18f))
                    color(StockChatTheme.textSecondary)
                    flex(1f)
                }
            }
            if (onAction != null) {
                View {
                    attr {
                        marginLeft(metrics.dp(10f))
                        height(metrics.dp(30f))
                        paddingLeft(metrics.dp(12f))
                        paddingRight(metrics.dp(12f))
                        borderRadius(metrics.dp(15f))
                        backgroundColor(StockChatTheme.accentSoft)
                        allCenter()
                    }
                    event { click { onAction() } }
                    Text {
                        attr {
                            text(actionText)
                            fontSize(metrics.dp(12f))
                            fontWeightBold()
                            color(StockChatTheme.accent)
                        }
                    }
                }
            }
        }
    }
}

// 模型面板空态：当前 Provider 还没有拉取到任何模型时引导去模型配置页
internal fun StockChatPage.DrawerModelEmptyState(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                marginTop(metrics.dp(6f))
                borderRadius(metrics.dp(18f))
                backgroundColor(StockChatTheme.surfaceSoft)
                padding(all = metrics.dp(18f))
                alignItemsCenter()
            }
            Text {
                attr {
                    text("该 Provider 暂无可用模型列表")
                    fontSize(metrics.dp(15f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    textAlignCenter()
                }
            }
            Text {
                attr {
                    text("在「模型配置」页填写 API Key 并获取可用模型后，会同步显示在这里")
                    fontSize(metrics.dp(12f))
                    lineHeight(metrics.dp(18f))
                    color(StockChatTheme.textSecondary)
                    textAlignCenter()
                    marginTop(metrics.dp(6f))
                }
            }
            View {
                attr {
                    marginTop(metrics.dp(12f))
                    height(metrics.dp(36f))
                    paddingLeft(metrics.dp(16f))
                    paddingRight(metrics.dp(16f))
                    borderRadius(metrics.dp(18f))
                    backgroundColor(StockChatTheme.accent)
                    allCenter()
                }
                event { click { ctx.openModelConfiguration() } }
                Text {
                    attr {
                        text("前往模型配置")
                        fontSize(metrics.dp(13f))
                        fontWeightBold()
                        color(Color.WHITE)
                    }
                }
            }
        }
    }
}

internal fun StockChatPage.openModelMenu() {
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    resetKeyboardState()
    messageMenuTargetId = ""
    conversationMenuOpen = false
    closeDrawer()
    // 每次打开面板前都从最新 settings 重新构建 provider / model 选项，
    // 即便用户在「模型配置」页里切换了当前 provider 也能即时同步
    configureChatProvider()
    // 先展示已入库列表，再刷新一次 Provider，确保配置页和 Drawer 不会使用旧目录。
    fetchDrawerModels(force = true)
    modelMenuOpen = true
}

internal fun StockChatPage.closeModelMenu() {
    modelMenuOpen = false
}

internal fun StockChatPage.handleModelMenuPan(params: PanGestureParams) {
    when (params.state) {
        "start" -> {
            modelMenuPanStartX = params.pageX
            modelMenuPanStartY = params.pageY
        }
        "end" -> {
            val deltaX = params.pageX - modelMenuPanStartX
            val deltaY = params.pageY - modelMenuPanStartY
            if (modelMenuOpen && deltaY >= MODEL_MENU_DISMISS_DISTANCE && kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)) {
                closeModelMenu()
            }
            modelMenuPanStartX = 0f
            modelMenuPanStartY = 0f
        }
    }
}

private fun StockChatPage.ModelOptionTitle(container: ViewContainer<*, *>, option: ChatModelOption) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text(option.displayName)
                    fontSize(metrics.dp(17f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            vif({ option.isLocked }) {
                Text {
                    attr {
                        text("🔒")
                        fontSize(metrics.dp(12f))
                        marginLeft(metrics.dp(6f))
                    }
                }
            }
            View {
                attr {
                    backgroundColor(Color(StockChatTheme.COLOR_FFDDF5EC))
                    borderRadius(metrics.dp(5f))
                    padding(top = metrics.dp(2f), left = metrics.dp(5f), right = metrics.dp(5f), bottom = metrics.dp(2f))
                    marginLeft(metrics.dp(7f))
                }
                Text {
                    attr {
                        text(option.badge)
                        fontSize(metrics.dp(11f))
                        color(StockChatTheme.accent)
                    }
                }
            }
        }
    }
}
