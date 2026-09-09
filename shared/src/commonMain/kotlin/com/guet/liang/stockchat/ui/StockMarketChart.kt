package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.FinancialChart
import com.guet.liang.kuiklychart.finance.FinancialChartMode
import com.guet.liang.stockchat.model.MarketChartData
import com.guet.liang.stockchat.model.MarketChartResult
import com.guet.liang.stockchat.model.MarketPeriod
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.ChartPanel(container: ViewContainer<*, *>) {
    val owner = this
    with(container) {
        View {
            owner.chartTop = { frame.y }
            attr {
                marginTop(12f)
                padding(12f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            owner.PeriodTabs(this)
            vif({ owner.chartResult == null }) { owner.Status(this, "正在加载${owner.period.label}行情…") }
            vif({ owner.chartResult is MarketChartResult.Error }) {
                owner.Status(this, (owner.chartResult as? MarketChartResult.Error)?.message.orEmpty()) { owner.loadChart() }
            }
            // One branch per period ensures a fresh chart viewport when switching cached series.
            MarketPeriod.entries.forEach { requested ->
                vif({
                        owner.period == requested && owner.loadedPeriod == requested && owner.chartResult is MarketChartResult.Content
                }) {
                    val data = (owner.chartResult as? MarketChartResult.Content)?.data
                    if (data == null || data.points.isEmpty()) {
                        owner.Status(this, "暂无${requested.label}数据 · 点击重新加载") { owner.loadChart() }
                    } else {
                        owner.ChartContent(this, requested, data)
                    }
                }
            }
        }
    }
}

internal fun StockMarketPanel.PeriodTabs(container: ViewContainer<*, *>) {
    val owner = this
    with(container) {
        View {
            attr {
                flexDirectionRow()
                justifyContentSpaceBetween()
                marginBottom(8f)
            }
            MarketPeriod.entries.forEach { item ->
                View {
                    attr {
                        flex(1f)
                        height(32f)
                        marginRight(3f)
                        borderRadius(16f)
                        justifyContentCenter()
                        alignItemsCenter()
                        backgroundColor(if (owner.period == item) StockChatTheme.textPrimary else StockChatTheme.surfaceSoft)
                    }
                    event {
                        click {
                            if (owner.period != item) {
                                owner.pendingEvidence = null
                                owner.evidenceStatus = ""
                                owner.period = item
                                owner.loadChart()
                            }
                        }
                    }
                    Text {
                        attr {
                            text(item.label)
                            fontSize(13f)
                            fontWeightMedium()
                            color(if (owner.period == item) StockChatTheme.surface else StockChatTheme.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

internal fun StockMarketPanel.ChartContent(container: ViewContainer<*, *>, requested: MarketPeriod, data: MarketChartData) {
    val owner = this
    with(container) {

        owner.ChartToolbar(this, requested, data)
        owner.ChartCanvas(this, requested, data)
        vif({ owner.evidenceStatus.isNotBlank() }) {
            Text {
                attr {
                    text(owner.evidenceStatus)
                    fontSize(11f)
                    lineHeight(17f)
                    color(StockChatTheme.accent)
                    marginTop(6f)
                }
            }
        }
        vif({ owner.evidenceActive }) {
            View {
                attr {
                    minHeight(40f)
                    justifyContentCenter()
                }
                event { click { owner.chartView?.clearEvidence() } }
                Text {
                    attr {
                        text("清除区间高亮")
                        fontSize(12f)
                        color(StockChatTheme.accent)
                    }
                }
            }
        }
        Text {
            attr {
                text(owner.chartGestureDescription(requested))
                fontSize(10f)
                color(StockChatTheme.textTertiary)
                marginTop(5f)
            }
        }

    }
}

internal fun StockMarketPanel.ChartToolbar(container: ViewContainer<*, *>, requested: MarketPeriod, data: MarketChartData) {
    val owner = this
    with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                height(30f)
            }
            Text {
                attr {
                    text(owner.chartDescription(requested, data))
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                    flex(1f)
                }
            }
            if (!requested.isIntraday) {
                owner.Control(this, "−") { owner.chartView?.zoom(0.75f) }
                owner.Control(this, "+") { owner.chartView?.zoom(1.4f) }
                owner.Control(this, "复位") { owner.chartView?.resetViewport() }
            }
        }
    }
}

internal fun StockMarketPanel.ChartCanvas(container: ViewContainer<*, *>, requested: MarketPeriod, data: MarketChartData) {
    val owner = this
    with(container) {
        FinancialChart {
            owner.chartView = this
            attr { height(422f) }
            onEvidenceCleared = {
                owner.evidenceActive = false
                owner.evidenceStatus = ""
            }
            onSelectionChanged = { owner.selectedIndex = it }
            onGestureActiveChanged = owner.onGestureActiveChanged
            chart {
                points = data.points
                valueLabel = if (owner.controller.isIndex) "点位" else "价格"
                intradayDescription = if (owner.controller.isIndex) "指数点位 · 涨跌幅相对昨收" else "价格走势 · 均价暂无数据"
                mode = if (requested.isIntraday) FinancialChartMode.INTRADAY else FinancialChartMode.CANDLES
                previousClose = data.previousClose
                sessionSlots = data.sessionSlots
                if (data.labels.isNotEmpty()) sessionLabels = data.labels
                volumeUnit = owner.snapshot.volumeUnit
                backgroundColor = StockChatTheme.surface
                textColor = StockChatTheme.textPrimary
                mutedColor = StockChatTheme.textTertiary
                gridColor = StockChatTheme.border
                evidenceColor = StockChatTheme.accent
                riseColor = StockChatTheme.positive
                fallColor = StockChatTheme.negative
            }
            owner.pendingEvidence?.let { reference ->
                owner.pendingEvidence = null
                owner.locateEvidence(reference)
            }
        }
    }
}

private fun StockMarketPanel.chartDescription(period: MarketPeriod, data: MarketChartData): String {
    if (!period.isIntraday) return "${data.adjustment} · ${data.points.size}根历史K线"
    val value = if (controller.isIndex) "指数点位" else "价格 / 均价"
    val interval = if (period == MarketPeriod.FIVE_DAYS) "近五个交易日" else "当日分时"
    return "$value · $interval"
}

private fun StockMarketPanel.chartGestureDescription(period: MarketPeriod): String = when {
    !period.isIntraday -> "点选后左右滑动查看开高低收 · 双指缩放 · 横拖查看历史"
    controller.isIndex -> "点选后左右滑动查看指数点位及分钟成交量"
    else -> "点选后左右滑动查看价格、均价及分钟成交量"
}
