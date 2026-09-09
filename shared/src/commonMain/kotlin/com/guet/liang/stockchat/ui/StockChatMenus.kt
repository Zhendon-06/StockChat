package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.ChatMessage
import com.tencent.kuikly.core.base.Anchor
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 弹出菜单：消息操作菜单与会话「更多」菜单。

// 消息「更多」底部弹出菜单：暗色遮罩 + 白色圆角面板，点击遮罩或取消关闭。
// 常驻挂载（不用 vif）以支持开合过渡动画：遮罩淡入淡出、面板自底部滑入滑出
internal fun StockChatPage.MessageMenuOverlay(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val open = ctx.messageMenuTargetId.isNotEmpty()
                absolutePositionAllZero()
                backgroundColor(Color(0x8C141A18))
                opacity(if (open) 1f else 0f)
                touchEnable(open)
                zIndex(11)
                animation(Animation.easeOut(0.24f), ctx.messageMenuTargetId)
            }
            event { click { ctx.messageMenuTargetId = "" } }
        }
        View {
            attr {
                val open = ctx.messageMenuTargetId.isNotEmpty()
                absolutePosition(left = 0f, right = 0f, bottom = 0f)
                borderRadius(metrics.dp(22f), metrics.dp(22f), 0f, 0f)
                backgroundColor(StockChatTheme.surface)
                padding(bottom = pagerData.safeAreaInsets.bottom + metrics.dp(6f))
                // 关闭态整体下移自身高度藏到屏幕外，开合切换即形成滑入滑出
                transform(Translate(0f, if (open) 0f else 1f))
                touchEnable(open)
                zIndex(12)
                animation(Animation.easeOut(0.28f), ctx.messageMenuTargetId)
            }
            ctx.MessageMenuItem(this, "复制内容", divider = false) { target -> ctx.copyMessage(target) }
            ctx.MessageMenuItem(this, "重新生成") { target -> ctx.regenerateMessage(target) }
            ctx.MessageMenuItem(this, "朗读") { target -> ctx.readMessageAloud(target) }
            ctx.MessageMenuItem(this, "分享") { target -> ctx.shareMessage(target) }
            ctx.MessageMenuItem(this, "删除", labelColor = StockChatTheme.positive) { target -> ctx.deleteMessage(target) }
            View {
                attr {
                    height(metrics.dp(8f))
                    backgroundColor(Color(StockChatTheme.COLOR_FFF2F3F1))
                }
            }
            View {
                attr {
                    height(metrics.dp(58f))
                    allCenter()
                }
                event { click { ctx.messageMenuTargetId = "" } }
                Text {
                    attr {
                        text("取消")
                        fontSize(metrics.dp(18f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
        }
    }
}

internal fun StockChatPage.MessageMenuItem(
    container: ViewContainer<*, *>,
    label: String,
    labelColor: Color = StockChatTheme.textPrimary,
    divider: Boolean = true,
    onClick: (ChatMessage) -> Unit,
) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        if (divider) {
            View {
                attr {
                    height(0.7f)
                    backgroundColor(StockChatTheme.border)
                }
            }
        }
        View {
            attr {
                height(metrics.dp(58f))
                allCenter()
            }
            event {
                click {
                    val target = ctx.messages.firstOrNull { it.id == ctx.messageMenuTargetId }
                    ctx.messageMenuTargetId = ""
                    if (target != null) {
                        onClick(target)
                    }
                }
            }
            Text {
                attr {
                    text(label)
                    fontSize(metrics.dp(18f))
                    color(labelColor)
                }
            }
        }
    }
}

internal fun StockChatPage.ConversationMenuOverlay(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0x00000000))
                touchEnable(ctx.conversationMenuOpen)
                zIndex(15)
            }
            event { click { ctx.closeConversationMenu() } }
        }
        View {
            attr {
                absolutePosition(top = pagerData.statusBarHeight + metrics.dp(76f), right = metrics.dp(18f))
                width(metrics.dp(220f))
                borderRadius(metrics.dp(22f))
                backgroundColor(StockChatTheme.surface)
                padding(all = metrics.dp(8f))
                boxShadow(BoxShadow(metrics.dp(1f), metrics.dp(8f), metrics.dp(24f), Color(0x26000000)))
                val open = ctx.conversationMenuOpen
                val menuScale = if (open) 1f else 0.92f
                opacity(if (open) 1f else 0f)
                transform(
                    scale = Scale(menuScale, menuScale),
                    translate = Translate(0f, 0f, 0f, if (open) 0f else -metrics.dp(6f)),
                    anchor = Anchor(1f, 0f),
                )
                touchEnable(open)
                animation(if (open) Animation.easeOut(0.22f) else Animation.easeIn(0.16f), ctx.conversationMenuOpen)
                zIndex(16)
            }
            ctx.ConversationAction(this, "table_icon.png", "会话表格对比", "汇总会话全部股票", { StockChatTheme.accentSoft }) {
                ctx.createConversationStockComparison()
            }
            View {
                attr {
                    height(metrics.dp(1f))
                    backgroundColor(StockChatTheme.border)
                    margin(left = metrics.dp(16f), right = metrics.dp(16f))
                }
            }
            ctx.ConversationAction(this, "ranking_icon.png", "思维导图", "梳理当前对话", { Color(StockChatTheme.COLOR_FFEAF2FF) }) {
                ctx.createConversationMindMapArtifact()
            }
        }
    }
}

internal fun StockChatPage.openConversationMenu() {
    cancelVoiceInput()
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    resetKeyboardState()
    messageMenuTargetId = ""
    modelMenuOpen = false
    closeDrawer()
    conversationMenuOpen = true
}

internal fun StockChatPage.closeConversationMenu() {
    conversationMenuOpen = false
}

internal fun StockChatPage.openMessageMenu(messageId: String) {
    conversationMenuOpen = false
    modelMenuOpen = false
    messageMenuTargetId = messageId
}

private fun StockChatPage.ConversationAction(
    container: ViewContainer<*, *>,
    icon: String,
    title: String,
    subtitle: String,
    background: () -> Color,
    onClick: () -> Unit,
) {
    val ctx = this
    val metrics = layoutMetrics
    with(container) {
        View {
            attr {
                height(metrics.dp(64f))
                borderRadius(metrics.dp(16f))
                flexDirectionRow()
                alignItemsCenter()
                padding(left = metrics.dp(16f), right = metrics.dp(12f))
                touchEnable(ctx.conversationMenuOpen)
            }
            event {
                click {
                    if (ctx.conversationMenuOpen) {
                        onClick()
                    }
                }
            }
            View {
                attr {
                    size(metrics.dp(42f), metrics.dp(42f))
                    borderRadius(metrics.dp(13f))
                    backgroundColor(background())
                    allCenter()
                }
                Image {
                    attr {
                        size(metrics.dp(23f), metrics.dp(23f))
                        resizeContain()
                        src(ImageUri.commonAssets(icon))
                    }
                }
            }
            View {
                attr {
                    flex(1f)
                    marginLeft(metrics.dp(12f))
                }
                Text {
                    attr {
                        text(title)
                        fontSize(metrics.dp(17f))
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(subtitle)
                        fontSize(metrics.dp(11f))
                        color(StockChatTheme.textTertiary)
                        marginTop(metrics.dp(2f))
                    }
                }
            }
            Text {
                attr {
                    text("›")
                    fontSize(metrics.dp(24f))
                    color(StockChatTheme.textTertiary)
                }
            }
        }
    }
}
