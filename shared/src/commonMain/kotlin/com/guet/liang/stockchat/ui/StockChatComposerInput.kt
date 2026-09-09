package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.maxTextLengthLegacy
import com.guet.liang.stockchat.base.setTimeout
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.TextArea

// StockChatComposerInput：输入组件与交互的独立区块。

internal fun StockChatPage.ComposerTextInput(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        TextArea {
            ref { ctx.inputRef = it }
            attr { configureComposerInput(ctx) }
            ctx.bindComposerInput(this)
        }
    }
}

internal fun StockChatPage.bindComposerInput(view: com.tencent.kuikly.core.views.TextAreaView) {
    val ctx = this
    with(view) {
        event {
            textDidChange(isSyncEdit = true) {
                ctx.inputText = it.text
                ctx.updateInputLineMetrics(it.text)
            }
            inputFocus { ctx.handleComposerFocus(it.text) }
            inputBlur {
                ctx.inputText = it.text
                ctx.resetKeyboardState()
            }
            keyboardHeightChange { ctx.handleComposerKeyboard(it.height, it.duration) }
            inputReturn {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.sendMessage(it.text)
                }
            }
        }
    }
}

internal fun com.tencent.kuikly.core.views.TextAreaAttr.configureComposerInput(ctx: StockChatPage) {
    val metrics = ctx.layoutMetrics

    configureComposerGeometry(ctx)
    text(ctx.inputText)
    fontSize(metrics.dp(17f))
    lineHeight(metrics.dp(23f))
    color(if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.textPrimary)
    tintColor(if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.accent)
    placeholder(
        if (ctx.composerExpanded) {
            "问行情、炒股知识或其他问题…"
        } else {
            ctx.voiceInputHint()
        }
    )
    placeholderColor(if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.textTertiary)
    returnKeyTypeSend()
    enablesReturnKeyAutomatically(true)
    maxTextLengthLegacy(MAX_COMPOSER_TEXT_LENGTH)
    // 展开未聚焦时也可点：直接点输入区域原生聚焦拉起键盘
    touchEnable(ctx.selectedHomeTab == HOME_TAB_CHAT && ctx.composerExpanded && !ctx.voiceMode)
    zIndex(2)
    animate(Animation.easeOut(0.2f), ctx.composerExpanded)
    animate(Animation.easeOut(0.2f), ctx.voiceMode)
    animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
    animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
}

private fun StockChatPage.handleComposerFocus(text: String) {
    val ctx = this

    ctx.inputText = text
    if (ctx.selectedHomeTab != HOME_TAB_CHAT) {
        ctx.composerFocused = false
        setTimeout(0) {
            if (ctx.selectedHomeTab != HOME_TAB_CHAT && ctx.inputRefReady) {
                ctx.inputRef.view?.blur()
            }
        }
        return
    }
    ctx.closeDrawer()
    if (ctx.voiceMode) {
        // 语音模式下输入框不应持有焦点（如长按触发录音前后的异常聚焦回调），
        // 立即交还焦点，避免焦点态与语音态叠加导致按钮行显示错乱
        setTimeout(0) {
            if (ctx.voiceMode && ctx.inputRefReady) {
                ctx.inputRef.view?.blur()
            }
        }
        return
    }
    ctx.composerFocused = true
    ctx.composerExpanded = true
    ctx.collapseComposerAfterSettle = false
    ctx.updateInputLineMetrics(text)
}

private fun StockChatPage.handleComposerKeyboard(height: Float, duration: Float) {
    val ctx = this

    // 安卓上 duration 常回调为 0，直接沿用会导致面板瞬移没有过渡；
    // 无有效时长时用 0.25s 兜底，有则归一并限制在合理区间
    ctx.keyboardAnimDuration =
        when {
            duration <= 0.01f -> DEFAULT_KEYBOARD_ANIM_DURATION
            duration > 3f -> duration / 1000f
            else -> duration
        }.coerceIn(0.15f, 0.35f)
    val nextKeyboardHeight = maxOf(height, 0f)
    val nextKeyboardVisible = nextKeyboardHeight > 0.5f
    val keyboardWasVisible = ctx.keyboardHeight > 0f
    if (!ctx.composerFocused || ctx.voiceMode || ctx.imagePickerOpen) {
        ctx.resetKeyboardState()
    } else if (nextKeyboardVisible) {
        ctx.keyboardVisible = true
        ctx.keyboardHeight = nextKeyboardHeight
    } else {
        ctx.keyboardHeight = 0f
        ctx.keyboardVisible = false
        if (keyboardWasVisible) {
            // 键盘回落只退出聚焦态，面板保持展开（composerExpanded
            // 不变），点空白区域才收缩，见 handleBlankAreaTap
            ctx.beginComposerDockSettle()
            ctx.composerFocused = false
            if (ctx.inputRefReady) {
                ctx.inputRef.view?.blur()
            }
        }
    }
}

private fun com.tencent.kuikly.core.views.TextAreaAttr.configureComposerGeometry(ctx: StockChatPage) {
    val metrics = ctx.layoutMetrics
    val hasAttachments = ctx.selectedImageCount > 0
    val expanded = ctx.composerExpanded || ctx.voiceMode || hasAttachments
    val attachmentOffset =
        if (hasAttachments) {
            metrics.composerAttachmentStripHeight
        } else {
            0f
        }
    val inputLayoutNudge = (ctx.composerInputLayoutNudge % 2) * 0.01f
    absolutePosition(
        // 折叠态：面板高 68dp，按钮行居中后按钮中心在 34dp（即面板中心）；
        // 文字行高 23dp 且从 TextArea 顶部绘制，top 取 (68 - 23) / 2 = 22.5，
        // 让占位文字与加号、语音按钮一起垂直居中
        top = attachmentOffset + if (expanded) metrics.dp(14f) else metrics.dp(22.5f),
        left = metrics.dp(if (expanded) 20f else 61f) + inputLayoutNudge,
        right = metrics.dp(if (expanded) 20f else 60f) + inputLayoutNudge,
    )
    // 单行高 38dp（23 行高 + 15 上下留白）；超过一行后按行数撑高，面板同步增高，
    // 到 MAX_INPUT_LINES 后框高封顶——超出的内容交由原生多行输入框自身的
    // 内部滚动查看（不额外包 Scroller：那会与原生输入框自带的拖拽滚动争抢
    // 触摸，导致滑动时好时坏）
    val visibleLines =
        if (expanded && !ctx.voiceMode) {
            ctx.inputLineCount
        } else {
            1
        }
    height(metrics.dp(15f) + metrics.composerInputLineHeight * visibleLines)
}
