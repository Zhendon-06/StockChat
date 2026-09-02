package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.MarketDataResult
import com.guet.liang.stockchat.data.StockChatShareContentBuilder
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.TencentMarketDataService
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.base.event.PinchGestureParams
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.TextAlign
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.round

private const val DETAIL_PAGE_NAME = "stock_detail"
private const val CHART_AXIS_WIDTH = 44f
private const val CHART_RIGHT_INSET = 4f
private const val CHART_PLOT_TOP = 10f
private const val CHART_PLOT_BOTTOM = 16f
private const val CHART_MIN_SCALE = 1f
private const val CHART_MAX_SCALE = 4f
private const val CHART_PREDICTION_HISTORY_SIZE = 12
private const val CHART_PREDICTION_DELTA_SIZE = 5
private const val CHART_PREDICTION_POINT_COUNT = 8
private const val CHART_PREDICTION_DAMPING_STEP = 0.08f

private fun scaledFontSize(baseSize: Float): Float = baseSize * StockChatTheme.fontScale

private fun axisLabel(value: Float): String {
    val rounded = round(value * 100f) / 100f
    return rounded.toString()
}

private sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Content(val quote: StockQuote) : DetailUiState()
    data object Empty : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@Page(DETAIL_PAGE_NAME, supportInLocal = true)
internal class StockDetailPage : BasePager() {
    private var detailState by observable<DetailUiState>(DetailUiState.Loading)
    private var chartShowingPrediction by observable(false)
    private var chartScale by observable(1f)
    private var chartOffset by observable(0f)
    private var symbol = ""
    private var loadToken = 0
    private var chartPanStartX = 0f
    private var chartPanStartY = 0f
    private var chartPanStartOffset = 0f
    private var chartPinchStartScale = 1f
    private var chartViewportWidth = 0f
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
                    fontSize(scaledFontSize(18f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginLeft(14f)
                    flex(1f)
                }
            }
            View {
                attr {
                    height(38f)
                    borderRadius(19f)
                    padding(left = 15f, right = 15f)
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
                        fontSize(scaledFontSize(14f))
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
                    width(pagerData.pageViewWidth - 36f)
                    alignSelfCenter()
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
                                fontSize(scaledFontSize(22f))
                                fontWeightBold()
                                color(StockChatTheme.textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("${quote.marketLabel} · ${quote.symbol}")
                                fontSize(scaledFontSize(13f))
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
                        fontSize(scaledFontSize(38f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginTop(22f)
                    }
                }
                Text {
                    attr {
                        text("${quote.change}   ${quote.changePercent}")
                        fontSize(scaledFontSize(16f))
                        fontWeightBold()
                        color(if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative)
                        marginTop(5f)
                    }
                }
                Text {
                    attr {
                        text(quote.updatedAt)
                        fontSize(scaledFontSize(11f))
                        color(StockChatTheme.textTertiary)
                        marginTop(8f)
                    }
                }
            }
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
                            text(if (ctx.chartShowingPrediction) "AI 预测走势" else "走势")
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
                                text("分时")
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
                                if (ctx.chartShowingPrediction) StockChatTheme.accent
                                else StockChatTheme.accentSoft,
                            )
                            allCenter()
                        }
                        event {
                            click { ctx.toggleChartPrediction() }
                        }
                        Text {
                            attr {
                                text(if (ctx.chartShowingPrediction) "返回走势" else "AI 预测")
                                fontSize(scaledFontSize(12f))
                                fontWeightMedium()
                                color(
                                    if (ctx.chartShowingPrediction) Color.WHITE
                                    else StockChatTheme.accent,
                                )
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
                            text(if (ctx.chartShowingPrediction) "预测起点" else "09:30")
                            fontSize(scaledFontSize(10f))
                            color(StockChatTheme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(if (ctx.chartShowingPrediction) "下一期" else "11:30")
                            fontSize(scaledFontSize(10f))
                            color(StockChatTheme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(if (ctx.chartShowingPrediction) "AI 模拟" else "15:00")
                            fontSize(scaledFontSize(10f))
                            color(StockChatTheme.textTertiary)
                        }
                    }
                }
                Text {
                    attr {
                        text(
                            if (ctx.chartShowingPrediction) {
                                "双指缩放、左右滑动查看预测区间 · AI 预测仅为演示"
                            } else {
                                "双指缩放、左右滑动查看完整走势"
                            },
                        )
                        fontSize(scaledFontSize(10f))
                        color(StockChatTheme.textTertiary)
                        marginTop(8f)
                    }
                }
            }
            ctx.InsightCard(this, "行情摘要", quote.summary, false)
            ctx.InsightCard(this, "AI 解读", quote.aiInsight, true)
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

    private fun LargeTrendChart(container: ViewContainer<*, *>, quote: StockQuote) {
        val ctx = this
        with(container) {
        View {
            attr {
                height(188f)
                marginTop(18f)
                alignSelfStretch()
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            event {
                pan { params -> ctx.handleChartPan(params) }
            }
            Canvas({
                attr {
                    absolutePositionAllZero()
                }
                event {
                    pinch { params -> ctx.handleChartPinch(params) }
                }
            }) { context, width, height ->
            ctx.chartViewportWidth = width
            val points = if (ctx.chartShowingPrediction) {
                ctx.aiPredictionPoints(quote.trendPoints)
            } else {
                quote.trendPoints
            }
            val axisWidth = CHART_AXIS_WIDTH
            val rightInset = CHART_RIGHT_INSET
            val plotLeft = axisWidth
            val plotRight = (width - rightInset).coerceAtLeast(plotLeft + 1f)
            val plotTop = CHART_PLOT_TOP
            val plotBottom = (height - CHART_PLOT_BOTTOM).coerceAtLeast(plotTop + 1f)
            val plotHeight = plotBottom - plotTop
            val plotWidth = plotRight - plotLeft
            val contentWidth = plotWidth * ctx.chartScale
            val minimumOffset = -(contentWidth - plotWidth).coerceAtLeast(0f)
            val offset = ctx.chartOffset.coerceIn(minimumOffset, 0f)

            val dataMin = points.minOrNull() ?: 0f
            val dataMax = points.maxOrNull() ?: 1f
            val dataRange = (dataMax - dataMin).takeIf { it > 0f } ?: 1f
            val dataCenter = (dataMax + dataMin) / 2f
            val visibleRange = (dataRange / ctx.chartScale).coerceAtLeast(0.0001f)
            val visibleMin = dataCenter - visibleRange / 2f
            val visibleMax = dataCenter + visibleRange / 2f
            val gridColor = Color(0xFFE9EDEB)
            val axisColor = Color(0xFFB7C4BF)
            val labelColor = Color(0xFF7A8A84)
            for (index in 0..4) {
                val fraction = index / 4f
                val y = plotTop + plotHeight * fraction
                context.beginPath()
                context.moveTo(plotLeft, y)
                context.lineTo(plotRight, y)
                context.lineWidth(1f)
                context.strokeStyle(gridColor)
                context.stroke()

                context.font(10f)
                context.fillStyle(labelColor)
                context.textAlign(TextAlign.RIGHT)
                context.fillText(axisLabel(visibleMax - visibleRange * fraction), plotLeft - 6f, y + 3f)
            }

            context.beginPath()
            context.moveTo(plotLeft, plotTop)
            context.lineTo(plotLeft, plotBottom)
            context.lineWidth(1f)
            context.strokeStyle(axisColor)
            context.stroke()

            if (points.size < 2) {
                return@Canvas
            }

            context.save()
            context.beginPath()
            context.moveTo(plotLeft, plotTop)
            context.lineTo(plotRight, plotTop)
            context.lineTo(plotRight, plotBottom)
            context.lineTo(plotLeft, plotBottom)
            context.closePath()
            context.clip()
            context.beginPath()
            points.forEachIndexed { index, value ->
                val x = plotLeft + index.toFloat() / (points.size - 1).toFloat() * contentWidth + offset
                val normalized = (value - visibleMin) / visibleRange
                val y = plotTop + (1f - normalized) * plotHeight
                if (index == 0) {
                    context.moveTo(x, y)
                } else {
                    context.lineTo(x, y)
                }
            }
            context.lineWidth(3f)
            context.lineCapRound()
            context.strokeStyle(
                if (ctx.chartShowingPrediction) StockChatTheme.accent
                else if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative,
            )
            context.stroke()
            context.restore()
            }
        }
        }
    }

    private fun toggleChartPrediction() {
        chartShowingPrediction = !chartShowingPrediction
        chartOffset = 0f
        chartScale = 1f
    }

    private fun handleChartPan(params: PanGestureParams) {
        when (params.state) {
            "start" -> {
                chartPanStartX = params.pageX
                chartPanStartY = params.pageY
                chartPanStartOffset = chartOffset
            }
            "move" -> {
                val deltaX = params.pageX - chartPanStartX
                val deltaY = params.pageY - chartPanStartY
                if (abs(deltaX) <= abs(deltaY)) {
                    return
                }
                chartOffset = chartPanStartOffset + deltaX
                clampChartOffset()
            }
            "end", "cancel" -> {
                chartPanStartX = 0f
                chartPanStartY = 0f
                chartPanStartOffset = chartOffset
            }
        }
    }

    private fun handleChartPinch(params: PinchGestureParams) {
        when (params.state) {
            "start" -> chartPinchStartScale = chartScale
            "move" -> {
                val gestureScale = params.scale.takeIf { it > 0f } ?: 1f
                chartScale = (chartPinchStartScale * gestureScale).coerceIn(
                    CHART_MIN_SCALE,
                    CHART_MAX_SCALE,
                )
                clampChartOffset()
            }
        }
    }

    private fun clampChartOffset() {
        val viewport = chartViewportWidth
        if (viewport <= 0f) {
            chartOffset = 0f
            return
        }
        val plotWidth = (viewport - CHART_AXIS_WIDTH - CHART_RIGHT_INSET).coerceAtLeast(1f)
        val minimumOffset = -(plotWidth * chartScale - plotWidth).coerceAtLeast(0f)
        chartOffset = chartOffset.coerceIn(minimumOffset, 0f)
    }

    private fun aiPredictionPoints(points: List<Float>): List<Float> {
        if (points.isEmpty()) {
            return points
        }
        if (points.size == 1) {
            return buildList {
                add(points.first())
                repeat(CHART_PREDICTION_POINT_COUNT) { add(points.first()) }
            }
        }
        val source = points.takeLast(CHART_PREDICTION_HISTORY_SIZE)
        val recentDeltas = source.zipWithNext { previous, current -> current - previous }
            .takeLast(CHART_PREDICTION_DELTA_SIZE)
        val averageDelta = recentDeltas.average().toFloat()
        val last = source.last()
        return buildList {
            add(last)
            repeat(CHART_PREDICTION_POINT_COUNT) { index ->
                val damping = 1f - index * CHART_PREDICTION_DAMPING_STEP
                add(last + averageDelta * (index + 1) * damping.coerceAtLeast(0.45f))
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
                width(pagerData.pageViewWidth - 36f)
                alignSelfCenter()
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
                        fontSize(scaledFontSize(16f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
            Text {
                attr {
                    text(content)
                    fontSize(scaledFontSize(14f))
                    lineHeight(scaledFontSize(22f))
                    color(StockChatTheme.textSecondary)
                    marginTop(11f)
                }
            }
        }
        }
    }

    private fun loadDetail() {
        detailState = DetailUiState.Loading
        loadToken += 1
        val currentLoadToken = loadToken
        marketDataService.loadDetail(symbol) result@{ result ->
            if (currentLoadToken != loadToken) {
                return@result
            }
            detailState = when (result) {
                is MarketDataResult.Success -> result.snapshots.firstOrNull()?.quote
                    ?.let(DetailUiState::Content)
                    ?: DetailUiState.Empty
                MarketDataResult.Empty -> DetailUiState.Empty
                is MarketDataResult.Failure -> DetailUiState.Error(result.message)
            }
        }
    }

    private fun shareQuote() {
        val quote = (detailState as? DetailUiState.Content)?.quote
        if (quote == null) {
            bridgeModule.toast(
                if (detailState is DetailUiState.Loading) "行情加载中，请稍后" else "暂无可分享的行情"
            )
            return
        }
        val content = StockChatShareContentBuilder.fromQuote(quote)
        acquireModule<ShareModule>(ShareModule.MODULE_NAME).share(content) { result ->
            when (result) {
                ShareResult.Success -> StockChatSettingsStore.repository.recordSharedChat(
                    sessionId = "stock-detail-${quote.symbol}",
                    question = "${quote.name}（${quote.symbol}）行情详情",
                    content = content,
                )
                ShareResult.Cancelled -> Unit
                is ShareResult.Failure -> bridgeModule.toast(result.errorMessage)
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
