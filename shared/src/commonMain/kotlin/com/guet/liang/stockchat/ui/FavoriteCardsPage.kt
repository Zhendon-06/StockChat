package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.FavoriteCardsStore
import com.guet.liang.stockchat.data.providerSymbolForQuote
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal const val FAVORITE_CARDS_PAGE_NAME = "favorite_cards"

@Page(FAVORITE_CARDS_PAGE_NAME, supportInLocal = true)
internal class FavoriteCardsPage : BasePager() {
    private var refreshRevision by observable(0)

    override fun created() {
        super.created()
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
                val favorites = FavoriteCardsStore.all()
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
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
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
                Text {
                    attr {
                        text("已收藏 ${favorites.size} 张行情卡片")
                        fontSize(13f)
                        color(StockChatTheme.textSecondary)
                        marginBottom(10f)
                    }
                }
                favorites.forEach { quote ->
                    View {
                        attr {
                            width(pagerData.pageViewWidth - 36f)
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
        val params = JSONObject()
        params.put("symbol", providerSymbolForQuote(quote) ?: quote.symbol)
        pageData.params.optString("qwenApiKey").trim()
            .takeIf(String::isNotBlank)
            ?.let { params.put("qwenApiKey", it) }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "stock_detail",
            params,
        )
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private companion object {
        const val HEADER_HEIGHT = 68f
    }
}
