package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.CapitalFlowPoint

import com.guet.liang.kuiklychart.ui.BarChart
import com.guet.liang.kuiklychart.api.ChartLegendPosition
import com.guet.liang.kuiklychart.finance.DualAxisChart
import com.guet.liang.kuiklychart.finance.DualAxisPoint
import com.guet.liang.kuiklychart.finance.financialVolume
import com.guet.liang.stockchat.model.CapitalFlowResult
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.CapitalFlow(container: ViewContainer<*, *>) {
    val owner = this
    with(container) {
        vif({ owner.capitalResult == null }) { owner.Status(this, "正在加载资金流向…") }
        vif({ owner.capitalResult is CapitalFlowResult.Error }) {
            owner.Status(this, (owner.capitalResult as? CapitalFlowResult.Error)?.message.orEmpty()) { owner.loadCapital() }
            owner.Control(this, "查看资金演示样例（非实时）") {
                owner.showCapitalDemo()
            }
        }
        vif({ owner.capitalResult is CapitalFlowResult.Content }) {
            val content = owner.capitalResult as? CapitalFlowResult.Content
            val points = content?.points.orEmpty()
            val isDemo = content?.isDemo == true
            if (points.isEmpty()) {
                owner.Status(this, "暂无资金流向 · 点击重试") { owner.loadCapital() }
                owner.Control(this, "查看资金演示样例（非实时）") {
                    owner.showCapitalDemo()
                }
            } else {
                owner.CapitalContent(this, points, isDemo)
            }
        }
    }
}

internal fun StockMarketPanel.CapitalContent(container: ViewContainer<*, *>, points: List<CapitalFlowPoint>, isDemo: Boolean) {
    val owner = this
    with(container) {
        val latest = points.last()
        if (isDemo) View {
            attr {
                padding(12f)
                marginBottom(10f)
                borderRadius(10f)
                backgroundColor(StockChatTheme.warningSoft)
            }
            Text {
                attr {
                    text("演示样例 · 万向德农 600371\n用于展示资金图形，不代表当前标的或实时行情。")
                    fontSize(12f)
                    lineHeight(19f)
                    color(StockChatTheme.warning)
                }
            }
            owner.Control(this, "返回真实资金数据") { owner.loadCapital() }
        }
        val distribution = if (isDemo) owner.controller.demoDistribution.mapIndexed { index, value ->
            MarketDistributionEntry(value.label, value.amount, financialVolume(value.amount) + "元",
                (if (value.inflow) StockChatTheme.positive else StockChatTheme.negative).opacity(if (index < 2) 1f else 0.5f))
        } else listOf("超大单" to latest.superLarge, "大单" to latest.large, "中单" to latest.medium, "小单" to latest.small).map {
            (label, value) ->
            MarketDistributionEntry(label + if (value >= 0) "净入" else "净出",
                kotlin.math.abs(value),
                signedAmount(value),
                flowColor(value))
        }
        MarketDistributionCard(
            if (isDemo) "主力 / 散户资金分布" else "订单资金净额分布",
            distribution,
            if (isDemo) "主力成交" else "净额分布",
            if (isDemo) "42.5% · 演示" else "点选查看占比",
            if (isDemo) "参考图中的流入/流出金额样例；点击扇区查看占比。" else "占比按各类净额的绝对值计算，不是买入/卖出成交占比。",
        )
        owner.CapitalSummary(this, latest, isDemo)
        owner.CapitalComparison(this, points)
        owner.CapitalTrend(this, points, isDemo)
    }
}

internal fun StockMarketPanel.CapitalSummary(container: ViewContainer<*, *>, latest: CapitalFlowPoint, isDemo: Boolean) {
    val owner = this
    with(container) {
        View {
            attr {
                padding(14f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("主力资金")
                    fontSize(13f)
                    color(StockChatTheme.textSecondary)
                }
            }
            Text {
                attr {
                    text(if (latest.main >= 0) "主力资金净流入" else "主力资金净流出")
                    fontSize(20f)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                    marginTop(8f)
                }
            }
            Text {
                attr {
                    text("${signedAmount(latest.main)}元")
                    fontSize(25f)
                    fontWeightBold()
                    color(flowColor(latest.main))
                    marginTop(7f)
                }
            }
            Text {
                attr {
                    text("${latest.date} · ${if (isDemo) "本地演示 · " else ""}主力 = 超大单 + 大单")
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                    marginTop(5f)
                }
            }
            val categories = listOf("超大单" to latest.superLarge, "大单" to latest.large, "中单" to latest.medium, "小单" to latest.small)
            categories.forEach { (label, value) ->
                View {
                    attr {
                        flexDirectionRow()
                        marginTop(12f)
                    }
                    Text {
                        attr {
                            text(label + if (value >= 0) "净流入" else "净流出")
                            fontSize(12f)
                            color(StockChatTheme.textSecondary)
                            flex(1f)
                        }
                    }
                    Text {
                        attr {
                            text(signedAmount(value) + "元")
                            fontSize(13f)
                            fontWeightMedium()
                            color(flowColor(value))
                        }
                    }
                }
            }
        }
    }
}

internal fun StockMarketPanel.CapitalComparison(container: ViewContainer<*, *>, points: List<CapitalFlowPoint>) {
    val owner = this
    with(container) {
        vif({ owner.comparisonPrices != null }) {
            val prices = owner.comparisonPrices?.points.orEmpty()
            val returns = prices.zipWithNext().associate { (previous, current) ->
                current.label to ((current.close / previous.close - 1f) * 100f)
            }
            val aligned = points.filter { returns.containsKey(it.date) }
            if (aligned.isNotEmpty()) View {
                attr {
                    padding(14f)
                    borderRadius(16f)
                    backgroundColor(StockChatTheme.surface)
                    marginTop(12f)
                }
                Text {
                    attr {
                        text("资金与股价对照")
                        fontSize(16f)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("按交易日对齐 · 左轴净额 / 右轴涨跌幅")
                        fontSize(10f)
                        color(StockChatTheme.textTertiary)
                        marginTop(6f)
                    }
                }
                DualAxisChart {
                    attr {
                        height(220f)
                        marginTop(14f)
                    }
                    chart {
                        this.points = aligned.map { DualAxisPoint(it.date, it.main, returns[it.date]) }
                        leftName = "主力净额"
                        backgroundColor = StockChatTheme.surface
                        mutedColor = StockChatTheme.textTertiary
                        gridColor = StockChatTheme.border
                    }
                }
                Text {
                    attr {
                        text("涨跌幅按相邻${owner.comparisonPrices?.adjustment.orEmpty()}收盘价计算；点选查看当日数值。")
                        fontSize(10f)
                        lineHeight(16f)
                        color(StockChatTheme.textTertiary)
                        marginTop(4f)
                    }
                }
            }
        }
    }
}

internal fun StockMarketPanel.CapitalTrend(container: ViewContainer<*, *>, points: List<CapitalFlowPoint>, isDemo: Boolean) {
    val owner = this
    with(container) {
        View {
            attr {
                padding(14f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
                marginTop(12f)
            }
            Text {
                attr {
                    text("近${points.size}个交易日资金趋势")
                    fontSize(16f)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("${points.count { it.main > 0 }}日净流入 · ${points.count { it.main < 0 }}日净流出")
                    fontSize(11f)
                    color(StockChatTheme.textTertiary)
                    marginTop(5f)
                }
            }
            owner.CapitalTotals(this, points)
            Text {
                attr {
                    text("红色 + 净流入    绿色 − 净流出 · 单位：元")
                    fontSize(10f)
                    color(StockChatTheme.textSecondary)
                }
            }
            owner.CapitalTrendChart(this, points)
            Text {
                attr {
                    text(if (isDemo) "本地演示资金数据 · 非实时，不用于判断当前标的。" else "资金来源：东方财富 · 与价格快照可能不同步。\n按成交单规模估算，净流入不代表未来涨跌。")
                    fontSize(10f)
                    lineHeight(16f)
                    color(StockChatTheme.textTertiary)
                    marginTop(6f)
                }
            }
        }
    }
}

internal fun StockMarketPanel.CapitalTotals(container: ViewContainer<*, *>, points: List<CapitalFlowPoint>) {
    val owner = this
    with(container) {
        View {
            attr {
                flexDirectionRow()
                marginTop(16f)
                marginBottom(12f)
            }
            listOf(3, 5, 10, 20).forEach { days ->
                val value = if (points.size >= days) points.takeLast(days).sumOf { it.main.toDouble() }.toFloat() else null
                View {
                    attr { flex(1f) }
                    Text {
                        attr {
                            text("${days}日净额")
                            fontSize(10f)
                            color(StockChatTheme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(value?.let(::signedAmount) ?: "--")
                            fontSize(10f)
                            fontWeightMedium()
                            color(value?.let(::flowColor) ?: StockChatTheme.textTertiary)
                            marginTop(6f)
                        }
                    }
                }
            }
        }
    }
}

internal fun StockMarketPanel.CapitalTrendChart(container: ViewContainer<*, *>, points: List<CapitalFlowPoint>) {
    val owner = this
    with(container) {
        BarChart {
            attr {
                height(222f)
                marginTop(8f)
            }
            chart {
                labels(points.map { it.date.substring(5) })
                bars("主力净额", points.map { it.main }) {
                    barColors(points.map { flowColor(it.main) })
                }
                legend { position = ChartLegendPosition.NONE }
                theme {
                    backgroundColor = StockChatTheme.surface
                    textColor = StockChatTheme.textPrimary
                    mutedTextColor = StockChatTheme.textTertiary
                    gridColor = StockChatTheme.border
                    labelFontSize = 10f
                }
                axes {
                    x { maxLabelCount = 4 }
                    y {
                        val extent = points.maxOf { kotlin.math.abs(it.main) }.coerceAtLeast(1f) * 1.12f
                        minimum = -extent
                        maximum = extent
                        formatter = {
                            financialVolume(it)
                        }
                    }
                }
            }
        }
    }
}
