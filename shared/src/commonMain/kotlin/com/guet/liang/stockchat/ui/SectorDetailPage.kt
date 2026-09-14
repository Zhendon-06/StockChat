package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.closePage
import com.guet.liang.stockchat.base.openRoute
import com.guet.liang.stockchat.base.stockDetailRouteParams
import com.guet.liang.stockchat.controller.artifactController
import com.guet.liang.stockchat.data.toStockQuoteOrNull
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 板块二级页：从今日市场「板块观察」点入，展示样本均值与成员个股；成员可继续点进个股详情。

internal const val SECTOR_NAME_PARAM = "sectorName"
internal const val SECTOR_CHANGE_PARAM = "sectorChange"
internal const val SECTOR_POSITIVE_PARAM = "sectorPositive"
internal const val SECTOR_MEMBERS_PARAM = "sectorMembers"

@Page(SECTOR_DETAIL_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class SectorDetailPage : BasePager() {

    private var sectorName by observable("")
    private var sectorChange by observable("")
    private var sectorPositive by observable(false)
    private var members: List<StockQuote> = emptyList()

    override fun created() {
        super.created()
        applySavedAppearance()
        sectorName = pageData.params.optString(SECTOR_NAME_PARAM).ifBlank { "板块详情" }
        sectorChange = pageData.params.optString(SECTOR_CHANGE_PARAM)
        sectorPositive = pageData.params.optBoolean(SECTOR_POSITIVE_PARAM, false)
        val decoded = mutableListOf<StockQuote>()
        val array = pageData.params.optJSONArray(SECTOR_MEMBERS_PARAM)
        if (array != null) {
            repeat(array.length()) { index ->
                array.optJSONObject(index)?.toStockQuoteOrNull()?.let(decoded::add)
            }
        }
        members = decoded
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        applySavedAppearance()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(StockChatTheme.background) }
            ctx.SectorHeader(this)
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = pagerData.safeAreaInsets.bottom,
                    )
                }
                ctx.SectorBody(this)
            }
        }
    }

    private fun SectorHeader(container: ViewContainer<*, *>) {
        val ctx = this
        val tint = if (sectorPositive) StockChatTheme.positive else StockChatTheme.negative
        val softTint = if (sectorPositive) StockChatTheme.marketPositiveSoft else StockChatTheme.marketNegativeSoft
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
                        text(ctx.sectorName)
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginLeft(14f)
                        flex(1f)
                        lines(1)
                    }
                }
                View {
                    attr {
                        height(30f)
                        borderRadius(15f)
                        padding(left = 12f, right = 12f)
                        backgroundColor(softTint)
                        allCenter()
                    }
                    Text {
                        attr {
                            text(ctx.sectorChange.ifBlank { "--" })
                            fontSize(13f)
                            fontWeightBold()
                            color(tint)
                        }
                    }
                }
            }
        }
    }

    private fun SectorBody(container: ViewContainer<*, *>) {
        val ctx = this
        val tint = if (sectorPositive) StockChatTheme.positive else StockChatTheme.negative
        val contentWidth = (pagerData.pageViewWidth - 36f).coerceAtLeast(1f)
        with(container) {
            Scroller {
                attr {
                    absolutePositionAllZero()
                    showScrollerIndicator(false)
                    padding(top = 14f, left = 18f, right = 18f, bottom = 28f)
                }
                Text {
                    attr {
                        width(contentWidth)
                        text("样本均值变动")
                        fontSize(11f)
                        color(StockChatTheme.textTertiary)
                    }
                }
                Text {
                    attr {
                        width(contentWidth)
                        text(ctx.sectorChange.ifBlank { "--" })
                        fontSize(36f)
                        fontWeightBold()
                        color(tint)
                        marginTop(6f)
                    }
                }
                Text {
                    attr {
                        width(contentWidth)
                        text(
                            "${ctx.members.size} 只样本 · 按当日涨跌幅排序\n" +
                                (ctx.members.firstOrNull()?.updatedAt ?: "")
                        )
                        fontSize(11f)
                        lineHeight(17f)
                        color(StockChatTheme.textTertiary)
                        marginTop(8f)
                    }
                }
                View {
                    attr {
                        width(contentWidth)
                        height(1f)
                        backgroundColor(StockChatTheme.border)
                        marginTop(16f)
                    }
                }
                ctx.SectorMemberList(this, contentWidth)
                Text {
                    attr {
                        width(contentWidth)
                        text("板块为固定样本股聚合观察，不是全市场板块排名；行情与结论仅供参考，不构成投资建议。")
                        fontSize(10f)
                        lineHeight(16f)
                        color(StockChatTheme.textTertiary)
                        marginTop(16f)
                    }
                }
            }
        }
    }

    private fun SectorMemberList(container: ViewContainer<*, *>, contentWidth: Float) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width(contentWidth)
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(16f)
                    marginBottom(4f)
                }
                View {
                    attr {
                        width(4f)
                        height(15f)
                        borderRadius(2f)
                        backgroundColor(StockChatTheme.accent)
                        marginRight(8f)
                    }
                }
                Text {
                    attr {
                        text("成员表现")
                        fontSize(16f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text("点击个股查看详情")
                        fontSize(11f)
                        color(StockChatTheme.textTertiary)
                    }
                }
            }
            ctx.members.forEachIndexed { index, quote ->
                ctx.SectorMemberRow(this, quote, contentWidth)
                if (index != ctx.members.lastIndex) {
                    View {
                        attr {
                            width(contentWidth)
                            height(1f)
                            backgroundColor(StockChatTheme.border)
                        }
                    }
                }
            }
        }
    }

    private fun SectorMemberRow(container: ViewContainer<*, *>, quote: StockQuote, contentWidth: Float) {
        val ctx = this
        val tint = if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative
        val softTint = if (quote.isPositive) StockChatTheme.marketPositiveSoft else StockChatTheme.marketNegativeSoft
        with(container) {
            View {
                attr {
                    width(contentWidth)
                    flexDirectionRow()
                    alignItemsCenter()
                    padding(top = 12f, bottom = 12f)
                }
                event { click { ctx.openMemberDetail(quote) } }
                View {
                    attr { flex(1f) }
                    Text {
                        attr {
                            text(quote.name)
                            fontSize(14f)
                            fontWeightMedium()
                            color(StockChatTheme.textPrimary)
                            lines(1)
                        }
                    }
                    Text {
                        attr {
                            text("${quote.marketLabel} · ${quote.symbol}")
                            fontSize(10f)
                            color(StockChatTheme.textTertiary)
                            marginTop(3f)
                            lines(1)
                        }
                    }
                }
                Text {
                    attr {
                        text(quote.price)
                        fontSize(14f)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                        marginRight(10f)
                    }
                }
                View {
                    attr {
                        width(66f)
                        height(26f)
                        borderRadius(13f)
                        backgroundColor(softTint)
                        allCenter()
                    }
                    Text {
                        attr {
                            text(quote.changePercent)
                            fontSize(12f)
                            fontWeightBold()
                            color(tint)
                        }
                    }
                }
            }
        }
    }

    private fun openMemberDetail(quote: StockQuote) {
        openRoute(
            STOCK_DETAIL_PAGE_NAME,
            stockDetailRouteParams(
                artifactController().providerSymbol(quote),
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
