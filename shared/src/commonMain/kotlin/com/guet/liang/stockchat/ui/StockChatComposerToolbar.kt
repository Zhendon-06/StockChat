package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// StockChatComposerToolbar：输入组件与交互的独立区块。

internal fun StockChatPage.ComposerToolbar(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val expanded = ctx.composerExpanded || ctx.voiceMode || ctx.selectedImageCount > 0
                absolutePosition(
                    left = metrics.dp(14f),
                    right = metrics.dp(14f),
                    // 折叠态：面板高 68dp、行高 42dp，bottom 取 (68 - 42) / 2 = 13
                    // 让按钮行在面板内垂直居中；展开态贴底部
                    bottom = metrics.dp(if (expanded) 8f else 13f),
                )
                height(metrics.dp(42f))
                flexDirectionRow()
                alignItemsCenter()
                zIndex(6)
                animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                animate(Animation.easeOut(0.2f), ctx.voiceMode)
            }
            View {
                attr {
                    size(metrics.dp(34f), metrics.dp(34f))
                    allCenter()
                }
                ctx.InputModeMark(this, metrics.scale) {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.toggleVoiceMode()
                    }
                }
            }
            ctx.ComposerModelButton(this)
            View { attr { flex(1f) } }
            View {
                attr {
                    size(metrics.dp(34f), metrics.dp(34f))
                    allCenter()
                }
                ctx.PlusMark(this, metrics.scale) {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.openImagePicker()
                    }
                }
            }
            ctx.ComposerSendButton(this)
        }
    }
}

internal fun StockChatPage.ComposerModelButton(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val showModel = ctx.composerExpanded || ctx.voiceMode || ctx.selectedImageCount > 0
                width(metrics.dp(132f))
                height(metrics.dp(34f))
                borderRadius(metrics.dp(17f))
                marginLeft(metrics.dp(10f))
                padding(left = metrics.dp(10f), right = metrics.dp(10f))
                backgroundColor(StockChatTheme.recessed)
                themedBorder()
                flexDirectionRow()
                alignItemsCenter()
                justifyContentCenter()
                opacity(if (showModel) 1f else 0f)
                touchEnable(showModel)
                animate(Animation.easeOut(0.2f), showModel)
            }
            event {
                click {
                    val showModelNow = ctx.composerExpanded || ctx.voiceMode || ctx.selectedImageCount > 0
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT && showModelNow) {
                        ctx.openModelMenu()
                    }
                }
            }
            View {
                attr {
                    size(metrics.dp(22f), metrics.dp(22f))
                    borderRadius(metrics.dp(6f))
                    backgroundColor(Color.WHITE)
                    marginRight(metrics.dp(6f))
                    allCenter()
                }
                Image {
                    attr {
                        size(metrics.dp(18f), metrics.dp(18f))
                        resizeContain()
                        src(ImageUri.commonAssets(ctx.composerModelIconAsset()))
                    }
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                    maxWidth(metrics.dp(84f))
                }
                Text {
                    attr {
                        text(ctx.composerModelDisplayName())
                        fontSize(metrics.dp(14f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        lines(1)
                    }
                }
            }
        }
    }
}

internal fun StockChatPage.ComposerSendButton(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val visible = !ctx.voiceMode && (ctx.composerExpanded || ctx.selectedImageCount > 0)
                width(if (visible) metrics.dp(42f) else 0f)
                height(metrics.dp(42f))
                marginLeft(if (visible) metrics.dp(12f) else 0f)
                opacity(if (visible) 1f else 0f)
                touchEnable(visible)
                animate(Animation.easeOut(0.22f), ctx.composerExpanded)
                animate(Animation.easeOut(0.22f), ctx.voiceMode)
                animate(Animation.easeOut(0.22f), ctx.selectedImageCount)
            }
            event {
                click {
                    // 可见性必须在点击时实时判断：构建期缓存的布尔值会永远
                    // 停留在初始 false，导致按钮显示出来了却点不了
                    val visibleNow = !ctx.voiceMode && (ctx.composerExpanded || ctx.selectedImageCount > 0)
                    if (visibleNow && ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.sendMessage()
                    }
                }
            }
            View {
                attr {
                    val canSend = !ctx.isSending && (ctx.inputText.isNotBlank() || ctx.selectedImageCount > 0)
                    size(metrics.dp(42f), metrics.dp(42f))
                    borderRadius(metrics.dp(21f))
                    backgroundColor(
                        if (!canSend) {
                            Color(StockChatTheme.COLOR_FFE4EAE7)
                        } else {
                            StockChatTheme.accent
                        }
                    )
                    allCenter()
                }
                Text {
                    attr {
                        text("↑")
                        fontSize(metrics.dp(23f))
                        fontWeightBold()
                        color(Color.WHITE)
                    }
                }
            }
        }
    }
}

internal fun StockChatPage.InputModeMark(container: ViewContainer<*, *>, scale: Float, onClick: () -> Unit) {
    val ctx = this
    with(container) {
        View {
            attr {
                size(27f * scale, 34f * scale)
                allCenter()
            }
            event { click { onClick() } }
            Image {
                attr {
                    size(32f * scale, 32f * scale)
                    resizeContain()
                    src(
                        ImageUri.commonAssets(
                            stockChatThemedAsset(lightAsset = "composer_voice_32.png", darkAsset = "composer_voice_32_white.png")
                        )
                    )
                    opacity(if (ctx.voiceMode) 0f else 1f)
                    animation(Animation.easeOut(0.18f), ctx.voiceMode)
                }
            }
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                }
                View {
                    attr {
                        size(25f * scale, 19f * scale)
                        borderRadius(4f * scale)
                        border(Border(2f * scale, BorderStyle.SOLID, StockChatTheme.textPrimary))
                        opacity(if (ctx.voiceMode) 1f else 0f)
                        animation(Animation.easeOut(0.18f), ctx.voiceMode)
                    }
                    repeat(3) { row ->
                        repeat(5) { column ->
                            View {
                                attr {
                                    absolutePosition(top = (3f + row * 4f) * scale, left = (3f + column * 4f) * scale)
                                    size(2f * scale, 2f * scale)
                                    borderRadius(1f * scale)
                                    backgroundColor(StockChatTheme.textPrimary)
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            absolutePosition(bottom = 2f * scale, left = 7f * scale)
                            size(11f * scale, 2f * scale)
                            borderRadius(1f * scale)
                            backgroundColor(StockChatTheme.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

internal fun StockChatPage.PlusMark(container: ViewContainer<*, *>, scale: Float, onClick: () -> Unit) {
    val ctx = this
    with(container) {
        View {
            attr {
                size(34f * scale, 34f * scale)
                allCenter()
            }
            event { click { onClick() } }
            Image {
                attr {
                    size(32f * scale, 32f * scale)
                    resizeContain()
                    src(
                        ImageUri.commonAssets(
                            stockChatThemedAsset(lightAsset = "composer_plus_32.png", darkAsset = "composer_plus_32_white.png")
                        )
                    )
                }
            }
        }
    }
}
