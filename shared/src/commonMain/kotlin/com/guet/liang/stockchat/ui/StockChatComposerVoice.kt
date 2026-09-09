package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.VoiceInputState
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// StockChatComposerVoice：输入组件与交互的独立区块。

internal fun StockChatPage.ComposerVoiceModeHint(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        vif({ ctx.voiceMode }) {
            Text {
                attr {
                    val attachmentOffset =
                        if (ctx.selectedImageCount > 0) {
                            metrics.composerAttachmentStripHeight
                        } else {
                            0f
                        }
                    absolutePosition(top = attachmentOffset + metrics.dp(22f), left = metrics.dp(56f), right = metrics.dp(56f))
                    height(metrics.dp(32f))
                    text(ctx.voiceModePrompt())
                    textAlignCenter()
                    fontSize(metrics.dp(18f))
                    fontWeightMedium()
                    color(
                        if (ctx.voicePressCanceled) {
                            StockChatTheme.warning
                        } else {
                            StockChatTheme.textPrimary
                        }
                    )
                    zIndex(4)
                    animate(Animation.easeOut(0.2f), ctx.voiceMode)
                    animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
                    animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
                }
            }
        }
    }
}

internal fun StockChatPage.ComposerHoldToTalkLayer(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        // 折叠态用于点击聚焦/长按说话；语音模式下即使保留草稿、面板处于
        // 展开态，也需要这层接收按住说话手势。bounds 不依赖 inputLineCount，
        // 避免文字输入继续增高时覆盖原生输入框的选区和内部滚动。
        vif({ !ctx.composerExpanded || ctx.voiceMode }) {
            View {
                attr {
                    val hasAttachments = ctx.selectedImageCount > 0
                    val attachmentOffset = if (hasAttachments) metrics.composerAttachmentStripHeight else 0f
                    absolutePosition(
                        top = attachmentOffset,
                        left = metrics.dp(51f),
                        right = metrics.dp(51f),
                        bottom = if (ctx.voiceMode || hasAttachments) metrics.dp(52f) else 0f,
                    )
                    // 非语音展开态提前让开，避免覆盖原生输入框的点按选区和内部滚动；
                    // 语音模式则保留整块输入区域用于按住说话
                    touchEnable(
                        ctx.selectedHomeTab == HOME_TAB_CHAT &&
                            (!ctx.composerExpanded || ctx.voiceMode) &&
                            (!ctx.composerFocused || ctx.voiceMode)
                    )
                    zIndex(3)
                    // 常驻捕获长按：是否真正进入按住说话在事件回调里实时判断，
                    // 避免 attr 依赖 composerFocused/inputText 等未注册动画的键
                    // 触发重跑打断本节点在飞动画
                    capture(CaptureRule.longPress())
                    animate(Animation.easeOut(0.2f), ctx.voiceMode)
                    animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
                }
                ctx.bindHoldToTalk(this)
            }
        }
    }
}

private fun StockChatPage.bindHoldToTalk(view: com.tencent.kuikly.core.views.DivView) {
    val ctx = this
    with(view) {
        event {
            click {
                // 长按松手后可能补发 click，语音流程未回到 IDLE 时不聚焦
                if (!ctx.voiceMode && !ctx.voicePressActive && ctx.voiceInputState == VoiceInputState.IDLE) {
                    ctx.focusComposer()
                }
            }
            longPress { params ->
                if (ctx.voiceMode || ctx.voicePressActive || ctx.composerHoldToTalkReady()) {
                    ctx.handleVoiceLongPress(params)
                }
            }
            touchCancel {
                if (ctx.voiceMode || ctx.voicePressActive) {
                    ctx.cancelVoicePress()
                }
            }
            touchUp {
                if (ctx.voiceMode || ctx.voicePressActive) {
                    ctx.finishVoicePress()
                } else if (!ctx.composerExpanded && ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    // 鸿蒙侧长按捕获会吞掉 click；抬手时仍确保折叠输入框可以聚焦。
                    ctx.focusComposer()
                }
            }
        }
    }
}
