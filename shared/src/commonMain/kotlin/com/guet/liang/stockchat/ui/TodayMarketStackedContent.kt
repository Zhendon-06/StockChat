package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSectorObservation
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场采用非对称 Bento 构图：主卡、辅卡和底板各司其职，内容之间不互相遮挡。

internal fun ViewContainer<*, *>.TodayMarketStackedContent(
    snapshot: TodayMarketSnapshot,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
    onSectorClick: (TodayMarketSectorObservation) -> Unit,
    indexFocus: () -> Int,
    onAdvanceIndexFocus: () -> Unit,
    sectorFocus: () -> Int,
    onAdvanceSectorFocus: () -> Unit,
    quoteFocus: () -> Int,
    onAdvanceQuoteFocus: () -> Unit,
) {
    val contentWidth = marketContentWidth(pageWidth, scale)
    MarketPulseBento(snapshot, contentWidth, scale)
    vbind({ indexFocus() }) {
        MarketIndexBento(
            indices = snapshot.indices,
            contentWidth = contentWidth,
            scale = scale,
            onQuoteClick = onQuoteClick,
            focusIndex = indexFocus(),
            onFocusAdvance = onAdvanceIndexFocus,
        )
    }
    vbind({ sectorFocus() }) {
        MarketSectorBento(
            sectors = snapshot.sectors,
            contentWidth = contentWidth,
            scale = scale,
            onSectorClick = onSectorClick,
            focusIndex = sectorFocus(),
            onFocusAdvance = onAdvanceSectorFocus,
        )
    }
    vbind({ quoteFocus() }) {
        MarketWatchBento(
            quotes = snapshot.sampleStocks,
            contentWidth = contentWidth,
            scale = scale,
            onQuoteClick = onQuoteClick,
            focusIndex = quoteFocus(),
            onFocusAdvance = onAdvanceQuoteFocus,
        )
    }
    MarketPlainSummary(this, snapshot, contentWidth, scale)
    View {
        attr {
            width(contentWidth)
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

private fun marketContentWidth(pageWidth: Float, scale: Float): Float =
    (pageWidth - 36f * scale).coerceAtLeast(1f)

internal fun ViewContainer<*, *>.TodayMarketStackedLoading(
    scale: Float,
    pageWidth: Float,
    phase: () -> Int,
) {
    val contentWidth = marketContentWidth(pageWidth, scale)
    View {
        attr { width(contentWidth) }
        LoadingPulseBento(contentWidth, scale, phase)
        LoadingSectionTitle(scale, phase)
        LoadingIndexBento(contentWidth, scale, phase)
        LoadingSectionTitle(scale, phase)
        LoadingWatchBento(contentWidth, scale, phase)
        View {
            attr {
                alignItemsCenter()
                marginTop(22f * scale)
                marginBottom(8f * scale)
            }
            Text {
                attr {
                    text("正在整理今日市场…")
                    fontSize(14f * scale)
                    color(StockChatTheme.textSecondary)
                }
            }
            Text {
                attr {
                    text("稍等一下，先看指数整体表现")
                    fontSize(12f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(6f * scale)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketPulseBento(
    snapshot: TodayMarketSnapshot,
    contentWidth: Float,
    scale: Float,
) {
    val total = (
        snapshot.advancingCount +
            snapshot.decliningCount +
            snapshot.unchangedCount
        ).coerceAtLeast(1)
    val advancingRatio = (snapshot.advancingCount.toFloat() / total * 100f).toInt()
    val mainWidth = contentWidth * 0.68f
    val sideWidth = contentWidth - mainWidth - 10f * scale
    val trendColor = if (snapshot.advancingCount >= snapshot.decliningCount) {
        StockChatTheme.positive
    } else {
        StockChatTheme.negative
    }
    View {
        attr {
            width(contentWidth)
            height(207f * scale)
            positionRelative()
        }
        // 只做背景层，不承载文字，避免装饰层与数据层争夺视觉焦点。
        View {
            attr {
                absolutePosition(top = 9f * scale, left = 10f * scale, right = 0f)
                height(188f * scale)
                borderRadius(25f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(mainWidth)
                height(194f * scale)
                padding(
                    top = 16f * scale,
                    left = 17f * scale,
                    bottom = 13f * scale,
                    right = 17f * scale,
                )
                borderRadius(23f * scale)
                backgroundLinearGradient(
                    Direction.TO_BOTTOM_RIGHT,
                    ColorStop(StockChatTheme.marketMoodBackgroundStart, 0f),
                    ColorStop(StockChatTheme.marketMoodBackgroundEnd, 1f),
                )
                themedBorder()
                boxShadow(BoxShadow(0f, 6f * scale, 16f * scale, Color(0x16000000)))
                zIndex(2)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(8f * scale, 8f * scale)
                        borderRadius(4f * scale)
                        backgroundColor(trendColor)
                        marginRight(7f * scale)
                    }
                }
                Text {
                    attr {
                        text("市场脉搏")
                        fontSize(13f * scale)
                        fontWeightMedium()
                        color(StockChatTheme.textSecondary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text(if (snapshot.isDemo) "演示快照" else "实时快照")
                        fontSize(10f * scale)
                        color(StockChatTheme.textTertiary)
                    }
                }
            }
            Text {
                attr {
                    text(if (snapshot.advancingCount >= snapshot.decliningCount) "热度回升" else "分化延续")
                    fontSize(24f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(13f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text("上涨 ${snapshot.advancingCount} · 下跌 ${snapshot.decliningCount} · 持平 ${snapshot.unchangedCount}")
                    fontSize(13f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(6f * scale)
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    marginTop(15f * scale)
                }
                PulseMetric("上涨占比", "$advancingRatio%", StockChatTheme.positive, scale)
                PulseMetricDivider(scale)
                PulseMetric("覆盖指数", "${snapshot.indices.size} 个", StockChatTheme.accent, scale)
            }
            Text {
                attr {
                    text("先看基准，再看结构")
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(11f * scale)
                }
            }
        }
        PulseSideCard(
            container = this,
            title = "情绪",
            value = snapshot.mood,
            detail = "样本状态",
            width = sideWidth,
            top = 7f * scale,
            height = 84f * scale,
            tint = trendColor,
            scale = scale,
        )
        PulseSideCard(
            container = this,
            title = "覆盖",
            value = "${snapshot.indices.size} 个",
            detail = "主要指数",
            width = sideWidth,
            top = 101f * scale,
            height = 84f * scale,
            tint = StockChatTheme.accent,
            scale = scale,
        )
    }
}

private fun ViewContainer<*, *>.PulseMetric(
    label: String,
    value: String,
    tint: Color,
    scale: Float,
) {
    View {
        attr { flex(1f) }
        Text {
            attr {
                text(label)
                fontSize(9f * scale)
                color(StockChatTheme.textTertiary)
            }
        }
        Text {
            attr {
                text(value)
                fontSize(13f * scale)
                fontWeightMedium()
                color(tint)
                marginTop(3f * scale)
                lines(1)
            }
        }
    }
}

private fun ViewContainer<*, *>.PulseMetricDivider(scale: Float) {
    View {
        attr {
            width(1f * scale)
            height(24f * scale)
            backgroundColor(StockChatTheme.border)
            margin(left = 8f * scale, right = 8f * scale)
        }
    }
}

private fun PulseSideCard(
    container: ViewContainer<*, *>,
    title: String,
    value: String,
    detail: String,
    width: Float,
    top: Float,
    height: Float,
    tint: Color,
    scale: Float,
) {
    with(container) {
        View {
            attr {
                absolutePosition(top = top, right = 0f)
                width(width)
                height(height)
                padding(top = 12f * scale, left = 12f * scale, bottom = 11f * scale, right = 11f * scale)
                borderRadius(18f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                boxShadow(BoxShadow(0f, 4f * scale, 11f * scale, Color(0x14000000)))
                zIndex(3)
            }
            Text {
                attr {
                    text(title)
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                }
            }
            Text {
                attr {
                    text(value)
                    fontSize(21f * scale)
                    fontWeightBold()
                    color(tint)
                    marginTop(7f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(detail)
                    fontSize(9f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(3f * scale)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketIndexBento(
    indices: List<StockQuote>,
    contentWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
    focusIndex: Int,
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

private fun ViewContainer<*, *>.MarketSectorBento(
    sectors: List<TodayMarketSectorObservation>,
    contentWidth: Float,
    scale: Float,
    onSectorClick: (TodayMarketSectorObservation) -> Unit,
    focusIndex: Int,
    onFocusAdvance: () -> Unit,
) {
    if (sectors.isEmpty()) return
    val focusedSectors = sectors.focusedFrom(focusIndex)
    TodayMarketSectionTitle(this, "板块观察", "方向与分化", scale, onFocusAdvance)
    val featureWidth = contentWidth * 0.58f
    val sideWidth = contentWidth - featureWidth - 9f * scale
    val stageHeight = if (focusedSectors.size > 2) 305f * scale else 197f * scale
    View {
        attr {
            width(contentWidth)
            height(stageHeight)
            positionRelative()
        }
        View {
            attr {
                absolutePosition(top = 8f * scale, left = 10f * scale, right = 0f)
                height(181f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        SectorFeatureCard(
            container = this,
            sector = focusedSectors.first(),
            width = featureWidth,
            height = 177f * scale,
            scale = scale,
        ) {
            onSectorClick(focusedSectors.first())
        }
        focusedSectors.getOrNull(1)?.let { sector ->
            SectorSideCard(
                container = this,
                sector = sector,
                width = sideWidth,
                top = 11f * scale,
                height = 103f * scale,
                scale = scale,
            ) {
                onSectorClick(sector)
            }
        }
        focusedSectors.getOrNull(2)?.let { sector ->
            SectorStripCard(this, sector, contentWidth * 0.47f, 187f * scale, 3f * scale, scale, 3) {
                onSectorClick(sector)
            }
        }
        focusedSectors.getOrNull(3)?.let { sector ->
            SectorStripCard(
                this,
                sector,
                contentWidth * 0.47f,
                187f * scale,
                contentWidth * 0.51f,
                scale,
                4,
            ) {
                onSectorClick(sector)
            }
        }
        focusedSectors.getOrNull(4)?.let { sector ->
            SectorStripCard(this, sector, contentWidth * 0.72f, 245f * scale, contentWidth * 0.14f, scale, 5) {
                onSectorClick(sector)
            }
        }
    }
}

private fun SectorFeatureCard(
    container: ViewContainer<*, *>,
    sector: TodayMarketSectorObservation,
    width: Float,
    height: Float,
    scale: Float,
    onClick: () -> Unit,
) {
    val tint = marketSectorColor(sector)
    with(container) {
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(width)
                height(height)
                padding(
                    top = 15f * scale,
                    left = 15f * scale,
                    bottom = 12f * scale,
                    right = 15f * scale,
                )
                borderRadius(22f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                boxShadow(BoxShadow(0f, 6f * scale, 16f * scale, Color(0x1A000000)))
                zIndex(2)
            }
            event { click { onClick() } }
            Text {
                attr {
                    text("最活跃方向")
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                }
            }
            Text {
                attr {
                    text(sector.name)
                    fontSize(21f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(12f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(sector.changeLabel)
                    fontSize(28f * scale)
                    fontWeightBold()
                    color(tint)
                    marginTop(3f * scale)
                }
            }
            Text {
                attr {
                    text(sector.members.ifBlank { "暂无成员快照" })
                    fontSize(11f * scale)
                    lineHeight(17f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(11f * scale)
                    lines(2)
                }
            }
            Text {
                attr {
                    text("查看成员  ›")
                    fontSize(10f * scale)
                    color(StockChatTheme.accent)
                    marginTop(9f * scale)
                }
            }
        }
    }
}

private fun SectorSideCard(
    container: ViewContainer<*, *>,
    sector: TodayMarketSectorObservation,
    width: Float,
    top: Float,
    height: Float,
    scale: Float,
    onClick: () -> Unit,
) {
    val tint = marketSectorColor(sector)
    with(container) {
        View {
            attr {
                absolutePosition(top = top, right = 0f)
                width(width)
                height(height)
                padding(top = 13f * scale, left = 12f * scale, bottom = 11f * scale, right = 11f * scale)
                borderRadius(19f * scale)
                backgroundColor(StockChatTheme.accentSoft)
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
                    text(sector.name)
                    fontSize(15f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(8f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(sector.changeLabel)
                    fontSize(19f * scale)
                    fontWeightBold()
                    color(tint)
                    marginTop(8f * scale)
                }
            }
            Text {
                attr {
                    text("点击查看成员")
                    fontSize(9f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(4f * scale)
                }
            }
        }
    }
}

private fun SectorStripCard(
    container: ViewContainer<*, *>,
    sector: TodayMarketSectorObservation,
    width: Float,
    top: Float,
    left: Float,
    scale: Float,
    zIndex: Int,
    onClick: () -> Unit,
) {
    val tint = marketSectorColor(sector)
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
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text(sector.name)
                        fontSize(12f * scale)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text(sector.members)
                        fontSize(9f * scale)
                        color(StockChatTheme.textTertiary)
                        marginTop(2f * scale)
                        lines(1)
                    }
                }
            }
            Text {
                attr {
                    text(sector.changeLabel)
                    fontSize(14f * scale)
                    fontWeightBold()
                    color(tint)
                    lines(1)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketWatchBento(
    quotes: List<StockQuote>,
    contentWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
    focusIndex: Int,
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

private fun <T> List<T>.focusedFrom(focusIndex: Int): List<T> {
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

private fun MarketPlainSummary(
    container: ViewContainer<*, *>,
    snapshot: TodayMarketSnapshot,
    contentWidth: Float,
    scale: Float,
) {
    with(container) {
        View {
            attr {
                width(contentWidth)
                alignSelfCenter()
                marginTop(22f * scale)
                padding(left = 2f * scale, right = 2f * scale)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        width(4f * scale)
                        height(15f * scale)
                        borderRadius(2f * scale)
                        backgroundColor(StockChatTheme.accent)
                        marginRight(8f * scale)
                    }
                }
                Text {
                    attr {
                        text("白话小结")
                        fontSize(16f * scale)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
            Text {
                attr {
                    text(snapshot.summary)
                    fontSize(14f * scale)
                    lineHeight(23f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(10f * scale)
                }
            }
            Text {
                attr {
                    text(snapshot.sourceLabel)
                    fontSize(11f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(10f * scale)
                }
            }
        }
    }
}

private fun marketQuoteColor(quote: StockQuote): Color =
    if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative

private fun marketSectorColor(sector: TodayMarketSectorObservation): Color =
    if (sector.isPositive) StockChatTheme.positive else StockChatTheme.negative

private fun ViewContainer<*, *>.LoadingPulseBento(
    contentWidth: Float,
    scale: Float,
    phase: () -> Int,
) {
    val mainWidth = contentWidth * 0.68f
    val sideWidth = contentWidth - mainWidth - 10f * scale
    View {
        attr {
            width(contentWidth)
            height(207f * scale)
            positionRelative()
        }
        View {
            attr {
                absolutePosition(top = 9f * scale, left = 10f * scale, right = 0f)
                height(188f * scale)
                borderRadius(25f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(mainWidth)
                height(194f * scale)
                padding(17f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            LoadingBar(74f * scale, 13f * scale, scale, phase)
            LoadingBar(mainWidth * 0.62f, 26f * scale, scale, phase, marginTop = 15f * scale)
            LoadingBar(mainWidth * 0.5f, 13f * scale, scale, phase, marginTop = 8f * scale)
            View {
                attr {
                    flexDirectionRow()
                    marginTop(18f * scale)
                }
                repeat(2) {
                    View {
                        attr {
                            flex(1f)
                            if (it > 0) marginLeft(12f * scale)
                        }
                        LoadingBar(46f * scale, 9f * scale, scale, phase)
                        LoadingBar(58f * scale, 14f * scale, scale, phase, marginTop = 6f * scale)
                    }
                }
            }
        }
        LoadingSideBento(sideWidth, 7f * scale, scale, phase)
        LoadingSideBento(sideWidth, 101f * scale, scale, phase)
    }
}

private fun ViewContainer<*, *>.LoadingSideBento(
    width: Float,
    top: Float,
    scale: Float,
    phase: () -> Int,
) {
    View {
        attr {
            absolutePosition(top = top, right = 0f)
            width(width)
            height(84f * scale)
            padding(12f * scale)
            borderRadius(18f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
        }
        LoadingBar(42f * scale, 10f * scale, scale, phase)
        LoadingBar(width * 0.7f, 20f * scale, scale, phase, marginTop = 8f * scale)
        LoadingBar(48f * scale, 9f * scale, scale, phase, marginTop = 4f * scale)
    }
}

private fun ViewContainer<*, *>.LoadingSectionTitle(
    scale: Float,
    phase: () -> Int,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginTop(20f * scale)
            marginBottom(10f * scale)
        }
        LoadingBar(4f * scale, 16f * scale, scale, phase)
        LoadingBar(84f * scale, 18f * scale, scale, phase, marginLeft = 8f * scale)
        View { attr { flex(1f) } }
        LoadingBar(92f * scale, 10f * scale, scale, phase)
    }
}

private fun ViewContainer<*, *>.LoadingIndexBento(
    contentWidth: Float,
    scale: Float,
    phase: () -> Int,
) {
    val leadWidth = contentWidth * 0.61f
    val sideWidth = contentWidth - leadWidth - 9f * scale
    View {
        attr {
            width(contentWidth)
            height(248f * scale)
            positionRelative()
        }
        View {
            attr {
                absolutePosition(top = 7f * scale, left = 9f * scale, right = 0f)
                height(193f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(leadWidth)
                height(200f * scale)
                padding(16f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            LoadingBar(72f * scale, 22f * scale, scale, phase)
            LoadingBar(82f * scale, 18f * scale, scale, phase, marginTop = 12f * scale)
            LoadingBar(leadWidth * 0.68f, 30f * scale, scale, phase, marginTop = 7f * scale)
            LoadingBar(leadWidth - 32f * scale, 47f * scale, scale, phase, marginTop = 15f * scale)
        }
        LoadingIndexMiniBento(sideWidth, 2f * scale, scale, phase)
        LoadingIndexMiniBento(sideWidth, 101f * scale, scale, phase)
        View {
            attr {
                absolutePosition(top = 194f * scale, left = contentWidth * 0.13f)
                width(contentWidth * 0.74f)
                height(51f * scale)
                borderRadius(17f * scale)
                backgroundColor(StockChatTheme.accentSoft)
                themedBorder()
            }
            LoadingBar(contentWidth * 0.28f, 13f * scale, scale, phase, marginLeft = 13f * scale)
            LoadingBar(contentWidth * 0.18f, 13f * scale, scale, phase, marginLeft = 13f * scale)
        }
    }
}

private fun ViewContainer<*, *>.LoadingIndexMiniBento(
    width: Float,
    top: Float,
    scale: Float,
    phase: () -> Int,
) {
    View {
        attr {
            absolutePosition(top = top, right = 0f)
            width(width)
            height(88f * scale)
            padding(11f * scale)
            borderRadius(18f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
        }
        LoadingBar(width * 0.65f, 13f * scale, scale, phase)
        LoadingBar(width * 0.36f, 9f * scale, scale, phase, marginTop = 6f * scale)
        LoadingBar(width * 0.8f, 18f * scale, scale, phase, marginTop = 13f * scale)
    }
}

private fun ViewContainer<*, *>.LoadingWatchBento(
    contentWidth: Float,
    scale: Float,
    phase: () -> Int,
) {
    val featureWidth = contentWidth * 0.61f
    val sideWidth = contentWidth - featureWidth - 9f * scale
    View {
        attr {
            width(contentWidth)
            height(310f * scale)
            positionRelative()
        }
        View {
            attr {
                absolutePosition(top = 8f * scale, left = 9f * scale, right = 0f)
                height(185f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(featureWidth)
                height(183f * scale)
                padding(15f * scale)
                borderRadius(22f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            LoadingBar(52f * scale, 10f * scale, scale, phase)
            LoadingBar(84f * scale, 20f * scale, scale, phase, marginTop = 13f * scale)
            LoadingBar(featureWidth * 0.64f, 28f * scale, scale, phase, marginTop = 6f * scale)
            LoadingBar(featureWidth - 30f * scale, 46f * scale, scale, phase, marginTop = 13f * scale)
        }
        View {
            attr {
                absolutePosition(top = 11f * scale, right = 0f)
                width(sideWidth)
                height(125f * scale)
                padding(12f * scale)
                borderRadius(19f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            LoadingBar(48f * scale, 10f * scale, scale, phase)
            LoadingBar(sideWidth * 0.8f, 16f * scale, scale, phase, marginTop = 10f * scale)
            LoadingBar(50f * scale, 20f * scale, scale, phase, marginTop = 12f * scale)
        }
        LoadingBar(contentWidth * 0.49f, 50f * scale, scale, phase, marginTop = 193f * scale, marginLeft = 3f * scale, radius = 16f * scale)
        LoadingBar(contentWidth * 0.63f, 50f * scale, scale, phase, marginTop = 249f * scale, marginLeft = contentWidth * 0.24f, radius = 16f * scale)
    }
}

private fun ViewContainer<*, *>.LoadingBar(
    width: Float,
    height: Float,
    scale: Float,
    phase: () -> Int,
    marginTop: Float = 0f,
    marginLeft: Float = 0f,
    radius: Float = height / 2f,
) {
    View {
        attr {
            size(width, height)
            borderRadius(radius)
            backgroundColor(StockChatTheme.surfaceSoft)
            if (marginTop > 0f) marginTop(marginTop)
            if (marginLeft > 0f) marginLeft(marginLeft)
            val current = phase()
            opacity(if (current % 2 == 0) 1f else 0.45f)
            animation(Animation.easeInOut(0.7f), current)
        }
    }
}
