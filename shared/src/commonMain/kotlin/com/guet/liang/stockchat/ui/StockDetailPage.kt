package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.FinancialChart
import com.guet.liang.kuiklychart.finance.FinancialChartMode
import com.guet.liang.kuiklychart.finance.FinancialChartView
import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.ChartEvidenceResolver
import com.guet.liang.stockchat.data.ChartEvidenceResolution
import com.guet.liang.stockchat.data.MarketPeriod
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.kuiklychart.finance.FinancialPoint
import com.guet.liang.stockchat.data.MarketDataResult
import com.guet.liang.stockchat.data.HistoricalPointsResult
import com.guet.liang.stockchat.data.FavoriteCardsStore
import com.guet.liang.stockchat.data.StockChatShareContentBuilder
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.StockPredictionService
import com.guet.liang.stockchat.data.TencentMarketDataService
import com.guet.liang.stockchat.data.TencentMarketSnapshot
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.StockPrediction
import com.guet.liang.stockchat.model.StockPredictionConfig
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockPredictionInput
import com.guet.liang.stockchat.model.StockPredictionPoint
import com.guet.liang.stockchat.model.StockPredictionResult
import com.guet.liang.stockchat.model.ModelProviderKind
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.round
import kotlin.math.abs

private const val DETAIL_PAGE_NAME = "stock_detail"
private const val PREDICTION_HISTORY_COUNT = 120
private const val DEFAULT_CHAT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
private const val STOCK_PREDICTION_LOG_TAG = "StockPrediction"

private enum class DetailInsightFocus(
    val label: String,
) {
    TREND("走势解读"),
    PREDICTION("预测依据"),
    RISK("风险提醒"),
}

private enum class DetailTab(
    val label: String,
) {
    MARKET("行情展示"),
    PREDICTION("AI 走势"),
}

private fun scaledFontSize(baseSize: Float): Float = baseSize * StockChatTheme.fontScale

private sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Content(val snapshot: TencentMarketSnapshot) : DetailUiState()
    data object Empty : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

private sealed class PredictionUiState {
    data object NotRequested : PredictionUiState()
    data object Loading : PredictionUiState()
    data class Content(
        val prediction: StockPrediction,
        val history: List<StockPredictionHistoryPoint>,
    ) : PredictionUiState()
    data class Unavailable(val message: String) : PredictionUiState()
    data class Error(val message: String) : PredictionUiState()
}

@Page(DETAIL_PAGE_NAME, supportInLocal = true)
internal class StockDetailPage : BasePager() {
    private var detailState by observable<DetailUiState>(DetailUiState.Loading)
    private var selectedDetailTab by observable(DetailTab.MARKET)
    private var predictionState by observable<PredictionUiState>(PredictionUiState.NotRequested)
    private var predictionRenderRevision by observable(0)
    private var chartShowingPrediction by observable(false)
    private var predictionHistoryCache: List<StockPredictionHistoryPoint> = emptyList()
    private var detailScroller: ViewRef<ScrollerView<*, *>>? = null
    private var pendingMarketEvidence: ChartEvidenceReference? = null
    private var predictionChartView: FinancialChartView? = null
    private var selectedChartPointIndex by observable(-1)
    private var favoriteCardsRevision by observable(0)
    private var insightFocus by observable(DetailInsightFocus.TREND)
    private var symbol = ""
    private var loadToken = 0
    private var predictionToken = 0
    private lateinit var marketDataService: TencentMarketDataService

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
                ctx.DetailTabSwitcher(this)
                View {
                    attr {
                        size(36f, 36f)
                        borderRadius(18f)
                        marginLeft(8f)
                        backgroundColor(
                            if (ctx.currentDetailQuote()?.let(ctx::isFavorite) == true) {
                                StockChatTheme.warningSoft
                            } else {
                                StockChatTheme.surface
                            },
                        )
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        allCenter()
                        touchEnable(ctx.currentDetailQuote() != null)
                    }
                    event {
                        click { ctx.currentDetailQuote()?.let(ctx::toggleFavorite) }
                    }
                    Text {
                        attr {
                            text(
                                if (ctx.currentDetailQuote()?.let(ctx::isFavorite) == true) {
                                    "★"
                                } else {
                                    "☆"
                                },
                            )
                            fontSize(21f)
                            color(
                                if (ctx.currentDetailQuote()?.let(ctx::isFavorite) == true) {
                                    StockChatTheme.warning
                                } else {
                                    StockChatTheme.accent
                                },
                            )
                        }
                    }
                }
                View {
                    attr {
                        height(36f)
                        borderRadius(18f)
                        padding(left = 13f, right = 13f)
                        marginLeft(8f)
                        backgroundColor(StockChatTheme.surface)
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        allCenter()
                    }
                    event {
                        click { ctx.shareQuote() }
                    }
                    Text {
                        attr {
                            text("分享")
                            fontSize(scaledFontSize(13f))
                            fontWeightMedium()
                            color(StockChatTheme.textPrimary)
                        }
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
                    fontSize(scaledFontSize(14f))
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
                    fontSize(scaledFontSize(19f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("暂未收录该股票或指数的行情信息。")
                    fontSize(scaledFontSize(14f))
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
                    fontSize(scaledFontSize(19f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text((ctx.detailState as? DetailUiState.Error)?.message ?: "请稍后重试")
                    fontSize(scaledFontSize(14f))
                    lineHeight(scaledFontSize(21f))
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
                        fontSize(scaledFontSize(14f))
                        fontWeightMedium()
                        color(Color.WHITE)
                    }
                }
            }
        }
        }
    }

    private fun DetailContent(container: ViewContainer<*, *>, snapshot: TencentMarketSnapshot) {
        val ctx = this
        val quote = snapshot.quote
        with(container) {
            Scroller {
            ref { ctx.detailScroller = it }
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
            vif({ ctx.selectedDetailTab == DetailTab.MARKET }) {
                ctx.MarketDisplayContent(this, snapshot)
            }
            vif({ ctx.selectedDetailTab == DetailTab.PREDICTION }) {
                ctx.AiPredictionContent(this, quote)
            }
            View {
                attr {
                    width(pagerData.pageViewWidth - 36f)
                    alignSelfCenter()
                    marginTop(14f)
                    padding(top = 13f, left = 14f, bottom = 13f, right = 14f)
                    borderRadius(16f)
                    backgroundColor(StockChatTheme.warningSoft)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.warningBorder))
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
                        text("StockChat Demo 信息，仅供参考，不构成投资建议。")
                        fontSize(scaledFontSize(12f))
                        lineHeight(scaledFontSize(18f))
                        color(StockChatTheme.warning)
                        flex(1f)
                    }
                }
            }
        }
        }
    }

    private fun DetailTabSwitcher(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    flex(1f)
                    height(40f)
                    marginLeft(10f)
                    padding(all = 3f)
                    borderRadius(20f)
                    backgroundColor(StockChatTheme.recessed)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    flexDirectionRow()
                }
                View {
                    attr {
                        flex(1f)
                        height(34f)
                        borderRadius(17f)
                        backgroundColor(StockChatTheme.surface)
                        boxShadow(
                            BoxShadow(
                                0f,
                                2f,
                                8f,
                                Color(0x1F000000),
                            )
                        )
                        transform(
                            Translate(
                                if (ctx.selectedDetailTab == DetailTab.PREDICTION) 1f else 0f,
                            )
                        )
                        animate(
                            Animation.springEaseOut(0.34f, 0.9f, 0.12f),
                            ctx.selectedDetailTab,
                        )
                        touchEnable(false)
                    }
                }
                View {
                    attr {
                        flex(1f)
                        height(34f)
                        touchEnable(false)
                    }
                }
                View {
                    attr {
                        absolutePosition(top = 3f, left = 3f, right = 3f, bottom = 3f)
                        flexDirectionRow()
                        alignItemsCenter()
                        zIndex(1)
                    }
                    DetailTab.values().forEach { tab ->
                        View {
                            attr {
                                flex(1f)
                                height(34f)
                                alignItemsCenter()
                                justifyContentCenter()
                            }
                            event {
                                click {
                                    if (tab == DetailTab.MARKET) {
                                        ctx.selectedChartPointIndex = -1
                                    }
                                    if (ctx.selectedDetailTab != tab) ctx.detailScroller?.view?.setContentOffset(0f, 0f, false)
                                    ctx.selectedDetailTab = tab
                                }
                            }
                            Text {
                                attr {
                                    text(tab.label)
                                    fontSize(scaledFontSize(13f))
                                    if (ctx.selectedDetailTab == tab) {
                                        fontWeightBold()
                                    }
                                    color(
                                        if (ctx.selectedDetailTab == tab) {
                                            StockChatTheme.textPrimary
                                        } else {
                                            StockChatTheme.textSecondary
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun AiPredictionContent(
        container: ViewContainer<*, *>,
        quote: StockQuote,
    ) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width(pagerData.pageViewWidth - 36f)
                    alignSelfCenter()
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
                            text(
                                if (ctx.isShowingPrediction()) "AI 预测走势" else "走势"
                            )
                            fontSize(scaledFontSize(17f))
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
                            backgroundColor(StockChatTheme.recessed)
                            allCenter()
                        }
                        Text {
                            attr {
                                text("日线")
                                fontSize(scaledFontSize(12f))
                                fontWeightMedium()
                                color(StockChatTheme.textPrimary)
                            }
                        }
                    }
                    View {
                        attr {
                            height(28f)
                            borderRadius(14f)
                            padding(left = 10f, right = 10f)
                            marginLeft(8f)
                            backgroundColor(
                                when {
                                    ctx.predictionState is PredictionUiState.Loading ->
                                        StockChatTheme.recessed
                                    ctx.isShowingPrediction() -> StockChatTheme.accent
                                    else -> StockChatTheme.accentSoft
                                },
                            )
                            allCenter()
                        }
                        event {
                            click {
                                if (ctx.predictionState !is PredictionUiState.Loading) {
                                    ctx.toggleChartPrediction(quote)
                                }
                            }
                        }
                        Text {
                            attr {
                                text(
                                    when {
                                        ctx.predictionState is PredictionUiState.Loading -> "分析中…"
                                        ctx.isShowingPrediction() -> "返回走势"
                                        else -> "AI 预测"
                                    }
                                )
                                fontSize(scaledFontSize(12f))
                                fontWeightMedium()
                                color(
                                    when {
                                        ctx.predictionState is PredictionUiState.Loading ->
                                            StockChatTheme.textSecondary
                                        ctx.isShowingPrediction() -> Color.WHITE
                                        else -> StockChatTheme.accent
                                    },
                                )
                            }
                        }
                    }
                }
                ctx.PredictionTrendChart(this)
                Text {
                    attr {
                        text(ctx.chartHint())
                        fontSize(scaledFontSize(10f))
                        color(StockChatTheme.textTertiary)
                        marginTop(8f)
                    }
                }
            }
            vif({ ctx.predictionRenderRevision % 2 == 0 }) {
                ctx.PredictionCards(this, quote)
            }
            velse {
                ctx.PredictionCards(this, quote)
            }
        }
        }

    private fun isFavorite(quote: StockQuote): Boolean {
        val revision = favoriteCardsRevision
        return revision >= 0 && FavoriteCardsStore.contains(quote)
    }

    private fun currentDetailQuote(): StockQuote? {
        return (detailState as? DetailUiState.Content)?.snapshot?.quote
    }

    private fun toggleFavorite(quote: StockQuote) {
        val isFavorite = FavoriteCardsStore.toggle(quote)
        favoriteCardsRevision += 1
        bridgeModule.toast(if (isFavorite) "已收藏行情卡片" else "已取消收藏")
    }

    private fun MarketDisplayContent(
        container: ViewContainer<*, *>,
        snapshot: TencentMarketSnapshot,
    ) {
        val ctx = this
        val reference = pendingMarketEvidence
        pendingMarketEvidence = null
        with(container) {
            StockMarket(snapshot, reference) { chartTop ->
                ctx.detailScroller?.view?.setContentOffset(0f, chartTop, false)
            }
        }
    }

    private fun PredictionCards(
        container: ViewContainer<*, *>,
        quote: StockQuote,
    ) {
        val prediction = (predictionState as? PredictionUiState.Content)?.prediction
        LinkedInsightCard(container, quote, prediction)
        PredictionStatusCard(container, quote)
    }

    private fun predictionHistory(): List<StockPredictionHistoryPoint> =
        (predictionState as? PredictionUiState.Content)?.history
            ?: predictionHistoryCache.ifEmpty {
                (detailState as? DetailUiState.Content)?.snapshot?.dailyCandles.orEmpty().map {
                    StockPredictionHistoryPoint(it.date, it.close)
                }
            }

    private fun predictionPlot(): PredictionChartData = predictionChartData(
        predictionHistory(),
        if (isShowingPrediction()) (predictionState as? PredictionUiState.Content)?.prediction?.forecastPoints.orEmpty()
        else emptyList(),
    )

    private fun PredictionTrendChart(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            // Recreate only when the data response or visibility changes, preserving point-selection updates.
            for (showForecast in listOf(false, true)) {
                vif({ ctx.isShowingPrediction() == showForecast }) {
                    val plot = ctx.predictionPlot()
                    if (plot.points.isEmpty()) {
                        Text { attr { text("暂无日线历史数据，暂不能绘制预测图"); fontSize(12f); color(StockChatTheme.textSecondary); marginTop(20f) } }
                    } else {
                        View {
                            attr { flexDirectionRow(); alignItemsCenter(); marginTop(14f) }
                            Text {
                                attr { text(if (showForecast) "历史收盘 + 模型预测区间" else "日线收盘 · ${plot.points.size}个交易日"); fontSize(11f); color(StockChatTheme.textTertiary); flex(1f) }
                            }
                            for (label in listOf("−", "+", "复位")) View {
                                attr { padding(8f); marginLeft(4f); borderRadius(6f); backgroundColor(StockChatTheme.surfaceSoft) }
                                event { click { when (label) { "+" -> ctx.predictionChartView?.zoom(1.4f); "−" -> ctx.predictionChartView?.zoom(0.75f); else -> ctx.predictionChartView?.resetViewport() } } }
                                Text { attr { text(label); fontSize(12f); color(StockChatTheme.textPrimary) } }
                            }
                        }
                        FinancialChart {
                            ctx.predictionChartView = this
                            attr { height(340f); marginTop(8f) }
                            onSelectionChanged = { index -> ctx.selectedChartPointIndex = index ?: -1 }
                            chart {
                                points = plot.points
                                mode = FinancialChartMode.CLOSE_LINE
                                showVolume = false
                                visibleCount = 48
                                forecastRevealDurationMillis = if (showForecast) 800 else 0
                                forecastStartIndex = plot.forecastStart
                                forecastIntervals = plot.intervals
                                backgroundColor = StockChatTheme.surface
                                textColor = StockChatTheme.textPrimary
                                mutedColor = StockChatTheme.textTertiary
                                gridColor = StockChatTheme.border
                            }
                        }
                        if (showForecast) Text {
                            attr { text("阴影仅表示模型返回的价格区间，不是收益保证；未返回上下界的节点不绘制区间。"); fontSize(10f); lineHeight(16f); color(StockChatTheme.textTertiary); marginTop(7f) }
                        }
                    }
                }
            }
        }
    }

    private fun chartPoints(quote: StockQuote): List<Float> = predictionPlot().points.map { it.close }

    private fun isShowingPrediction(): Boolean {
        return chartShowingPrediction && predictionState is PredictionUiState.Content
    }

    private fun chartHint(): String {
        return when (val state = predictionState) {
            PredictionUiState.NotRequested -> "双指缩放、左右滑动查看完整走势；点击 AI 预测请求模型分析"
            PredictionUiState.Loading -> "正在请求模型分析真实历史数据，不使用本地外推"
            is PredictionUiState.Content -> if (chartShowingPrediction) {
                "实线为历史收盘，虚线为 ${state.prediction.modelName} 返回的模型估计；点选查看日期与价格"
            } else {
                "双指缩放、左右滑动查看完整走势"
            }
            is PredictionUiState.Unavailable -> "AI 预测不可用：${state.message}"
            is PredictionUiState.Error -> "AI 预测失败：${state.message}"
        }
    }

    private fun toggleChartPrediction(quote: StockQuote) {
        if (chartShowingPrediction) {
            chartShowingPrediction = false
            selectedChartPointIndex = -1
            return
        }
        if (predictionState is PredictionUiState.Content) {
            chartShowingPrediction = true
            selectedChartPointIndex = -1
            return
        }
        requestPrediction(quote)
    }

    private fun PredictionStatusCard(
        container: ViewContainer<*, *>,
        quote: StockQuote,
    ) {
        val ctx = this
        val state = predictionState
        val (statusLabel, statusColor, statusBackground) = when (state) {
            PredictionUiState.NotRequested -> Triple(
                "未请求",
                StockChatTheme.textSecondary,
                StockChatTheme.recessed,
            )
            PredictionUiState.Loading -> Triple(
                "请求中",
                StockChatTheme.accent,
                StockChatTheme.accentSoft,
            )
            is PredictionUiState.Content -> Triple(
                "模型已返回",
                StockChatTheme.accent,
                StockChatTheme.accentSoft,
            )
            is PredictionUiState.Unavailable -> Triple(
                "不可用",
                StockChatTheme.warning,
                StockChatTheme.warningSoft,
            )
            is PredictionUiState.Error -> Triple(
                "请求失败",
                StockChatTheme.negative,
                StockChatTheme.marketNegativeSoft,
            )
        }
        with(container) {
            View {
                attr {
                    width(pagerData.pageViewWidth - 36f)
                    alignSelfCenter()
                    marginTop(14f)
                    padding(top = 16f, left = 16f, bottom = 16f, right = 16f)
                    borderRadius(20f)
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
                            text("AI 预测状态")
                            fontSize(scaledFontSize(16f))
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                            flex(1f)
                        }
                    }
                    View {
                        attr {
                            height(26f)
                            borderRadius(13f)
                            padding(left = 10f, right = 10f)
                            backgroundColor(statusBackground)
                            allCenter()
                        }
                        Text {
                            attr {
                                text(statusLabel)
                                fontSize(scaledFontSize(11f))
                                fontWeightMedium()
                                color(statusColor)
                            }
                        }
                    }
                }
                when (state) {
                    PredictionUiState.NotRequested -> Text {
                        attr {
                            text("点击“AI 预测”后，应用会把真实历史行情发送给当前配置的模型；未成功返回前不会绘制预测曲线。")
                            fontSize(scaledFontSize(13f))
                            lineHeight(scaledFontSize(20f))
                            color(StockChatTheme.textSecondary)
                            marginTop(10f)
                        }
                    }
                    PredictionUiState.Loading -> Text {
                        attr {
                            text("正在读取历史行情并等待模型返回结构化预测，请不要重复提交。")
                            fontSize(scaledFontSize(13f))
                            lineHeight(scaledFontSize(20f))
                            color(StockChatTheme.textSecondary)
                            marginTop(10f)
                        }
                    }
                    is PredictionUiState.Content -> {
                        val prediction = state.prediction
                        val confidencePercent = round(prediction.confidence * 100f).toInt()
                        Text {
                            attr {
                                text("方向：${prediction.direction}  ·  模型自报置信度：$confidencePercent%")
                                fontSize(scaledFontSize(14f))
                                fontWeightMedium()
                                color(StockChatTheme.textPrimary)
                                marginTop(10f)
                            }
                        }
                        Text {
                            attr {
                                text("预测周期：未来 ${prediction.horizon} 个交易点  ·  历史样本：${prediction.historyPointCount} 点")
                                fontSize(scaledFontSize(12f))
                                color(StockChatTheme.textSecondary)
                                marginTop(6f)
                            }
                        }
                        Text {
                            attr {
                                text("模型：${prediction.modelName}")
                                fontSize(scaledFontSize(12f))
                                color(StockChatTheme.textSecondary)
                                marginTop(5f)
                            }
                        }
                        Text {
                            attr {
                                text("生成时间：${prediction.generatedAt}")
                                fontSize(scaledFontSize(12f))
                                color(StockChatTheme.textTertiary)
                                marginTop(5f)
                            }
                        }
                        Text {
                            attr {
                                text("行情数据截至：${prediction.sourceUpdatedAt}")
                                fontSize(scaledFontSize(12f))
                                color(StockChatTheme.textTertiary)
                                marginTop(4f)
                            }
                        }
                    }
                    is PredictionUiState.Unavailable -> {
                        Text {
                            attr {
                                text(state.message)
                                fontSize(scaledFontSize(13f))
                                lineHeight(scaledFontSize(20f))
                                color(StockChatTheme.textSecondary)
                                marginTop(10f)
                            }
                        }
                        View {
                            attr {
                                height(34f)
                                borderRadius(17f)
                                padding(left = 14f, right = 14f)
                                marginTop(11f)
                                backgroundColor(StockChatTheme.accentSoft)
                                allCenter()
                            }
                            event { click { ctx.requestPrediction(quote) } }
                            Text {
                                attr {
                                    text("重新请求")
                                    fontSize(scaledFontSize(12f))
                                    fontWeightMedium()
                                    color(StockChatTheme.accent)
                                }
                            }
                        }
                    }
                    is PredictionUiState.Error -> {
                        Text {
                            attr {
                                text(state.message)
                                fontSize(scaledFontSize(13f))
                                lineHeight(scaledFontSize(20f))
                                color(StockChatTheme.textSecondary)
                                marginTop(10f)
                            }
                        }
                        View {
                            attr {
                                height(34f)
                                borderRadius(17f)
                                padding(left = 14f, right = 14f)
                                marginTop(11f)
                                backgroundColor(StockChatTheme.accentSoft)
                                allCenter()
                            }
                            event { click { ctx.requestPrediction(quote) } }
                            Text {
                                attr {
                                    text("重试")
                                    fontSize(scaledFontSize(12f))
                                    fontWeightMedium()
                                    color(StockChatTheme.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPrediction(quote: StockQuote) {
        if (predictionState is PredictionUiState.Loading) {
            stockPredictionUiLog("ui_request_ignored reason=already_loading symbol=$symbol")
            return
        }
        predictionToken += 1
        val currentPredictionToken = predictionToken
        updatePredictionState(PredictionUiState.Loading)
        chartShowingPrediction = false
        selectedChartPointIndex = -1
        stockPredictionUiLog(
            "ui_request_started symbol=$symbol quoteName=${quote.name} " +
                "quotePrice=${quote.price} quoteUpdatedAt=${quote.updatedAt}"
        )

        val configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
        val provider = configuration.providers.firstOrNull { candidate ->
            candidate.id == configuration.activeProviderId
        }
        val usesDashScope = provider == null || provider.kind == ModelProviderKind.DEFAULT ||
            provider.kind == ModelProviderKind.ALIYUN
        val routeApiKey = pageData.params.optString("qwenApiKey").trim()
        val apiKey = when {
            provider == null -> routeApiKey
            !provider.isEnabled -> ""
            provider.apiKey.isNotBlank() -> provider.apiKey.trim()
            usesDashScope -> routeApiKey
            else -> ""
        }
        val model = provider?.selectedModelId?.trim().orEmpty()
            .ifBlank { provider?.models?.firstOrNull()?.id?.trim().orEmpty() }
        val config = StockPredictionConfig(
            apiKey = apiKey,
            baseUrl = provider?.baseUrl?.trim()?.takeIf(String::isNotBlank)
                ?: if (usesDashScope) DEFAULT_CHAT_BASE_URL else "",
            model = model,
            providerDisplayName = provider?.displayName?.trim()
                ?.takeIf(String::isNotBlank)
                ?: "AI 模型",
            useAliyunExtensions = usesDashScope,
        )
        stockPredictionUiLog(
            "ui_config providerId=${provider?.id ?: "none"} " +
                "provider=${config.providerDisplayName} kind=${provider?.kind ?: "none"} " +
                "enabled=${provider?.isEnabled ?: true} keyPresent=${config.apiKey.isNotBlank()} " +
                "baseUrl=${config.baseUrl} model=${config.model} " +
                "aliyunExtensions=${config.useAliyunExtensions}"
        )

        if (config.apiKey.isBlank()) {
            stockPredictionUiLog("ui_request_rejected reason=missing_api_key")
            if (currentPredictionToken == predictionToken) {
                updatePredictionState(PredictionUiState.Unavailable(
                    "当前 Provider 没有可用 API Key，请先在模型配置页面填写后重试。",
                ))
            }
            return
        }
        if (config.model.isBlank()) {
            stockPredictionUiLog("ui_request_rejected reason=missing_model")
            if (currentPredictionToken == predictionToken) {
                updatePredictionState(PredictionUiState.Unavailable(
                    "当前 Provider 没有可用模型，请先选择模型后重试。",
                ))
            }
            return
        }

        marketDataService.loadHistoricalPoints(
            symbol = symbol,
            count = PREDICTION_HISTORY_COUNT,
        ) history@{ historyResult ->
            if (currentPredictionToken != predictionToken) {
                return@history
            }
            when (historyResult) {
                HistoricalPointsResult.Empty -> {
                    stockPredictionUiLog(
                        "history_empty symbol=$symbol requestedCount=$PREDICTION_HISTORY_COUNT"
                    )
                    updatePredictionState(PredictionUiState.Unavailable(
                        "腾讯行情没有返回足够的历史收盘数据，未生成预测曲线。",
                    ))
                }
                is HistoricalPointsResult.Failure -> {
                    stockPredictionUiLog(
                        "history_failed symbol=$symbol message=${historyResult.message.logSafe()}"
                    )
                    updatePredictionState(PredictionUiState.Error(historyResult.message))
                }
                is HistoricalPointsResult.Success -> {
                    stockPredictionUiLog(
                        "history_loaded symbol=$symbol count=${historyResult.points.size} " +
                            "first=${historyResult.points.firstOrNull()?.date ?: "none"} " +
                            "last=${historyResult.points.lastOrNull()?.date ?: "none"}"
                    )
                    val history = historyResult.points.map { point ->
                        StockPredictionHistoryPoint(
                            timestamp = point.date,
                            close = point.close,
                        )
                    }
                    val input = StockPredictionInput(
                        quote = quote,
                        history = history,
                        forecastHorizon = StockPredictionInput.DEFAULT_STOCK_PREDICTION_HORIZON,
                        sourceUpdatedAt = quote.updatedAt,
                    )
                    stockPredictionUiLog(
                        "prediction_input_ready symbol=${input.quote.symbol} " +
                            "historyCount=${input.history.size} horizon=${input.forecastHorizon} " +
                            "sourceUpdatedAt=${input.sourceUpdatedAt}"
                    )
                    try {
                        StockPredictionService(
                            networkModule = acquireModule(NetworkModule.MODULE_NAME),
                            config = config,
                        ).predict(input) prediction@{ predictionResult ->
                            if (currentPredictionToken != predictionToken) {
                                return@prediction
                            }
                            when (predictionResult) {
                                is StockPredictionResult.Success -> {
                                    stockPredictionUiLog(
                                        "prediction_success symbol=$symbol " +
                                            "points=${predictionResult.prediction.forecastPoints.size} " +
                                            "direction=${predictionResult.prediction.direction} " +
                                            "confidence=${predictionResult.prediction.confidence}"
                                    )
                                    updatePredictionState(PredictionUiState.Content(
                                        prediction = predictionResult.prediction,
                                        history = history,
                                    ))
                                    chartShowingPrediction = true
                                }
                                is StockPredictionResult.Unavailable -> {
                                    stockPredictionUiLog(
                                        "prediction_unavailable symbol=$symbol " +
                                            "message=${predictionResult.message.logSafe()}"
                                    )
                                    updatePredictionState(PredictionUiState.Unavailable(
                                        predictionResult.message,
                                    ))
                                }
                                is StockPredictionResult.Failure -> {
                                    stockPredictionUiLog(
                                        "prediction_failed symbol=$symbol status=${predictionResult.statusCode ?: "unknown"} " +
                                            "message=${predictionResult.message.logSafe()}"
                                    )
                                    updatePredictionState(PredictionUiState.Error(
                                        predictionResult.message,
                                    ))
                                }
                            }
                        }
                    } catch (throwable: Throwable) {
                        stockPredictionUiLog(
                            "prediction_exception symbol=$symbol " +
                                "type=${throwable::class.simpleName ?: "unknown"}"
                        )
                        updatePredictionState(PredictionUiState.Error(
                            "AI 预测请求失败，请稍后重试；未生成预测曲线。",
                        ))
                    }
                }
            }
        }
    }

    private data class SelectedChartPoint(
        val label: String,
        val price: String,
        val value: Float,
        val index: Int,
    )

    private fun LinkedInsightCard(
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

    private fun selectedChartPoint(quote: StockQuote): SelectedChartPoint? {
        val index = selectedChartPointIndex
        val points = chartPoints(quote)
        if (index !in points.indices) {
            return null
        }
        val predictionContent = predictionState as? PredictionUiState.Content
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

    private fun linkedInsightText(
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

    private fun updatePredictionState(nextState: PredictionUiState) {
        if (nextState is PredictionUiState.Content) predictionHistoryCache = nextState.history
        predictionState = nextState
        predictionRenderRevision += 1
    }

    private fun openChatWithStock(
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

    private fun loadDetail() {
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

    private fun shareQuote() {
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

    private fun applySavedAppearance() {
        StockChatTheme.applyAppearance(
            appearance = StockChatSettingsStore.repository.loadSnapshot().appearance,
            systemDark = isNightMode(),
        )
    }

}

private fun stockPredictionUiLog(message: String) {
    runCatching { KLog.i(STOCK_PREDICTION_LOG_TAG, message) }
        .onFailure { println("[$STOCK_PREDICTION_LOG_TAG] $message") }
}

private fun String.logSafe(): String {
    return replace(Regex("\\s+"), " ").trim().take(240)
}
