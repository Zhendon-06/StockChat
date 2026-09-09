package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.ChartEvidenceResolver
import com.guet.liang.stockchat.data.ChartEvidenceResolution
import com.guet.liang.stockchat.data.MarketPeriod
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.StockPrediction
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 详情页联动解读卡：选中点、解读文案与卡片渲染。

internal data class SelectedChartPoint(
    val label: String,
    val price: String,
    val value: Float,
    val index: Int,
)

internal fun StockDetailPage.LinkedInsightCard(
    container: ViewContainer<*, *>,
    quote: StockQuote,
    prediction: StockPrediction?,
) {
    val ctx = this
    with(container) {
    View {
        attr {
            width(pagerData.pageViewWidth - 36f)
            alignSelfCenter()
            marginTop(14f)
            padding(top = 17f, left = 16f, bottom = 17f, right = 16f)
            borderRadius(20f)
            backgroundColor(StockChatTheme.accentSoft)
            border(Border(1f, BorderStyle.SOLID, Color(0xFFC8EBDD)))
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
                    backgroundColor(StockChatTheme.accent)
                    marginRight(9f)
                }
            }
            Text {
                attr {
                    text("AI 联动解读")
                    fontSize(scaledFontSize(16f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
        }
        Text {
            attr {
                val selectedPoint = ctx.selectedChartPoint(quote)
                text(
                    if (selectedPoint == null) {
                        "点击走势图选中节点，AI 会把该点位与整体行情放在一起解释。"
                    } else {
                        "已选 ${selectedPoint.label} · ${selectedPoint.price}"
                    }
                )
                fontSize(scaledFontSize(12f))
                color(StockChatTheme.textSecondary)
                marginTop(7f)
            }
        }
        View {
            attr {
                flexDirectionRow()
                marginTop(12f)
            }
            DetailInsightFocus.values().forEach { focus ->
                View {
                    attr {
                        height(30f)
                        borderRadius(15f)
                        padding(left = 11f, right = 11f)
                        marginRight(if (focus == DetailInsightFocus.RISK) 0f else 7f)
                        backgroundColor(
                            if (ctx.insightFocus == focus) {
                                StockChatTheme.accent
                            } else {
                                StockChatTheme.surface
                            }
                        )
                        allCenter()
                    }
                    event {
                        click { ctx.insightFocus = focus }
                    }
                    Text {
                        attr {
                            text(focus.label)
                            fontSize(scaledFontSize(12f))
                            fontWeightMedium()
                            color(
                                if (ctx.insightFocus == focus) {
                                    Color.WHITE
                                } else {
                                    StockChatTheme.textSecondary
                                }
                            )
                        }
                    }
                }
            }
        }
        Text {
            attr {
                text(
                    ctx.linkedInsightText(
                        quote,
                        prediction,
                        ctx.selectedChartPoint(quote),
                    )
                )
                fontSize(scaledFontSize(14f))
                lineHeight(scaledFontSize(22f))
                color(StockChatTheme.textSecondary)
                marginTop(11f)
            }
        }
        prediction?.conclusions?.forEach { conclusion ->
            val history = ctx.predictionHistory().map {
                FinancialPoint(it.timestamp, it.close, it.close, it.close, it.close)
            }
            val resolution = ChartEvidenceResolver.resolve(conclusion.reference, quote.symbol,
                MarketPeriod.DAY, quote.updatedAt, history)
            ChartConclusionCard(conclusion, resolution) {
                // Revalidate at click time; the market panel validates again against its OHLC series.
                val currentHistory = ctx.predictionHistory().map {
                    FinancialPoint(it.timestamp, it.close, it.close, it.close, it.close)
                }
                val current = ChartEvidenceResolver.resolve(conclusion.reference, quote.symbol,
                    MarketPeriod.DAY, quote.updatedAt, currentHistory)
                if (current is ChartEvidenceResolution.Valid) {
                    ctx.pendingMarketEvidence = conclusion.reference
                    ctx.selectedDetailTab = DetailTab.MARKET
                    ctx.detailScroller?.view?.setContentOffset(0f, 0f, false)
                } else {
                    ctx.bridgeModule.toast((current as ChartEvidenceResolution.Invalid).reason)
                }
            }
        }
        if (prediction != null && prediction.conclusions.isEmpty()) {
            Text { attr { text("模型未提供结构化依据，暂无可定位区间。"); fontSize(11f); color(StockChatTheme.textTertiary); marginTop(8f) } }
        }
        View {
            attr {
                height(36f)
                borderRadius(18f)
                padding(left = 14f, right = 14f)
                marginTop(13f)
                backgroundColor(StockChatTheme.surface)
                border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                allCenter()
                alignSelfFlexStart()
            }
            event { click { ctx.openChatWithStock(quote, ctx.selectedChartPoint(quote)) } }
            Text {
                attr {
                    text("围绕此标的继续追问 AI  ›")
                    fontSize(scaledFontSize(12f))
                    fontWeightMedium()
                    color(StockChatTheme.accent)
                }
            }
        }
    }
    }
}

internal fun StockDetailPage.selectedChartPoint(quote: StockQuote): SelectedChartPoint? {
    val index = selectedChartPointIndex
    val points = chartPoints(quote)
    if (index !in points.indices) {
        return null
    }
    val historyCount = predictionPlot().forecastStart ?: predictionPlot().points.size
    val label = if (chartShowingPrediction && index >= historyCount) {
        "模型估计 ${predictionPlot().points[index].label}"
    } else {
        "历史收盘 ${predictionPlot().points[index].label}"
    }
    return SelectedChartPoint(
        label = label,
        price = financialNumber(points[index]),
        value = points[index],
        index = index,
    )
}

internal fun StockDetailPage.linkedInsightText(
    quote: StockQuote,
    prediction: StockPrediction?,
    selectedPoint: SelectedChartPoint?,
): String {
    return when (insightFocus) {
        DetailInsightFocus.TREND -> {
            if (selectedPoint == null) {
                "当前行情${if (quote.isPositive) "偏强" else "偏弱"}，${quote.change}（${quote.changePercent}）。先点击走势图中的节点，查看该位置相对近期高低点的变化，再结合成交量、基本面和消息面验证。"
            } else {
                val points = chartPoints(quote)
                val minimum = points.minOrNull() ?: selectedPoint.value
                val maximum = points.maxOrNull() ?: selectedPoint.value
                val range = (maximum - minimum).takeIf { it > 0f } ?: 1f
                val position = when {
                    selectedPoint.value >= minimum + range * 0.66f -> "高位"
                    selectedPoint.value <= minimum + range * 0.34f -> "低位"
                    else -> "中部"
                }
                val neighbor = points.getOrNull(selectedPoint.index - 1)
                val movement = when {
                    neighbor == null -> "位于走势起点"
                    selectedPoint.value > neighbor -> "较前一点上行"
                    selectedPoint.value < neighbor -> "较前一点回落"
                    else -> "与前一点基本持平"
                }
                "选中${selectedPoint.label}，价格约 ${selectedPoint.price}，处于近段走势${position}，${movement}。结合当前涨跌${quote.changePercent}，这个节点更适合用来观察趋势是否延续，不宜只凭单点下结论。"
            }
        }
        DetailInsightFocus.PREDICTION -> prediction?.rationale
            ?: "点击走势图右上角“AI 预测”，模型会先读取真实历史行情；只有成功返回并通过校验后，才会显示虚线预测区间。"
        DetailInsightFocus.RISK -> {
            val base = quote.summary.ifBlank { quote.aiInsight }
            "$base\n\n风险提醒：这是基于当前快照的演示解读，价格和结论会随数据更新；请同时核对估值、公告和自身风险承受能力。"
        }
    }
}
