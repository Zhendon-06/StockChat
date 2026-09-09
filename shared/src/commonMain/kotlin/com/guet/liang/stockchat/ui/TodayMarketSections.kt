package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场内容分区：快照、情绪卡、观察/样例股票与摘要。

internal fun ViewContainer<*, *>.TodayMarketSnapshotContent(
    snapshot: TodayMarketSnapshot,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
) {
    TodayMarketMoodCard(snapshot, scale)
    MarketDistributionCard(
        "指数涨跌分布",
        listOf(
            MarketDistributionEntry("上涨", snapshot.advancingCount.toFloat(), "${snapshot.advancingCount}个", StockChatTheme.positive),
            MarketDistributionEntry("下跌", snapshot.decliningCount.toFloat(), "${snapshot.decliningCount}个", StockChatTheme.negative),
            MarketDistributionEntry("持平", snapshot.unchangedCount.toFloat(), "${snapshot.unchangedCount}个", StockChatTheme.textTertiary),
        ),
        "${snapshot.indices.size}个指数", "${snapshot.mood} · 样本",
        "统计范围仅为本页指数样本，不代表全市场股票涨跌家数。点选环图查看占比。",
    )
    Text {
        attr {
            text("主要指数")
            fontSize(18f * scale)
            fontWeightBold()
            color(StockChatTheme.textPrimary)
            marginTop(20f * scale)
            marginBottom(10f * scale)
        }
    }
    snapshot.indices.forEach { quote ->
        View {
            attr {
                width(pageWidth - 36f * scale)
                marginBottom(10f * scale)
            }
            IndexQuoteCard(quote, scale, onClick = { onQuoteClick(quote) })
        }
    }
    TodayMarketObservationStocks(snapshot, pageWidth, scale, onQuoteClick)
    TodayMarketSampleStocks(snapshot, pageWidth, scale, onQuoteClick)
    TodayMarketSummary(snapshot, pageWidth, scale)
    View {
        attr {
            marginTop(14f * scale)
            marginBottom(8f * scale)
            padding(top = 12f * scale, left = 14f * scale, bottom = 12f * scale, right = 14f * scale)
            borderRadius(15f * scale)
            backgroundColor(StockChatTheme.warningSoft)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.warningBorder))
        }
        Text {
            attr {
                text(snapshot.disclaimer)
                fontSize(12f * scale)
                lineHeight(18f * scale)
                color(StockChatTheme.warning)
            }
        }
    }
}

private fun ViewContainer<*, *>.TodayMarketMoodCard(
    snapshot: TodayMarketSnapshot,
    scale: Float,
) {
    View {
        attr {
            padding(
                top = 16f * scale,
                left = 16f * scale,
                bottom = 16f * scale,
                right = 16f * scale,
            )
            borderRadius(20f * scale)
            backgroundLinearGradient(
                Direction.TO_BOTTOM_RIGHT,
                com.tencent.kuikly.core.base.ColorStop(
                    StockChatTheme.marketMoodBackgroundStart,
                    0f,
                ),
                com.tencent.kuikly.core.base.ColorStop(
                    StockChatTheme.marketMoodBackgroundEnd,
                    1f,
                ),
            )
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.marketMoodBorder))
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text("市场温度")
                    fontSize(16f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    flex(1f)
                }
            }
            View {
                attr {
                    height(28f * scale)
                    borderRadius(14f * scale)
                    padding(left = 11f * scale, right = 11f * scale)
                    backgroundColor(StockChatTheme.surface)
                    allCenter()
                }
                Text {
                    attr {
                        text(snapshot.mood)
                        fontSize(12f * scale)
                        fontWeightBold()
                        color(StockChatTheme.accent)
                    }
                }
            }
        }
        Text {
            attr {
                text("样本上涨 ${snapshot.advancingCount} · 下跌 ${snapshot.decliningCount} · 持平 ${snapshot.unchangedCount}")
                fontSize(14f * scale)
                color(StockChatTheme.textSecondary)
                marginTop(13f * scale)
            }
        }
        Text {
            attr {
                text("温度只描述当前指数样本，不代表涨跌预测。")
                fontSize(11f * scale)
                color(StockChatTheme.textTertiary)
                marginTop(5f * scale)
            }
        }
    }
}

private fun ViewContainer<*, *>.TodayMarketObservationStocks(
    snapshot: TodayMarketSnapshot,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
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
                text("观察方向")
                fontSize(18f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
                flex(1f)
            }
        }
        Text {
            attr {
                text("10只样本企业 · 横向浏览")
                fontSize(11f * scale)
                color(StockChatTheme.textTertiary)
            }
        }
    }
    val columnWidth = 132f * scale
    val cardHeight = 78f * scale
    val rowGap = 8f * scale
    val columns = snapshot.sampleStocks.chunked(2)
    Scroller {
        attr {
            width((pageWidth - 36f * scale).coerceAtLeast(columnWidth))
            height(cardHeight * 2f + rowGap)
            flexDirectionRow()
            showScrollerIndicator(false)
            bouncesEnable(true)
            scrollEnable(snapshot.sampleStocks.size > 5)
            capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
        }
        columns.forEachIndexed { columnIndex, column ->
            View {
                attr {
                    width(columnWidth)
                    marginRight(if (columnIndex == columns.lastIndex) 0f else rowGap)
                }
                column.forEachIndexed { rowIndex, quote ->
                    View {
                        attr {
                            height(cardHeight)
                            padding(
                                top = 10f * scale,
                                left = 11f * scale,
                                bottom = 9f * scale,
                                right = 11f * scale,
                            )
                            borderRadius(15f * scale)
                            backgroundColor(StockChatTheme.surface)
                            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                            marginBottom(if (rowIndex == 0) rowGap else 0f)
                        }
                        event {
                            click { onQuoteClick(quote) }
                        }
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
            }
        }
    }
}

private fun ViewContainer<*, *>.TodayMarketSampleStocks(
    snapshot: TodayMarketSnapshot,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
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
                text("样本个股动向")
                fontSize(18f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
                flex(1f)
            }
        }
        Text {
            attr {
                text("按当日涨跌排序")
                fontSize(11f * scale)
                color(StockChatTheme.textTertiary)
            }
        }
    }
    View {
        attr {
            width((pageWidth - 36f * scale).coerceAtLeast(1f))
            alignSelfCenter()
            borderRadius(17f * scale)
            backgroundColor(StockChatTheme.surface)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
            padding(left = 14f * scale, right = 14f * scale)
        }
        snapshot.sampleStocks.forEachIndexed { index, quote ->
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    height(52f * scale)
                }
                event {
                    click { onQuoteClick(quote) }
                }
                View {
                    attr {
                        flex(1f)
                    }
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
            if (index != snapshot.sampleStocks.lastIndex) {
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

private fun ViewContainer<*, *>.TodayMarketSummary(
    snapshot: TodayMarketSnapshot,
    pageWidth: Float,
    scale: Float,
) {
    View {
        attr {
            width((pageWidth - 36f * scale).coerceAtLeast(1f))
            alignSelfCenter()
            marginTop(14f * scale)
            padding(top = 16f * scale, left = 16f * scale, bottom = 16f * scale, right = 16f * scale)
            borderRadius(19f * scale)
            backgroundColor(StockChatTheme.surface)
            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
        }
        Text {
            attr {
                text("白话小结")
                fontSize(16f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
            }
        }
        Text {
            attr {
                text(snapshot.summary)
                fontSize(14f * scale)
                lineHeight(22f * scale)
                color(StockChatTheme.textSecondary)
                marginTop(9f * scale)
            }
        }
        Text {
            attr {
                text(snapshot.sourceLabel)
                fontSize(11f * scale)
                color(StockChatTheme.textTertiary)
                marginTop(9f * scale)
            }
        }
    }
}
