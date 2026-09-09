package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.FinancialChartView
import com.guet.liang.stockchat.controller.MarketPanelController
import com.guet.liang.stockchat.controller.marketPanelController
import com.guet.liang.stockchat.model.CapitalFlowResult
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.stockchat.model.MarketChartData
import com.guet.liang.stockchat.model.MarketChartResult
import com.guet.liang.stockchat.model.MarketPeriod
import com.guet.liang.stockchat.model.TencentMarketSnapshot
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Individual-security market terminal; all view and state logic stays in commonMain. */
internal class StockMarketPanel : ComposeView<ComposeAttr, ComposeEvent>() {
    lateinit var snapshot: TencentMarketSnapshot
    var initialEvidence: ChartEvidenceReference? = null
    var onEvidenceLocated: ((Float) -> Unit)? = null
    var onGestureActiveChanged: ((Boolean) -> Unit)? = null
    internal var chartTop: (() -> Float)? = null
    internal var pendingEvidence: ChartEvidenceReference? = null
    internal var selectedIndex by observable<Int?>(null)
    internal var evidenceStatus by observable("")
    internal var evidenceActive by observable(false)
    internal var period by observable(MarketPeriod.INTRADAY)
    internal var loadedPeriod by observable<MarketPeriod?>(null)
    internal var chartResult by observable<MarketChartResult?>(null)
    internal var capitalResult by observable<CapitalFlowResult?>(null)
    internal var section by observable("盘口")
    internal var expandedMetrics by observable(false)
    internal var comparisonPrices by observable<MarketChartData?>(null)
    internal var destroyed = false
    internal lateinit var controller: MarketPanelController
    internal var chartView: FinancialChartView? = null

    override fun createAttr(): ComposeAttr = ComposeAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()
    override fun created() {
        super.created()
        controller = marketPanelController(
            acquireModule<NetworkModule>(NetworkModule.MODULE_NAME), snapshot,
            onChartChanged = { requested, result ->
                chartResult = result
                loadedPeriod = if (result == null) null else requested
            },
            onCapitalChanged = { result, prices ->
                capitalResult = result
                comparisonPrices = prices
            },
        )
        if (controller.isIndex) section = "概览"
        pendingEvidence = initialEvidence
        if (pendingEvidence != null) {
            period = MarketPeriod.DAY
            evidenceStatus = "正在加载日 K 并校验结论引用…"
        }
        loadChart()
    }
    override fun viewDestroyed() {
        destroyed = true
        controller.dispose()
        chartView = null
        super.viewDestroyed()
    }

    internal fun loadChart() {
        loadedPeriod = null
        chartView = null
        selectedIndex = null
        evidenceActive = false
        controller.loadChart(period)
    }

    internal fun showCapitalDemo() = controller.showCapitalDemo()

    internal fun loadCapital() = controller.loadCapital()

    override fun body(): ViewBuilder {
        val owner = this
        return {
            attr { flexDirectionColumn() }
            owner.QuoteHeader(this)
            owner.ChartPanel(this)
            MarketPeriod.entries.filter { !it.isIntraday }.forEach { requested ->
                vif({ owner.period == requested && owner.loadedPeriod == requested && owner.chartResult is MarketChartResult.Content }) {
                    owner.MarketInsight(this)
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    marginTop(16f)
                    marginBottom(10f)
                }
                (if (owner.controller.isIndex) listOf("概览", "简况") else listOf("盘口", "简况")).forEach { tab ->
                    View {
                        attr {
                            flex(1f)
                            alignItemsCenter()
                            padding(9f)
                            borderRadius(8f)
                            backgroundColor(if (owner.section == tab) StockChatTheme.surface else Color.TRANSPARENT)
                        }
                        event { click { owner.section = tab } }
                        Text {
                            attr {
                                text(tab)
                                fontSize(15f)
                                fontWeightMedium()
                                color(if (owner.section == tab) StockChatTheme.textPrimary else StockChatTheme.textTertiary)
                            }
                        }
                    }
                }
            }
            vif({ owner.section == "盘口" || owner.section == "概览" }) {
                if (owner.controller.isIndex) owner.IndexOverview(this) else owner.OrderBook(this)
            }
            vif({ owner.section == "简况" }) {
                if (owner.controller.isIndex) owner.IndexProfile(this) else owner.Overview(this)
            }
            Text {
                attr {
                    text("演示行情 · 腾讯证券快照，非逐笔实时推送\n${owner.snapshot.quote.updatedAt.removePrefix("腾讯行情 · ")}")
                    fontSize(10f)
                    lineHeight(16f)
                    color(StockChatTheme.textTertiary)
                    marginTop(12f)
                }
            }

        }
    }

}

internal fun ViewContainer<*, *>.StockMarket(
    snapshot: TencentMarketSnapshot,
    initialEvidence: ChartEvidenceReference? = null,
    onGestureActiveChanged: ((Boolean) -> Unit)? = null,
    onEvidenceLocated: ((Float) -> Unit)? = null,
) {
    addChild(StockMarketPanel()) {
        this.snapshot = snapshot
        this.initialEvidence = initialEvidence
        this.onGestureActiveChanged = onGestureActiveChanged
        this.onEvidenceLocated = onEvidenceLocated
    }
}
