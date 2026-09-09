package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.MarketStockList(
    title: String,
    subtitle: String,
    quotes: List<StockQuote>,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
    horizontal: Boolean = false,
) {
    MarketStockListHeader(title, subtitle, scale)
    if (horizontal) {
        MarketStockColumns(quotes, pageWidth, scale, onQuoteClick)
    } else {
        MarketStockRows(quotes, pageWidth, scale, onQuoteClick)
    }
}

private fun ViewContainer<*, *>.MarketStockListHeader(
    title: String,
    subtitle: String,
    scale: Float,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginTop(20f * scale)
            marginBottom(10f * scale)
        }
        Text {
            attr {
                text(title)
                fontSize(18f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
                flex(1f)
            }
        }
        Text {
            attr {
                text(subtitle)
                fontSize(11f * scale)
                color(StockChatTheme.textTertiary)
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketStockColumns(
    quotes: List<StockQuote>,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
) {
    val columnWidth = 132f * scale
    val cardHeight = 78f * scale
    val rowGap = 8f * scale
    val columns = quotes.chunked(2)
    Scroller {
        attr {
            width((pageWidth - 36f * scale).coerceAtLeast(columnWidth))
            height(cardHeight * 2f + rowGap)
            flexDirectionRow()
            showScrollerIndicator(false)
            bouncesEnable(true)
            scrollEnable(quotes.size > 5)
            capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
        }
        columns.forEachIndexed { columnIndex, column ->
            View {
                attr {
                    width(columnWidth)
                    marginRight(if (columnIndex == columns.lastIndex) 0f else rowGap)
                }
                column.forEachIndexed { rowIndex, quote ->
                    MarketStockCard(quote, scale, rowIndex == 0, onQuoteClick)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketStockRows(
    quotes: List<StockQuote>,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
) {
    View {
        attr {
            width((pageWidth - 36f * scale).coerceAtLeast(1f))
            alignSelfCenter()
            borderRadius(17f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
            padding(left = 14f * scale, right = 14f * scale)
        }
        quotes.forEachIndexed { index, quote ->
            MarketStockRow(quote, scale, onQuoteClick)
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

private fun ViewContainer<*, *>.MarketStockCard(
    quote: StockQuote,
    scale: Float,
    firstRow: Boolean,
    onQuoteClick: (StockQuote) -> Unit,
) {
    View {
        attr {
            height(78f * scale)
            padding(top = 10f * scale, left = 11f * scale, bottom = 9f * scale, right = 11f * scale)
            borderRadius(15f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
            marginBottom(if (firstRow) 8f * scale else 0f)
        }
        event { click { onQuoteClick(quote) } }
        Text {
            attr {
                text(quote.name)
                fontSize(13f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
                lines(1)
            }
        }
        Text {
            attr {
                text(quote.symbol)
                fontSize(10f * scale)
                color(StockChatTheme.textTertiary)
                marginTop(3f * scale)
                lines(1)
            }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(7f * scale)
            }
            Text {
                attr {
                    text(quote.price)
                    fontSize(12f * scale)
                    fontWeightMedium()
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
                    color(
                        if (quote.isPositive) {
                            StockChatTheme.positive
                        } else {
                            StockChatTheme.negative
                        }
                    )
                    lines(1)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketStockRow(
    quote: StockQuote,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            height(52f * scale)
        }
        event { click { onQuoteClick(quote) } }
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text(quote.name)
                    fontSize(14f * scale)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(quote.symbol)
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(3f * scale)
                }
            }
        }
        Text {
            attr {
                text(quote.price)
                fontSize(14f * scale)
                fontWeightMedium()
                color(StockChatTheme.textPrimary)
                marginRight(12f * scale)
            }
        }
        View {
            attr {
                width(64f * scale)
                height(26f * scale)
                borderRadius(13f * scale)
                allCenter()
                backgroundColor(
                    if (quote.isPositive) {
                        StockChatTheme.marketPositiveSoft
                    } else {
                        StockChatTheme.marketNegativeSoft
                    }
                )
            }
            Text {
                attr {
                    text(quote.changePercent)
                    fontSize(12f * scale)
                    fontWeightBold()
                    color(
                        if (quote.isPositive) {
                            StockChatTheme.positive
                        } else {
                            StockChatTheme.negative
                        }
                    )
                }
            }
        }
    }
}
