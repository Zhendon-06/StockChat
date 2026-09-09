package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BridgeModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.maxTextLengthLegacy
import com.guet.liang.stockchat.base.replaceNativeText
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.model.VoiceInputState
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

// 底部输入面板：输入框、展开/收缩、键盘跟随、图片选择与行数估算。

internal const val MAX_COMPOSER_TEXT_LENGTH = 300

internal const val MAX_INPUT_LINES = 5

internal fun StockChatPage.ComposerDock(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
    View {
        attr {
            val active = ctx.selectedHomeTab == HOME_TAB_CHAT
            val effectiveInset = metrics.composerBottomInset(
                ctx.keyboardHeight,
                pagerData.safeAreaInsets.bottom,
            )
            absolutePosition(
                left = metrics.dp(18f),
                right = metrics.dp(18f),
                // nudge 项：键盘关闭后强制归位用的 0.1px 无感偏移
                bottom = effectiveInset + metrics.composerBottomGap +
                    (ctx.composerDockNudge % 2) * 0.1f,
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
            transform(
                Translate(
                    0f,
                    0f,
                    0f,
                    if (active) 0f else metrics.dp(30f),
                )
            )
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
            click { }
        }
        View {
            attr {
                absolutePosition(
                    left = 0f,
                    right = 0f,
                    bottom = metrics.composerFooterHeight,
                )
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
                    boxShadow(
                        BoxShadow(
                            metrics.dp(1f),
                            metrics.dp(5f),
                            metrics.dp(14f),
                            Color(0x1A000000),
                        )
                    )
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
        ctx.ComposerFooter(this)
    }
    }
}


// 已选图片条：面板顶部的横向缩略图列表
internal fun StockChatPage.ComposerAttachmentStrip(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        vif({ ctx.selectedImageCount > 0 }) {
            View {
                attr {
                    absolutePosition(
                        top = metrics.dp(9f),
                        left = metrics.dp(14f),
                        right = metrics.dp(14f),
                    )
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

// 原生多行输入框：文本、占位、聚焦/键盘/回车事件
internal fun StockChatPage.ComposerTextInput(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        TextArea {
                ref {
                    ctx.inputRef = it
                }
                attr {
                    val hasAttachments = ctx.selectedImageCount > 0
                    val expanded = ctx.composerExpanded || ctx.voiceMode || hasAttachments
                    val attachmentOffset = if (hasAttachments) {
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
                    val visibleLines = if (expanded && !ctx.voiceMode) {
                        ctx.inputLineCount
                    } else {
                        1
                    }
                    height(metrics.dp(15f) + metrics.composerInputLineHeight * visibleLines)
                    text(ctx.inputText)
                    fontSize(metrics.dp(17f))
                    lineHeight(metrics.dp(23f))
                    color(
                        if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.textPrimary
                    )
                    tintColor(
                        if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.accent
                    )
                    placeholder(
                        if (ctx.composerExpanded) {
                            "问行情、炒股知识或其他问题…"
                        } else {
                            ctx.voiceInputHint()
                        }
                    )
                    placeholderColor(
                        if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.textTertiary
                    )
                    returnKeyTypeSend()
                    enablesReturnKeyAutomatically(true)
                    maxTextLengthLegacy(MAX_COMPOSER_TEXT_LENGTH)
                    // 展开未聚焦时也可点：直接点输入区域原生聚焦拉起键盘
                    touchEnable(
                        ctx.selectedHomeTab == HOME_TAB_CHAT &&
                            ctx.composerExpanded && !ctx.voiceMode
                    )
                    zIndex(2)
                    animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                    animate(Animation.easeOut(0.2f), ctx.voiceMode)
                    animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
                    animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
                }
                event {
                    textDidChange(isSyncEdit = true) {
                        ctx.inputText = it.text
                        ctx.updateInputLineMetrics(it.text)
                    }
                    inputFocus {
                        ctx.inputText = it.text
                        if (ctx.selectedHomeTab != HOME_TAB_CHAT) {
                            ctx.composerFocused = false
                            setTimeout(0) {
                                if (
                                    ctx.selectedHomeTab != HOME_TAB_CHAT &&
                                    ctx.inputRefReady
                                ) {
                                    ctx.inputRef.view?.blur()
                                }
                            }
                            return@inputFocus
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
                            return@inputFocus
                        }
                        ctx.composerFocused = true
                        ctx.composerExpanded = true
                        ctx.collapseComposerAfterSettle = false
                        ctx.updateInputLineMetrics(it.text)
                    }
                    inputBlur {
                        ctx.inputText = it.text
                        ctx.resetKeyboardState()
                    }
                    keyboardHeightChange {
                        // 安卓上 duration 常回调为 0，直接沿用会导致面板瞬移没有过渡；
                        // 无有效时长时用 0.25s 兜底，有则归一并限制在合理区间
                        ctx.keyboardAnimDuration = when {
                            it.duration <= 0.01f -> DEFAULT_KEYBOARD_ANIM_DURATION
                            it.duration > 3f -> it.duration / 1000f
                            else -> it.duration
                        }.coerceIn(0.15f, 0.35f)
                        val nextKeyboardHeight = maxOf(it.height, 0f)
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
                    inputReturn {
                        if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                            ctx.sendMessage(it.text)
                        }
                    }
                }
        }
    }
}

// 语音模式下替代输入框显示的提示文案
internal fun StockChatPage.ComposerVoiceModeHint(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        vif({ ctx.voiceMode }) {
            Text {
                attr {
                    val attachmentOffset = if (ctx.selectedImageCount > 0) {
                        metrics.composerAttachmentStripHeight
                    } else {
                        0f
                    }
                    absolutePosition(
                        top = attachmentOffset + metrics.dp(22f),
                        left = metrics.dp(56f),
                        right = metrics.dp(56f),
                    )
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

// 折叠态点击聚焦 / 语音模式按住说话的手势层
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
            event {
                click {
                    // 长按松手后可能补发 click，语音流程未回到 IDLE 时不聚焦
                    if (
                        !ctx.voiceMode &&
                        !ctx.voicePressActive &&
                        ctx.voiceInputState == VoiceInputState.IDLE
                    ) {
                        ctx.focusComposer()
                    }
                }
                longPress { params ->
                    if (
                        ctx.voiceMode ||
                        ctx.voicePressActive ||
                        ctx.composerHoldToTalkReady()
                    ) {
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
    }
}

// 面板底部工具行：输入模式切换、模型选择、加号与发送按钮
internal fun StockChatPage.ComposerToolbar(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val expanded = ctx.composerExpanded ||
                    ctx.voiceMode ||
                    ctx.selectedImageCount > 0
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
            View {
                attr {
                    val showModel = ctx.composerExpanded ||
                        ctx.voiceMode ||
                        ctx.selectedImageCount > 0
                    width(metrics.dp(132f))
                    height(metrics.dp(34f))
                    borderRadius(metrics.dp(17f))
                    marginLeft(metrics.dp(10f))
                    padding(left = metrics.dp(10f), right = metrics.dp(10f))
                    backgroundColor(StockChatTheme.recessed)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                    opacity(if (showModel) 1f else 0f)
                    touchEnable(showModel)
                    animate(Animation.easeOut(0.2f), showModel)
                }
                event {
                    click {
                        val showModelNow = ctx.composerExpanded ||
                            ctx.voiceMode ||
                            ctx.selectedImageCount > 0
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
            View {
                attr {
                    flex(1f)
                }
            }
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
                        val visibleNow = !ctx.voiceMode &&
                            (ctx.composerExpanded || ctx.selectedImageCount > 0)
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
                                Color(0xFFE4EAE7)
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
}

// 输入区底部安全区留白
internal fun StockChatPage.ComposerFooter(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePosition(
                    left = 0f,
                    right = 0f,
                    bottom = 0f,
                )
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
internal fun StockChatPage.InputModeMark(
    container: ViewContainer<*, *>,
    scale: Float,
    onClick: () -> Unit,
) {
    val ctx = this
    with(container) {
    View {
        attr {
            size(27f * scale, 34f * scale)
            allCenter()
        }
        event {
            click { onClick() }
        }
        Image {
            attr {
                size(32f * scale, 32f * scale)
                resizeContain()
                src(
                    ImageUri.commonAssets(
                        stockChatThemedAsset(
                            lightAsset = "composer_voice_32.png",
                            darkAsset = "composer_voice_32_white.png",
                        )
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
                                absolutePosition(
                                    top = (3f + row * 4f) * scale,
                                    left = (3f + column * 4f) * scale,
                                )
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

internal fun StockChatPage.PlusMark(
    container: ViewContainer<*, *>,
    scale: Float,
    onClick: () -> Unit,
) {
    val ctx = this
    with(container) {
    View {
        attr {
            size(34f * scale, 34f * scale)
            allCenter()
        }
        event {
            click { onClick() }
        }
        Image {
            attr {
                size(32f * scale, 32f * scale)
                resizeContain()
                src(
                    ImageUri.commonAssets(
                        stockChatThemedAsset(
                            lightAsset = "composer_plus_32.png",
                            darkAsset = "composer_plus_32_white.png",
                        )
                    )
                )
            }
        }
    }
    }
}

internal fun StockChatPage.focusComposer() {
    if (selectedHomeTab != HOME_TAB_CHAT) {
        return
    }
    composerFocused = true
    composerExpanded = true
    collapseComposerAfterSettle = false
    voiceMode = false
    conversationMenuOpen = false
    closeDrawer()
    focusTextInputAfterLayout()
}

// 点击/滑动输入框以外的区域：用户焦点离开输入，面板收缩还给页面空间。
// 键盘在场时先收键盘，收缩挂起到回落动画播完再播（两个动画在同一批节点上
// 并发会互相打断）；键盘静止时立即收缩
internal fun StockChatPage.handleBlankAreaTap() {
    if (voiceMode || voicePressActive) {
        return
    }
    val keyboardWasUp = keyboardVisible || keyboardHeight > 0f
    if (composerFocused || keyboardWasUp) {
        if (inputRefReady) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
    }
    // 输入框有内容时保持展开：收缩态按单行布局排版，多行文本会挤乱
    if (inputText.isNotEmpty()) {
        return
    }
    if (keyboardWasUp || keyboardDropSettling) {
        // 回落窗口结束时由 beginComposerDockSettle 的定时器执行收缩
        collapseComposerAfterSettle = true
    } else {
        collapseComposer()
    }
}

internal fun StockChatPage.resetKeyboardState() {
    val keyboardWasUp = keyboardHeight > 0f
    keyboardHeight = 0f
    keyboardVisible = false
    if (keyboardWasUp) {
        beginComposerDockSettle()
    }
    composerFocused = false
}

// 键盘开始落下时调用：标记回落窗口（keyboardDropSettling）。窗口内主页
// 内容不回挂（避免挂载大子树拖慢回落）、空白点击不触发面板收缩（避免
// 收缩动画打断同节点的回落动画），并调度 nudge 兜底归位
internal fun StockChatPage.beginComposerDockSettle() {
    keyboardDropSettling = true
    val generation = ++dockSettleGeneration
    setTimeout(((keyboardAnimDuration + 0.03f) * 1000).toInt()) {
        if (generation == dockSettleGeneration) {
            keyboardDropSettling = false
            // 回落期间挂起的空白交互收缩，此刻键盘动画已结束，可安全播放
            if (collapseComposerAfterSettle) {
                collapseComposerAfterSettle = false
                collapseComposer()
            }
        }
    }
    scheduleComposerDockResync()
}

// 键盘落底动画应结束的时刻，再强制同步一次跟随键盘的容器位置：
// composerDockNudge 自增会让 bottom 产生 0.1px 的无感变化，以一次无动画的
// 属性更新把原生侧位置拉回正确值——兜底修复回落动画被打断后面板悬停的问题
internal fun StockChatPage.scheduleComposerDockResync() {
    setTimeout(((keyboardAnimDuration + 0.12f) * 1000).toInt()) {
        if (keyboardHeight <= 0f && !keyboardVisible) {
            composerDockNudge += 1
        }
    }
}

internal fun StockChatPage.focusTextInputAfterLayout() {
    updateInputLineMetrics(inputText)
    setTimeout(0) {
        if (composerFocused && !voiceMode && inputRefReady) {
            inputRef.view?.replaceNativeText(inputText)
            inputRef.view?.focus()
        }
    }
}

internal fun StockChatPage.collapseComposer() {
    composerExpanded = false
    val generation = ++composerInputLayoutResyncGeneration
    setTimeout(260) {
        if (
            generation == composerInputLayoutResyncGeneration &&
            !composerExpanded &&
            !voiceMode &&
            selectedImageCount == 0
        ) {
            composerInputLayoutNudge += 1
        }
    }
}

// 输入面板处于展开非语音态时，超出单行的部分行数，用于撑高输入框和面板
internal fun StockChatPage.composerExtraInputLines(): Int {
    return if (composerExpanded && !voiceMode) {
        (inputLineCount - 1).coerceAtLeast(0)
    } else {
        0
    }
}

internal fun StockChatPage.updateInputLineMetrics(text: String) {
    val metrics = layoutMetrics
    // 展开态输入框可用宽 = 页宽 - 面板左右外边距 18*2 - 输入框左右内边距 20*2
    val availableWidth = (pagerData.pageViewWidth - metrics.dp(76f)) * 0.97f
    inputLineCount = estimateWrappedLineCount(
        text,
        metrics.dp(17f),
        availableWidth,
    ).coerceIn(1, MAX_INPUT_LINES)
}

internal fun StockChatPage.resetInputLineMetrics() {
    inputLineCount = 1
}

internal fun StockChatPage.openImagePicker() {
    if (imagePickerOpen) {
        bridgeModule.toast("图片选择器已打开")
        return
    }
    val remainingCount = BridgeModule.MAX_IMAGE_SELECTION_COUNT - selectedImageCount
    if (remainingCount <= 0) {
        bridgeModule.toast("最多选择 9 张图片")
        return
    }
    imagePickerOpen = true
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    resetKeyboardState()
    bridgeModule.pickImages(remainingCount) pickerResult@{ result ->
        resetKeyboardState()
        imagePickerOpen = false
        if (result == null) {
            bridgeModule.toast("图片选择暂时不可用")
            return@pickerResult
        }
        if (result.optInt("cancelled", 0) == 1) {
            return@pickerResult
        }
        if (result.optInt("success", 0) != 1) {
            bridgeModule.toast(
                result.optString("errorMessage").ifBlank { "图片选择失败，请稍后重试" }
            )
            return@pickerResult
        }
        val imageArray = result.optJSONArray("images")
        val previewImageArray = result.optJSONArray("previewImages")
        val newImagePreviews = mutableListOf<String>()
        val newImagePayloads = mutableListOf<String>()
        if (imageArray != null) {
            for (index in 0 until imageArray.length()) {
                if (selectedImagePreviews.size + newImagePreviews.size >= BridgeModule.MAX_IMAGE_SELECTION_COUNT) {
                    break
                }
                val imagePayload = imageArray.optString(index).orEmpty().trim()
                val imagePreview = previewImageArray
                    ?.optString(index)
                    .orEmpty()
                    .trim()
                    .ifBlank { imagePayload }
                if (
                    imagePayload.isNotEmpty() &&
                    imagePreview.isNotEmpty() &&
                    imagePreview !in selectedImagePreviews &&
                    imagePreview !in newImagePreviews
                ) {
                    newImagePreviews.add(imagePreview)
                    newImagePayloads.add(imagePayload)
                }
            }
        }
        selectedImagePreviews.addAll(newImagePreviews)
        selectedImagePayloads.addAll(newImagePayloads)
        selectedImages.addAll(newImagePreviews)
        selectedImageCount = selectedImagePreviews.size
        if (newImagePreviews.isEmpty()) {
            bridgeModule.toast("没有选择新的图片")
        } else {
            if (result.optInt("truncated", 0) == 1) {
                bridgeModule.toast("已保留前 9 张图片")
            }
        }
    }
}

internal fun StockChatPage.removeSelectedImage(imageUri: String) {
    val imageIndex = selectedImagePreviews.indexOf(imageUri)
    if (imageIndex < 0) {
        return
    }
    selectedImagePreviews.removeAt(imageIndex)
    selectedImages.removeAt(imageIndex)
    if (imageIndex < selectedImagePayloads.size) {
        selectedImagePayloads.removeAt(imageIndex)
    }
    selectedImageCount = selectedImagePreviews.size
}
