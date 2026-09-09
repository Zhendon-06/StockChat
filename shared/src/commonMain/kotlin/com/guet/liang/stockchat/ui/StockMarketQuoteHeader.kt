package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.QuoteHeader(container: ViewContainer<*, *>) {
    val snapshot = this.snapshot
    val owner = this
    val q = snapshot.quote
    with(container) {
        View {
            attr {
                padding(16f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(q.name)
                        fontSize(21f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text(snapshot.providerSymbol.uppercase())
                        fontSize(12f)
                        color(StockChatTheme.textSecondary)
                    }
                }
            }
            Text {
                attr {
                    text(q.marketLabel.replace(" · 腾讯行情", "") + " · 行情快照")
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                    marginTop(4f)
                }
            }
            owner.QuotePrice(this)
            val stats = listOf(
                "今开" to snapshot.open, "最高" to snapshot.high, "最低" to snapshot.low,
                "昨收" to snapshot.previousClose, "成交量" to quantity(snapshot.volume, snapshot.volumeUnit),
                "成交额" to amount(snapshot.amount, snapshot.amountUnit),
                "换手率" to percent(snapshot.turnoverRate), "振幅" to percent(snapshot.amplitude), "量比" to snapshot.volumeRatio,
                "市盈率TTM" to snapshot.priceEarningsRatio, "总市值" to unit(snapshot.totalMarketValue, "亿"), "市净率" to snapshot.priceBookRatio,
            )

            metricRows(this, stats.take(6))
            View {
                attr {
                    flexDirectionRow()
                    marginTop(13f)
                }
                Text {
                    attr {
                        text("换手 ${percent(snapshot.turnoverRate)}  ·  量比 ${snapshot.volumeRatio.ifBlank { "--" }}")
                        fontSize(10f)
                        color(StockChatTheme.textSecondary)
                        flex(1f)
                    }
                }
                View {
                    event { click { owner.expandedMetrics = !owner.expandedMetrics } }
                    Text {
                        attr {
                            text(if (owner.expandedMetrics) "收起 ∧" else "更多指标 ∨")
                            fontSize(10f)
                            color(StockChatTheme.textSecondary)
                        }
                    }
                }
            }
            vif({ owner.expandedMetrics }) { metricRows(this, stats.drop(6)) }

        }
    }
}

private fun metricRows(target: ViewContainer<*, *>, items: List<Pair<String, String>>) {
    with(target) {
        items.chunked(3).forEach { row ->
            View {
                attr {
                    flexDirectionRow()
                    marginTop(11f)
                }
                row.forEach { (label, value) ->
                    View {
                        attr { flex(1f) }
                        Text {
                            attr {
                                text(label)
                                fontSize(10f)
                                color(StockChatTheme.textTertiary)
                            }
                        }
                        Text {
                            attr {
                                text(value.ifBlank {
                                        "--"
                                })
                                fontSize(12f)
                                fontWeightMedium()
                                color(StockChatTheme.textPrimary)
                                marginTop(3f)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun StockMarketPanel.QuotePrice(container: ViewContainer<*, *>) {
    val owner = this
    with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsFlexEnd()
                marginTop(9f)
            }
            Text {
                attr {
                    text(owner.snapshot.quote.price)
                    fontSize(38f)
                    fontWeightBold()
                    color(if (owner.snapshot.quote.isPositive) StockChatTheme.positive else StockChatTheme.negative)
                }
            }
            View {
                attr {
                    marginLeft(14f)
                    marginBottom(5f)
                }
                Text {
                    attr {
                        text(owner.snapshot.quote.change)
                        fontSize(15f)
                        fontWeightMedium()
                        color(if (owner.snapshot.quote.isPositive) StockChatTheme.positive else StockChatTheme.negative)
                    }
                }
                Text {
                    attr {
                        text(owner.snapshot.quote.changePercent)
                        fontSize(15f)
                        fontWeightMedium()
                        color(if (owner.snapshot.quote.isPositive) StockChatTheme.positive else StockChatTheme.negative)
                    }
                }
            }
        }
    }
}
