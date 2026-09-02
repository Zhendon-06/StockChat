package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.guet.liang.stockchat.model.TodayMarketUiState
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.TodayMarketContent(
    state: () -> TodayMarketUiState,
    pageWidth: Float,
    scale: Float,
    safeAreaBottom: Float,
    touchEnabled: () -> Boolean = { true },
    onQuoteClick: (StockQuote) -> Unit,
    onRetry: () -> Unit,
) {
    val bottomSwitcherHeight = 44f * scale
    val bottomSwitcherOffset = safeAreaBottom + 14f * scale
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(StockChatTheme.background)
            touchEnable(touchEnabled())
        }
        Scroller {
            attr {
                absolutePosition(
                    top = 0f,
                    left = 0f,
                    right = 0f,
                    bottom = bottomSwitcherOffset + bottomSwitcherHeight + 18f * scale,
                )
                showScrollerIndicator(false)
                bouncesEnable(true)
                touchEnable(touchEnabled())
                padding(
                    top = 12f * scale,
                    left = 18f * scale,
                    right = 18f * scale,
                    bottom = 24f * scale,
                )
            }
            TodayMarketHeader(
                state = state,
                scale = scale,
                onRetry = onRetry,
            )
            vif({ state() is TodayMarketUiState.Loading }) {
                TodayMarketLoading(scale)
            }
            vif({ state() is TodayMarketUiState.Empty }) {
                TodayMarketEmpty(scale, onRetry)
            }
            vif({ state() is TodayMarketUiState.Error }) {
                TodayMarketError(
                    message = (state() as? TodayMarketUiState.Error)?.message.orEmpty(),
                    scale = scale,
                    onRetry = onRetry,
                )
            }
            vif({ state() is TodayMarketUiState.Content }) {
                val snapshot = (state() as? TodayMarketUiState.Content)?.snapshot
                if (snapshot != null) {
                    TodayMarketSnapshotContent(
                        snapshot = snapshot,
                        pageWidth = pageWidth,
                        scale = scale,
                        onQuoteClick = onQuoteClick,
                    )
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.TodayMarketHeader(
    state: () -> TodayMarketUiState,
    scale: Float,
    onRetry: () -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginBottom(16f * scale)
        }
        View {
            attr {
                flex(1f)
            }
            Text {
                attr {
                    text("今日市场")
                    fontSize(26f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("先看整体，再看具体标的")
                    fontSize(13f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(5f * scale)
                }
            }
        }
        View {
            attr {
                height(34f * scale)
                borderRadius(17f * scale)
                padding(left = 12f * scale, right = 12f * scale)
                backgroundColor(StockChatTheme.surface)
                border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                allCenter()
            }
            event {
                click { onRetry() }
            }
            Text {
                attr {
                    text("刷新")
                    fontSize(12f * scale)
                    fontWeightMedium()
                    color(StockChatTheme.accent)
                }
            }
        }
    }
    vif({ state() is TodayMarketUiState.Content }) {
        val snapshot = (state() as TodayMarketUiState.Content).snapshot
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginBottom(12f * scale)
            }
            View {
                attr {
                    height(24f * scale)
                    borderRadius(12f * scale)
                    padding(left = 9f * scale, right = 9f * scale)
                    backgroundColor(
                        if (snapshot.isDemo) StockChatTheme.warningSoft else StockChatTheme.accentSoft
                    )
                    allCenter()
                }
                Text {
                    attr {
                        text(if (snapshot.isDemo) "演示数据" else "行情快照")
                        fontSize(11f * scale)
                        color(
                            if (snapshot.isDemo) StockChatTheme.warning else StockChatTheme.accent
                        )
                    }
                }
            }
            Text {
                attr {
                    text(snapshot.asOf)
                    fontSize(11f * scale)
                    color(StockChatTheme.textTertiary)
                    marginLeft(8f * scale)
                    lines(1)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.TodayMarketSnapshotContent(
    snapshot: TodayMarketSnapshot,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
) {
    TodayMarketMoodCard(snapshot, scale)
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
            MarketQuoteCard(
                quote = quote,
                scale = scale,
                onClick = { onQuoteClick(quote) },
            )
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
            backgroundColor(Color(0xFFFFF7EA))
            border(Border(1f, BorderStyle.SOLID, Color(0xFFF2DEBA)))
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
                com.tencent.kuikly.core.base.ColorStop(Color(0xFFEAF8F2), 0f),
                com.tencent.kuikly.core.base.ColorStop(Color(0xFFF7FBF8), 1f),
            )
            border(Border(1f, BorderStyle.SOLID, Color(0xFFD7EDE2)))
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
                                Color(0xFFFDEEED)
                            } else {
                                Color(0xFFE9F5F1)
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

private fun ViewContainer<*, *>.TodayMarketLoading(scale: Float) {
    View {
        attr {
            height(260f * scale)
            allCenter()
        }
        Text {
            attr {
                text("正在整理今日市场…")
                fontSize(15f * scale)
                color(StockChatTheme.textSecondary)
            }
        }
        Text {
            attr {
                text("稍等一下，先看指数整体表现")
                fontSize(12f * scale)
                color(StockChatTheme.textTertiary)
                marginTop(8f * scale)
            }
        }
    }
}

private fun ViewContainer<*, *>.TodayMarketEmpty(
    scale: Float,
    onRetry: () -> Unit,
) {
    View {
        attr {
            height(260f * scale)
            allCenter()
        }
        Text {
            attr {
                text("暂时没有市场数据")
                fontSize(16f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
            }
        }
        View {
            attr {
                height(36f * scale)
                borderRadius(18f * scale)
                padding(left = 16f * scale, right = 16f * scale)
                backgroundColor(StockChatTheme.accent)
                allCenter()
                marginTop(14f * scale)
            }
            event {
                click { onRetry() }
            }
            Text {
                attr {
                    text("重新加载")
                    fontSize(13f * scale)
                    fontWeightMedium()
                    color(Color.WHITE)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.TodayMarketError(
    message: String,
    scale: Float,
    onRetry: () -> Unit,
) {
    View {
        attr {
            height(260f * scale)
            allCenter()
            padding(left = 30f * scale, right = 30f * scale)
        }
        Text {
            attr {
                text("市场数据加载失败")
                fontSize(16f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
            }
        }
        Text {
            attr {
                text(message.ifBlank { "请稍后重试" })
                fontSize(13f * scale)
                color(StockChatTheme.textSecondary)
                textAlignCenter()
                marginTop(9f * scale)
            }
        }
        View {
            attr {
                height(36f * scale)
                borderRadius(18f * scale)
                padding(left = 16f * scale, right = 16f * scale)
                backgroundColor(StockChatTheme.accent)
                allCenter()
                marginTop(14f * scale)
            }
            event {
                click { onRetry() }
            }
            Text {
                attr {
                    text("重新加载")
                    fontSize(13f * scale)
                    fontWeightMedium()
                    color(Color.WHITE)
                }
            }
        }
    }
}
