package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketUiState
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
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
    onQuoteClick: (StockQuote) -> Unit,
    onRetry: () -> Unit,
    scrollerRef: ((com.tencent.kuikly.core.base.ViewRef<ScrollerView<*, *>>) -> Unit)? = null,
    onScroll: ((Float) -> Unit)? = null,
    restoreOffsetY: Float = 0f,
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
                scroll { params ->
                    onScroll?.invoke(params.offsetY)
                }
                contentSizeChanged { _, _ ->
                    if (restoreOffsetY > 0f) {
                        marketScroller?.view?.setContentOffset(0f, restoreOffsetY, false)
                    }
                }
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
