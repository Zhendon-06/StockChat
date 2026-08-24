package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.data.MockStockChatDataSource
import com.guet.liang.stockchat.data.StockChatDataSource
import com.guet.liang.stockchat.model.StockDetailResult
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private const val DETAIL_PAGE_NAME = "stock_detail"

private sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Content(val quote: StockQuote) : DetailUiState()
    data object Empty : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@Page(DETAIL_PAGE_NAME, supportInLocal = true)
internal class StockDetailPage : BasePager() {
    private var detailState by observable<DetailUiState>(DetailUiState.Loading)
    private var symbol = ""
    private val dataSource: StockChatDataSource = MockStockChatDataSource

    override fun created() {
        super.created()
        symbol = pageData.params.optString("symbol").trim().uppercase()
        loadDetail()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(StockChatTheme.background)
            }
            ctx.DetailHeader(this)
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + 68f,
                        left = 0f,
                        right = 0f,
                        bottom = 0f,
                    )
                }
                vif({ ctx.detailState is DetailUiState.Loading }) {
                    ctx.LoadingState(this)
                }
                vif({ ctx.detailState is DetailUiState.Empty }) {
                    ctx.EmptyState(this)
                }
                vif({ ctx.detailState is DetailUiState.Error }) {
                    ctx.ErrorState(this)
                }
                vif({ ctx.detailState is DetailUiState.Content }) {
                    val quote = (ctx.detailState as DetailUiState.Content).quote
                    ctx.DetailContent(this, quote)
                }
            }
        }
    }

    private fun DetailHeader(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
        View {
            attr {
                height(pagerData.statusBarHeight + 68f)
                padding(
                    top = pagerData.statusBarHeight + 12f,
                    left = 18f,
                    right = 18f,
                )
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
                event {
                    click {
                        ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                    }
                }
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
                    text("行情详情")
                    fontSize(18f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginLeft(14f)
                    flex(1f)
                }
            }
        }
        }
    }

    private fun LoadingState(container: ViewContainer<*, *>) {
        with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
            }
            View {
                attr {
                    size(42f, 42f)
                    borderRadius(21f)
                    backgroundColor(StockChatTheme.accentSoft)
                    allCenter()
                }
                Text {
                    attr {
                        text("…")
                        fontSize(22f)
                        color(StockChatTheme.accent)
                        marginBottom(8f)
                    }
                }
            }
            Text {
                attr {
                    text("正在加载行情")
                    fontSize(14f)
                    color(StockChatTheme.textSecondary)
                    marginTop(14f)
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
                    text("暂无该标的行情")
                    fontSize(19f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("暂未收录该股票或指数的行情信息。")
                    fontSize(14f)
                    color(StockChatTheme.textSecondary)
                    marginTop(8f)
                    textAlignCenter()
                }
            }
        }
        }
    }

    private fun ErrorState(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
                padding(left = 32f, right = 32f)
            }
            Text {
                attr {
                    text("行情加载失败")
                    fontSize(19f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text((ctx.detailState as? DetailUiState.Error)?.message ?: "请稍后重试")
                    fontSize(14f)
                    lineHeight(21f)
                    color(StockChatTheme.textSecondary)
                    marginTop(8f)
                    textAlignCenter()
                }
            }
            View {
                attr {
                    height(40f)
                    borderRadius(20f)
                    padding(left = 20f, right = 20f)
                    marginTop(20f)
                    backgroundColor(StockChatTheme.accent)
                    allCenter()
                }
                event {
                    click { ctx.loadDetail() }
                }
                Text {
                    attr {
                        text("重新加载")
                        fontSize(14f)
                        fontWeightMedium()
                        color(Color.WHITE)
                    }
                }
            }
        }
        }
    }

    private fun DetailContent(container: ViewContainer<*, *>, quote: StockQuote) {
        val ctx = this
        with(container) {
        Scroller {
            attr {
                absolutePositionAllZero()
                showScrollerIndicator(false)
                padding(
                    top = 8f,
                    left = 18f,
                    right = 18f,
                    bottom = pagerData.safeAreaInsets.bottom + 28f,
                )
            }
            View {
                attr {
                    padding(top = 20f, left = 18f, bottom = 18f, right = 18f)
                    borderRadius(22f)
                    backgroundColor(StockChatTheme.surface)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    View {
                        attr { flex(1f) }
                        Text {
                            attr {
                                text(quote.name)
                                fontSize(22f)
                                fontWeightBold()
                                color(StockChatTheme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("${quote.marketLabel} · ${quote.symbol}")
                                fontSize(13f)
                                color(StockChatTheme.textSecondary)
                                marginTop(4f)
                            }
                        }
                    }
                    View {
                        attr {
                            size(40f, 40f)
                            borderRadius(20f)
                            backgroundColor(StockChatTheme.accentSoft)
                            allCenter()
                        }
                        Text {
                            attr {
                                text("☆")
                                fontSize(23f)
                                color(StockChatTheme.accent)
                            }
                        }
                    }
                }
                Text {
                    attr {
                        text(quote.price)
                        fontSize(38f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginTop(22f)
                    }
                }
                Text {
                    attr {
                        text("${quote.change}   ${quote.changePercent}")
                        fontSize(16f)
                        fontWeightBold()
                        color(if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative)
                        marginTop(5f)
                    }
                }
                Text {
                    attr {
                        text(quote.updatedAt)
                        fontSize(11f)
                        color(StockChatTheme.textTertiary)
                        marginTop(8f)
                    }
                }
            }
            View {
                attr {
                    marginTop(14f)
                    padding(top = 18f, left = 16f, bottom = 14f, right = 16f)
                    borderRadius(22f)
                    backgroundColor(StockChatTheme.surface)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                }
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            text("走势")
                            fontSize(17f)
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                            flex(1f)
                        }
                    }
                    View {
                        attr {
                            height(28f)
                            borderRadius(14f)
                            padding(left = 10f, right = 10f)
                            backgroundColor(Color(0xFFF1F4F2))
                            allCenter()
                        }
                        Text {
                            attr {
                                text("分时")
                                fontSize(12f)
                                fontWeightMedium()
                                color(StockChatTheme.textPrimary)
                            }
                        }
                    }
                }
                ctx.LargeTrendChart(this, quote)
                View {
                    attr {
                        flexDirectionRow()
                        justifyContentSpaceBetween()
                        marginTop(7f)
                    }
                    Text {
                        attr {
                            text("09:30")
                            fontSize(10f)
                            color(StockChatTheme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text("11:30")
                            fontSize(10f)
                            color(StockChatTheme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text("15:00")
                            fontSize(10f)
                            color(StockChatTheme.textTertiary)
                        }
                    }
                }
            }
            ctx.InsightCard(this, "行情摘要", quote.summary, false)
            ctx.InsightCard(this, "AI 解读", quote.aiInsight, true)
            View {
                attr {
                    marginTop(14f)
                    padding(top = 13f, left = 14f, bottom = 13f, right = 14f)
                    borderRadius(16f)
                    backgroundColor(Color(0xFFFFF7EA))
                    border(Border(1f, BorderStyle.SOLID, Color(0xFFF2DEBA)))
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
                        text("以上信息仅供参考，不构成投资建议。")
                        fontSize(12f)
                        lineHeight(18f)
                        color(StockChatTheme.warning)
                        flex(1f)
                    }
                }
            }
        }
        }
    }

    private fun LargeTrendChart(container: ViewContainer<*, *>, quote: StockQuote) {
        with(container) {
        Canvas({
            attr {
                height(188f)
                marginTop(18f)
            }
        }) { context, width, height ->
            val gridColor = Color(0xFFE9EDEB)
            for (index in 1..3) {
                val y = height * index / 4f
                context.beginPath()
                context.moveTo(0f, y)
                context.lineTo(width, y)
                context.lineWidth(1f)
                context.strokeStyle(gridColor)
                context.stroke()
            }
            val points = quote.trendPoints
            if (points.size < 2) {
                return@Canvas
            }
            val min = points.minOrNull() ?: 0f
            val max = points.maxOrNull() ?: 1f
            val range = (max - min).takeIf { it > 0f } ?: 1f
            context.beginPath()
            points.forEachIndexed { index, value ->
                val x = index.toFloat() / (points.size - 1).toFloat() * width
                val normalized = (value - min) / range
                val y = 12f + (1f - normalized) * (height - 24f)
                if (index == 0) {
                    context.moveTo(x, y)
                } else {
                    context.lineTo(x, y)
                }
            }
            context.lineWidth(3f)
            context.lineCapRound()
            context.strokeStyle(if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative)
            context.stroke()
        }
        }
    }

    private fun InsightCard(
        container: ViewContainer<*, *>,
        title: String,
        content: String,
        highlighted: Boolean,
    ) {
        with(container) {
        View {
            attr {
                marginTop(14f)
                padding(top = 17f, left = 16f, bottom = 17f, right = 16f)
                borderRadius(20f)
                backgroundColor(if (highlighted) StockChatTheme.accentSoft else StockChatTheme.surface)
                border(
                    Border(
                        1f,
                        BorderStyle.SOLID,
                        if (highlighted) Color(0xFFC8EBDD) else StockChatTheme.border,
                    )
                )
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(8f, 8f)
                        borderRadius(4f)
                        backgroundColor(if (highlighted) StockChatTheme.accent else StockChatTheme.textTertiary)
                        marginRight(9f)
                    }
                }
                Text {
                    attr {
                        text(title)
                        fontSize(16f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
            Text {
                attr {
                    text(content)
                    fontSize(14f)
                    lineHeight(22f)
                    color(StockChatTheme.textSecondary)
                    marginTop(11f)
                }
            }
        }
        }
    }

    private fun loadDetail() {
        detailState = DetailUiState.Loading
        setTimeout(420) {
            detailState = when (val result = dataSource.stockDetail(symbol)) {
                is StockDetailResult.Success -> DetailUiState.Content(result.quote)
                StockDetailResult.Empty -> DetailUiState.Empty
                is StockDetailResult.Failure -> DetailUiState.Error(result.message)
            }
        }
    }
}
