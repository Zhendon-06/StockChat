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


internal fun ViewContainer<*, *>.MarketIndexBento(
    indices: List<StockQuote>,
    contentWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
    focusIndex: Int,
    pulse: Boolean,
    onFocusAdvance: () -> Unit,
) {
    if (indices.isEmpty()) return
    val focusedIndices = indices.focusedFrom(focusIndex)
    TodayMarketSectionTitle(this, "指数焦点", "基准与分化", scale, onFocusAdvance)
    val gap = 9f * scale
    val leadWidth = contentWidth * 0.61f
    val sideWidth = contentWidth - leadWidth - gap
    val ribbonTop = 194f * scale
    View {
        attr {
            width(contentWidth)
            height(248f * scale)
            positionRelative()
            opacity(if (pulse) 0.84f else 1f)
            transform(Translate(0f, 0f, 0f, if (pulse) 4f * scale else 0f))
            animate(Animation.easeInOut(0.24f), pulse)
        }
        View {
            attr {
                absolutePosition(top = 7f * scale, left = 9f * scale, right = 0f)
                height(193f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        IndexFeatureCard(
            container = this,
            quote = focusedIndices.first(),
            width = leadWidth,
            height = 200f * scale,
            scale = scale,
            onClick = { onQuoteClick(focusedIndices.first()) },
        )
        focusedIndices.getOrNull(1)?.let { quote ->
            IndexMiniCard(this, quote, sideWidth, 2f * scale, 88f * scale, scale) {
                onQuoteClick(quote)
            }
        }
        focusedIndices.getOrNull(2)?.let { quote ->
            IndexMiniCard(this, quote, sideWidth, 101f * scale, 88f * scale, scale) {
                onQuoteClick(quote)
            }
        }
        focusedIndices.getOrNull(3)?.let { quote ->
            IndexRibbonCard(
                container = this,
                quote = quote,
                width = contentWidth * 0.74f,
                top = ribbonTop,
                left = contentWidth * 0.13f,
                scale = scale,
            ) {
                onQuoteClick(quote)
            }
        }
    }
}

private fun IndexFeatureCard(
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
                    left = 16f * scale,
                    bottom = 11f * scale,
                    right = 16f * scale,
                )
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                boxShadow(BoxShadow(0f, 6f * scale, 16f * scale, Color(0x1B000000)))
                zIndex(2)
            }
            event { click { onClick() } }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        height(22f * scale)
                        borderRadius(11f * scale)
                        padding(left = 8f * scale, right = 8f * scale)
                        backgroundColor(StockChatTheme.accentSoft)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("基准指数")
                            fontSize(10f * scale)
                            fontWeightMedium()
                            color(StockChatTheme.accent)
                        }
                    }
                }
                Text {
                    attr {
                        text("详情  ›")
                        fontSize(11f * scale)
                        color(StockChatTheme.accent)
                        marginLeft(7f * scale)
                    }
                }
            }
            Text {
                attr {
                    text(quote.name)
                    fontSize(18f * scale)
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
                    marginTop(4f * scale)
                }
                Text {
                    attr {
                        text(quote.price)
                        fontSize(28f * scale)
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
            Text {
                attr {
                    text(quote.change)
                    fontSize(11f * scale)
                    color(marketQuoteColor(quote))
                    marginTop(2f * scale)
                }
            }
            TrendSparkline(
                quote = quote,
                width = (width - 32f * scale).coerceAtLeast(1f),
                height = 47f * scale,
            )
            Text {
                attr {
                    text("近${quote.trendPoints.size}期走势 · ${quote.symbol}")
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(2f * scale)
                    lines(1)
                }
            }
        }
    }
}

private fun IndexMiniCard(
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
                padding(top = 11f * scale, left = 11f * scale, bottom = 10f * scale, right = 10f * scale)
                borderRadius(18f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                boxShadow(BoxShadow(0f, 4f * scale, 11f * scale, Color(0x14000000)))
                zIndex(3)
            }
            event { click { onClick() } }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(quote.name)
                        fontSize(13f * scale)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        flex(1f)
                        lines(1)
                    }
                }
                View {
                    attr {
                        size(6f * scale, 6f * scale)
                        borderRadius(3f * scale)
                        backgroundColor(marketQuoteColor(quote))
                    }
                }
            }
            Text {
                attr {
                    text(quote.symbol)
                    fontSize(9f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(3f * scale)
                    lines(1)
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsFlexEnd()
                    marginTop(8f * scale)
                }
                Text {
                    attr {
                        text(quote.price)
                        fontSize(16f * scale)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        flex(1f)
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
}

private fun IndexRibbonCard(
    container: ViewContainer<*, *>,
    quote: StockQuote,
    width: Float,
    top: Float,
    left: Float,
    scale: Float,
    onClick: () -> Unit,
) {
    with(container) {
        View {
            attr {
                absolutePosition(top = top, left = left)
                width(width)
                height(51f * scale)
                padding(left = 13f * scale, right = 12f * scale)
                borderRadius(17f * scale)
                backgroundColor(StockChatTheme.accentSoft)
                themedBorder()
                boxShadow(BoxShadow(0f, 4f * scale, 12f * scale, Color(0x17000000)))
                flexDirectionRow()
                alignItemsCenter()
                zIndex(4)
            }
            event { click { onClick() } }
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
                    fontSize(14f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginRight(10f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(quote.changePercent)
                    fontSize(12f * scale)
                    fontWeightBold()
                    color(marketQuoteColor(quote))
                    lines(1)
                }
            }
        }
    }
}
