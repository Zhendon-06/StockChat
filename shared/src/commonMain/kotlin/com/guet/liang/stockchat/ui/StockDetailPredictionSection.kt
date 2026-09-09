package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.finance.FinancialChart
import com.guet.liang.kuiklychart.finance.FinancialChartMode
import com.guet.liang.stockchat.controller.StockDetailControllerState
import com.guet.liang.stockchat.controller.StockDetailPredictionControllerState
import com.guet.liang.stockchat.model.StockPredictionHistoryPoint
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.round

// 详情页 AI 预测区：预测卡片、走势图与状态卡。

internal const val PREDICTION_HISTORY_COUNT = 120

internal fun StockDetailPage.AiPredictionContent(container: ViewContainer<*, *>, quote: StockQuote) {
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
                themedBorder()
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(if (ctx.isShowingPrediction()) "AI 预测走势" else "走势")
                        fontSize(scaledFontSize(17f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        flex(1f)
                    }
                }
                ctx.PredictionChartHeader(this)
                ctx.PredictionToggleButton(this, quote)
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
        vif({ ctx.predictionRenderRevision % 2 == 0 }) { ctx.PredictionCards(this, quote) }
        velse { ctx.PredictionCards(this, quote) }
    }
}

internal fun StockDetailPage.PredictionCards(container: ViewContainer<*, *>, quote: StockQuote) {
    val prediction = (predictionState as? StockDetailPredictionControllerState.Content)?.prediction
    LinkedInsightCard(container, quote, prediction)
    PredictionStatusCard(container, quote)
}

internal fun StockDetailPage.predictionHistory(): List<StockPredictionHistoryPoint> =
    (predictionState as? StockDetailPredictionControllerState.Content)?.history
        ?: predictionHistoryCache.ifEmpty {
            (detailState as? StockDetailControllerState.Content)?.snapshot?.dailyCandles.orEmpty().map {
                StockPredictionHistoryPoint(it.date, it.close)
            }
        }

internal fun StockDetailPage.predictionPlot(): PredictionChartData =
    predictionChartData(
        predictionHistory(),
        if (isShowingPrediction()) (predictionState as? StockDetailPredictionControllerState.Content)?.prediction?.forecastPoints.orEmpty()
        else emptyList(),
    )

internal fun StockDetailPage.PredictionTrendChart(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        // Recreate only when the data response or visibility changes, preserving point-selection updates.
        for (showForecast in listOf(false, true)) {
            vif({ ctx.isShowingPrediction() == showForecast }) {
                val plot = ctx.predictionPlot()
                if (plot.points.isEmpty()) {
                    Text {
                        attr {
                            text("暂无日线历史数据，暂不能绘制预测图")
                            fontSize(12f)
                            color(StockChatTheme.textSecondary)
                            marginTop(20f)
                        }
                    }
                } else {
                    ctx.PredictionChartToolbar(this, showForecast, plot.points.size)
                    ctx.PredictionFinancialChart(this, plot, showForecast)
                    if (showForecast) this.PredictionForecastDisclaimer()
                }
            }
        }
    }
}

private fun StockDetailPage.PredictionChartToolbar(
    container: ViewContainer<*, *>,
    showForecast: Boolean,
    pointCount: Int,
) {
    val ctx = this
    with(container) {
        View {
            attr { flexDirectionRow(); alignItemsCenter(); marginTop(14f) }
            Text {
                attr {
                    text(if (showForecast) "历史收盘 + 模型预测区间" else "日线收盘 · ${pointCount}个交易日")
                    fontSize(11f); color(StockChatTheme.textTertiary); flex(1f)
                }
            }
            for (label in listOf("−", "+", "复位")) View {
                attr { padding(8f); marginLeft(4f); borderRadius(6f); backgroundColor(StockChatTheme.surfaceSoft) }
                event {
                    click {
                        when (label) {
                            "+" -> ctx.predictionChartView?.zoom(1.4f)
                            "−" -> ctx.predictionChartView?.zoom(0.75f)
                            else -> ctx.predictionChartView?.resetViewport()
                        }
                    }
                }
                Text { attr { text(label); fontSize(12f); color(StockChatTheme.textPrimary) } }
            }
        }
    }
}

private fun StockDetailPage.PredictionFinancialChart(
    container: ViewContainer<*, *>,
    plot: PredictionChartData,
    showForecast: Boolean,
) {
    val ctx = this
    with(container) {
        FinancialChart {
            ctx.predictionChartView = this
            attr { height(340f); marginTop(8f) }
            onSelectionChanged = { index -> ctx.selectedChartPointIndex = index ?: -1 }
            onGestureActiveChanged = { ctx.chartGestureActive = it }
            chart {
                points = plot.points; mode = FinancialChartMode.CLOSE_LINE; showVolume = false; visibleCount = 48
                forecastRevealDurationMillis = if (showForecast) 800 else 0
                forecastStartIndex = plot.forecastStart; forecastIntervals = plot.intervals
                backgroundColor = StockChatTheme.surface; textColor = StockChatTheme.textPrimary
                mutedColor = StockChatTheme.textTertiary; gridColor = StockChatTheme.border
            }
        }
    }
}

private fun ViewContainer<*, *>.PredictionForecastDisclaimer() {
    Text {
        attr {
            text("阴影仅表示模型返回的价格区间，不是收益保证；未返回上下界的节点不绘制区间。")
            fontSize(10f); lineHeight(16f); color(StockChatTheme.textTertiary); marginTop(7f)
        }
    }
}

internal fun StockDetailPage.chartPoints(): List<Float> = predictionPlot().points.map { it.close }

internal fun StockDetailPage.isShowingPrediction(): Boolean {
    return chartShowingPrediction && predictionState is StockDetailPredictionControllerState.Content
}

internal fun StockDetailPage.chartHint(): String {
    return when (val state = predictionState) {
        StockDetailPredictionControllerState.NotRequested -> "双指缩放、左右滑动查看完整走势；点击 AI 预测请求模型分析"
        StockDetailPredictionControllerState.Loading -> "正在请求模型分析真实历史数据，不使用本地外推"
        is StockDetailPredictionControllerState.Content ->
            if (chartShowingPrediction) {
                "实线为历史收盘，虚线为 ${state.prediction.modelName} 返回的模型估计；点选查看日期与价格"
            } else {
                "双指缩放、左右滑动查看完整走势"
            }
        is StockDetailPredictionControllerState.Unavailable -> "AI 预测不可用：${state.message}"
        is StockDetailPredictionControllerState.Error -> "AI 预测失败：${state.message}"
    }
}

internal fun StockDetailPage.toggleChartPrediction(quote: StockQuote) {
    if (chartShowingPrediction) {
        chartShowingPrediction = false
        selectedChartPointIndex = -1
        return
    }
    if (predictionState is StockDetailPredictionControllerState.Content) {
        chartShowingPrediction = true
        selectedChartPointIndex = -1
        return
    }
    requestPrediction(quote)
}

internal fun StockDetailPage.PredictionStatusCard(container: ViewContainer<*, *>, quote: StockQuote) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                alignSelfCenter()
                marginTop(14f)
                padding(top = 16f, left = 16f, bottom = 16f, right = 16f)
                borderRadius(20f)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            ctx.PredictionStatusHeader(this)
            ctx.PredictionStatusBody(this, quote)
        }
    }
}

private fun StockDetailPage.PredictionStatusHeader(container: ViewContainer<*, *>) {
    val state = predictionState
    val (statusLabel, statusColor, statusBackground) =
        when (state) {
            StockDetailPredictionControllerState.NotRequested -> Triple("未请求", StockChatTheme.textSecondary, StockChatTheme.recessed)
            StockDetailPredictionControllerState.Loading -> Triple("请求中", StockChatTheme.accent, StockChatTheme.accentSoft)
            is StockDetailPredictionControllerState.Content -> Triple("模型已返回", StockChatTheme.accent, StockChatTheme.accentSoft)
            is StockDetailPredictionControllerState.Unavailable -> Triple("不可用", StockChatTheme.warning, StockChatTheme.warningSoft)
            is StockDetailPredictionControllerState.Error -> Triple("请求失败", StockChatTheme.negative, StockChatTheme.marketNegativeSoft)
        }
    with(container) {
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
    }
}

private fun StockDetailPage.PredictionResultDetails(container: ViewContainer<*, *>, state: StockDetailPredictionControllerState.Content) {
    val ctx = this
    with(container) {
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
}

private fun StockDetailPage.PredictionUnavailable(
    container: ViewContainer<*, *>,
    state: StockDetailPredictionControllerState.Unavailable,
    quote: StockQuote,
) {
    val ctx = this
    with(container) {
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
}

private fun StockDetailPage.PredictionFailure(
    container: ViewContainer<*, *>,
    state: StockDetailPredictionControllerState.Error,
    quote: StockQuote,
) {
    val ctx = this
    with(container) {
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

private fun StockDetailPage.PredictionStatusBody(container: ViewContainer<*, *>, quote: StockQuote) {
    val ctx = this
    val state = predictionState
    with(container) {
        when (state) {
            StockDetailPredictionControllerState.NotRequested ->
                Text {
                    attr {
                        text("点击“AI 预测”后，应用会把真实历史行情发送给当前配置的模型；未成功返回前不会绘制预测曲线。")
                        fontSize(scaledFontSize(13f))
                        lineHeight(scaledFontSize(20f))
                        color(StockChatTheme.textSecondary)
                        marginTop(10f)
                    }
                }
            StockDetailPredictionControllerState.Loading ->
                Text {
                    attr {
                        text("正在读取历史行情并等待模型返回结构化预测，请不要重复提交。")
                        fontSize(scaledFontSize(13f))
                        lineHeight(scaledFontSize(20f))
                        color(StockChatTheme.textSecondary)
                        marginTop(10f)
                    }
                }
            is StockDetailPredictionControllerState.Content -> ctx.PredictionResultDetails(this, state)
            is StockDetailPredictionControllerState.Unavailable -> ctx.PredictionUnavailable(this, state, quote)
            is StockDetailPredictionControllerState.Error -> ctx.PredictionFailure(this, state, quote)
        }
    }
}
