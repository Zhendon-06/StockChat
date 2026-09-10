package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.FinancialChartView
import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.STOCK_DETAIL_PREVIEW_QUOTE_PARAM
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.openRoute
import com.guet.liang.stockchat.controller.StockDetailController
import com.guet.liang.stockchat.controller.StockDetailControllerState
import com.guet.liang.stockchat.controller.StockDetailPredictionControllerState
import com.guet.liang.stockchat.controller.stockDetailController
import com.guet.liang.stockchat.data.toStockQuoteOrNull
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.View

@Page(STOCK_DETAIL_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class StockDetailPage : BasePager() {
    internal var detailState by observable<StockDetailControllerState>(StockDetailControllerState.Loading)
    internal var selectedDetailTab by observable(DetailTab.MARKET)
    internal var predictionState by observable<StockDetailPredictionControllerState>(StockDetailPredictionControllerState.NotRequested)
    internal var predictionRenderRevision by observable(0)
    internal var chartShowingPrediction by observable(false)
    internal var predictionHistoryCache: List<StockPredictionHistoryPoint> = emptyList()
    internal var detailScroller: ViewRef<ScrollerView<*, *>>? = null
    internal var pendingMarketEvidence: ChartEvidenceReference? = null
    internal var predictionChartView: FinancialChartView? = null
    internal var selectedChartPointIndex by observable(-1)
    /** While a chart drag is in flight the page must not scroll, or a wobbling finger hands the gesture to the Scroller. */
    internal var chartGestureActive by observable(false)
    private var favoriteCardsRevision by observable(0)
    internal var insightFocus by observable(DetailInsightFocus.TREND)
    internal var symbol = ""
    // 入口页带来的行情快照：请求返回前先按它绘制名称、价格与走势，其余部分显示骨架
    internal var previewQuote: StockQuote? = null
    // 骨架呼吸相位，定时器驱动；只在 Loading 期间运行
    internal var skeletonPhase by observable(0)
    private var skeletonTimer: Timer? = null
    internal lateinit var controller: StockDetailController

    override fun created() {
        super.created()
        applySavedAppearance()
        symbol = pageData.params.optString("symbol").trim().uppercase()
        previewQuote = pageData.params.optJSONObject(STOCK_DETAIL_PREVIEW_QUOTE_PARAM)?.toStockQuoteOrNull()
        controller =
            stockDetailController(
                onMarketStateChanged = {
                    detailState = it
                    updateSkeletonTimer()
                },
                onPredictionStateChanged = { updatePredictionState(it) },
            )
        loadDetail()
    }

    private fun updateSkeletonTimer() {
        val loading = detailState is StockDetailControllerState.Loading
        if (loading && skeletonTimer == null) {
            skeletonPhase = 0
            // 首个 tick 延后一个周期：骨架节点刚创建的批次里不能命中动画键，否则首帧会从 (0,0) 飞入
            skeletonTimer = Timer().also { timer -> timer.schedule(SKELETON_PULSE_INTERVAL_MS, SKELETON_PULSE_INTERVAL_MS) { skeletonPhase += 1 } }
        } else if (!loading && skeletonTimer != null) {
            skeletonTimer?.cancel()
            skeletonTimer = null
        }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        applySavedAppearance()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        applySavedAppearance()
    }

    override fun pageWillDestroy() {
        skeletonTimer?.cancel()
        skeletonTimer = null
        controller.invalidate()
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(StockChatTheme.background) }
            ctx.DetailHeader(this)
            View {
                attr { absolutePosition(top = pagerData.statusBarHeight + 68f, left = 0f, right = 0f, bottom = 0f) }
                vif({ ctx.detailState is StockDetailControllerState.Loading }) { ctx.DetailSkeleton(this, ctx.previewQuote) }
                vif({ ctx.detailState is StockDetailControllerState.Empty }) { ctx.EmptyState(this) }
                vif({ ctx.detailState is StockDetailControllerState.Error }) { ctx.ErrorState(this) }
                vif({ ctx.detailState is StockDetailControllerState.Content }) {
                    val snapshot = (ctx.detailState as StockDetailControllerState.Content).snapshot
                    ctx.DetailContent(this, snapshot)
                }
            }
        }
    }

    internal fun isFavorite(quote: StockQuote): Boolean {
        val revision = favoriteCardsRevision
        return revision >= 0 && controller.isFavorite(quote)
    }

    internal fun currentDetailQuote(): StockQuote? {
        return (detailState as? StockDetailControllerState.Content)?.snapshot?.quote
    }

    internal fun toggleFavorite(quote: StockQuote) {
        val isFavorite = controller.toggleFavorite(quote)
        favoriteCardsRevision += 1
        bridgeModule.toast(if (isFavorite) "已收藏行情卡片" else "已取消收藏")
    }

    internal fun updatePredictionState(nextState: StockDetailPredictionControllerState) {
        if (nextState is StockDetailPredictionControllerState.Content) {
            predictionHistoryCache = nextState.history
            chartShowingPrediction = true
        }
        predictionState = nextState
        predictionRenderRevision += 1
    }

    internal fun openChatWithStock(quote: StockQuote, selectedPoint: SelectedChartPoint?) {
        val pointContext = selectedPoint?.let { "我在走势图中选中了${it.label}，价格约 ${it.price}。" }.orEmpty()
        val params = JSONObject()
        val prediction = (predictionState as? StockDetailPredictionControllerState.Content)?.prediction
        val selectedContext = selectedPoint?.let { "选中节点=${it.label},价格=${it.price},序号=${it.index}" }.orEmpty()
        params.put(
            "prefillQuestion",
            "请结合${quote.name}（${quote.symbol}）当前价格 ${quote.price}（${quote.change}，" +
                "${quote.changePercent}，数据时间 ${quote.updatedAt}）、走势图和 AI 解读，" +
                "${pointContext}说明关键观察点、风险与后续验证条件。",
        )
        params.put(
            "stockContext",
            JSONObject().apply {
                put("name", quote.name)
                put("symbol", quote.symbol)
                put("price", quote.price)
                put("change", quote.change)
                put("changePercent", quote.changePercent)
                put("updatedAt", quote.updatedAt)
                put("selectedPoint", selectedContext)
                put("insightFocus", insightFocus.label)
                put("insight", linkedInsightText(quote, prediction, selectedPoint))
                put("predictionRationale", prediction?.rationale.orEmpty())
                put("chartPoints", JSONArray().apply { chartPoints().forEach { put(it.toDouble()) } })
            },
        )
        pageData.params.optString("qwenApiKey").trim().takeIf(String::isNotBlank)?.let { params.put("qwenApiKey", it) }
        openRoute(CHAT_PAGE_NAME, params)
    }

    internal fun loadDetail() {
        pendingMarketEvidence = null
        predictionHistoryCache = emptyList()
        predictionChartView = null
        detailState = StockDetailControllerState.Loading
        updateSkeletonTimer()
        selectedDetailTab = DetailTab.MARKET
        updatePredictionState(StockDetailPredictionControllerState.NotRequested)
        chartShowingPrediction = false
        selectedChartPointIndex = -1
        insightFocus = DetailInsightFocus.TREND
        controller.load(symbol)
    }

    internal fun requestPrediction(quote: StockQuote) {
        if (predictionState is StockDetailPredictionControllerState.Loading) return
        chartShowingPrediction = false
        selectedChartPointIndex = -1
        controller.requestPrediction(symbol, quote)
    }

    internal fun shareQuote() {
        val quote = (detailState as? StockDetailControllerState.Content)?.snapshot?.quote
        if (quote == null) {
            bridgeModule.toast(if (detailState is StockDetailControllerState.Loading) "行情加载中，请稍后" else "暂无可分享的行情")
            return
        }
        controller.shareSnapshot(quote) { result ->
            when (result) {
                ShareResult.Success,
                ShareResult.Cancelled -> Unit
                is ShareResult.Failure -> bridgeModule.toast(result.errorMessage)
            }
        }
    }
}

private const val SKELETON_PULSE_INTERVAL_MS = 700
