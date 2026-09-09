package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun StockMarketPanel.Control(container: ViewContainer<*, *>, label: String, action: () -> Unit) = with(container) {
    View {
        attr {
            minWidth(29f)
            height(28f)
            paddingLeft(5f)
            paddingRight(5f)
            justifyContentCenter()
            alignItemsCenter()
            marginLeft(4f)
            borderRadius(6f)
            backgroundColor(StockChatTheme.surfaceSoft)
        }
        event { click { action() } }
        Text {
            attr {
                text(label)
                fontSize(12f)
                color(StockChatTheme.textPrimary)
            }
        }
    }
}

internal fun StockMarketPanel.Status(container: ViewContainer<*, *>, message: String, action: (() -> Unit)? = null) = with(container) {
    View {
        attr {
            height(180f)
            padding(18f)
            justifyContentCenter()
            alignItemsCenter()
            borderRadius(16f)
            backgroundColor(StockChatTheme.surface)
        }
        event { click { action?.invoke() } }
        Text {
            attr {
                text(message)
                fontSize(13f)
                lineHeight(20f)
                color(StockChatTheme.textSecondary)
            }
        }
    }
}
