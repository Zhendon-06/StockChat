package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.RichText
import com.tencent.kuikly.core.views.Span
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
private enum class MessageActionIcon {
    COPY,
    REGENERATE,
    READ_ALOUD,
    MORE,
}

internal fun ViewContainer<*, *>.ChatMessageItem(
    message: ChatMessage,
    scale: Float = 1f,
    isFirst: Boolean = false,
    // 读取跳点动画相位的函数：在 attr 内调用才能建立对页面 observable 的依赖
    typingPhase: () -> Int = { 0 },
    // 朗读声纹动画相位：>=0 表示该消息正在生成/播放语音，<0 表示空闲
    readAloudPhase: () -> Int = { -1 },
    onQuoteClick: (StockQuote) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onCopy: (ChatMessage) -> Unit = {},
    onCopySelection: (String) -> Unit = {},
    onRegenerate: (ChatMessage) -> Unit = {},
    onReadAloud: (ChatMessage) -> Unit = {},
    onMore: (ChatMessage) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    /** Page width the row is laid out in; 0 means unknown and disables width-aware table layout. */
    pageWidth: Float = 0f,
    /** Live markdown of an in-flight answer; null renders the snapshot text captured at row creation. */
    liveMarkdown: (() -> String)? = null,
) {
    val contentWidth = (pageWidth - MESSAGE_HORIZONTAL_PADDING * scale * 2).coerceAtLeast(0f)
    View {
        attr {
            padding(left = MESSAGE_HORIZONTAL_PADDING * scale, right = MESSAGE_HORIZONTAL_PADDING * scale)
            if (isFirst) marginTop(12f * scale)
            marginBottom(18f * scale)
        }
        if (message.role == ChatRole.USER) {
            UserMessageContent(message, scale, onImageClick) { onCopy(message) }
        } else {
            View {
                attr {
                    flex(1f)
                }
                when (message.state) {
                    // 等待首 token 时显示三点跳动动画；流式内容到达后直接渲染已有块
                    MessageState.GENERATING -> if (message.blocks.isEmpty()) {
                        TypingIndicator(scale, typingPhase)
                    } else {
                        AssistantBlocks(message, scale, contentWidth, onQuoteClick, onImageClick, onCopySelection, liveMarkdown)
                    }
                    MessageState.FAILED -> FailedMessage(message.errorMessage, scale) { onRetry(message) }
                    MessageState.DELIVERED -> {
                        AssistantBlocks(message, scale, contentWidth, onQuoteClick, onImageClick, onCopySelection)
                        Text {
                            attr {
                                text("StockChat Demo · 内容仅供参考，投资相关内容不构成投资建议")
                                fontSize(11f * scale)
                                color(StockChatTheme.textTertiary)
                                marginTop(8f * scale)
                            }
                        }
                        MessageActionRow(
                            scale = scale,
                            readAloudPhase = readAloudPhase,
                            onCopy = { onCopy(message) },
                            onRegenerate = { onRegenerate(message) },
                            onReadAloud = { onReadAloud(message) },
                            onMore = { onMore(message) },
                        )
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AssistantBlocks(
    message: ChatMessage,
    scale: Float,
    contentWidth: Float,
    onQuoteClick: (StockQuote) -> Unit,
    onImageClick: (String) -> Unit,
    onCopySelection: (String) -> Unit,
    liveMarkdown: (() -> String)? = null,
) {
    var liveBound = false
    message.blocks.forEach { block ->
        when (block) {
            is AnswerBlock.Markdown -> if (liveMarkdown != null && !liveBound) {
                liveBound = true
                // 流式正文单独 vbind：每个片段只重建正文子树，同行的行情卡片保持挂载，
                // 用户按下的卡片节点在抬起前不会被销毁
                vbind({ liveMarkdown() }) {
                    val text = liveMarkdown()
                    MarkdownContent(
                        block = AnswerBlock.Markdown(text, text),
                        scale = scale,
                        selectionEnabled = false,
                        onCopySelection = onCopySelection,
                        availableWidth = contentWidth,
                    )
                }
            } else {
                MarkdownContent(
                    block = block,
                    scale = scale,
                    selectionEnabled = message.state == MessageState.DELIVERED,
                    onCopySelection = onCopySelection,
                    availableWidth = contentWidth,
                )
            }
            is AnswerBlock.MarketQuote -> MarketQuoteCard(block.quote, scale) {
                onQuoteClick(block.quote)
            }
            is AnswerBlock.ImageGallery -> MessageImageGallery(block.images, scale, onImageClick)
        }
    }
}

private fun ViewContainer<*, *>.MarkdownContent(
    block: AnswerBlock.Markdown,
    scale: Float,
    selectionEnabled: Boolean,
    onCopySelection: (String) -> Unit,
    availableWidth: Float,
) {
    SelectableMarkdownContent(
        source = block.source,
        fallbackText = block.fallbackText,
        scale = scale,
        selectionEnabled = selectionEnabled,
        onCopySelection = onCopySelection,
        availableWidth = availableWidth,
    )
}

private const val MESSAGE_HORIZONTAL_PADDING = 18f

private fun ViewContainer<*, *>.MessageActionRow(
    scale: Float,
    readAloudPhase: () -> Int = { -1 },
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onReadAloud: () -> Unit,
    onMore: () -> Unit,
) {
    View {
        attr {
            height(38f * scale)
            flexDirectionRow()
            alignItemsCenter()
            marginTop(2f * scale)
        }
        MessageActionButton(MessageActionIcon.COPY, scale, onCopy)
        MessageActionButton(MessageActionIcon.REGENERATE, scale, onRegenerate)
        MessageActionButton(MessageActionIcon.READ_ALOUD, scale, onReadAloud, readAloudPhase)
        MessageActionButton(MessageActionIcon.MORE, scale, onMore)
    }
}

private fun ViewContainer<*, *>.MessageActionButton(
    icon: MessageActionIcon,
    scale: Float,
    onClick: () -> Unit,
    wavePhase: (() -> Int)? = null,
) {
    View {
        attr {
            width(48f * scale)
            height(38f * scale)
            allCenter()
        }
        event {
            click { onClick() }
        }
        if (wavePhase == null) {
            MessageActionMark(icon, scale)
        } else {
            // 静态图标与声纹共存，用 visibility 切换：结构只建一次，状态变化走 attr 重渲染
            MessageActionMark(icon, scale, hidden = { wavePhase() >= 0 })
            ReadAloudWaveMark(scale, visible = { wavePhase() >= 0 }, phase = wavePhase)
        }
    }
}

private fun ViewContainer<*, *>.MessageActionMark(
    icon: MessageActionIcon,
    scale: Float,
    hidden: () -> Boolean = { false },
) {
    val asset = when (icon) {
        MessageActionIcon.COPY -> stockChatThemedAsset("copy_all.png", "copy_all_white.png")
        MessageActionIcon.REGENERATE -> stockChatThemedAsset("reuptransport.png", "reuptransport_white.png")
        MessageActionIcon.READ_ALOUD -> stockChatThemedAsset("tts_voice.png", "tts_voice_white.png")
        MessageActionIcon.MORE -> stockChatThemedAsset("menu.png", "menu_white.png")
    }
    Image {
        attr {
            size(22f * scale, 22f * scale)
            resizeContain()
            src(ImageUri.commonAssets(asset))
            visibility(!hidden())
        }
    }
}

// 朗读进行时的声音按钮声纹：4 根竖条以错相正弦流动，节奏与语音输入声纹一致
private fun ViewContainer<*, *>.ReadAloudWaveMark(
    scale: Float,
    visible: () -> Boolean,
    phase: () -> Int,
) {
    View {
        attr {
            absolutePositionAllZero()
            flexDirectionRow()
            alignItemsCenter()
            justifyContentCenter()
            visibility(visible())
        }
        repeat(4) { barIndex ->
            View {
                attr {
                    val p = phase().toFloat()
                    val primary = kotlin.math.abs(
                        kotlin.math.sin((barIndex * 1.05f + p * 0.55f).toDouble())
                    ).toFloat()
                    val secondary = kotlin.math.abs(
                        kotlin.math.sin((barIndex * 0.47f - p * 0.36f).toDouble())
                    ).toFloat()
                    width(3f * scale)
                    height((4f + primary * 11f + secondary * 4f) * scale)
                    borderRadius(1.5f * scale)
                    margin(left = 1.5f * scale, right = 1.5f * scale)
                    backgroundColor(StockChatTheme.accent)
                    animation(Animation.easeInOut(0.12f), phase())
                }
            }
        }
    }
}

// 等待首 token 的三点跳动指示：相位轮到的点加深并上跳一下
private fun ViewContainer<*, *>.TypingIndicator(scale: Float, phase: () -> Int) {
    View {
        attr {
            height(34f * scale)
            flexDirectionRow()
            alignItemsCenter()
            padding(left = 4f * scale)
        }
        repeat(3) { index ->
            View {
                attr {
                    val active = phase() % 3 == index
                    size(9f * scale, 9f * scale)
                    borderRadius(4.5f * scale)
                    marginRight(10f * scale)
                    backgroundColor(if (active) StockChatTheme.typingActive else StockChatTheme.typingInactive)
                    transform(
                        Translate(
                            percentageX = 0f,
                            percentageY = 0f,
                            offsetY = if (active) -3f * scale else 0f,
                        )
                    )
                    animation(Animation.easeInOut(0.24f), phase())
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.FailedMessage(
    message: String,
    scale: Float,
    onRetry: () -> Unit,
) {
    View {
        attr {
            padding(
                top = 13f * scale,
                left = 15f * scale,
                bottom = 13f * scale,
                right = 15f * scale,
            )
            borderRadius(6f * scale, 18f * scale, 18f * scale, 18f * scale)
            backgroundColor(StockChatTheme.warningSoft)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.warningBorder))
        }
        Text {
            attr {
                text(message)
                fontSize(14f * scale)
                lineHeight(21f * scale)
                color(StockChatTheme.warning)
            }
        }
        View {
            attr {
                alignSelfFlexStart()
                height(34f * scale)
                borderRadius(17f * scale)
                padding(left = 14f * scale, right = 14f * scale)
                marginTop(10f * scale)
                backgroundColor(StockChatTheme.surface)
                border(Border(1f, BorderStyle.SOLID, StockChatTheme.warningBorder))
                allCenter()
            }
            event {
                click { onRetry() }
            }
            Text {
                attr {
                    text("重新生成")
                    fontSize(13f * scale)
                    fontWeightMedium()
                    color(StockChatTheme.warning)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.UserMessageContent(
    message: ChatMessage,
    scale: Float,
    onImageClick: (String) -> Unit,
    /** Long-pressing a text bubble copies the whole sent message. */
    onLongPress: () -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            justifyContentFlexEnd()
        }
        View {
            attr {
                maxWidth(290f * scale)
            }
            message.blocks.forEach { block ->
                when (block) {
                    is AnswerBlock.Markdown -> UserMarkdownBubble(block, scale, onLongPress)
                    is AnswerBlock.ImageGallery -> MessageImageGallery(block.images, scale, onImageClick)
                    is AnswerBlock.MarketQuote -> Unit
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.UserMarkdownBubble(
    block: AnswerBlock.Markdown,
    scale: Float,
    onLongPress: () -> Unit,
) {
    View {
                            attr {
                                padding(
                                    top = 12f * scale,
                                    left = 16f * scale,
                                    bottom = 12f * scale,
                                    right = 16f * scale,
                                )
                                borderRadius(22f * scale)
                                backgroundColor(StockChatTheme.userBubble)
                                marginBottom(8f * scale)
                            }
                            event {
                                // 长按只在手势开始时触发一次，移动/结束阶段不重复复制
                                longPress { params -> if (params.state == "start") onLongPress() }
                            }
                            RichText {
                                attr {
                                    val fontSize = StockChatTheme.chatTextSizeSp * scale
                                    // 气泡最大宽 290 - 左右内边距 16*2 = 258
                                    val maxTextWidth = 258f * scale
                                    // 超宽时给定确定宽度触发折行；短文本保持内容撑宽
                                    if (estimateWrappedLineCount(
                                            block.fallbackText,
                                            fontSize,
                                            maxTextWidth * 0.96f,
                                        ) > 1
                                    ) {
                                        width(maxTextWidth)
                                    }
                                }
                                Span {
                                    text(block.fallbackText)
                                    fontSize(StockChatTheme.chatTextSizeSp * scale)
                                    lineHeight(StockChatTheme.chatTextSizeSp * 1.45f * scale)
                                    color(StockChatTheme.chatTextColor)
                                }
                            }
                        }
}
