package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.MarketOrderLevel

import com.guet.liang.kuiklychart.finance.financialNumber
import com.guet.liang.kuiklychart.finance.financialVolume
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.OrderBook(container: ViewContainer<*, *>) {
    val snapshot = this.snapshot
    val owner = this
    with(container) {
        View {
            attr {
                padding(14f)
                borderRadius(16f)
                backgroundColor(StockChatTheme.surface)
            }
            Text {
                attr {
                    text("五档盘口")
                    fontSize(16f)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                }
            }
            if (snapshot.orderBook.isEmpty()) {
                Text {
                    attr {
                        text("该标的暂无五档报价，停牌或指数可能不提供盘口。")
                        fontSize(12f)
                        color(StockChatTheme.textSecondary)
                        marginTop(14f)
                    }
                }
            } else {
                View {
                    attr {
                        flexDirectionRow()
                        marginTop(12f)
                    }
                    listOf("买", "卖").forEach { side ->
                        owner.OrderSide(this, side)
                    }
                }
                val bids = snapshot.orderBook.filter { it.side == "买" }.sumOf { it.volume.toDouble() }
                val asks = snapshot.orderBook.filter { it.side == "卖" }.sumOf { it.volume.toDouble() }
                val imbalance = if (bids + asks > 0) ((bids - asks) / (bids + asks) * 100).toFloat() else null
                Text {
                    attr {
                        text("五档委比  ${imbalance?.let { signed(it) + "%" } ?: "--"}  ·  买卖量仅统计当前五档")
                        fontSize(10f)
                        color(StockChatTheme.textTertiary)
                        marginTop(12f)
                    }
                }
            }
        }
    }
}

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
                        color(StockChatTheme.textTertiary)
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
