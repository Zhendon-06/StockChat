package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.BarChart
import com.guet.liang.kuiklychart.api.ChartLegendPosition
import com.guet.liang.kuiklychart.finance.FinancialChart
import com.guet.liang.kuiklychart.finance.DualAxisChart
import com.guet.liang.kuiklychart.finance.DualAxisPoint
import com.guet.liang.kuiklychart.finance.FinancialChartMode
import com.guet.liang.kuiklychart.finance.FinancialChartView
import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.kuiklychart.finance.financialVolume
import com.guet.liang.stockchat.data.*
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Individual-security market terminal; all view and state logic stays in commonMain. */
internal class StockMarketPanel : ComposeView<ComposeAttr, ComposeEvent>() {
    lateinit var snapshot: TencentMarketSnapshot
    var initialEvidence: ChartEvidenceReference? = null
    var onEvidenceLocated: ((Float) -> Unit)? = null
    private var chartTop: (() -> Float)? = null
    private var pendingEvidence: ChartEvidenceReference? = null
    private var selectedIndex by observable<Int?>(null)
    private var evidenceStatus by observable("")
    private var evidenceActive by observable(false)
    private var period by observable(MarketPeriod.INTRADAY)
    private var loadedPeriod by observable<MarketPeriod?>(null)
    private var chartResult by observable<MarketChartResult?>(null)
    private var capitalResult by observable<CapitalFlowResult?>(null)
    private var section by observable("盘口")
    private var expandedMetrics by observable(false)
    private var comparisonPrices by observable<MarketChartData?>(null)
    private var requestGeneration = 0
    private var capitalGeneration = 0
    private var destroyed = false
    private val cache = mutableMapOf<MarketPeriod, MarketChartData>()
    private lateinit var source: StockMarketDetailDataSource
    private var chartView: FinancialChartView? = null

    override fun createAttr(): ComposeAttr = ComposeAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()
    override fun created() {
        super.created()
        source = RemoteStockMarketDetailDataSource(acquireModule<NetworkModule>(NetworkModule.MODULE_NAME))
        if (isMarketIndex(snapshot.providerSymbol)) section = "概览"
        pendingEvidence = initialEvidence
        if (pendingEvidence != null) {
            period = MarketPeriod.DAY
            evidenceStatus = "正在加载日 K 并校验结论引用…"
        }
        loadChart()
    }
    override fun viewDestroyed() {
        destroyed = true
        requestGeneration++
        chartView = null
        super.viewDestroyed()
    }

    private fun loadChart() {
        val generation = ++requestGeneration
        val requested = period
        loadedPeriod = null
        chartView = null
        selectedIndex = null
        evidenceActive = false
        val cached = cache[requested]
        if (cached != null) { chartResult = MarketChartResult.Content(cached); loadedPeriod = requested; return }
        chartResult = null
        source.loadChart(snapshot.providerSymbol, requested, snapshot.previousClose.toFloatOrNull()) { result ->
            if (destroyed || generation != requestGeneration) return@loadChart
            if (result is MarketChartResult.Content && result.data.points.isNotEmpty()) cache[requested] = result.data
            chartResult = result
            loadedPeriod = requested
        }
    }

    private fun showCapitalDemo() {
        capitalGeneration++
        comparisonPrices = null
        capitalResult = CapitalFlowResult.Content(CapitalFlowDemoData.points, isDemo = true)
    }

    private fun loadCapital() {
        val generation = ++capitalGeneration
        comparisonPrices = null
        capitalResult = null
        source.loadCapitalFlow(snapshot.providerSymbol) { result ->
            if (!destroyed && generation == capitalGeneration) {
                capitalResult = result
                if (result is CapitalFlowResult.Content && result.points.isNotEmpty()) {
                    source.loadChart(snapshot.providerSymbol, MarketPeriod.DAY, snapshot.previousClose.toFloatOrNull()) { prices ->
                        if (!destroyed && generation == capitalGeneration && prices is MarketChartResult.Content) comparisonPrices = prices.data
                    }
                }
            }
        }
    }

    override fun body(): ViewBuilder {
        val owner = this
        return {
            attr { flexDirectionColumn() }
            owner.QuoteHeader(this)
            View {
                owner.chartTop = { frame.y }
                attr { marginTop(12f); padding(12f); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
                View {
                    attr { flexDirectionRow(); justifyContentSpaceBetween(); marginBottom(8f) }
                    MarketPeriod.entries.forEach { item ->
                        View {
                            attr {
                                flex(1f); height(32f); marginRight(3f); borderRadius(16f); justifyContentCenter(); alignItemsCenter()
                                backgroundColor(if (owner.period == item) StockChatTheme.textPrimary else StockChatTheme.surfaceSoft)
                            }
                            event { click { if (owner.period != item) { owner.pendingEvidence = null; owner.evidenceStatus = ""; owner.period = item; owner.loadChart() } } }
                            Text { attr { text(item.label); fontSize(13f); fontWeightMedium(); color(if (owner.period == item) StockChatTheme.surface else StockChatTheme.textSecondary) } }
                        }
                    }
                }
                vif({ owner.chartResult == null }) { owner.Status(this, "正在加载${owner.period.label}行情…") }
                vif({ owner.chartResult is MarketChartResult.Error }) {
                    owner.Status(this, (owner.chartResult as? MarketChartResult.Error)?.message.orEmpty()) { owner.loadChart() }
                }
                // One branch per period ensures a fresh chart viewport when switching cached series.
                MarketPeriod.entries.forEach { requested ->
                    vif({ owner.period == requested && owner.loadedPeriod == requested && owner.chartResult is MarketChartResult.Content }) {
                        val data = (owner.chartResult as? MarketChartResult.Content)?.data
                        if (data == null || data.points.isEmpty()) {
                            owner.Status(this, "暂无${requested.label}数据 · 点击重新加载") { owner.loadChart() }
                        } else {
                            View {
                                attr { flexDirectionRow(); alignItemsCenter(); height(30f) }
                                Text {
                                    attr {
                                        text(if (requested.isIntraday) "${if (isMarketIndex(owner.snapshot.providerSymbol)) "指数点位" else "价格 / 均价"} · ${if (requested == MarketPeriod.FIVE_DAYS) "近五个交易日" else "当日分时"}" else "${data.adjustment} · ${data.points.size}根历史K线")
                                        fontSize(10f); color(StockChatTheme.textTertiary); flex(1f)
                                    }
                                }
                                if (!requested.isIntraday) {
                                    owner.Control(this, "−") { owner.chartView?.zoom(0.75f) }
                                    owner.Control(this, "+") { owner.chartView?.zoom(1.4f) }
                                    owner.Control(this, "复位") { owner.chartView?.resetViewport() }
                                }
                            }
                            FinancialChart {
                                owner.chartView = this
                                attr { height(422f) }
                                onEvidenceCleared = { owner.evidenceActive = false; owner.evidenceStatus = "" }
                                onSelectionChanged = { owner.selectedIndex = it }
                                chart {
                                    points = data.points
                                    valueLabel = if (isMarketIndex(owner.snapshot.providerSymbol)) "点位" else "价格"
                                    intradayDescription = if (isMarketIndex(owner.snapshot.providerSymbol)) "指数点位 · 涨跌幅相对昨收" else "价格走势 · 均价暂无数据"
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
                            vif({ owner.evidenceStatus.isNotBlank() }) {
                                Text { attr { text(owner.evidenceStatus); fontSize(11f); lineHeight(17f); color(StockChatTheme.accent); marginTop(6f) } }
                            }
                            vif({ owner.evidenceActive }) {
                                View {
                                    attr { minHeight(40f); justifyContentCenter() }
                                    event { click { owner.chartView?.clearEvidence() } }
                                    Text { attr { text("清除区间高亮"); fontSize(12f); color(StockChatTheme.accent) } }
                                }
                            }
                            Text { attr { text(if (requested.isIntraday) (if (isMarketIndex(owner.snapshot.providerSymbol)) "点选查看指数点位及分钟成交量" else "点选查看价格、均价及分钟成交量") else "点选查看开高低收 · 双指缩放 · 横拖查看历史"); fontSize(10f); color(StockChatTheme.textTertiary); marginTop(5f) } }
                        }
                    }
                }
            }
            MarketPeriod.entries.filter { !it.isIntraday }.forEach { requested ->
                vif({ owner.period == requested && owner.loadedPeriod == requested && owner.chartResult is MarketChartResult.Content }) {
                    owner.MarketInsight(this)
                }
            }
            View {
                attr { flexDirectionRow(); marginTop(16f); marginBottom(10f) }
                (if (isMarketIndex(owner.snapshot.providerSymbol)) listOf("概览", "简况") else listOf("盘口", "简况")).forEach { tab ->
                    View {
                        attr { flex(1f); alignItemsCenter(); padding(9f); borderRadius(8f); backgroundColor(if (owner.section == tab) StockChatTheme.surface else Color.TRANSPARENT) }
                        event { click { owner.section = tab } }
                        Text { attr { text(tab); fontSize(15f); fontWeightMedium(); color(if (owner.section == tab) StockChatTheme.textPrimary else StockChatTheme.textTertiary) } }
                    }
                }
            }
            vif({ owner.section == "盘口" || owner.section == "概览" }) {
                if (isMarketIndex(owner.snapshot.providerSymbol)) owner.IndexOverview(this) else owner.OrderBook(this)
            }
            vif({ owner.section == "简况" }) {
                if (isMarketIndex(owner.snapshot.providerSymbol)) owner.IndexProfile(this) else owner.Overview(this)
            }
            Text {
                attr {
                    text("演示行情 · 腾讯证券快照，非逐笔实时推送\n${owner.snapshot.quote.updatedAt.removePrefix("腾讯行情 · ")}")
                    fontSize(10f); lineHeight(16f); color(StockChatTheme.textTertiary); marginTop(12f)
                }
            }

        }
    }

    private fun locateEvidence(reference: ChartEvidenceReference) {
        val data = (chartResult as? MarketChartResult.Content)?.data ?: return
        when (val result = ChartEvidenceResolver.resolve(reference, snapshot.quote.symbol, period, snapshot.quote.updatedAt, data.points)) {
            is ChartEvidenceResolution.Invalid -> {
                chartView?.clearEvidence()
                evidenceActive = false
                evidenceStatus = result.reason
            }
            is ChartEvidenceResolution.Valid -> {
                if (chartView?.highlightRange(result.indices) == true) {
                    evidenceActive = true
                    evidenceStatus = "已高亮 ${result.label}"
                    val top = chartTop?.invoke() ?: 0f
                    if (top > 0f) {
                        onEvidenceLocated?.invoke(top + frame.y)
                    } else {
                        getPager().addTaskWhenPagerDidCalculateLayout {
                            if (!destroyed && evidenceActive) {
                                onEvidenceLocated?.invoke((chartTop?.invoke() ?: 0f) + frame.y)
                            }
                        }
                    }
                } else {
                    evidenceStatus = "图表尚未就绪，请重新查看对应区间"
                }
            }
        }
    }

    private fun MarketInsight(container: ViewContainer<*, *>) {
        val owner = this
        with(container) {
            View {
                attr { marginTop(12f) }
                Text { attr { text("AI 数据解读 · 本地演示"); fontSize(14f); fontWeightBold(); color(StockChatTheme.textPrimary) } }
                // A keyed loop rebuilds the card for point changes without resetting the chart viewport.
                vfor({ ObservableList(mutableListOf(owner.selectedIndex ?: -1)) }) { index ->
                    val data = (owner.chartResult as? MarketChartResult.Content)?.data
                    val conclusion = data?.let { marketDemoConclusion(owner.snapshot.quote.symbol, owner.period,
                        owner.snapshot.quote.updatedAt, it.points, index.takeIf { value -> value >= 0 }) }
                    if (conclusion != null) {
                        ChartConclusionCard(conclusion, ChartEvidenceResolver.resolve(conclusion.reference,
                            owner.snapshot.quote.symbol, owner.period, owner.snapshot.quote.updatedAt, data.points)) {
                            conclusion.reference?.let(owner::locateEvidence)
                        }
                    }
                }
                Text { attr { text("点选 K 线看解释，点解释查看依据。演示信息仅供参考，不构成投资建议。"); fontSize(10f); lineHeight(16f); color(StockChatTheme.textTertiary); marginTop(5f) } }
            }
        }
    }

    private fun QuoteHeader(container: ViewContainer<*, *>) {
        val snapshot = this.snapshot
        val owner = this
        val q = snapshot.quote
        with(container) {
            View {
                attr { padding(16f); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text(q.name); fontSize(21f); fontWeightBold(); color(StockChatTheme.textPrimary); flex(1f) } }
                    Text { attr { text(snapshot.providerSymbol.uppercase()); fontSize(12f); color(StockChatTheme.textSecondary) } }
                }
                Text { attr { text(q.marketLabel.replace(" · 腾讯行情", "") + " · 行情快照"); fontSize(10f); color(StockChatTheme.textTertiary); marginTop(4f) } }
                View {
                    attr { flexDirectionRow(); alignItemsFlexEnd(); marginTop(9f) }
                    Text { attr { text(q.price); fontSize(38f); fontWeightBold(); color(if (q.isPositive) StockChatTheme.positive else StockChatTheme.negative) } }
                    View {
                        attr { marginLeft(14f); marginBottom(5f) }
                        Text { attr { text(q.change); fontSize(15f); fontWeightMedium(); color(if (q.isPositive) StockChatTheme.positive else StockChatTheme.negative) } }
                        Text { attr { text(q.changePercent); fontSize(15f); fontWeightMedium(); color(if (q.isPositive) StockChatTheme.positive else StockChatTheme.negative) } }
                    }
                }
                val stats = listOf(
                    "今开" to snapshot.open, "最高" to snapshot.high, "最低" to snapshot.low,
                    "昨收" to snapshot.previousClose, "成交量" to quantity(snapshot.volume, snapshot.volumeUnit),
                    "成交额" to amount(snapshot.amount, snapshot.amountUnit),
                    "换手率" to percent(snapshot.turnoverRate), "振幅" to percent(snapshot.amplitude), "量比" to snapshot.volumeRatio,
                    "市盈率TTM" to snapshot.priceEarningsRatio, "总市值" to unit(snapshot.totalMarketValue, "亿"), "市净率" to snapshot.priceBookRatio,
                )
                fun rows(target: ViewContainer<*, *>, items: List<Pair<String, String>>) {
                    with(target) {
                        items.chunked(3).forEach { row ->
                            View {
                                attr { flexDirectionRow(); marginTop(11f) }
                                row.forEach { (label, value) ->
                                    View {
                                        attr { flex(1f) }
                                        Text { attr { text(label); fontSize(10f); color(StockChatTheme.textTertiary) } }
                                        Text { attr { text(value.ifBlank { "--" }); fontSize(12f); fontWeightMedium(); color(StockChatTheme.textPrimary); marginTop(3f) } }
                                    }
                                }
                            }
                        }
                    }
                }
                rows(this, stats.take(6))
                View {
                    attr { flexDirectionRow(); marginTop(13f) }
                    Text { attr { text("换手 ${percent(snapshot.turnoverRate)}  ·  量比 ${snapshot.volumeRatio.ifBlank { "--" }}"); fontSize(10f); color(StockChatTheme.textSecondary); flex(1f) } }
                    View {
                        event { click { owner.expandedMetrics = !owner.expandedMetrics } }
                        Text { attr { text(if (owner.expandedMetrics) "收起 ∧" else "更多指标 ∨"); fontSize(10f); color(StockChatTheme.textSecondary) } }
                    }
                }
                vif({ owner.expandedMetrics }) { rows(this, stats.drop(6)) }

            }
        }
    }

    private fun OrderBook(container: ViewContainer<*, *>) {
        val snapshot = this.snapshot
        val owner = this
        with(container) {
            View {
                attr { padding(14f); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
                Text { attr { text("五档盘口"); fontSize(16f); fontWeightMedium(); color(StockChatTheme.textPrimary) } }
                if (snapshot.orderBook.isEmpty()) {
                    Text { attr { text("该标的暂无五档报价，停牌或指数可能不提供盘口。"); fontSize(12f); color(StockChatTheme.textSecondary); marginTop(14f) } }
                } else {
                    View {
                        attr { flexDirectionRow(); marginTop(12f) }
                        listOf("买", "卖").forEach { side ->
                            View {
                                attr { flex(1f); if (side == "买") marginRight(14f) }
                                View {
                                    attr { flexDirectionRow(); marginBottom(7f) }
                                    Text { attr { text(if (side == "买") "买盘" else "卖盘"); fontSize(10f); color(StockChatTheme.textTertiary); flex(1f) } }
                                    Text { attr { text("价格 / ${owner.snapshot.volumeUnit}"); fontSize(10f); color(StockChatTheme.textTertiary) } }
                                }
                                val levels = owner.snapshot.orderBook.filter { it.side == side }
                                val max = levels.maxOfOrNull { it.volume }?.coerceAtLeast(1f) ?: 1f
                                (1..5).forEach { index ->
                                    val level = levels.firstOrNull { it.level == index }
                                    View {
                                        attr { height(31f); justifyContentCenter() }
                                        View {
                                            attr {
                                                absolutePosition(2f, 0f, 2f, 0f)
                                                backgroundColor((if (side == "买") StockChatTheme.positive else StockChatTheme.negative).opacity(0.04f + (level?.volume ?: 0f) / max * 0.10f))
                                            }
                                        }
                                        View {
                                            attr { flexDirectionRow(); alignItemsCenter(); padding(3f) }
                                            Text { attr { text("$side$index"); fontSize(10f); color(StockChatTheme.textSecondary); width(25f) } }
                                            Text {
                                                attr { text(level?.price?.let(::financialNumber) ?: "--"); fontSize(12f); fontWeightMedium(); color(owner.priceColor(level?.price)); flex(1f) }
                                            }
                                            Text { attr { text(level?.volume?.let(::financialVolume) ?: "--"); fontSize(10f); color(StockChatTheme.textPrimary) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val bids = snapshot.orderBook.filter { it.side == "买" }.sumOf { it.volume.toDouble() }
                    val asks = snapshot.orderBook.filter { it.side == "卖" }.sumOf { it.volume.toDouble() }
                    val imbalance = if (bids + asks > 0) ((bids - asks) / (bids + asks) * 100).toFloat() else null
                    Text { attr { text("五档委比  ${imbalance?.let { signed(it) + "%" } ?: "--"}  ·  买卖量仅统计当前五档"); fontSize(10f); color(StockChatTheme.textTertiary); marginTop(12f) } }
                }
            }
        }
    }

    private fun CapitalFlow(container: ViewContainer<*, *>) {
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
                    val latest = points.last()
                    if (isDemo) View {
                        attr { padding(12f); marginBottom(10f); borderRadius(10f); backgroundColor(StockChatTheme.warningSoft) }
                        Text { attr { text("演示样例 · 万向德农 600371\n用于展示资金图形，不代表当前标的或实时行情。"); fontSize(12f); lineHeight(19f); color(StockChatTheme.warning) } }
                        owner.Control(this, "返回真实资金数据") { owner.loadCapital() }
                    }
                    val distribution = if (isDemo) CapitalFlowDemoData.distribution.mapIndexed { index, value ->
                        MarketDistributionEntry(value.label, value.amount, financialVolume(value.amount) + "元",
                            (if (value.inflow) StockChatTheme.positive else StockChatTheme.negative).opacity(if (index < 2) 1f else 0.5f))
                    } else listOf("超大单" to latest.superLarge, "大单" to latest.large, "中单" to latest.medium, "小单" to latest.small).map { (label, value) ->
                        MarketDistributionEntry(label + if (value >= 0) "净入" else "净出", kotlin.math.abs(value), signedAmount(value), flowColor(value))
                    }
                    MarketDistributionCard(
                        if (isDemo) "主力 / 散户资金分布" else "订单资金净额分布",
                        distribution,
                        if (isDemo) "主力成交" else "净额分布",
                        if (isDemo) "42.5% · 演示" else "点选查看占比",
                        if (isDemo) "参考图中的流入/流出金额样例；点击扇区查看占比。" else "占比按各类净额的绝对值计算，不是买入/卖出成交占比。",
                    )
                    View {
                        attr { padding(14f); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
                        Text { attr { text("主力资金"); fontSize(13f); color(StockChatTheme.textSecondary) } }
                        Text { attr { text(if (latest.main >= 0) "主力资金净流入" else "主力资金净流出"); fontSize(20f); fontWeightMedium(); color(StockChatTheme.textPrimary); marginTop(8f) } }
                        Text { attr { text("${signedAmount(latest.main)}元"); fontSize(25f); fontWeightBold(); color(flowColor(latest.main)); marginTop(7f) } }
                        Text { attr { text("${latest.date} · ${if (isDemo) "本地演示 · " else ""}主力 = 超大单 + 大单"); fontSize(10f); color(StockChatTheme.textTertiary); marginTop(5f) } }
                        val categories = listOf("超大单" to latest.superLarge, "大单" to latest.large, "中单" to latest.medium, "小单" to latest.small)
                        categories.forEach { (label, value) ->
                            View {
                                attr { flexDirectionRow(); marginTop(12f) }
                                Text { attr { text(label + if (value >= 0) "净流入" else "净流出"); fontSize(12f); color(StockChatTheme.textSecondary); flex(1f) } }
                                Text { attr { text(signedAmount(value) + "元"); fontSize(13f); fontWeightMedium(); color(flowColor(value)) } }
                            }
                        }
                    }
                    vif({ owner.comparisonPrices != null }) {
                        val prices = owner.comparisonPrices?.points.orEmpty()
                        val returns = prices.zipWithNext().associate { (previous, current) ->
                            current.label to ((current.close / previous.close - 1f) * 100f)
                        }
                        val aligned = points.filter { returns.containsKey(it.date) }
                        if (aligned.isNotEmpty()) View {
                            attr { padding(14f); borderRadius(16f); backgroundColor(StockChatTheme.surface); marginTop(12f) }
                            Text { attr { text("资金与股价对照"); fontSize(16f); fontWeightMedium(); color(StockChatTheme.textPrimary) } }
                            Text { attr { text("按交易日对齐 · 左轴净额 / 右轴涨跌幅"); fontSize(10f); color(StockChatTheme.textTertiary); marginTop(6f) } }
                            DualAxisChart {
                                attr { height(220f); marginTop(14f) }
                                chart {
                                    this.points = aligned.map { DualAxisPoint(it.date, it.main, returns[it.date]) }
                                    leftName = "主力净额"
                                    backgroundColor = StockChatTheme.surface
                                    mutedColor = StockChatTheme.textTertiary
                                    gridColor = StockChatTheme.border
                                }
                            }
                            Text { attr { text("涨跌幅按相邻${owner.comparisonPrices?.adjustment.orEmpty()}收盘价计算；点选查看当日数值。"); fontSize(10f); lineHeight(16f); color(StockChatTheme.textTertiary); marginTop(4f) } }
                        }
                    }
                    View {
                        attr { padding(14f); borderRadius(16f); backgroundColor(StockChatTheme.surface); marginTop(12f) }
                        Text { attr { text("近${points.size}个交易日资金趋势"); fontSize(16f); fontWeightMedium(); color(StockChatTheme.textPrimary) } }
                        Text { attr { text("${points.count { it.main > 0 }}日净流入 · ${points.count { it.main < 0 }}日净流出"); fontSize(11f); color(StockChatTheme.textTertiary); marginTop(5f) } }
                        View {
                            attr { flexDirectionRow(); marginTop(16f); marginBottom(12f) }
                            listOf(3, 5, 10, 20).forEach { days ->
                                val value = if (points.size >= days) points.takeLast(days).sumOf { it.main.toDouble() }.toFloat() else null
                                View {
                                    attr { flex(1f) }
                                    Text { attr { text("${days}日净额"); fontSize(10f); color(StockChatTheme.textTertiary) } }
                                    Text { attr { text(value?.let(::signedAmount) ?: "--"); fontSize(10f); fontWeightMedium(); color(value?.let(::flowColor) ?: StockChatTheme.textTertiary); marginTop(6f) } }
                                }
                            }
                        }
                        Text { attr { text("红色 + 净流入    绿色 − 净流出 · 单位：元"); fontSize(10f); color(StockChatTheme.textSecondary) } }
                        BarChart {
                            attr { height(222f); marginTop(8f) }
                            chart {
                                labels(points.map { it.date.substring(5) })
                                bars("主力净额", points.map { it.main }) {
                                    barColors(points.map { flowColor(it.main) })
                                }
                                legend { position = ChartLegendPosition.NONE }
                                theme {
                                    backgroundColor = StockChatTheme.surface; textColor = StockChatTheme.textPrimary
                                    mutedTextColor = StockChatTheme.textTertiary; gridColor = StockChatTheme.border
                                    labelFontSize = 10f
                                }
                                axes {
                                    x { maxLabelCount = 4 }
                                    y {
                                        val extent = points.maxOf { kotlin.math.abs(it.main) }.coerceAtLeast(1f) * 1.12f
                                        minimum = -extent; maximum = extent; formatter = { financialVolume(it) }
                                    }
                                }
                            }
                        }
                        Text { attr { text(if (isDemo) "本地演示资金数据 · 非实时，不用于判断当前标的。" else "资金来源：东方财富 · 与价格快照可能不同步。\n按成交单规模估算，净流入不代表未来涨跌。"); fontSize(10f); lineHeight(16f); color(StockChatTheme.textTertiary); marginTop(6f) } }
                    }
                }
            }
        }
    }

    private fun Overview(container: ViewContainer<*, *>) {
        val snapshot = this.snapshot
        with(container) {
            View {
                attr { padding(16f); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
                Text { attr { text("行情摘要"); fontSize(16f); fontWeightMedium(); color(StockChatTheme.textPrimary) } }
                Text { attr { text(snapshot.quote.summary); fontSize(13f); lineHeight(22f); color(StockChatTheme.textSecondary); marginTop(10f) } }
                Text { attr { text("流通市值 ${unit(snapshot.floatMarketValue, "亿")}\n价格与成交量可用于观察活跃度；资金净额与短期涨跌可能背离，请结合公告、财务与估值分析。"); fontSize(12f); lineHeight(21f); color(StockChatTheme.textSecondary); marginTop(12f) } }
                Text { attr { text("可切换顶部「AI 预测」查看解读及风险依据。仅供参考，不构成投资建议。"); fontSize(11f); lineHeight(18f); color(StockChatTheme.textTertiary); marginTop(12f) } }
            }
        }
    }

    private fun IndexOverview(container: ViewContainer<*, *>) {
        val snapshot = this.snapshot
        with(container) {
            View {
                attr { padding(16f); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
                Text { attr { text("今日表现"); fontSize(16f); fontWeightMedium(); color(StockChatTheme.textPrimary) } }
                Text { attr { text("开盘 ${snapshot.open} · 最高 ${snapshot.high} · 最低 ${snapshot.low} · 昨收 ${snapshot.previousClose}"); fontSize(13f); lineHeight(22f); color(StockChatTheme.textSecondary); marginTop(10f) } }
                Text { attr { text("成交额 ${amount(snapshot.amount, snapshot.amountUnit)}\n成交量 ${quantity(snapshot.volume, snapshot.volumeUnit)} · 振幅 ${percent(snapshot.amplitude)}"); fontSize(12f); lineHeight(21f); color(StockChatTheme.textSecondary); marginTop(12f) } }
                Text { attr { text("指数点位反映样本整体表现，数据为演示行情，仅供参考，不构成投资建议。"); fontSize(11f); lineHeight(18f); color(StockChatTheme.textTertiary); marginTop(12f) } }
            }
        }
    }

    private fun IndexProfile(container: ViewContainer<*, *>) {
        val snapshot = this.snapshot
        with(container) {
            View {
                attr { padding(16f); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
                Text { attr { text("指数简况"); fontSize(16f); fontWeightMedium(); color(StockChatTheme.textPrimary) } }
                Text { attr { text("标的名称  ${snapshot.quote.name}\n交易代码  ${snapshot.providerSymbol.uppercase()}\n所属市场  ${snapshot.quote.marketLabel}"); fontSize(13f); lineHeight(23f); color(StockChatTheme.textSecondary); marginTop(10f) } }
                Text { attr { text("市盈率 ${snapshot.priceEarningsRatio.ifBlank { "--" }} · 市净率 ${snapshot.priceBookRatio.ifBlank { "--" }}\n指数说明：${snapshot.quote.summary}"); fontSize(12f); lineHeight(21f); color(StockChatTheme.textSecondary); marginTop(12f) } }
                Text { attr { text("估值与指数说明仅作信息展示。仅供参考，不构成投资建议。"); fontSize(11f); lineHeight(18f); color(StockChatTheme.textTertiary); marginTop(12f) } }
            }
        }
    }

    private fun Control(container: ViewContainer<*, *>, label: String, action: () -> Unit) = with(container) {
        View {
            attr { minWidth(29f); height(28f); paddingLeft(5f); paddingRight(5f); justifyContentCenter(); alignItemsCenter(); marginLeft(4f); borderRadius(6f); backgroundColor(StockChatTheme.surfaceSoft) }
            event { click { action() } }
            Text { attr { text(label); fontSize(12f); color(StockChatTheme.textPrimary) } }
        }
    }

    private fun Status(container: ViewContainer<*, *>, message: String, action: (() -> Unit)? = null) = with(container) {
        View {
            attr { height(180f); padding(18f); justifyContentCenter(); alignItemsCenter(); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
            event { click { action?.invoke() } }
            Text { attr { text(message); fontSize(13f); lineHeight(20f); color(StockChatTheme.textSecondary) } }
        }
    }

    private fun priceColor(value: Float?): Color {
        val previous = snapshot.previousClose.toFloatOrNull() ?: return StockChatTheme.textPrimary
        return when { value == null || value == previous -> StockChatTheme.textPrimary; value > previous -> StockChatTheme.positive; else -> StockChatTheme.negative }
    }
}

private fun flowColor(value: Float): Color = if (value >= 0) StockChatTheme.positive else StockChatTheme.negative
private fun signed(value: Float): String = (if (value > 0) "+" else "") + financialNumber(value)
private fun signedAmount(value: Float): String = (if (value > 0) "+" else "") + financialVolume(value)
private fun unit(value: String, suffix: String): String = if (value.isBlank() || value == "-") "--" else value + suffix
private fun percent(value: String): String = unit(value, "%")
private fun quantity(value: String, unit: String): String = value.toFloatOrNull()?.let { financialVolume(it) + unit } ?: "--"
private fun amount(value: String, unit: String): String = value.toFloatOrNull()?.let { financialVolume(if (unit == "万元") it * 10000f else it) + if (unit == "港元") "港元" else "元" } ?: "--"

internal fun ViewContainer<*, *>.StockMarket(
    snapshot: TencentMarketSnapshot,
    initialEvidence: ChartEvidenceReference? = null,
    onEvidenceLocated: ((Float) -> Unit)? = null,
) {
    addChild(StockMarketPanel()) {
        this.snapshot = snapshot
        this.initialEvidence = initialEvidence
        this.onEvidenceLocated = onEvidenceLocated
    }
}
