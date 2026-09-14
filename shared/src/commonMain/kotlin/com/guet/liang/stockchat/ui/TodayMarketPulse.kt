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


internal fun ViewContainer<*, *>.MarketPulseBento(
    snapshot: TodayMarketSnapshot,
    contentWidth: Float,
    scale: Float,
) {
    val total = (
        snapshot.advancingCount +
            snapshot.decliningCount +
            snapshot.unchangedCount
        ).coerceAtLeast(1)
    val advancingRatio = (snapshot.advancingCount.toFloat() / total * 100f).toInt()
    val mainWidth = contentWidth * 0.68f
    val sideWidth = contentWidth - mainWidth - 10f * scale
    val trendColor = if (snapshot.advancingCount >= snapshot.decliningCount) {
        StockChatTheme.positive
    } else {
        StockChatTheme.negative
    }
    View {
        attr {
            width(contentWidth)
            height(207f * scale)
            positionRelative()
        }
        // 只做背景层，不承载文字，避免装饰层与数据层争夺视觉焦点。
        View {
            attr {
                absolutePosition(top = 9f * scale, left = 10f * scale, right = 0f)
                height(188f * scale)
                borderRadius(25f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(mainWidth)
                height(194f * scale)
                padding(
                    top = 16f * scale,
                    left = 17f * scale,
                    bottom = 13f * scale,
                    right = 17f * scale,
                )
                borderRadius(23f * scale)
                backgroundLinearGradient(
                    Direction.TO_BOTTOM_RIGHT,
                    ColorStop(StockChatTheme.marketMoodBackgroundStart, 0f),
                    ColorStop(StockChatTheme.marketMoodBackgroundEnd, 1f),
                )
                themedBorder()
                boxShadow(BoxShadow(0f, 6f * scale, 16f * scale, Color(0x16000000)))
                zIndex(2)
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(8f * scale, 8f * scale)
                        borderRadius(4f * scale)
                        backgroundColor(trendColor)
                        marginRight(7f * scale)
                    }
                }
                Text {
                    attr {
                        text("市场脉搏")
                        fontSize(13f * scale)
                        fontWeightMedium()
                        color(StockChatTheme.textSecondary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text(if (snapshot.isDemo) "演示快照" else "实时快照")
                        fontSize(10f * scale)
                        color(StockChatTheme.textTertiary)
                    }
                }
            }
            Text {
                attr {
                    text(if (snapshot.advancingCount >= snapshot.decliningCount) "热度回升" else "分化延续")
                    fontSize(24f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(13f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text("上涨 ${snapshot.advancingCount} · 下跌 ${snapshot.decliningCount} · 持平 ${snapshot.unchangedCount}")
                    fontSize(13f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(6f * scale)
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    marginTop(15f * scale)
                }
                PulseMetric("上涨占比", "$advancingRatio%", StockChatTheme.positive, scale)
                PulseMetricDivider(scale)
                PulseMetric("覆盖指数", "${snapshot.indices.size} 个", StockChatTheme.accent, scale)
            }
            Text {
                attr {
                    text("先看基准，再看结构")
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(11f * scale)
                }
            }
        }
        PulseSideCard(
            container = this,
            title = "情绪",
            value = snapshot.mood,
            detail = "样本状态",
            width = sideWidth,
            top = 7f * scale,
            height = 84f * scale,
            tint = trendColor,
            scale = scale,
        )
        PulseSideCard(
            container = this,
            title = "覆盖",
            value = "${snapshot.indices.size} 个",
            detail = "主要指数",
            width = sideWidth,
            top = 101f * scale,
            height = 84f * scale,
            tint = StockChatTheme.accent,
            scale = scale,
        )
    }
}

private fun ViewContainer<*, *>.PulseMetric(
    label: String,
    value: String,
    tint: Color,
    scale: Float,
) {
    View {
        attr { flex(1f) }
        Text {
            attr {
                text(label)
                fontSize(9f * scale)
                color(StockChatTheme.textTertiary)
            }
        }
        Text {
            attr {
                text(value)
                fontSize(13f * scale)
                fontWeightMedium()
                color(tint)
                marginTop(3f * scale)
                lines(1)
            }
        }
    }
}

private fun ViewContainer<*, *>.PulseMetricDivider(scale: Float) {
    View {
        attr {
            width(1f * scale)
            height(24f * scale)
            backgroundColor(StockChatTheme.border)
            margin(left = 8f * scale, right = 8f * scale)
        }
    }
}

private fun PulseSideCard(
    container: ViewContainer<*, *>,
    title: String,
    value: String,
    detail: String,
    width: Float,
    top: Float,
    height: Float,
    tint: Color,
    scale: Float,
) {
    with(container) {
        View {
            attr {
                absolutePosition(top = top, right = 0f)
                width(width)
                height(height)
                padding(top = 12f * scale, left = 12f * scale, bottom = 11f * scale, right = 11f * scale)
                borderRadius(18f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                boxShadow(BoxShadow(0f, 4f * scale, 11f * scale, Color(0x14000000)))
                zIndex(3)
            }
            Text {
                attr {
                    text(title)
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                }
            }
            Text {
                attr {
                    text(value)
                    fontSize(21f * scale)
                    fontWeightBold()
                    color(tint)
                    marginTop(7f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(detail)
                    fontSize(9f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(3f * scale)
                }
            }
        }
    }
}

