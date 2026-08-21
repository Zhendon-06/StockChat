package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
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

internal fun ViewContainer<*, *>.HamburgerButton(
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    View {
        attr {
            size(52f * scale, 52f * scale)
            borderRadius(26f * scale)
            backgroundColor(StockChatTheme.surface)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.borderStrong))
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

internal fun ViewContainer<*, *>.PromptChip(
    label: String,
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(42f * scale)
            borderRadius(21f * scale)
            padding(left = 15f * scale, right = 15f * scale)
            margin(right = 8f * scale, bottom = 0f)
            backgroundColor(StockChatTheme.surface)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
            flexDirectionRow()
            alignItemsCenter()
        }
        event {
            click { onClick() }
        }
        View {
            attr {
                size(7f * scale, 7f * scale)
                borderRadius(4f * scale)
                marginRight(8f * scale)
                backgroundColor(StockChatTheme.accent)
            }
        }
        Text {
            attr {
                text(label)
                fontSize(14f * scale)
                fontWeightMedium()
                color(StockChatTheme.textPrimary)
            }
        }
    }
}

internal fun ViewContainer<*, *>.ChatMessageItem(
    message: ChatMessage,
    scale: Float = 1f,
    onQuoteClick: (StockQuote) -> Unit,
    onRetry: (ChatMessage) -> Unit,
) {
    View {
        attr {
            padding(left = 18f * scale, right = 18f * scale)
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
                        padding(
                            top = 12f * scale,
                            left = 16f * scale,
                            bottom = 12f * scale,
                            right = 16f * scale,
                        )
                        borderRadius(20f * scale, 20f * scale, 20f * scale, 6f * scale)
                        backgroundColor(Color(0xFF1C2925))
                    }
                    Text {
                        attr {
                            text((message.blocks.firstOrNull() as? AnswerBlock.Markdown)?.fallbackText ?: "")
                            fontSize(15f * scale)
                            lineHeight(22f * scale)
                            color(Color.WHITE)
                        }
                    }
                }
            }
        } else {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsFlexStart()
                }
                View {
                    attr {
                        size(32f * scale, 32f * scale)
                        borderRadius(11f * scale)
                        backgroundColor(Color(0xFF17241F))
                        allCenter()
                        marginRight(10f * scale)
                    }
                    Text {
                        attr {
                            text("AI")
                            fontSize(11f * scale)
                            fontWeightBold()
                            color(Color(0xFF54DDB4))
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                    }
                    when (message.state) {
                        MessageState.GENERATING -> GeneratingMessage(scale)
                        MessageState.FAILED -> FailedMessage(message.errorMessage, scale) { onRetry(message) }
                        MessageState.DELIVERED -> {
                            message.blocks.forEach { block ->
                                when (block) {
                                    is AnswerBlock.Markdown -> MarkdownContent(block, scale)
                                    is AnswerBlock.MarketQuote -> MarketQuoteCard(block.quote, scale) {
                                        onQuoteClick(block.quote)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text("演示信息 · 仅供参考，不构成投资建议")
                                    fontSize(11f * scale)
                                    color(StockChatTheme.textTertiary)
                                    marginTop(10f * scale)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarkdownContent(block: AnswerBlock.Markdown, scale: Float) {
    View {
        attr {
            padding(
                top = 14f * scale,
                left = 15f * scale,
                bottom = 14f * scale,
                right = 15f * scale,
            )
            borderRadius(6f * scale, 18f * scale, 18f * scale, 18f * scale)
            backgroundColor(StockChatTheme.surface)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
            marginBottom(10f * scale)
        }
        if (block.source.isBlank()) {
            Text {
                attr {
                    text(block.fallbackText)
                    fontSize(15f * scale)
                    lineHeight(23f * scale)
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

private fun ViewContainer<*, *>.GeneratingMessage(scale: Float) {
    View {
        attr {
            height(54f * scale)
            borderRadius(6f * scale, 18f * scale, 18f * scale, 18f * scale)
            padding(left = 15f * scale, right = 15f * scale)
            backgroundColor(StockChatTheme.surface)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
            flexDirectionRow()
            alignItemsCenter()
        }
        View {
            attr {
                size(8f * scale, 8f * scale)
                borderRadius(4f * scale)
                backgroundColor(StockChatTheme.accent)
                marginRight(9f * scale)
            }
        }
        Text {
            attr {
                text("正在分析演示行情…")
                fontSize(14f * scale)
                color(StockChatTheme.textSecondary)
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
            fontSize(11f * scale)
            color(StockChatTheme.textTertiary)
            textAlignCenter()
        }
    }
}
