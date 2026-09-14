package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场采用非对称 Bento 构图：主卡、辅卡和底板各司其职，内容之间不互相遮挡。

internal fun ViewContainer<*, *>.TodayMarketStackedContent(
    snapshot: TodayMarketSnapshot,
    pageWidth: Float,
    scale: Float,
    interactions: TodayMarketInteractions,
) {
    val contentWidth = marketContentWidth(pageWidth, scale)
    MarketPulseBento(snapshot, contentWidth, scale)
    View {
        attr {
            width(contentWidth)
        }
        vbind({ interactions.indexFocus() }) {
            MarketIndexBento(snapshot.indices, contentWidth, scale, interactions.onQuoteClick, interactions.indexFocus(), interactions.indexRevealPhase, interactions.onAdvanceIndexFocus)
        }
    }
    View {
        attr {
            width(contentWidth)
        }
        vbind({ interactions.sectorFocus() }) {
            MarketSectorBento(snapshot.sectors, contentWidth, scale, interactions.onSectorClick, interactions.sectorFocus(), interactions.sectorRevealPhase, interactions.onAdvanceSectorFocus)
        }
    }
    View {
        attr {
            width(contentWidth)
        }
        vbind({ interactions.quoteFocus() }) {
            MarketWatchBento(snapshot.sampleStocks, contentWidth, scale, interactions.onQuoteClick, interactions.quoteFocus(), interactions.quoteRevealPhase, interactions.onAdvanceQuoteFocus)
        }
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
