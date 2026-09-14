package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.resolveDetailEvidence
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.stockchat.model.ChartEvidenceResolution
import com.guet.liang.stockchat.model.MarketChartResult
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.MarketAiSummary(container: ViewContainer<*, *>) {
    val quote = snapshot.quote
    val trendTint = if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative
    val amplitudeText = "振幅 ${percent(snapshot.amplitude)}"
    val rangeText = "日内区间 ${snapshot.low} – ${snapshot.high}"
    with(container) {
        View {
            attr {
                marginTop(14f)
                padding(15f)
                borderRadius(18f)
                backgroundColor(StockChatTheme.accentSoft)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        size(30f, 30f)
                        borderRadius(9f)
                        backgroundColor(StockChatTheme.accent)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("AI")
                            fontSize(12f)
                            fontWeightBold()
                            color(Color.WHITE)
                        }
                    }
                }
                Text {
                    attr {
                        text("AI 快速解读")
                        fontSize(16f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginLeft(9f)
                        flex(1f)
                    }
                }
                View {
                    attr {
                        padding(top = 4f, left = 10f, bottom = 4f, right = 10f)
                        borderRadius(10f)
                        backgroundColor(StockChatTheme.surface)
                    }
                    Text {
                        attr {
                            text("基于当前快照")
                            fontSize(10f)
                            fontWeightMedium()
                            color(StockChatTheme.accent)
                        }
                    }
                }
            }
            Text {
                attr {
                    text(quote.summary.ifBlank { quote.aiInsight }.ifBlank { "当前暂无可用的文字解读。" })
                    fontSize(14f)
                    lineHeight(22f)
                    color(StockChatTheme.textSecondary)
                    marginTop(10f)
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(11f)
                    padding(top = 8f, left = 10f, bottom = 8f, right = 10f)
                    borderRadius(9f)
                    backgroundColor(StockChatTheme.surface)
                }
                Text {
                    attr {
                        text("现价 ${quote.price}")
                        fontSize(11f)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("  ${quote.change}（${quote.changePercent}）")
                        fontSize(11f)
                        fontWeightMedium()
                        color(trendTint)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text(amplitudeText)
                        fontSize(11f)
                        color(StockChatTheme.textSecondary)
                    }
                }
            }
            Text {
                attr {
                    text(rangeText)
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                    marginTop(7f)
                }
            }
            Text {
                attr {
                    text("结合走势图、成交量和公告信息继续判断；本卡片仅供参考。")
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                    marginTop(8f)
                }
            }
        }
    }
}

internal fun StockMarketPanel.locateEvidence(reference: ChartEvidenceReference) {
    val data = (chartResult as? MarketChartResult.Content)?.data ?: return
    when (val result = resolveDetailEvidence(reference, snapshot.quote.symbol, period, snapshot.quote.updatedAt, data.points)) {
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

internal fun StockMarketPanel.MarketInsight(container: ViewContainer<*, *>) {
    val owner = this
    with(container) {
        View {
            attr { marginTop(12f) }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        width(4f)
                        height(14f)
                        borderRadius(2f)
                        backgroundColor(StockChatTheme.accent)
                        marginRight(8f)
                    }
                }
                Text {
                    attr {
                        text("AI 数据解读 · 本地演示")
                        fontSize(15f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
            // A keyed loop rebuilds the card for point changes without resetting the chart viewport.
            vfor({ ObservableList(mutableListOf(owner.selectedIndex ?: -1)) }) { index ->
                val data = (owner.chartResult as? MarketChartResult.Content)?.data
                val conclusion = data?.let { owner.controller.conclusion(owner.period, it, index.takeIf { value -> value >= 0 }) }
                if (conclusion != null) {
                    ChartConclusionCard(conclusion, resolveDetailEvidence(conclusion.reference,
                        owner.snapshot.quote.symbol, owner.period, owner.snapshot.quote.updatedAt, data.points)) {
                        conclusion.reference?.let(owner::locateEvidence)
                    }
                }
            }
            Text {
                attr {
                    text("点选 K 线看解释，点解释查看依据。演示信息仅供参考，不构成投资建议。")
                    fontSize(10f)
                    lineHeight(16f)
                    color(StockChatTheme.textTertiary)
                    marginTop(5f)
                }
            }
        }
    }
}
