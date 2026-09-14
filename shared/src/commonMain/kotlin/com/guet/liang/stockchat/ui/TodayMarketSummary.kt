package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSectorObservation
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场采用非对称 Bento 构图：主卡、辅卡和底板各司其职，内容之间不互相遮挡。


internal fun MarketPlainSummary(
    container: ViewContainer<*, *>,
    snapshot: TodayMarketSnapshot,
    contentWidth: Float,
    scale: Float,
) {
    with(container) {
        View {
            attr {
                width(contentWidth)
                alignSelfCenter()
                marginTop(22f * scale)
                padding(left = 2f * scale, right = 2f * scale)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        width(4f * scale)
                        height(15f * scale)
                        borderRadius(2f * scale)
                        backgroundColor(StockChatTheme.accent)
                        marginRight(8f * scale)
                    }
                }
                Text {
                    attr {
                        text("白话小结")
                        fontSize(16f * scale)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
            Text {
                attr {
                    text(snapshot.summary)
                    fontSize(14f * scale)
                    lineHeight(23f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(10f * scale)
                }
            }
            Text {
                attr {
                    text(snapshot.sourceLabel)
                    fontSize(11f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(10f * scale)
                }
            }
        }
    }
}

