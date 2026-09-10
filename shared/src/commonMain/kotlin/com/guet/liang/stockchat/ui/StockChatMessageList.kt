package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.MessageState
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.timer.Timer
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.View

// 消息列表：滚动容器、贴底策略、回到底部按钮与生成中指示动画。

internal fun StockChatPage.MessageList(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        Scroller {
            ref { ctx.messageScrollerRef = it }
            attr {
                absolutePositionAllZero()
                showScrollerIndicator(false)
                bouncesEnable(true)
                // 顶部不留 padding，内容直接从顶栏下缘开始；
                // 底部留出渐隐区高度，滚到底时最后一条消息不会停在淡出区内
                padding(bottom = ctx.layoutMetrics.composerContentFadeHeight + 8f)
            }
            event {
                // 滚动容器会消费触摸，点击/拖动到不了根容器的空白点击处理，
                // 在这里单独接上：点消息区空白或开始拖动列表都视作离开输入
                click {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.handleBlankAreaTap()
                    }
                }
                dragBegin {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.handleBlankAreaTap()
                    }
                }
                scroll { params ->
                    ctx.messageListContentHeight = params.contentHeight
                    ctx.messageListViewHeight = params.viewHeight
                    val remaining = params.contentHeight - params.offsetY - params.viewHeight
                    ctx.messageListNearBottom = remaining <= 48f
                    if (!ctx.messageListNearBottom) {
                        ctx.stickMessageListToBottom = false
                    }
                }
                contentSizeChanged { _, contentHeight ->
                    ctx.messageListContentHeight = contentHeight
                    if (ctx.stickMessageListToBottom || ctx.messageListNearBottom) {
                        ctx.scrollMessageListToBottom(animated = true)
                    }
                }
            }
            vfor({ ctx.messages }) { message -> ctx.MessageRow(this, message) }
        }
    }
}

internal fun StockChatPage.ScrollToBottomButton(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePosition(
                    right = metrics.dp(18f),
                    bottom =
                        metrics.composerContentBottom(
                            metrics.composerBottomInset(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom),
                            ctx.composerExpanded,
                            ctx.voiceMode,
                            ctx.selectedImageCount > 0,
                            ctx.composerExtraInputLines(),
                        ) + metrics.dp(12f),
                )
                size(metrics.dp(44f), metrics.dp(44f))
                borderRadius(metrics.dp(22f))
                backgroundColor(StockChatTheme.surface)
                boxShadow(BoxShadow(metrics.dp(1f), metrics.dp(4f), metrics.dp(12f), Color(0x26000000)))
                allCenter()
                zIndex(7)
            }
            event {
                click {
                    ctx.stickMessageListToBottom = true
                    ctx.messageListNearBottom = true
                    ctx.scrollMessageListToBottom(animated = true)
                }
            }
            Image {
                attr {
                    size(metrics.dp(24f), metrics.dp(24f))
                    resizeContain()
                    src(ImageUri.commonAssets("down_to_bottom.png"))
                }
            }
        }
    }
}

internal fun StockChatPage.scrollMessageListToBottom(animated: Boolean) {
    if (!messageScrollerRefReady || messageListContentHeight <= 0f) {
        return
    }
    val targetOffset = maxOf(0f, messageListContentHeight - messageListViewHeight)
    messageScrollerRef.view?.setContentOffset(0f, targetOffset, animated)
}

/**
 * 按行身份同步消息列表：未变化的行保持挂载，流式回答只更新正文 observable。
 * 之前是 clear 后逐条 add，每个流式片段和每次 refresh（含回到前台）都会重建全部行，
 * 卡片点击落在被销毁的节点上、Scroller 内容清空后偏移也被重置。
 */
internal fun StockChatPage.syncMessageRows(next: List<ChatMessage>) {
    val live = ChatMessageRows.liveAnswer(next)
    // 先更新正文再同步行结构：新建的流式行首帧就能读到最新文本
    streamingAnswerMarkdown = live?.let(ChatMessageRows::markdownSource).orEmpty()
    streamingAnswerId = live?.id.orEmpty()
    messages.diffUpdate(next, ChatMessageRows::sameRow)
}

internal fun StockChatPage.resetMessageListScrollState() {
    messageListNearBottom = true
    stickMessageListToBottom = true
    messageListContentHeight = 0f
    messageListViewHeight = 0f
}

internal fun StockChatPage.updateTypingIndicatorTimer() {
    val waitingFirstToken = messages.any { it.state == MessageState.GENERATING && it.blocks.isEmpty() }
    if (waitingFirstToken && typingDotTimer == null) {
        typingDotPhase = 0
        typingDotTimer = Timer().also { timer -> timer.schedule(0, 320) { typingDotPhase = (typingDotPhase + 1) % 3 } }
    } else if (!waitingFirstToken && typingDotTimer != null) {
        typingDotTimer?.cancel()
        typingDotTimer = null
    }
}

private fun StockChatPage.MessageRow(container: ViewContainer<*, *>, message: ChatMessage) {
    val ctx = this
    with(container) {
        ChatMessageItem(
            message = message,
            scale = ctx.layoutMetrics.scale,
            pageWidth = ctx.pagerData.pageViewWidth,
            isFirst = message.id == ctx.messages.firstOrNull()?.id,
            typingPhase = { ctx.typingDotPhase },
            liveMarkdown =
                if (ChatMessageRows.isLiveRow(message)) {
                    {
                        if (ctx.streamingAnswerId == message.id) ctx.streamingAnswerMarkdown else ChatMessageRows.markdownSource(message)
                    }
                } else {
                    null
                },
            onQuoteClick = {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.openStockDetail(it, HOME_TAB_CHAT)
                }
            },
            onImageClick = {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.openImagePreview(it)
                }
            },
            onRetry = {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.retryMessage(it)
                }
            },
            onCopy = {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.copyMessage(it)
                }
            },
            onCopySelection = {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.copySelectedText(it)
                }
            },
            onRegenerate = {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.regenerateMessage(it)
                }
            },
            onReadAloud = {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.readMessageAloud(it)
                }
            },
            readAloudPhase = { if (ctx.readAloudMessageId == message.id) ctx.readAloudWavePhase else -1 },
            onMore = {
                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                    ctx.openMessageMenu(it.id)
                }
            },
        )
    }
}
