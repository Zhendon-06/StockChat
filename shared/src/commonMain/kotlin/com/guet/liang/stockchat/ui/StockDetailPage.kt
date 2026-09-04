package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.MarketDataResult
import com.guet.liang.stockchat.data.HistoricalPointsResult
import com.guet.liang.stockchat.data.StockChatShareContentBuilder
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.StockPredictionService
import com.guet.liang.stockchat.data.TencentMarketDataService
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
import com.tencent.kuikly.core.log.KLog
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
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

private const val DETAIL_PAGE_NAME = "stock_detail"
private const val CHART_AXIS_WIDTH = 44f
private const val CHART_RIGHT_INSET = 4f
private const val CHART_PLOT_TOP = 10f
private const val CHART_PLOT_BOTTOM = 16f
private const val CHART_MIN_SCALE = 1f
private const val CHART_MAX_SCALE = 4f
private const val PREDICTION_HISTORY_COUNT = 120
private const val DEFAULT_CHAT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
private const val STOCK_PREDICTION_LOG_TAG = "StockPrediction"

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
    private var predictionState by observable<PredictionUiState>(PredictionUiState.NotRequested)
    private var chartShowingPrediction by observable(false)
    private var chartScale by observable(1f)
    private var chartOffset by observable(0f)
    private var symbol = ""
    private var loadToken = 0
    private var predictionToken = 0
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
                ctx.LargeTrendChart(this, quote)
                View {
                    attr {
                        flexDirectionRow()
                        justifyContentSpaceBetween()
                        marginTop(7f)
                    }
                    Text {
                        attr {
                            text(if (ctx.isShowingPrediction()) "历史" else "09:30")
                            fontSize(scaledFontSize(10f))
                            color(StockChatTheme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(if (ctx.isShowingPrediction()) "预测起点" else "11:30")
                            fontSize(scaledFontSize(10f))
                            color(StockChatTheme.textTertiary)
                        }
                    }
                    Text {
                        attr {
                            text(if (ctx.isShowingPrediction()) "模型区间" else "15:00")
                            fontSize(scaledFontSize(10f))
                            color(StockChatTheme.textTertiary)
                        }
                    }
                }
                Text {
                    attr {
                        text(ctx.chartHint())
                        fontSize(scaledFontSize(10f))
                        color(StockChatTheme.textTertiary)
                        marginTop(8f)
                    }
                }
            }
            ctx.PredictionStatusCard(this, quote)
            ctx.InsightCard(this, "行情摘要", quote.summary, false)
            val prediction = (ctx.predictionState as? PredictionUiState.Content)?.prediction
            ctx.InsightCard(
                this,
                if (prediction == null) "行情规则摘要" else "AI 预测解读",
                prediction?.rationale ?: quote.aiInsight,
                prediction != null,
            )
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
            val predictionContent = ctx.predictionState as? PredictionUiState.Content
            val showingPrediction = ctx.chartShowingPrediction && predictionContent != null
            val actualPoints = if (showingPrediction) {
                predictionContent?.history.orEmpty().map(StockPredictionHistoryPoint::close)
                    .ifEmpty { quote.trendPoints }
            } else {
                quote.trendPoints
            }
            val predictedPoints = if (showingPrediction) {
                predictionContent?.prediction?.forecastPoints
                    ?.map(StockPredictionPoint::predictedPrice)
                    .orEmpty()
            } else {
                emptyList()
            }
            val points = actualPoints + predictedPoints
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
            fun xFor(index: Int): Float {
                return plotLeft + index.toFloat() / (points.size - 1).toFloat() * contentWidth + offset
            }

            fun yFor(value: Float): Float {
                val normalized = (value - visibleMin) / visibleRange
                return plotTop + (1f - normalized) * plotHeight
            }

            fun drawSolidPath(
                startIndex: Int,
                endIndex: Int,
                color: Color,
            ) {
                if (startIndex > endIndex || startIndex !in points.indices) {
                    return
                }
                context.beginPath()
                for (index in startIndex..endIndex) {
                    val x = xFor(index)
                    val y = yFor(points[index])
                    if (index == startIndex) {
                        context.moveTo(x, y)
                    } else {
                        context.lineTo(x, y)
                    }
                }
                context.lineWidth(3f)
                context.lineCapRound()
                context.strokeStyle(color)
                context.stroke()
            }

            fun drawDashedPath(
                startIndex: Int,
                endIndex: Int,
                color: Color,
                pattern: List<Float>,
            ) {
                if (startIndex >= endIndex || startIndex !in points.indices) {
                    return
                }
                val validPattern = pattern.filter { it.isFinite() && it > 0f }
                if (validPattern.isEmpty()) {
                    drawSolidPath(startIndex, endIndex, color)
                    return
                }
                context.lineWidth(3f)
                context.lineCapRound()
                context.strokeStyle(color)
                var patternIndex = 0
                var drawSegment = true
                var patternRemaining = validPattern.first()
                var startX = xFor(startIndex)
                var startY = yFor(points[startIndex])
                for (index in startIndex until endIndex) {
                    val endX = xFor(index + 1)
                    val endY = yFor(points[index + 1])
                    val deltaX = endX - startX
                    val deltaY = endY - startY
                    val lineLength = sqrt(deltaX * deltaX + deltaY * deltaY)
                    if (lineLength <= 0f) {
                        startX = endX
                        startY = endY
                        continue
                    }
                    val unitX = deltaX / lineLength
                    val unitY = deltaY / lineLength
                    var travelled = 0f
                    while (travelled < lineLength) {
                        val segmentLength = min(patternRemaining, lineLength - travelled)
                        if (drawSegment && segmentLength > 0f) {
                            context.beginPath()
                            context.moveTo(
                                startX + unitX * travelled,
                                startY + unitY * travelled,
                            )
                            context.lineTo(
                                startX + unitX * (travelled + segmentLength),
                                startY + unitY * (travelled + segmentLength),
                            )
                            context.stroke()
                        }
                        travelled += segmentLength
                        patternRemaining -= segmentLength
                        if (patternRemaining <= 0.0001f) {
                            patternIndex = (patternIndex + 1) % validPattern.size
                            drawSegment = !drawSegment
                            patternRemaining = validPattern[patternIndex]
                        }
                    }
                    startX = endX
                    startY = endY
                }
            }

            if (showingPrediction && actualPoints.size >= 2 && predictedPoints.size >= 2) {
                drawSolidPath(
                    startIndex = 0,
                    endIndex = actualPoints.lastIndex,
                    color = if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative,
                )
                drawDashedPath(
                    startIndex = actualPoints.lastIndex,
                    endIndex = points.lastIndex,
                    color = StockChatTheme.accent,
                    pattern = listOf(7f, 5f),
                )
                val boundaryIndex = actualPoints.lastIndex
                val boundaryX = plotLeft + boundaryIndex.toFloat() /
                    (points.size - 1).toFloat() * contentWidth + offset
                context.beginPath()
                context.moveTo(boundaryX, plotTop)
                context.lineTo(boundaryX, plotBottom)
                context.lineWidth(1f)
                context.strokeStyle(Color(0x668A9C95))
                val boundaryPattern = listOf(4f, 4f)
                var boundaryY = plotTop
                var boundaryPatternIndex = 0
                var boundaryDrawSegment = true
                var boundaryPatternRemaining = boundaryPattern.first()
                while (boundaryY < plotBottom) {
                    val segmentLength = min(boundaryPatternRemaining, plotBottom - boundaryY)
                    if (boundaryDrawSegment && segmentLength > 0f) {
                        context.beginPath()
                        context.moveTo(boundaryX, boundaryY)
                        context.lineTo(boundaryX, boundaryY + segmentLength)
                        context.stroke()
                    }
                    boundaryY += segmentLength
                    boundaryPatternRemaining -= segmentLength
                    if (boundaryPatternRemaining <= 0.0001f) {
                        boundaryPatternIndex = (boundaryPatternIndex + 1) % boundaryPattern.size
                        boundaryDrawSegment = !boundaryDrawSegment
                        boundaryPatternRemaining = boundaryPattern[boundaryPatternIndex]
                    }
                }
            } else {
                drawSolidPath(
                    startIndex = 0,
                    endIndex = points.lastIndex,
                    color = if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative,
                )
            }
            context.restore()
            }
        }
        }
    }

    private fun isShowingPrediction(): Boolean {
        return chartShowingPrediction && predictionState is PredictionUiState.Content
    }

    private fun chartHint(): String {
        return when (val state = predictionState) {
            PredictionUiState.NotRequested -> "双指缩放、左右滑动查看完整走势；点击 AI 预测请求模型分析"
            PredictionUiState.Loading -> "正在请求模型分析真实历史数据，不使用本地外推"
            is PredictionUiState.Content -> if (chartShowingPrediction) {
                "实线为历史行情，虚线为 ${state.prediction.modelName} 返回的模型估计"
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
            chartOffset = 0f
            chartScale = 1f
            return
        }
        if (predictionState is PredictionUiState.Content) {
            chartShowingPrediction = true
            chartOffset = 0f
            chartScale = 1f
            return
        }
        requestPrediction(quote)
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
        predictionState = PredictionUiState.Loading
        chartShowingPrediction = false
        chartScale = 1f
        chartOffset = 0f
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
                predictionState = PredictionUiState.Unavailable(
                    "当前 Provider 没有可用 API Key，请先在模型配置页面填写后重试。",
                )
            }
            return
        }
        if (config.model.isBlank()) {
            stockPredictionUiLog("ui_request_rejected reason=missing_model")
            if (currentPredictionToken == predictionToken) {
                predictionState = PredictionUiState.Unavailable(
                    "当前 Provider 没有可用模型，请先选择模型后重试。",
                )
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
                    predictionState = PredictionUiState.Unavailable(
                        "腾讯行情没有返回足够的历史收盘数据，未生成预测曲线。",
                    )
                }
                is HistoricalPointsResult.Failure -> {
                    stockPredictionUiLog(
                        "history_failed symbol=$symbol message=${historyResult.message.logSafe()}"
                    )
                    predictionState = PredictionUiState.Error(historyResult.message)
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
                                    predictionState = PredictionUiState.Content(
                                        prediction = predictionResult.prediction,
                                        history = history,
                                    )
                                    chartShowingPrediction = true
                                    chartScale = 1f
                                    chartOffset = 0f
                                }
                                is StockPredictionResult.Unavailable -> {
                                    stockPredictionUiLog(
                                        "prediction_unavailable symbol=$symbol " +
                                            "message=${predictionResult.message.logSafe()}"
                                    )
                                    predictionState = PredictionUiState.Unavailable(
                                        predictionResult.message,
                                    )
                                }
                                is StockPredictionResult.Failure -> {
                                    stockPredictionUiLog(
                                        "prediction_failed symbol=$symbol status=${predictionResult.statusCode ?: "unknown"} " +
                                            "message=${predictionResult.message.logSafe()}"
                                    )
                                    predictionState = PredictionUiState.Error(
                                        predictionResult.message,
                                    )
                                }
                            }
                        }
                    } catch (throwable: Throwable) {
                        stockPredictionUiLog(
                            "prediction_exception symbol=$symbol " +
                                "type=${throwable::class.simpleName ?: "unknown"}"
                        )
                        predictionState = PredictionUiState.Error(
                            "AI 预测请求失败，请稍后重试；未生成预测曲线。",
                        )
                    }
                }
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
        predictionToken += 1
        predictionState = PredictionUiState.NotRequested
        chartShowingPrediction = false
        chartScale = 1f
        chartOffset = 0f
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
