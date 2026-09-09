package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.HamburgerButton(
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    View {
        attr {
            size(52f * scale, 52f * scale)
            borderRadius(26f * scale)
            backgroundColor(StockChatTheme.surface)
            boxShadow(
                BoxShadow(
                    1f * scale,
                    5f * scale,
                    14f * scale,
                    Color(0x1A000000),
                )
            )
            allCenter()
        }
        event {
            click { onClick() }
        }
        View {
            attr {
                size(22f * scale, 14f * scale)
            }
            View {
                attr {
                    absolutePosition(top = 2f * scale, left = 0f)
                    size(22f * scale, 2.5f * scale)
                    borderRadius(2f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
            View {
                attr {
                    absolutePosition(bottom = 2f * scale, left = 0f)
                    size(15f * scale, 2.5f * scale)
                    borderRadius(2f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.RiskNotice(scale: Float = 1f) {
    Text {
        attr {
            text("内容由 AI 生成")
            fontSize(11f * scale)
            color(StockChatTheme.textTertiary)
            textAlignCenter()
        }
    }
}
