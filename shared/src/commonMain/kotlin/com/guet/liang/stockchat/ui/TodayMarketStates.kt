package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场加载 / 空数据 / 错误状态。

internal fun ViewContainer<*, *>.TodayMarketLoading(scale: Float) {
    View {
        attr {
            height(260f * scale)
            allCenter()
        }
        Text {
            attr {
                text("正在整理今日市场…")
                fontSize(15f * scale)
                color(StockChatTheme.textSecondary)
            }
        }
        Text {
            attr {
                text("稍等一下，先看指数整体表现")
                fontSize(12f * scale)
                color(StockChatTheme.textTertiary)
                marginTop(8f * scale)
            }
        }
    }
}

internal fun ViewContainer<*, *>.TodayMarketEmpty(
    scale: Float,
    onRetry: () -> Unit,
) {
    View {
        attr {
            height(260f * scale)
            allCenter()
        }
        Text {
            attr {
                text("暂时没有市场数据")
                fontSize(16f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
            }
        }
        View {
            attr {
                height(36f * scale)
                borderRadius(18f * scale)
                padding(left = 16f * scale, right = 16f * scale)
                backgroundColor(StockChatTheme.accent)
                allCenter()
                marginTop(14f * scale)
            }
            event {
                click { onRetry() }
            }
            Text {
                attr {
                    text("重新加载")
                    fontSize(13f * scale)
                    fontWeightMedium()
                    color(Color.WHITE)
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.TodayMarketError(
    message: String,
    scale: Float,
    onRetry: () -> Unit,
) {
    View {
        attr {
            height(260f * scale)
            allCenter()
            padding(left = 30f * scale, right = 30f * scale)
        }
        Text {
            attr {
                text("市场数据加载失败")
                fontSize(16f * scale)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
            }
        }
        Text {
            attr {
                text(message.ifBlank { "请稍后重试" })
                fontSize(13f * scale)
                color(StockChatTheme.textSecondary)
                textAlignCenter()
                marginTop(9f * scale)
            }
        }
        View {
            attr {
                height(36f * scale)
                borderRadius(18f * scale)
                padding(left = 16f * scale, right = 16f * scale)
                backgroundColor(StockChatTheme.accent)
                allCenter()
                marginTop(14f * scale)
            }
            event {
                click { onRetry() }
            }
            Text {
                attr {
                    text("重新加载")
                    fontSize(13f * scale)
                    fontWeightMedium()
                    color(Color.WHITE)
                }
            }
        }
    }
}
