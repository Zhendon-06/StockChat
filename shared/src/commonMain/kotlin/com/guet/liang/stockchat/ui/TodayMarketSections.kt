package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
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
    TodayMarketDistribution(snapshot)
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
    MarketStockList(
        title = "观察方向",
        subtitle = "10只样本企业 · 横向浏览",
        quotes = snapshot.sampleStocks,
        pageWidth = pageWidth,
        scale = scale,
        onQuoteClick = onQuoteClick,
        horizontal = true,
    )
    MarketStockList(
        title = "样本个股动向",
        subtitle = "按当日涨跌排序",
        quotes = snapshot.sampleStocks,
        pageWidth = pageWidth,
        scale = scale,
        onQuoteClick = onQuoteClick,
    )
    TodayMarketSummary(snapshot, pageWidth, scale)
    View {
        attr {
            marginTop(14f * scale)
            marginBottom(8f * scale)
            padding(
                top = 12f * scale,
                left = 14f * scale,
                bottom = 12f * scale,
                right = 14f * scale,
            )
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

private fun ViewContainer<*, *>.TodayMarketMoodCard(snapshot: TodayMarketSnapshot, scale: Float) {
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
                com.tencent.kuikly.core.base.ColorStop(StockChatTheme.marketMoodBackgroundEnd, 1f),
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
                text(
                    "样本上涨 ${snapshot.advancingCount} · 下跌 ${snapshot.decliningCount} · 持平 ${snapshot.unchangedCount}"
                )
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
            padding(
                top = 16f * scale,
                left = 16f * scale,
                bottom = 16f * scale,
                right = 16f * scale,
            )
            borderRadius(19f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
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

private fun ViewContainer<*, *>.TodayMarketDistribution(snapshot: TodayMarketSnapshot) {
    MarketDistributionCard(
        "指数涨跌分布",
        listOf(
            MarketDistributionEntry(
                "上涨",
                snapshot.advancingCount.toFloat(),
                "${snapshot.advancingCount}个",
                StockChatTheme.positive,
            ),
            MarketDistributionEntry(
                "下跌",
                snapshot.decliningCount.toFloat(),
                "${snapshot.decliningCount}个",
                StockChatTheme.negative,
            ),
            MarketDistributionEntry(
                "持平",
                snapshot.unchangedCount.toFloat(),
                "${snapshot.unchangedCount}个",
                StockChatTheme.textTertiary,
            ),
        ),
        "${snapshot.indices.size}个指数",
        "${snapshot.mood} · 样本",
        "统计范围仅为本页指数样本，不代表全市场股票涨跌家数。点选环图查看占比。",
    )
}
