package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketUiState
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场页入口与头部。

internal fun ViewContainer<*, *>.TodayMarketContent(
    state: () -> TodayMarketUiState,
    pageWidth: Float,
    scale: Float,
    safeAreaBottom: Float,
    touchEnabled: () -> Boolean = { true },
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
    onQuoteClick: (StockQuote) -> Unit,
    onSectorClick: (com.guet.liang.stockchat.model.TodayMarketSectorObservation) -> Unit = {},
    onRetry: () -> Unit,
    scrollerRef: ((com.tencent.kuikly.core.base.ViewRef<ScrollerView<*, *>>) -> Unit)? = null,
    onScroll: ((Float) -> Unit)? = null,
    restoreOffsetY: Float = 0f,
    skeletonPhase: () -> Int = { 0 },
) {
    var marketScroller: com.tencent.kuikly.core.base.ViewRef<ScrollerView<*, *>>? = null
    val bottomSwitcherHeight = 44f * scale
    val bottomSwitcherOffset = safeAreaBottom + 14f * scale
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(StockChatTheme.background)
            touchEnable(touchEnabled())
        }
        Scroller {
            if (scrollerRef != null) {
                ref {
                    marketScroller = it
                    scrollerRef.invoke(it)
                }
            }
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
            event {
                scroll { params -> onScroll?.invoke(params.offsetY) }
                contentSizeChanged { _, _ ->
                    if (restoreOffsetY > 0f) {
                        marketScroller?.view?.setContentOffset(0f, restoreOffsetY, false)
                    }
                }
            }
            TodayMarketHeader(state = state, scale = scale, onRetry = onRetry)
            vif({ state() is TodayMarketUiState.Loading }) { TodayMarketLoading(scale, pageWidth, skeletonPhase) }
            vif({ state() is TodayMarketUiState.Empty }) { TodayMarketEmpty(scale, onRetry) }
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
            attr { flex(1f) }
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
                    text("先看整体，再看具体")
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
                themedBorder()
                allCenter()
            }
            event { click { onRetry() } }
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
    TodayMarketSnapshotStatus(state, scale)
}

private fun ViewContainer<*, *>.TodayMarketSnapshotStatus(
    state: () -> TodayMarketUiState,
    scale: Float,
) {
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
                        if (snapshot.isDemo) StockChatTheme.warningSoft
                        else StockChatTheme.accentSoft
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
