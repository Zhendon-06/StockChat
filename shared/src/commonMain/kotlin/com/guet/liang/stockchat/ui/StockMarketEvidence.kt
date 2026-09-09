package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.resolveDetailEvidence
import com.guet.liang.stockchat.model.ChartEvidenceReference
import com.guet.liang.stockchat.model.ChartEvidenceResolution
import com.guet.liang.stockchat.model.MarketChartResult
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

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
            Text {
                attr {
                    text("AI 数据解读 · 本地演示")
                    fontSize(14f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
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
