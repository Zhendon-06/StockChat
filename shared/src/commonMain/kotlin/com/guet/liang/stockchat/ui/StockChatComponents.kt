package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.RichText
import com.tencent.kuikly.core.views.Span
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuiklybase.config.MarkdownConfig

internal object StockChatTheme {
    val background = Color(0xFFF6F7F4)
    val surface = Color.WHITE
    val surfaceSoft = Color(0xFFFBFCFB)
    val recessed = Color(0xFFECEFED)
    val textPrimary = Color(0xFF171A18)
    val textSecondary = Color(0xFF6F7672)
    val textTertiary = Color(0xFFA1A7A3)
    val accent = Color(0xFF13A87A)
    val accentSoft = Color(0xFFE8F7F1)
    val border = Color(0xFFE4E8E5)
    val borderStrong = Color(0xFFD9DFDC)
    val positive = Color(0xFFD84A43)
    val negative = Color(0xFF168765)
    val warningSoft = Color(0xFFFFF5E6)
    val warning = Color(0xFF9B6A18)
}

private enum class MessageActionIcon {
    COPY,
    REGENERATE,
    READ_ALOUD,
    MORE,
}

/**
 * 估算文本在给定可用宽度内折行后的行数。
 * Kuikly 的 Text/TextArea 只有在自身宽度确定时才会折行，
 * 对于内容撑宽（shrink-to-fit）的场景需要先估算是否超宽，再决定是否给定宽度。
 */
internal fun estimateWrappedLineCount(text: String, fontSize: Float, availableWidth: Float): Int {
    if (text.isEmpty() || availableWidth <= 0f) {
        return 1
    }
    var lines = 1
    var lineWidth = 0f
    for (ch in text) {
        if (ch == '\n') {
            lines += 1
            lineWidth = 0f
            continue
        }
        // CJK/全角字符约等于字号宽，拉丁字符按经验比例估算
        val charWidth = if (ch.code > 0x2E7F) fontSize else fontSize * 0.56f
        if (lineWidth > 0f && lineWidth + charWidth > availableWidth) {
            lines += 1
            lineWidth = charWidth
        } else {
            lineWidth += charWidth
        }
    }
    return lines
}

internal fun ViewContainer<*, *>.HamburgerButton(
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    View {
        attr {
            size(52f * scale, 52f * scale)
            borderRadius(26f * scale)
            backgroundColor(StockChatTheme.surface)
            boxShadow(
                BoxShadow(
                    1f * scale,
                    5f * scale,
                    14f * scale,
                    Color(0x1A000000),
                )
            )
            allCenter()
        }
        event {
            click { onClick() }
        }
        View {
            attr {
                size(22f * scale, 14f * scale)
            }
            View {
                attr {
                    absolutePosition(top = 2f * scale, left = 0f)
                    size(22f * scale, 2.5f * scale)
                    borderRadius(2f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
            View {
                attr {
                    absolutePosition(bottom = 2f * scale, left = 0f)
                    size(15f * scale, 2.5f * scale)
                    borderRadius(2f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.AssistantBadge(size: Float = 102f) {
    View {
        attr {
            size(size, size)
            allCenter()
        }
        View {
            attr {
                size(size * 0.76f, size * 0.76f)
                borderRadius(size * 0.25f)
                backgroundColor(Color(0xFF15231F))
                allCenter()
            }
            View {
                attr {
                    absolutePosition(top = size * 0.15f, left = size * 0.18f)
                    size(size * 0.08f, size * 0.19f)
                    borderRadius(size * 0.04f)
                    backgroundColor(Color(0xFF42E4B3))
                }
            }
            View {
                attr {
                    absolutePosition(top = size * 0.15f, right = size * 0.18f)
                    size(size * 0.08f, size * 0.19f)
                    borderRadius(size * 0.04f)
                    backgroundColor(Color(0xFF42E4B3))
                }
            }
            Text {
                attr {
                    text("AI")
                    fontSize(size * 0.22f)
                    fontWeightBold()
                    color(Color.WHITE)
                    marginTop(size * 0.25f)
                }
            }
        }
        View {
            attr {
                absolutePosition(bottom = 2f, right = 2f)
                size(size * 0.30f, size * 0.30f)
                borderRadius(size * 0.15f)
                backgroundColor(StockChatTheme.accent)
                border(Border(3f, BorderStyle.SOLID, StockChatTheme.background))
                allCenter()
            }
            Text {
                attr {
                    text("↗")
                    fontSize(size * 0.17f)
                    fontWeightBold()
                    color(Color.WHITE)
                }
            }
        }
    }
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
    onRegenerate: (ChatMessage) -> Unit = {},
    onReadAloud: (ChatMessage) -> Unit = {},
    onMore: (ChatMessage) -> Unit = {},
    onImageClick: (String) -> Unit = {},
) {
    View {
        attr {
            padding(left = 18f * scale, right = 18f * scale)
            if (isFirst) marginTop(12f * scale)
            marginBottom(18f * scale)
        }
        if (message.role == ChatRole.USER) {
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
                            is AnswerBlock.Markdown -> View {
                                attr {
                                    padding(
                                        top = 12f * scale,
                                        left = 16f * scale,
                                        bottom = 12f * scale,
                                        right = 16f * scale,
                                    )
                                    borderRadius(22f * scale)
                                    backgroundColor(Color(0xFFE2E4E5))
                                    marginBottom(8f * scale)
                                }
                                RichText {
                                    attr {
                                        val fontSize = 17f * scale
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
                                        fontSize(17f * scale)
                                        lineHeight(24f * scale)
                                        color(StockChatTheme.textPrimary)
                                    }
                                }
                            }
                            is AnswerBlock.ImageGallery -> MessageImageGallery(block.images, scale, onImageClick)
                            is AnswerBlock.MarketQuote -> Unit
                        }
                    }
                }
            }
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
                        AssistantBlocks(message, scale, onQuoteClick, onImageClick)
                    }
                    MessageState.FAILED -> FailedMessage(message.errorMessage, scale) { onRetry(message) }
                    MessageState.DELIVERED -> {
                        AssistantBlocks(message, scale, onQuoteClick, onImageClick)
                        Text {
                            attr {
                                text("仅供参考，不构成投资建议")
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
    onQuoteClick: (StockQuote) -> Unit,
    onImageClick: (String) -> Unit,
) {
    message.blocks.forEach { block ->
        when (block) {
            is AnswerBlock.Markdown -> MarkdownContent(block, scale)
            is AnswerBlock.MarketQuote -> MarketQuoteCard(block.quote, scale) {
                onQuoteClick(block.quote)
            }
            is AnswerBlock.ImageGallery -> MessageImageGallery(block.images, scale, onImageClick)
        }
    }
}

private fun ViewContainer<*, *>.MarkdownContent(block: AnswerBlock.Markdown, scale: Float) {
    View {
        attr {
            marginBottom(9f * scale)
        }
        if (block.source.isBlank()) {
            RichText {
                Span {
                    text(block.fallbackText)
                    fontSize(17f * scale)
                    lineHeight(25f * scale)
                    color(StockChatTheme.textPrimary)
                }
            }
        } else {
            KuiklyMarkdown(
                content = block.source,
                config = MarkdownConfig.Default,
            )
        }
    }
}

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
        MessageActionIcon.COPY -> "copy_all.png"
        MessageActionIcon.REGENERATE -> "reuptransport.png"
        MessageActionIcon.READ_ALOUD -> "tts_voice.png"
        MessageActionIcon.MORE -> "menu.png"
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
                    backgroundColor(if (active) Color(0xFF4A4F4C) else Color(0xFFB5BAB6))
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
            border(Border(1f, BorderStyle.SOLID, Color(0xFFF2D9AE)))
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
                border(Border(1f, BorderStyle.SOLID, Color(0xFFE8CAA0)))
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

internal fun ViewContainer<*, *>.MarketQuoteCard(
    quote: StockQuote,
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    View {
        attr {
            padding(
                top = 15f * scale,
                left = 15f * scale,
                bottom = 14f * scale,
                right = 15f * scale,
            )
            borderRadius(18f * scale)
            backgroundColor(StockChatTheme.surface)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
        }
        event {
            click { onClick() }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    flex(1f)
                }
                Text {
                    attr {
                        text(quote.name)
                        fontSize(17f * scale)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("${quote.marketLabel} · ${quote.symbol}")
                        fontSize(12f * scale)
                        color(StockChatTheme.textSecondary)
                        marginTop(3f * scale)
                    }
                }
            }
            Text {
                attr {
                    text("查看详情  ›")
                    fontSize(12f * scale)
                    fontWeightMedium()
                    color(StockChatTheme.accent)
                }
            }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsFlexEnd()
                marginTop(14f * scale)
            }
            View {
                attr {
                    flex(1f)
                }
                Text {
                    attr {
                        text(quote.price)
                        fontSize(25f * scale)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("${quote.change}  ${quote.changePercent}")
                        fontSize(13f * scale)
                        fontWeightMedium()
                        color(if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative)
                        marginTop(4f * scale)
                    }
                }
            }
            TrendSparkline(quote, 102f * scale, 48f * scale)
        }
        Text {
            attr {
                text(quote.updatedAt)
                fontSize(11f * scale)
                color(StockChatTheme.textTertiary)
                marginTop(10f * scale)
            }
        }
    }
}

internal fun ViewContainer<*, *>.TrendSparkline(quote: StockQuote, width: Float, height: Float) {
    Canvas({
        attr {
            size(width, height)
        }
    }) { context, canvasWidth, canvasHeight ->
        val points = quote.trendPoints
        if (points.size > 1) {
            val min = points.minOrNull() ?: 0f
            val max = points.maxOrNull() ?: 1f
            val range = (max - min).takeIf { it > 0f } ?: 1f
            context.beginPath()
            points.forEachIndexed { index, value ->
                val x = index.toFloat() / (points.size - 1).toFloat() * canvasWidth
                val normalized = (value - min) / range
                val y = 4f + (1f - normalized) * (canvasHeight - 8f)
                if (index == 0) {
                    context.moveTo(x, y)
                } else {
                    context.lineTo(x, y)
                }
            }
            context.lineWidth(2.5f)
            context.lineCapRound()
            context.strokeStyle(if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative)
            context.stroke()
        }
    }
}

internal fun ViewContainer<*, *>.RiskNotice(scale: Float = 1f) {
    Text {
        attr {
            text("内容由 AI 生成")
            fontSize(11f * scale)
            color(StockChatTheme.textTertiary)
            textAlignCenter()
        }
    }
}
