package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.ResearchAppendix(container: ViewContainer<*, *>) {
    val snapshot = this.snapshot
    val quote = snapshot.quote
    with(container) {
        SectionTitle(this, "估值与市值", "长期参考，非短期信号")
        View {
            attr {
                padding(15f)
                borderRadius(17f)
                backgroundColor(StockChatTheme.surface)
            }
            MetricRow(this, "市盈率 TTM", snapshot.priceEarningsRatio.ifBlank { "--" }, "市净率", snapshot.priceBookRatio.ifBlank { "--" })
            MetricRow(this, "总市值", unit(snapshot.totalMarketValue, "亿"), "流通市值", unit(snapshot.floatMarketValue, "亿"))
        }
        SectionTitle(this, "公司资料", "背景信息，低频查看")
        View {
            attr {
                padding(15f)
                borderRadius(17f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("${quote.name} · ${quote.marketLabel}")
                    fontSize(13f)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text(quote.summary.ifBlank { "当前标的暂无公司简介，已优先展示行情、走势和 AI 解读。" })
                    fontSize(12f)
                    lineHeight(19f)
                    color(StockChatTheme.textSecondary)
                    marginTop(7f)
                }
            }
            Text {
                attr {
                    text("公告和研报数据源将在接入后按日期展示；暂不使用过期或无法核验的内容。")
                    fontSize(11f)
                    lineHeight(17f)
                    color(StockChatTheme.textTertiary)
                    marginTop(8f)
                }
            }
            Text {
                attr {
                    text("数据代码 ${snapshot.providerSymbol.uppercase()} · 更新时间 ${quote.updatedAt}")
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                    marginTop(8f)
                }
            }
        }
    }
}

private fun SectionTitle(container: ViewContainer<*, *>, title: String, subtitle: String) {
    with(container) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginTop(20f)
            marginBottom(9f)
        }
        View {
            attr {
                width(4f)
                height(15f)
                borderRadius(2f)
                backgroundColor(StockChatTheme.accent)
                marginRight(8f)
            }
        }
        Text {
            attr {
                text(title)
                fontSize(17f)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
                flex(1f)
            }
        }
        Text {
            attr {
                text(subtitle)
                fontSize(10f)
                color(StockChatTheme.textTertiary)
            }
        }
    }
    }
}

private fun MetricRow(
    container: ViewContainer<*, *>,
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    with(container) {
        View {
            attr { flexDirectionRow(); marginBottom(12f) }
            MetricCell(this, firstLabel, firstValue)
            MetricCell(this, secondLabel, secondValue)
        }
    }
}

private fun MetricCell(container: ViewContainer<*, *>, label: String, value: String) {
    with(container) {
        View {
            attr { flex(1f) }
            Text { attr { text(label); fontSize(11f); color(StockChatTheme.textTertiary) } }
            Text { attr { text(value); fontSize(14f); fontWeightMedium(); color(StockChatTheme.textPrimary); marginTop(4f) } }
        }
    }
}

internal fun StockMarketPanel.Overview(container: ViewContainer<*, *>) {
    val snapshot = this.snapshot
    with(container) {
        View {
            attr {
                padding(16f)
                borderRadius(18f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("行情摘要")
                    fontSize(16f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text(snapshot.quote.summary)
                    fontSize(13f)
                    lineHeight(22f)
                    color(StockChatTheme.textSecondary)
                    marginTop(10f)
                }
            }
            Text {
                attr {
                    text("价格与成交量可用于观察活跃度；资金净额与短期涨跌可能背离，请结合公告、财务与估值分析。")
                    fontSize(12f)
                    lineHeight(21f)
                    color(StockChatTheme.textSecondary)
                    marginTop(12f)
                }
            }
            Text {
                attr {
                    text("可切换顶部「AI 预测」查看解读及风险依据。仅供参考，不构成投资建议。")
                    fontSize(11f)
                    lineHeight(18f)
                    color(StockChatTheme.textTertiary)
                    marginTop(12f)
                }
            }
        }
    }
}

internal fun StockMarketPanel.IndexOverview(container: ViewContainer<*, *>) {
    val snapshot = this.snapshot
    with(container) {
        View {
            attr {
                padding(16f)
                borderRadius(18f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("今日表现")
                    fontSize(16f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    marginTop(12f)
                }
                MetricCell(this, "今开", snapshot.open)
                MetricCell(this, "最高", snapshot.high)
                MetricCell(this, "最低", snapshot.low)
                MetricCell(this, "昨收", snapshot.previousClose)
            }
            View {
                attr {
                    flexDirectionRow()
                    marginTop(13f)
                }
                MetricCell(this, "成交额", amount(snapshot.amount, snapshot.amountUnit))
                MetricCell(this, "成交量", quantity(snapshot.volume, snapshot.volumeUnit))
                MetricCell(this, "振幅", percent(snapshot.amplitude))
            }
            Text {
                attr {
                    text("指数点位反映样本整体表现，数据为演示行情，仅供参考，不构成投资建议。")
                    fontSize(11f)
                    lineHeight(18f)
                    color(StockChatTheme.textTertiary)
                    marginTop(13f)
                }
            }
        }
    }
}

internal fun StockMarketPanel.IndexProfile(container: ViewContainer<*, *>) {
    val snapshot = this.snapshot
    with(container) {
        View {
            attr {
                padding(16f)
                borderRadius(18f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("指数简况")
                    fontSize(16f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("标的名称  ${snapshot.quote.name}\n交易代码  ${snapshot.providerSymbol.uppercase()}\n所属市场  ${snapshot.quote.marketLabel}")
                    fontSize(13f)
                    lineHeight(23f)
                    color(StockChatTheme.textSecondary)
                    marginTop(10f)
                }
            }
            Text {
                attr {
                    text("市盈率 ${snapshot.priceEarningsRatio.ifBlank { "--" }} · " +
                    "市净率 ${snapshot.priceBookRatio.ifBlank { "--" }}\n指数说明：${snapshot.quote.summary}")
                    fontSize(12f)
                    lineHeight(21f)
                    color(StockChatTheme.textSecondary)
                    marginTop(12f)
                }
            }
            Text {
                attr {
                    text("估值与指数说明仅作信息展示。仅供参考，不构成投资建议。")
                    fontSize(11f)
                    lineHeight(18f)
                    color(StockChatTheme.textTertiary)
                    marginTop(12f)
                }
            }
        }
    }
}
