package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.MarketPeriod
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 详情页骨架屏：进入页面的首帧就摆出行情区的版式，卡片里已知的名称、价格、走势直接显示，
// 需要网络请求的指标、图表、盘口用呼吸的占位条表示。用户点击后立刻得到反馈，而不是面对一个省略号。

private const val SKELETON_DIM_OPACITY = 0.45f
private const val SKELETON_PULSE_SECONDS = 0.7f

internal fun StockDetailPage.DetailSkeleton(container: ViewContainer<*, *>, preview: StockQuote?) {
    val ctx = this
    val contentWidth = pagerData.pageViewWidth - 36f
    with(container) {
        Scroller {
            attr {
                absolutePositionAllZero()
                showScrollerIndicator(false)
                scrollEnable(false)
                padding(top = 8f, left = 18f, right = 18f, bottom = pagerData.safeAreaInsets.bottom + 28f)
            }
            ctx.SkeletonQuoteCard(this, preview)
            ctx.SkeletonChartCard(this, preview, contentWidth - 24f)
            View {
                attr {
                    flexDirectionRow()
                    marginTop(16f)
                    marginBottom(10f)
                }
                repeat(2) { index ->
                    View {
                        attr {
                            flex(1f)
                            alignItemsCenter()
                            padding(9f)
                            borderRadius(8f)
                            backgroundColor(if (index == 0) StockChatTheme.surface else StockChatTheme.background)
                        }
                        ctx.SkeletonBar(this, width = 44f, height = 14f)
                    }
                }
            }
            ctx.SkeletonOrderBookCard(this)
            Text {
                attr {
                    text(if (preview == null) "正在加载行情…" else "已显示卡片上的快照，正在加载完整行情…")
                    fontSize(scaledFontSize(11f))
                    color(StockChatTheme.textTertiary)
                    marginTop(14f)
                    alignSelfCenter()
                }
            }
        }
    }
}

private fun StockDetailPage.SkeletonQuoteCard(container: ViewContainer<*, *>, preview: StockQuote?) {
    val ctx = this
    with(container) {
        View {
            attr {
                padding(16f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            if (preview == null) {
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentSpaceBetween()
                    }
                    ctx.SkeletonBar(this, width = 132f, height = 22f)
                    ctx.SkeletonBar(this, width = 64f, height = 12f)
                }
                ctx.SkeletonBar(this, width = 96f, height = 10f, marginTop = 8f)
                ctx.SkeletonBar(this, width = 150f, height = 38f, marginTop = 10f)
            } else {
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            text(preview.name)
                            fontSize(21f)
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                            flex(1f)
                        }
                    }
                    Text {
                        attr {
                            text(preview.symbol.uppercase())
                            fontSize(12f)
                            color(StockChatTheme.textSecondary)
                        }
                    }
                }
                Text {
                    attr {
                        text(preview.marketLabel.replace(" · 腾讯行情", "") + " · 行情快照")
                        fontSize(10f)
                        color(StockChatTheme.textTertiary)
                        marginTop(4f)
                    }
                }
                val tone = if (preview.isPositive) StockChatTheme.positive else StockChatTheme.negative
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsFlexEnd()
                        marginTop(9f)
                    }
                    Text {
                        attr {
                            text(preview.price)
                            fontSize(38f)
                            fontWeightBold()
                            color(tone)
                        }
                    }
                    View {
                        attr {
                            marginLeft(14f)
                            marginBottom(5f)
                        }
                        Text {
                            attr {
                                text(preview.change)
                                fontSize(15f)
                                fontWeightMedium()
                                color(tone)
                            }
                        }
                        Text {
                            attr {
                                text(preview.changePercent)
                                fontSize(15f)
                                fontWeightMedium()
                                color(tone)
                            }
                        }
                    }
                }
            }
            // 今开 / 最高 / 最低 … 六个指标的占位：两行三列，与 QuoteHeader.metricRows 版式一致
            repeat(2) {
                View {
                    attr {
                        flexDirectionRow()
                        marginTop(11f)
                    }
                    repeat(3) {
                        View {
                            attr { flex(1f) }
                            ctx.SkeletonBar(this, width = 34f, height = 10f)
                            ctx.SkeletonBar(this, width = 58f, height = 12f, marginTop = 5f)
                        }
                    }
                }
            }
        }
    }
}

private fun StockDetailPage.SkeletonChartCard(container: ViewContainer<*, *>, preview: StockQuote?, innerWidth: Float) {
    val ctx = this
    with(container) {
        View {
            attr {
                marginTop(12f)
                padding(12f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            View {
                attr {
                    flexDirectionRow()
                    justifyContentSpaceBetween()
                    marginBottom(8f)
                }
                MarketPeriod.entries.forEachIndexed { index, _ ->
                    View {
                        attr {
                            flex(1f)
                            height(32f)
                            marginRight(3f)
                            borderRadius(16f)
                            backgroundColor(if (index == 0) StockChatTheme.textPrimary else StockChatTheme.surfaceSoft)
                            opacity(if (index == 0) 0.2f else 1f)
                        }
                    }
                }
            }
            View {
                attr {
                    height(200f)
                    borderRadius(12f)
                    backgroundColor(StockChatTheme.surfaceSoft)
                    justifyContentCenter()
                    alignItemsCenter()
                    opacity(ctx.skeletonOpacity())
                    animation(Animation.easeInOut(SKELETON_PULSE_SECONDS), ctx.skeletonPhase)
                }
                if (preview != null && preview.trendPoints.size > 1) {
                    // 先用卡片上的迷你走势占住图表位置，让用户感到是同一只标的在展开
                    TrendSparkline(preview, innerWidth * 0.86f, 120f)
                }
            }
        }
    }
}

private fun StockDetailPage.SkeletonOrderBookCard(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                padding(14f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            ctx.SkeletonBar(this, width = 72f, height = 16f)
            View {
                attr {
                    flexDirectionRow()
                    marginTop(12f)
                }
                repeat(2) { side ->
                    View {
                        attr {
                            flex(1f)
                            if (side == 0) marginRight(10f)
                        }
                        repeat(5) {
                            View {
                                attr {
                                    flexDirectionRow()
                                    justifyContentSpaceBetween()
                                    marginTop(9f)
                                }
                                ctx.SkeletonBar(this, width = 30f, height = 11f)
                                ctx.SkeletonBar(this, width = 54f, height = 11f)
                                ctx.SkeletonBar(this, width = 40f, height = 11f)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun StockDetailPage.SkeletonBar(container: ViewContainer<*, *>, width: Float, height: Float, marginTop: Float = 0f) {
    val ctx = this
    with(container) {
        View {
            attr {
                size(width, height)
                borderRadius(height / 2f)
                backgroundColor(StockChatTheme.surfaceSoft)
                if (marginTop > 0f) marginTop(marginTop)
                opacity(ctx.skeletonOpacity())
                animation(Animation.easeInOut(SKELETON_PULSE_SECONDS), ctx.skeletonPhase)
            }
        }
    }
}

/** Two-step breathing: the phase flips on a timer while the page is still waiting for market data. */
internal fun StockDetailPage.skeletonOpacity(): Float = if (skeletonPhase % 2 == 0) 1f else SKELETON_DIM_OPACITY
