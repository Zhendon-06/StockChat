package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.data.TencentMarketSnapshot
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 详情页行情区：内容骨架、页签切换与行情展示。

internal fun StockDetailPage.DetailContent(container: ViewContainer<*, *>, snapshot: TencentMarketSnapshot) {
    val ctx = this
    val quote = snapshot.quote
    with(container) {
        Scroller {
        ref { ctx.detailScroller = it }
        attr {
            absolutePositionAllZero()
            showScrollerIndicator(false)
            scrollEnable(!ctx.chartGestureActive)
            padding(
                top = 8f,
                left = 18f,
                right = 18f,
                bottom = pagerData.safeAreaInsets.bottom + 28f,
            )
        }
        vif({ ctx.selectedDetailTab == DetailTab.MARKET }) {
            ctx.MarketDisplayContent(this, snapshot)
        }
        vif({ ctx.selectedDetailTab == DetailTab.PREDICTION }) {
            ctx.AiPredictionContent(this, quote)
        }
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                alignSelfCenter()
                marginTop(14f)
                padding(top = 13f, left = 14f, bottom = 13f, right = 14f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.warningSoft)
                border(Border(1f, BorderStyle.SOLID, StockChatTheme.warningBorder))
                flexDirectionRow()
                alignItemsFlexStart()
            }
            Text {
                attr {
                    text("!")
                    fontSize(13f)
                    fontWeightBold()
                    color(StockChatTheme.warning)
                    marginRight(9f)
                }
            }
            Text {
                attr {
                    text("StockChat Demo 信息，仅供参考，不构成投资建议。")
                    fontSize(scaledFontSize(12f))
                    lineHeight(scaledFontSize(18f))
                    color(StockChatTheme.warning)
                    flex(1f)
                }
            }
        }
    }
    }
}

internal fun StockDetailPage.DetailTabSwitcher(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                flex(1f)
                height(40f)
                marginLeft(10f)
                padding(all = 3f)
                borderRadius(20f)
                backgroundColor(StockChatTheme.recessed)
                border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                flexDirectionRow()
            }
            View {
                attr {
                    flex(1f)
                    height(34f)
                    borderRadius(17f)
                    backgroundColor(StockChatTheme.surface)
                    boxShadow(
                        BoxShadow(
                            0f,
                            2f,
                            8f,
                            Color(0x1F000000),
                        )
                    )
                    transform(
                        Translate(
                            if (ctx.selectedDetailTab == DetailTab.PREDICTION) 1f else 0f,
                        )
                    )
                    animate(
                        Animation.springEaseOut(0.34f, 0.9f, 0.12f),
                        ctx.selectedDetailTab,
                    )
                    touchEnable(false)
                }
            }
            View {
                attr {
                    flex(1f)
                    height(34f)
                    touchEnable(false)
                }
            }
            View {
                attr {
                    absolutePosition(top = 3f, left = 3f, right = 3f, bottom = 3f)
                    flexDirectionRow()
                    alignItemsCenter()
                    zIndex(1)
                }
                DetailTab.values().forEach { tab ->
                    View {
                        attr {
                            flex(1f)
                            height(34f)
                            alignItemsCenter()
                            justifyContentCenter()
                        }
                        event {
                            click {
                                if (tab == DetailTab.MARKET) {
                                    ctx.selectedChartPointIndex = -1
                                }
                                if (ctx.selectedDetailTab != tab) ctx.detailScroller?.view?.setContentOffset(0f, 0f, false)
                                ctx.selectedDetailTab = tab
                            }
                        }
                        Text {
                            attr {
                                text(tab.label)
                                fontSize(scaledFontSize(13f))
                                if (ctx.selectedDetailTab == tab) {
                                    fontWeightBold()
                                }
                                color(
                                    if (ctx.selectedDetailTab == tab) {
                                        StockChatTheme.textPrimary
                                    } else {
                                        StockChatTheme.textSecondary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun StockDetailPage.MarketDisplayContent(
    container: ViewContainer<*, *>,
    snapshot: TencentMarketSnapshot,
) {
    val ctx = this
    val reference = pendingMarketEvidence
    pendingMarketEvidence = null
    with(container) {
        StockMarket(snapshot, reference, onGestureActiveChanged = { ctx.chartGestureActive = it }) { chartTop ->
            ctx.detailScroller?.view?.setContentOffset(0f, chartTop, false)
        }
    }
}
