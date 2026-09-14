package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.MarketOrderLevel
import com.guet.liang.stockchat.model.TencentMarketSnapshot

import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.kuiklychart.finance.financialVolume
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.OrderBook(container: ViewContainer<*, *>) {
    val snapshot = this.snapshot
    val owner = this
    val metrics = orderBookMetrics(snapshot)
    with(container) {
        View {
            attr {
                padding(14f)
                borderRadius(18f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("五档盘口")
                    fontSize(16f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            if (snapshot.orderBook.isEmpty()) {
                OrderBookEmptyState(this)
            } else {
                OrderBookLevels(this, owner, metrics)
                OrderBookImbalance(this, metrics)
            }
        }
    }
}

private fun OrderBookEmptyState(container: ViewContainer<*, *>) {
    with(container) {
        Text {
            attr {
                text("该标的暂无五档报价，停牌或指数可能不提供盘口。")
                fontSize(12f)
                color(StockChatTheme.textSecondary)
                marginTop(14f)
            }
        }
    }
}

private fun OrderBookLevels(container: ViewContainer<*, *>, owner: StockMarketPanel, metrics: OrderBookMetrics) {
    val bidRatio = metrics.bidRatio
    with(container) {
        View {
            attr {
                flexDirectionRow()
                marginTop(12f)
            }
            listOf("买", "卖").forEach { side ->
                owner.OrderSide(this, side)
            }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(10f)
            }
            View {
                attr {
                    flex(bidRatio.coerceIn(0.03f, 0.97f))
                    height(6f)
                    borderRadius(3f)
                    backgroundColor(StockChatTheme.positive)
                }
            }
            View {
                attr {
                    flex((1f - bidRatio).coerceIn(0.03f, 0.97f))
                    height(6f)
                    borderRadius(3f)
                    marginLeft(2f)
                    backgroundColor(StockChatTheme.negative)
                }
            }
        }
    }
}

private fun OrderBookImbalance(container: ViewContainer<*, *>, metrics: OrderBookMetrics) {
    val imbalance = metrics.imbalance
    with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(8f)
            }
            Text {
                attr {
                    text("五档委比  ")
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                }
            }
            Text {
                attr {
                    text(imbalance?.let { signed(it) + "%" } ?: "--")
                    fontSize(10f)
                    fontWeightMedium()
                    color(
                        when {
                            imbalance == null -> StockChatTheme.textTertiary
                            imbalance > 0f -> StockChatTheme.positive
                            imbalance < 0f -> StockChatTheme.negative
                            else -> StockChatTheme.textSecondary
                        }
                    )
                }
            }
            Text {
                attr {
                    text("  ·  买卖量仅统计当前五档")
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                }
            }
        }
    }
}

private data class OrderBookMetrics(val bidVolume: Double, val askVolume: Double) {
    private val totalVolume: Double get() = bidVolume + askVolume
    val bidRatio: Float get() = if (totalVolume > 0) (bidVolume / totalVolume).toFloat() else 0.5f
    val imbalance: Float? get() = if (totalVolume > 0) ((bidVolume - askVolume) / totalVolume * 100).toFloat() else null
}

private fun orderBookMetrics(snapshot: TencentMarketSnapshot): OrderBookMetrics = OrderBookMetrics(
    bidVolume = snapshot.orderBook.filter { it.side == "买" }.sumOf { it.volume.toDouble() },
    askVolume = snapshot.orderBook.filter { it.side == "卖" }.sumOf { it.volume.toDouble() },
)

internal fun StockMarketPanel.priceColor(value: Float?): Color {
    val previous = snapshot.previousClose.toFloatOrNull() ?: return StockChatTheme.textPrimary
    return when {
        value == null || value == previous -> StockChatTheme.textPrimary
        value > previous -> StockChatTheme.positive
        else -> StockChatTheme.negative
    }
}

internal fun StockMarketPanel.OrderSide(container: ViewContainer<*, *>, side: String) {
    val owner = this
    with(container) {
        View {
            attr {
                flex(1f)
                if (side == "买") marginRight(14f)
            }
            View {
                attr {
                    flexDirectionRow()
                    marginBottom(7f)
                }
                Text {
                    attr {
                        text(if (side == "买") "买盘" else "卖盘")
                        fontSize(10f)
                        fontWeightMedium()
                        color(if (side == "买") StockChatTheme.positive else StockChatTheme.negative)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text("价格 / ${owner.snapshot.volumeUnit}")
                        fontSize(10f)
                        color(StockChatTheme.textTertiary)
                    }
                }
            }
            val levels = owner.snapshot.orderBook.filter { it.side == side }
            val max = levels.maxOfOrNull { it.volume }?.coerceAtLeast(1f) ?: 1f
            for (index in 1..5) {
                val level = levels.firstOrNull { it.level == index }
                owner.OrderLevel(this, side, index, level, max)
            }
        }
    }
}

internal fun StockMarketPanel.OrderLevel(container: ViewContainer<*,
 *>,
    side: String,
    index: Int,
    level: MarketOrderLevel?,
    maximumVolume: Float) {
    val owner = this
    with(container) {
        View {
            attr {
                height(31f)
                justifyContentCenter()
            }
            View {
                attr {
                    absolutePosition(2f, 0f, 2f, 0f)
                    borderRadius(5f)
                    val sideColor = if (side == "买") StockChatTheme.positive else StockChatTheme.negative
                    val opacity = 0.04f + (level?.volume ?: 0f) / maximumVolume * 0.10f
                    backgroundColor(sideColor.opacity(opacity))
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    padding(3f)
                }
                Text {
                    attr {
                        text("$side$index")
                        fontSize(10f)
                        color(StockChatTheme.textSecondary)
                        width(25f)
                    }
                }
                Text {
                    attr {
                        text(level?.price?.let(::financialNumber) ?: "--")
                        fontSize(12f)
                        fontWeightMedium()
                        color(owner.priceColor(level?.price))
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text(level?.volume?.let(::financialVolume) ?: "--")
                        fontSize(10f)
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
        }
    }
}
