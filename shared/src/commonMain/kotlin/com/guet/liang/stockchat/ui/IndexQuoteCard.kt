package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklychart.AreaChart
import com.guet.liang.kuiklychart.api.ChartLegendPosition
import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.stockchat.model.StockQuote
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** Daily closing points are explicitly labelled as such, rather than presented as intraday/K-line data. */
internal fun ViewContainer<*, *>.IndexQuoteCard(quote: StockQuote, scale: Float, onClick: () -> Unit) {
    val values = quote.trendPoints.filter { it.isFinite() && it > 0f }
    val movementColor = if (quote.isPositive) StockChatTheme.positive else StockChatTheme.negative
    View {
        attr { padding(14f * scale); borderRadius(16f * scale); backgroundColor(StockChatTheme.surface) }
        event { click { onClick() } }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text { attr { text(quote.name); fontSize(17f * scale); fontWeightBold(); color(StockChatTheme.textPrimary); flex(1f) } }
            Text { attr { text("分时 / K线  ›"); fontSize(11f * scale); color(StockChatTheme.accent) } }
        }
        View {
            attr { flexDirectionRow(); alignItemsFlexEnd(); marginTop(10f * scale) }
            Text { attr { text(quote.price); fontSize(27f * scale); fontWeightBold(); color(movementColor); flex(1f) } }
            Text { attr { text("${quote.change}  ${quote.changePercent}"); fontSize(13f * scale); fontWeightMedium(); color(movementColor); marginBottom(4f) } }
        }
        Text {
            attr {
                text(if (quote.updatedAt.contains("非实时")) "本地演示走势 · 非实时" else "近${values.size}期日线收盘 · 点位纵轴独立缩放")
                fontSize(10f * scale); color(StockChatTheme.textTertiary); marginTop(9f * scale)
            }
        }
        if (values.size > 1) {
            AreaChart {
                attr { height(96f * scale); marginTop(8f * scale); touchEnable(false) }
                chart {
                    area("收盘点位", values) { color = movementColor; showPoints = false; lineWidth = 1.4f; fillOpacity = 0.12f }
                    legend { position = ChartLegendPosition.NONE }
                    axes {
                        x { visible = false; showLabels = false }
                        y { includeZero = false; tickCount = 3; formatter = { financialNumber(it) } }
                    }
                    theme {
                        backgroundColor = StockChatTheme.surface; textColor = StockChatTheme.textSecondary
                        mutedTextColor = StockChatTheme.textTertiary; gridColor = StockChatTheme.border
                        contentPadding(left = 2f, top = 6f, right = 2f, bottom = 3f)
                    }
                }
            }
        } else Text { attr { text("历史数据不足 · 点击详情重新加载"); fontSize(11f); color(StockChatTheme.textTertiary); marginTop(10f) } }
        Text { attr { text(quote.updatedAt); fontSize(10f * scale); color(StockChatTheme.textTertiary); marginTop(7f * scale) } }
    }
}
