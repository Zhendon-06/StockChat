package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.Overview(container: ViewContainer<*, *>) {
    val snapshot = this.snapshot
    with(container) {
        View {
            attr {
                padding(16f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("行情摘要")
                    fontSize(16f)
                    fontWeightMedium()
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
                    text("流通市值 ${unit(snapshot.floatMarketValue, "亿")}\n价格与成交量可用于观察活跃度；资金净额与短期涨跌可能背离，请结合公告、财务与估值分析。")
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
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("今日表现")
                    fontSize(16f)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("开盘 ${snapshot.open} · 最高 ${snapshot.high} · 最低 ${snapshot.low} · 昨收 ${snapshot.previousClose}")
                    fontSize(13f)
                    lineHeight(22f)
                    color(StockChatTheme.textSecondary)
                    marginTop(10f)
                }
            }
            Text {
                attr {
                    text("成交额 ${amount(snapshot.amount, snapshot.amountUnit)}\n" +
                    "成交量 ${quantity(snapshot.volume, snapshot.volumeUnit)} · 振幅 ${percent(snapshot.amplitude)}")
                    fontSize(12f)
                    lineHeight(21f)
                    color(StockChatTheme.textSecondary)
                    marginTop(12f)
                }
            }
            Text {
                attr {
                    text("指数点位反映样本整体表现，数据为演示行情，仅供参考，不构成投资建议。")
                    fontSize(11f)
                    lineHeight(18f)
                    color(StockChatTheme.textTertiary)
                    marginTop(12f)
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
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("指数简况")
                    fontSize(16f)
                    fontWeightMedium()
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
