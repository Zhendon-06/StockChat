package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.ArtifactController
import com.guet.liang.stockchat.controller.artifactController
import com.guet.liang.stockchat.controller.riskMapSnapshot

import com.guet.liang.stockchat.base.openRoute
import com.guet.liang.stockchat.base.stockDetailRouteParams
import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.closePage
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.RiskMapSnapshot
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal const val FAVORITE_CARDS_PAGE_NAME = "favorite_cards"

@Page(FAVORITE_CARDS_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class FavoriteCardsPage : BasePager() {
    private var refreshRevision by observable(0)
    private lateinit var artifactController: ArtifactController

    override fun created() {
        super.created()
        artifactController = artifactController()
        refreshRevision += 1
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        refreshRevision += 1
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(StockChatTheme.background) }
            ctx.PageHeader(this)
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = pagerData.safeAreaInsets.bottom,
                    )
                }
                ctx.refreshRevision
                val favorites = ctx.artifactController.favorites()
                vif({ favorites.isEmpty() }) {
                    ctx.EmptyState(this)
                }
                vif({ favorites.isNotEmpty() }) {
                    ctx.FavoriteList(this, favorites)
                }
            }
        }
    }

    private fun PageHeader(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    height(pagerData.statusBarHeight + HEADER_HEIGHT)
                    padding(top = pagerData.statusBarHeight + 12f, left = 18f, right = 18f)
                    backgroundColor(StockChatTheme.background)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(44f, 44f)
                        borderRadius(22f)
                        backgroundColor(StockChatTheme.surface)
                        themedBorder()
                        allCenter()
                    }
                    event { click { ctx.closePage() } }
                    Text {
                        attr {
                            text("‹")
                            fontSize(34f)
                            color(StockChatTheme.textPrimary)
                            marginBottom(3f)
                        }
                    }
                }
                Text {
                    attr {
                        text("收藏卡片")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginLeft(14f)
                        flex(1f)
                    }
                }
            }
        }
    }

    private fun FavoriteList(container: ViewContainer<*, *>, favorites: List<StockQuote>) {
        val ctx = this
        with(container) {
            Scroller {
                attr {
                    absolutePositionAllZero()
                    showScrollerIndicator(false)
                    padding(top = 12f, left = 18f, right = 18f, bottom = 28f)
                }
                val cardWidth = (ctx.pagerData.pageViewWidth - 36f).coerceAtLeast(1f)
                ctx.FavoriteOverview(this, favorites, cardWidth)
                ctx.FavoriteRiskSummary(this, riskMapSnapshot(), cardWidth)
                Text {
                    attr {
                        text("已收藏 ${favorites.size} 张行情卡片")
                        fontSize(13f)
                        color(StockChatTheme.textSecondary)
                        width(cardWidth)
                        marginBottom(10f)
                    }
                }
                favorites.forEach { quote ->
                    View {
                        attr {
                            width(cardWidth)
                            alignSelfCenter()
                            marginBottom(10f)
                        }
                        MarketQuoteCard(
                            quote = quote,
                            scale = 1f,
                            onClick = { ctx.openStockDetail(quote) },
                        )
                    }
                }
                RiskNotice()
            }
        }
    }

    /** Keeps portfolio exposure beside the cards that produce the snapshot. */
    private fun FavoriteRiskSummary(
        container: ViewContainer<*, *>,
        snapshot: RiskMapSnapshot,
        cardWidth: Float,
    ) {
        with(container) {
            View {
                attr {
                    width(cardWidth)
                    alignSelfCenter()
                    padding(top = 14f, left = 16f, bottom = 14f, right = 16f)
                    borderRadius(18f)
                    backgroundColor(StockChatTheme.accentSoft)
                    themedBorder()
                    marginBottom(14f)
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text {
                        attr {
                            text("组合风险概览")
                            fontSize(16f)
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                            flex(1f)
                        }
                    }
                    Text {
                        attr {
                            text(snapshot.concentrationLabel)
                            fontSize(11f)
                            color(StockChatTheme.accent)
                        }
                    }
                }
                Text {
                    attr {
                        text(snapshot.headline)
                        fontSize(12f)
                        lineHeight(18f)
                        color(StockChatTheme.textSecondary)
                        marginTop(7f)
                    }
                }
                Text {
                    attr {
                        text(
                            "上涨 ${snapshot.rising} · 下跌 ${snapshot.falling} · 持平 ${snapshot.unchanged}" +
                                (snapshot.largestMove?.let { " · 最大波动 ${it.name} ${it.changePercent}" } ?: "")
                        )
                        fontSize(11f)
                        color(StockChatTheme.textTertiary)
                        marginTop(8f)
                    }
                }
                Text {
                    attr {
                        text("基于已收藏快照，仅用于解释暴露，不构成买卖建议。")
                        fontSize(10f)
                        color(StockChatTheme.textTertiary)
                        marginTop(7f)
                    }
                }
            }
        }
    }

    private fun FavoriteOverview(
        container: ViewContainer<*, *>,
        favorites: List<StockQuote>,
        cardWidth: Float,
    ) {
        val page = this
        val rising = favorites.count { it.isPositive }
        val falling = favorites.size - rising
        with(container) {
            View {
                attr {
                    width(cardWidth)
                    alignSelfCenter()
                    padding(top = 16f, left = 16f, bottom = 15f, right = 16f)
                    borderRadius(18f)
                    backgroundColor(StockChatTheme.surface)
                    themedBorder()
                    marginBottom(14f)
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text {
                        attr {
                            text("自选行情")
                            fontSize(16f)
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                            flex(1f)
                        }
                    }
                    page.SectionBadge(this, "实时快照")
                }
                Text {
                    attr {
                        text("收藏标的的价格、涨跌和 AI 观察集中展示")
                        fontSize(12f)
                        color(StockChatTheme.textSecondary)
                        marginTop(5f)
                    }
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        marginTop(14f)
                        padding(top = 12f, bottom = 12f)
                        borderRadius(12f)
                        backgroundColor(StockChatTheme.surfaceSoft)
                    }
                    page.FavoriteMetric(this, "收藏", "${favorites.size}", StockChatTheme.textPrimary)
                    page.MetricDivider(this)
                    page.FavoriteMetric(this, "上涨", "$rising", StockChatTheme.positive)
                    page.MetricDivider(this)
                    page.FavoriteMetric(this, "下跌", "$falling", StockChatTheme.negative)
                }
            }
        }
    }

    private fun FavoriteMetric(
        container: ViewContainer<*, *>,
        label: String,
        value: String,
        tint: com.tencent.kuikly.core.base.Color,
    ) {
        with(container) {
            View {
                attr { flex(1f); alignItemsCenter() }
                Text {
                    attr {
                        text(value)
                        fontSize(20f)
                        fontWeightBold()
                        color(tint)
                    }
                }
                Text {
                    attr {
                        text(label)
                        fontSize(11f)
                        color(StockChatTheme.textTertiary)
                        marginTop(3f)
                    }
                }
            }
        }
    }

    /** Thin vertical divider between the overview metrics. */
    private fun MetricDivider(container: ViewContainer<*, *>) {
        with(container) {
            View {
                attr {
                    width(1f)
                    height(30f)
                    backgroundColor(StockChatTheme.border)
                }
            }
        }
    }

    /** Accent-tinted pill shared by the overview and risk section headers. */
    private fun SectionBadge(container: ViewContainer<*, *>, label: String) {
        with(container) {
            View {
                attr {
                    padding(top = 4f, left = 10f, bottom = 4f, right = 10f)
                    borderRadius(10f)
                    backgroundColor(StockChatTheme.accentSoft)
                }
                Text {
                    attr {
                        text(label)
                        fontSize(11f)
                        fontWeightMedium()
                        color(StockChatTheme.accent)
                    }
                }
            }
        }
    }

    private fun EmptyState(container: ViewContainer<*, *>) {
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    padding(left = 32f, right = 32f)
                }
                Text {
                    attr {
                        text("还没有收藏卡片")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("在行情详情页点击星星，即可收藏行情卡片。")
                        fontSize(14f)
                        color(StockChatTheme.textSecondary)
                        marginTop(8f)
                        textAlignCenter()
                    }
                }
            }
        }
    }

    private fun openStockDetail(quote: StockQuote) {
        openRoute(
            STOCK_DETAIL_PAGE_NAME,
            stockDetailRouteParams(
                artifactController.providerSymbol(quote),
                pageData.params.optString("qwenApiKey"),
                preview = quote,
                aiProxyBaseUrl = pageData.params.optString("aiProxyBaseUrl"),
            ),
        )
    }

    private companion object {
        const val HEADER_HEIGHT = 68f
    }
}
