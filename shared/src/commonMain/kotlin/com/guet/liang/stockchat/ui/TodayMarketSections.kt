package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.guet.liang.stockchat.model.TodayMarketSectorObservation
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场内容分区：快照、情绪卡、观察/样例股票与摘要。

/** 分区标题：主题绿小竖条 + 加粗标题 + 右侧说明，替代卡片边框带来的分隔感。 */
internal fun TodayMarketSectionTitle(
    container: ViewContainer<*, *>,
    title: String,
    subtitle: String,
    scale: Float,
    onAction: (() -> Unit)? = null,
) {
    with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(20f * scale)
                marginBottom(10f * scale)
            }
            View {
                attr {
                    width(4f * scale)
                    height(16f * scale)
                    borderRadius(2f * scale)
                    backgroundColor(StockChatTheme.accent)
                    marginRight(8f * scale)
                }
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
            if (subtitle.isNotBlank()) {
                Text {
                    attr {
                        text(if (onAction == null) subtitle else "$subtitle  ·  换一组 ›")
                        fontSize(11f * scale)
                        color(if (onAction == null) StockChatTheme.textTertiary else StockChatTheme.accent)
                    }
                    event { click { onAction?.invoke() } }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.TodayMarketSnapshotContent(
    snapshot: TodayMarketSnapshot,
    pageWidth: Float,
    scale: Float,
    onQuoteClick: (StockQuote) -> Unit,
    onSectorClick: (TodayMarketSectorObservation) -> Unit = {},
    indexFocus: () -> Int = { 0 },
    indexPulse: () -> Boolean = { false },
    indexRevealPhase: () -> Int = { 0 },
    onAdvanceIndexFocus: () -> Unit = {},
    sectorFocus: () -> Int = { 0 },
    sectorPulse: () -> Boolean = { false },
    sectorRevealPhase: () -> Int = { 0 },
    onAdvanceSectorFocus: () -> Unit = {},
    quoteFocus: () -> Int = { 0 },
    quotePulse: () -> Boolean = { false },
    quoteRevealPhase: () -> Int = { 0 },
    onAdvanceQuoteFocus: () -> Unit = {},
) {
    TodayMarketStackedContent(
        snapshot = snapshot,
        pageWidth = pageWidth,
        scale = scale,
        interactions = TodayMarketInteractions(
            onQuoteClick = onQuoteClick,
            onSectorClick = onSectorClick,
            indexFocus = indexFocus,
            indexPulse = indexPulse,
            indexRevealPhase = indexRevealPhase,
            onAdvanceIndexFocus = onAdvanceIndexFocus,
            sectorFocus = sectorFocus,
            sectorPulse = sectorPulse,
            sectorRevealPhase = sectorRevealPhase,
            onAdvanceSectorFocus = onAdvanceSectorFocus,
            quoteFocus = quoteFocus,
            quotePulse = quotePulse,
            quoteRevealPhase = quoteRevealPhase,
            onAdvanceQuoteFocus = onAdvanceQuoteFocus,
        ),
    )
}

@Suppress("LongMethod")
private fun ViewContainer<*, *>.TodayMarketMoodCard(snapshot: TodayMarketSnapshot, scale: Float) {
    View {
        attr {
            padding(
                top = 18f * scale,
                left = 17f * scale,
                bottom = 18f * scale,
                right = 17f * scale,
            )
            borderRadius(18f * scale)
            backgroundLinearGradient(
                Direction.TO_BOTTOM_RIGHT,
                com.tencent.kuikly.core.base.ColorStop(
                    StockChatTheme.marketMoodBackgroundStart,
                    0f,
                ),
                com.tencent.kuikly.core.base.ColorStop(StockChatTheme.marketMoodBackgroundEnd, 1f),
            )
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
                text(if (snapshot.advancingCount >= snapshot.decliningCount) "市场热度偏强，关注结构性机会" else "市场分化明显，优先控制回撤")
                fontSize(20f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
                marginTop(14f * scale)
            }
        }
        View {
            attr {
                flexDirectionRow()
                marginTop(10f * scale)
            }
            MoodChip("情绪周期 · ${snapshot.mood}", StockChatTheme.positive, scale)
            MoodChip("指数样本 · ${snapshot.indices.size}", StockChatTheme.accent, scale)
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

private fun ViewContainer<*, *>.MoodChip(textValue: String, tint: com.tencent.kuikly.core.base.Color, scale: Float) {
    View {
        attr {
            height(25f * scale)
            borderRadius(13f * scale)
            padding(left = 10f * scale, right = 10f * scale)
            backgroundColor(StockChatTheme.surface)
            marginRight(8f * scale)
            allCenter()
        }
        Text {
            attr {
                text(textValue)
                fontSize(11f * scale)
                color(tint)
            }
        }
    }
}

private fun ViewContainer<*, *>.TodayMarketSectorStrip(
    sectors: List<TodayMarketSectorObservation>,
    pageWidth: Float,
    scale: Float,
    onSectorClick: (TodayMarketSectorObservation) -> Unit,
) {
    if (sectors.isEmpty()) return
    val visible = sectors.take(6)
    TodayMarketSectionTitle(this, "板块观察", "样本均值 · 点击查看", scale)
    View {
        attr {
            width((pageWidth - 36f * scale).coerceAtLeast(1f))
        }
        visible.forEachIndexed { index, sector ->
            val tint = if (sector.isPositive) StockChatTheme.positive else StockChatTheme.negative
            val softTint = if (sector.isPositive) StockChatTheme.marketPositiveSoft else StockChatTheme.marketNegativeSoft
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    padding(top = 11f * scale, bottom = 11f * scale)
                }
                event { click { onSectorClick(sector) } }
                View {
                    attr {
                        size(22f * scale, 22f * scale)
                        borderRadius(7f * scale)
                        backgroundColor(softTint)
                        allCenter()
                        marginRight(10f * scale)
                    }
                    Text {
                        attr {
                            text("${index + 1}")
                            fontSize(11f * scale)
                            fontWeightBold()
                            color(tint)
                        }
                    }
                }
                View {
                    attr { flex(1f) }
                    Text {
                        attr {
                            text(sector.name)
                            fontSize(14f * scale)
                            fontWeightMedium()
                            color(StockChatTheme.textPrimary)
                            lines(1)
                        }
                    }
                    Text {
                        attr {
                            text(sector.members)
                            fontSize(10f * scale)
                            color(StockChatTheme.textTertiary)
                            marginTop(3f * scale)
                            lines(1)
                        }
                    }
                }
                Text {
                    attr {
                        text(sector.changeLabel)
                        fontSize(15f * scale)
                        fontWeightBold()
                        color(tint)
                    }
                }
                Text {
                    attr {
                        text("›")
                        fontSize(17f * scale)
                        color(StockChatTheme.textTertiary)
                        marginLeft(8f * scale)
                    }
                }
            }
            if (index != visible.lastIndex) {
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
