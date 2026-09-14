package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSectorObservation
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场采用非对称 Bento 构图：主卡、辅卡和底板各司其职，内容之间不互相遮挡。


internal fun ViewContainer<*, *>.MarketWatchBento(
    quotes: List<StockQuote>,
    contentWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
    focusIndex: Int,
    pulse: Boolean,
    onFocusAdvance: () -> Unit,
) {
    if (quotes.isEmpty()) return
    val focusedQuotes = quotes.focusedFrom(focusIndex)
    TodayMarketSectionTitle(this, "观察雷达", "强弱样本", scale, onFocusAdvance)
    val featureWidth = contentWidth * 0.61f
    val sideWidth = contentWidth - featureWidth - 9f * scale
    val stageHeight = if (focusedQuotes.size > 2) 310f * scale else 196f * scale
    View {
        attr {
            width(contentWidth)
            height(stageHeight)
            positionRelative()
            opacity(if (pulse) 0.94f else 1f)
            animate(Animation.easeOut(0.15f), pulse)
        }
        View {
            attr {
                absolutePosition(top = 8f * scale, left = 9f * scale, right = 0f)
                height(185f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        WatchFeatureCard(
            container = this,
            quote = focusedQuotes.first(),
            width = featureWidth,
            height = 183f * scale,
            scale = scale,
        ) {
            onQuoteClick(focusedQuotes.first())
        }
        focusedQuotes.getOrNull(1)?.let { quote ->
            WatchSideCard(this, quote, sideWidth, 11f * scale, 125f * scale, scale) {
                onQuoteClick(quote)
            }
        }
        focusedQuotes.getOrNull(2)?.let { quote ->
            WatchStripCard(this, quote, contentWidth * 0.49f, 193f * scale, 3f * scale, scale, 3) {
                onQuoteClick(quote)
            }
        }
        focusedQuotes.getOrNull(3)?.let { quote ->
            WatchStripCard(
                this,
                quote,
                contentWidth * 0.63f,
                249f * scale,
                contentWidth * 0.24f,
                scale,
                4,
            ) {
                onQuoteClick(quote)
            }
        }
    }
    if (focusedQuotes.size > 4) {
        WatchTailList(focusedQuotes.drop(4), contentWidth, scale, onQuoteClick)
    }
}

internal fun <T> List<T>.focusedFrom(focusIndex: Int): List<T> {
    if (size < 2) return this
    val start = ((focusIndex % size) + size) % size
    if (start == 0) return this
    return drop(start) + take(start)
}

private fun WatchFeatureCard(
    container: ViewContainer<*, *>,
    quote: StockQuote,
    width: Float,
    height: Float,
    scale: Float,
    onClick: () -> Unit,
) {
    with(container) {
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(width)
                height(height)
                padding(
                    top = 15f * scale,
                    left = 15f * scale,
                    bottom = 11f * scale,
                    right = 15f * scale,
                )
                borderRadius(22f * scale)
                backgroundLinearGradient(
                    Direction.TO_BOTTOM_RIGHT,
                    ColorStop(StockChatTheme.surface, 0f),
                    ColorStop(StockChatTheme.surfaceSoft, 1f),
                )
                themedBorder()
                boxShadow(BoxShadow(0f, 6f * scale, 16f * scale, Color(0x1A000000)))
                zIndex(2)
            }
            event { click { onClick() } }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text("样本领涨")
                        fontSize(10f * scale)
                        color(StockChatTheme.textTertiary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text("详情  ›")
                        fontSize(10f * scale)
                        color(StockChatTheme.accent)
                    }
                }
            }
            Text {
                attr {
                    text(quote.name)
                    fontSize(20f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(11f * scale)
                    lines(1)
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsFlexEnd()
                    marginTop(3f * scale)
                }
                Text {
                    attr {
                        text(quote.price)
                        fontSize(27f * scale)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        flex(1f)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text(quote.changePercent)
                        fontSize(13f * scale)
                        fontWeightBold()
                        color(marketQuoteColor(quote))
                        marginBottom(3f * scale)
                    }
                }
            }
            TrendSparkline(
                quote = quote,
                width = (width - 30f * scale).coerceAtLeast(1f),
                height = 46f * scale,
            )
            Text {
                attr {
                    text(quote.aiInsight.ifBlank { quote.summary }.ifBlank { "样本行情仅作观察参考。" })
                    fontSize(10f * scale)
                    lineHeight(14f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(3f * scale)
                    lines(1)
                }
            }
        }
    }
}

private fun WatchSideCard(
    container: ViewContainer<*, *>,
    quote: StockQuote,
    width: Float,
    top: Float,
    height: Float,
    scale: Float,
    onClick: () -> Unit,
) {
    with(container) {
        View {
            attr {
                absolutePosition(top = top, right = 0f)
                width(width)
                height(height)
                padding(top = 13f * scale, left = 12f * scale, bottom = 11f * scale, right = 11f * scale)
                borderRadius(19f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                boxShadow(BoxShadow(0f, 4f * scale, 11f * scale, Color(0x14000000)))
                zIndex(2)
            }
            event { click { onClick() } }
            Text {
                attr {
                    text("同时观察")
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                }
            }
            Text {
                attr {
                    text(quote.name)
                    fontSize(15f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(8f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(quote.changePercent)
                    fontSize(20f * scale)
                    fontWeightBold()
                    color(marketQuoteColor(quote))
                    marginTop(11f * scale)
                }
            }
            Text {
                attr {
                    text(quote.price)
                    fontSize(13f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(3f * scale)
                }
            }
        }
    }
}

private fun WatchStripCard(
    container: ViewContainer<*, *>,
    quote: StockQuote,
    width: Float,
    top: Float,
    left: Float,
    scale: Float,
    zIndex: Int,
    onClick: () -> Unit,
) {
    with(container) {
        View {
            attr {
                absolutePosition(top = top, left = left)
                width(width)
                height(50f * scale)
                padding(left = 12f * scale, right = 11f * scale)
                borderRadius(16f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                flexDirectionRow()
                alignItemsCenter()
                boxShadow(BoxShadow(0f, 3f * scale, 9f * scale, Color(0x12000000)))
                zIndex(zIndex)
            }
            event { click { onClick() } }
            Text {
                attr {
                    text(quote.name)
                    fontSize(12f * scale)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                    flex(1f)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(quote.price)
                    fontSize(12f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginRight(9f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(quote.changePercent)
                    fontSize(11f * scale)
                    fontWeightBold()
                    color(marketQuoteColor(quote))
                    lines(1)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.WatchTailList(
    quotes: List<StockQuote>,
    contentWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
) {
    View {
        attr {
            width(contentWidth)
            marginTop(8f * scale)
            padding(left = 13f * scale, right = 13f * scale)
            borderRadius(18f * scale)
            backgroundColor(StockChatTheme.surfaceSoft)
            themedBorder()
        }
        quotes.forEachIndexed { index, quote ->
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    height(47f * scale)
                }
                event { click { onQuoteClick(quote) } }
                Text {
                    attr {
                        text(quote.name)
                        fontSize(13f * scale)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                        flex(1f)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text(quote.price)
                        fontSize(12f * scale)
                        color(StockChatTheme.textPrimary)
                        marginRight(10f * scale)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text(quote.changePercent)
                        fontSize(11f * scale)
                        fontWeightBold()
                        color(marketQuoteColor(quote))
                        lines(1)
                    }
                }
            }
            if (index != quotes.lastIndex) {
                View {
                    attr {
                        height(1f)
                        backgroundColor(StockChatTheme.border)
                    }
                }
            }
        }
    }
}
