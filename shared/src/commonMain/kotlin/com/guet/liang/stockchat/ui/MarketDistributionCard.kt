package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.PieChart
import com.guet.liang.kuiklychart.api.ChartLegendPosition
import com.guet.liang.kuiklychart.api.PieEntry
import com.guet.liang.kuiklychart.api.PieLabelMode
import com.guet.liang.kuiklychart.finance.financialNumber
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class MarketDistributionEntry(val label: String, val magnitude: Float, val valueLabel: String, val color: Color)

/** Labels remain outside the ring, legible on narrow phones; selection is shown in its center. */
internal fun ViewContainer<*, *>.MarketDistributionCard(
    title: String,
    entries: List<MarketDistributionEntry>,
    centerText: String,
    centerDetail: String,
    note: String,
) {
    val valid = entries.filter { it.magnitude.isFinite() && it.magnitude >= 0f }
    val total = valid.sumOf { it.magnitude.toDouble() }.toFloat()
    View {
        attr { padding(14f); marginTop(12f); borderRadius(16f); backgroundColor(StockChatTheme.surface) }
        Text { attr { text(title); fontSize(16f); fontWeightMedium(); color(StockChatTheme.textPrimary) } }
        View {
            attr { flexDirectionRow(); alignItemsCenter(); marginTop(8f) }
            PieChart {
                attr { width(146f); height(182f) }
                chart {
                    emptyText = "暂无分布数据"
                    pie("占比", valid.map { PieEntry(it.label, it.magnitude, it.color) })
                    legend { position = ChartLegendPosition.NONE }
                    pie {
                        innerRadiusRatio = 0.62f
                        sliceSpacingAngle = 0.025f
                        labelMode = PieLabelMode.NONE
                        this.centerText = centerText
                        centerSubtext = centerDetail
                    }
                    theme {
                        backgroundColor = StockChatTheme.surface
                        textColor = StockChatTheme.textPrimary
                        mutedTextColor = StockChatTheme.textTertiary
                        contentPadding(0f)
                    }
                    tooltip { enabled = false }
                }
            }
            View {
                attr { flex(1f); marginLeft(8f) }
                valid.forEach { entry ->
                    View {
                        attr { marginTop(7f); marginBottom(7f) }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter() }
                            View { attr { size(6f, 6f); borderRadius(3f); backgroundColor(entry.color); marginRight(5f) } }
                            Text { attr { text(entry.label); fontSize(10f); color(StockChatTheme.textSecondary) } }
                        }
                        Text {
                            attr {
                                text(entry.valueLabel + if (total > 0) "  ${financialNumber(entry.magnitude / total * 100f, 1)}%" else "  --")
                                fontSize(11f); fontWeightMedium(); color(entry.color); marginTop(4f)
                            }
                        }
                    }
                }
            }
        }
        Text { attr { text(note); fontSize(10f); lineHeight(16f); color(StockChatTheme.textTertiary); marginTop(3f) } }
    }
}
