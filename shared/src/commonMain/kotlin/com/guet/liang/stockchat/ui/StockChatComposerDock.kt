package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// StockChatComposerDock：输入组件与交互的独立区块。

internal fun StockChatPage.ComposerDock(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val active = ctx.selectedHomeTab == HOME_TAB_CHAT
                val effectiveInset = metrics.composerBottomInset(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom)
                absolutePosition(
                    left = metrics.dp(18f),
                    right = metrics.dp(18f),
                    // nudge 项：键盘关闭后强制归位用的 0.1px 无感偏移
                    bottom = effectiveInset + metrics.composerBottomGap + (ctx.composerDockNudge % 2) * 0.1f,
                )
                height(
                    metrics.composerDockHeight(
                        focused = ctx.composerExpanded,
                        voiceMode = ctx.voiceMode,
                        hasAttachments = ctx.selectedImageCount > 0,
                        extraInputLines = ctx.composerExtraInputLines(),
                    )
                )
                opacity(if (active) 1f else 0f)
                transform(Translate(0f, 0f, 0f, if (active) 0f else metrics.dp(30f)))
                touchEnable(active)
                zIndex(if (active) 6 else 0)
                // 展开态与键盘态解耦：键盘回落期间展开态不变，回落动画不会被
                // 无动画的几何更新打断；收缩只发生在键盘静止时，两条动画不并发
                animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                animate(
                    if (active) {
                        Animation.easeIn(0.16f)
                    } else {
                        Animation.springEaseOut(0.36f, 0.9f, 0.18f).delay(0.2f)
                    },
                    ctx.selectedHomeTab,
                )
            }
            event {
                // 吃掉输入区内未被子视图消费的点击，避免冒泡到根容器被当成
                // 空白区域点击而收缩面板
                click {}
            }
            ctx.ComposerPanel(this)
            ctx.ComposerFooter(this)
        }
    }
}

internal fun StockChatPage.ComposerPanel(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePosition(left = 0f, right = 0f, bottom = metrics.composerFooterHeight)
                height(
                    metrics.composerPanelHeight(
                        focused = ctx.composerExpanded,
                        voiceMode = ctx.voiceMode,
                        hasAttachments = ctx.selectedImageCount > 0,
                        extraInputLines = ctx.composerExtraInputLines(),
                    )
                )
                borderRadius(
                    metrics.dp(
                        if (ctx.composerExpanded || ctx.voiceMode || ctx.selectedImageCount > 0) {
                            24f
                        } else {
                            30f
                        }
                    )
                )
                backgroundColor(StockChatTheme.surface)
                // 恢复 Android 原有输入面板阴影，其他平台继续使用无阴影样式。
                if (pagerData.isAndroid) {
                    boxShadow(BoxShadow(metrics.dp(1f), metrics.dp(5f), metrics.dp(14f), Color(0x1A000000)))
                }
                animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                animate(Animation.easeOut(0.2f), ctx.voiceMode)
                animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
                animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
            }
            ctx.ComposerAttachmentStrip(this)
            ctx.ComposerTextInput(this)
            ctx.ComposerVoiceModeHint(this)
            ctx.ComposerHoldToTalkLayer(this)
            ctx.ComposerToolbar(this)
        }
    }
}

internal fun StockChatPage.ComposerAttachmentStrip(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        vif({ ctx.selectedImageCount > 0 }) {
            View {
                attr {
                    absolutePosition(top = metrics.dp(9f), left = metrics.dp(14f), right = metrics.dp(14f))
                    height(metrics.dp(70f))
                    zIndex(5)
                }
                ComposerImageAttachments(
                    images = { ctx.selectedImages },
                    scale = metrics.scale,
                    onRemove = {
                        if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                            ctx.removeSelectedImage(it)
                        }
                    },
                    onPreview = {
                        if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                            ctx.openImagePreview(it)
                        }
                    },
                )
            }
        }
    }
}

internal fun StockChatPage.ComposerFooter(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePosition(left = 0f, right = 0f, bottom = 0f)
                height(metrics.composerFooterHeight)
                justifyContentCenter()
                alignItemsCenter()
                opacity(if (ctx.composerExpanded) 0f else 1f)
                animation(Animation.easeOut(0.14f), ctx.composerExpanded)
            }
            Text {
                attr {
                    text("")
                    fontSize(metrics.dp(11f))
                    color(StockChatTheme.textTertiary)
                }
            }
        }
    }
}
