package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.StockDetailPredictionControllerState
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Renders the current chart range label in the prediction header. */
internal fun StockDetailPage.PredictionChartHeader(container: ViewContainer<*, *>) {
    with(container) {
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
    }
}

/** Renders the action that requests or toggles the AI prediction overlay. */
internal fun StockDetailPage.PredictionToggleButton(container: ViewContainer<*, *>, quote: StockQuote) {
    val ctx = this
    with(container) {
        View {
            attr {
                height(28f)
                borderRadius(14f)
                padding(left = 10f, right = 10f)
                marginLeft(8f)
                backgroundColor(
                    when {
                        ctx.predictionState is StockDetailPredictionControllerState.Loading -> StockChatTheme.recessed
                        ctx.isShowingPrediction() -> StockChatTheme.accent
                        else -> StockChatTheme.accentSoft
                    }
                )
                allCenter()
            }
            event {
                click {
                    if (ctx.predictionState !is StockDetailPredictionControllerState.Loading) {
                        ctx.toggleChartPrediction(quote)
                    }
                }
            }
            Text {
                attr {
                    text(
                        when {
                            ctx.predictionState is StockDetailPredictionControllerState.Loading -> "分析中…"
                            ctx.isShowingPrediction() -> "返回走势"
                            else -> "AI 预测"
                        }
                    )
                    fontSize(scaledFontSize(12f))
                    fontWeightMedium()
                    color(
                        when {
                            ctx.predictionState is StockDetailPredictionControllerState.Loading -> StockChatTheme.textSecondary
                            ctx.isShowingPrediction() -> Color.WHITE
                            else -> StockChatTheme.accent
                        }
                    )
                }
            }
        }
    }
}
