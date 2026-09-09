package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.FinancialChartView
import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.stockchat.data.MarketDataResult
import com.guet.liang.stockchat.data.FavoriteCardsStore
import com.guet.liang.stockchat.data.StockChatShareContentBuilder
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.TencentMarketDataService
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.View

@Page(STOCK_DETAIL_PAGE_NAME, supportInLocal = true)
internal class StockDetailPage : BasePager() {
    internal var detailState by observable<DetailUiState>(DetailUiState.Loading)
    internal var selectedDetailTab by observable(DetailTab.MARKET)
    internal var predictionState by observable<PredictionUiState>(PredictionUiState.NotRequested)
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
    private var loadToken = 0
    internal var predictionToken = 0
    internal lateinit var marketDataService: TencentMarketDataService

    override fun created() {
        super.created()
        applySavedAppearance()
        symbol = pageData.params.optString("symbol").trim().uppercase()
        marketDataService = TencentMarketDataService(
            acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        )
        loadDetail()
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
        loadToken += 1
        predictionToken += 1
        super.pageWillDestroy()
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
                    val snapshot = (ctx.detailState as DetailUiState.Content).snapshot
                    ctx.DetailContent(this, snapshot)
                }
            }
        }
    }

    internal fun isFavorite(quote: StockQuote): Boolean {
        val revision = favoriteCardsRevision
        return revision >= 0 && FavoriteCardsStore.contains(quote)
    }

    internal fun currentDetailQuote(): StockQuote? {
        return (detailState as? DetailUiState.Content)?.snapshot?.quote
    }

    internal fun toggleFavorite(quote: StockQuote) {
        val isFavorite = FavoriteCardsStore.toggle(quote)
        favoriteCardsRevision += 1
        bridgeModule.toast(if (isFavorite) "已收藏行情卡片" else "已取消收藏")
    }

    internal fun updatePredictionState(nextState: PredictionUiState) {
        if (nextState is PredictionUiState.Content) predictionHistoryCache = nextState.history
        predictionState = nextState
        predictionRenderRevision += 1
    }

    internal fun openChatWithStock(
        quote: StockQuote,
        selectedPoint: SelectedChartPoint?,
    ) {
        val pointContext = selectedPoint?.let {
            "我在走势图中选中了${it.label}，价格约 ${it.price}。"
        }.orEmpty()
        val params = JSONObject()
        val prediction = (predictionState as? PredictionUiState.Content)?.prediction
        val selectedContext = selectedPoint?.let {
            "选中节点=${it.label},价格=${it.price},序号=${it.index}"
        }.orEmpty()
        params.put(
            "prefillQuestion",
            "请结合${quote.name}（${quote.symbol}）当前价格 ${quote.price}（${quote.change}，${quote.changePercent}，数据时间 ${quote.updatedAt}）、走势图和 AI 解读，${pointContext}说明关键观察点、风险与后续验证条件。",
        )
        params.put("stockContext", JSONObject().apply {
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
            put("chartPoints", JSONArray().apply { chartPoints(quote).forEach { put(it.toDouble()) } })
        })
        pageData.params.optString("qwenApiKey").trim()
            .takeIf(String::isNotBlank)
            ?.let { params.put("qwenApiKey", it) }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("router", params)
    }

    internal fun loadDetail() {
        pendingMarketEvidence = null
        predictionHistoryCache = emptyList()
        predictionChartView = null
        detailState = DetailUiState.Loading
        selectedDetailTab = DetailTab.MARKET
        predictionToken += 1
        updatePredictionState(PredictionUiState.NotRequested)
        chartShowingPrediction = false
        selectedChartPointIndex = -1
        insightFocus = DetailInsightFocus.TREND
        loadToken += 1
        val currentLoadToken = loadToken
        marketDataService.loadDetail(symbol) result@{ result ->
            if (currentLoadToken != loadToken) {
                return@result
            }
            detailState = when (result) {
                is MarketDataResult.Success -> result.snapshots.firstOrNull()
                    ?.let(DetailUiState::Content)
                    ?: DetailUiState.Empty
                MarketDataResult.Empty -> DetailUiState.Empty
                is MarketDataResult.Failure -> DetailUiState.Error(result.message)
            }
        }
    }

    internal fun shareQuote() {
        val quote = (detailState as? DetailUiState.Content)?.snapshot?.quote
        if (quote == null) {
            bridgeModule.toast(
                if (detailState is DetailUiState.Loading) "行情加载中，请稍后" else "暂无可分享的行情"
            )
            return
        }
        val content = StockChatShareContentBuilder.fromQuote(quote)
        val sharedRecord = StockChatSettingsStore.repository.recordSharedChat(
            sessionId = "stock-detail-${quote.symbol}",
            question = "${quote.name}（${quote.symbol}）行情详情",
            content = content,
        )
        acquireModule<ShareModule>(ShareModule.MODULE_NAME).share(content) { result ->
            when (result) {
                ShareResult.Success,
                ShareResult.Cancelled -> Unit
                is ShareResult.Failure -> {
                    StockChatSettingsStore.repository.deleteSharedChat(sharedRecord.id)
                    bridgeModule.toast(result.errorMessage)
                }
            }
        }
    }
}
