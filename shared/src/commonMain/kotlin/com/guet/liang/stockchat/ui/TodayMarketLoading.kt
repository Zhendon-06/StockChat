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


internal fun ViewContainer<*, *>.LoadingPulseBento(
    contentWidth: Float,
    scale: Float,
    phase: () -> Int,
) {
    val mainWidth = contentWidth * 0.68f
    val sideWidth = contentWidth - mainWidth - 10f * scale
    View {
        attr {
            width(contentWidth)
            height(207f * scale)
            positionRelative()
        }
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
                padding(17f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            LoadingBar(74f * scale, 13f * scale, scale, phase)
            LoadingBar(mainWidth * 0.62f, 26f * scale, scale, phase, marginTop = 15f * scale)
            LoadingBar(mainWidth * 0.5f, 13f * scale, scale, phase, marginTop = 8f * scale)
            View {
                attr {
                    flexDirectionRow()
                    marginTop(18f * scale)
                }
                repeat(2) {
                    View {
                        attr {
                            flex(1f)
                            if (it > 0) marginLeft(12f * scale)
                        }
                        LoadingBar(46f * scale, 9f * scale, scale, phase)
                        LoadingBar(58f * scale, 14f * scale, scale, phase, marginTop = 6f * scale)
                    }
                }
            }
        }
        LoadingSideBento(sideWidth, 7f * scale, scale, phase)
        LoadingSideBento(sideWidth, 101f * scale, scale, phase)
    }
}

private fun ViewContainer<*, *>.LoadingSideBento(
    width: Float,
    top: Float,
    scale: Float,
    phase: () -> Int,
) {
    View {
        attr {
            absolutePosition(top = top, right = 0f)
            width(width)
            height(84f * scale)
            padding(12f * scale)
            borderRadius(18f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
        }
        LoadingBar(42f * scale, 10f * scale, scale, phase)
        LoadingBar(width * 0.7f, 20f * scale, scale, phase, marginTop = 8f * scale)
        LoadingBar(48f * scale, 9f * scale, scale, phase, marginTop = 4f * scale)
    }
}

internal fun ViewContainer<*, *>.LoadingSectionTitle(
    scale: Float,
    phase: () -> Int,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginTop(20f * scale)
            marginBottom(10f * scale)
        }
        LoadingBar(4f * scale, 16f * scale, scale, phase)
        LoadingBar(84f * scale, 18f * scale, scale, phase, marginLeft = 8f * scale)
        View { attr { flex(1f) } }
        LoadingBar(92f * scale, 10f * scale, scale, phase)
    }
}

internal fun ViewContainer<*, *>.LoadingIndexBento(
    contentWidth: Float,
    scale: Float,
    phase: () -> Int,
) {
    val leadWidth = contentWidth * 0.61f
    val sideWidth = contentWidth - leadWidth - 9f * scale
    View {
        attr {
            width(contentWidth)
            height(248f * scale)
            positionRelative()
        }
        View {
            attr {
                absolutePosition(top = 7f * scale, left = 9f * scale, right = 0f)
                height(193f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(leadWidth)
                height(200f * scale)
                padding(16f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            LoadingBar(72f * scale, 22f * scale, scale, phase)
            LoadingBar(82f * scale, 18f * scale, scale, phase, marginTop = 12f * scale)
            LoadingBar(leadWidth * 0.68f, 30f * scale, scale, phase, marginTop = 7f * scale)
            LoadingBar(leadWidth - 32f * scale, 47f * scale, scale, phase, marginTop = 15f * scale)
        }
        LoadingIndexMiniBento(sideWidth, 2f * scale, scale, phase)
        LoadingIndexMiniBento(sideWidth, 101f * scale, scale, phase)
        View {
            attr {
                absolutePosition(top = 194f * scale, left = contentWidth * 0.13f)
                width(contentWidth * 0.74f)
                height(51f * scale)
                borderRadius(17f * scale)
                backgroundColor(StockChatTheme.accentSoft)
                themedBorder()
            }
            LoadingBar(contentWidth * 0.28f, 13f * scale, scale, phase, marginLeft = 13f * scale)
            LoadingBar(contentWidth * 0.18f, 13f * scale, scale, phase, marginLeft = 13f * scale)
        }
    }
}

private fun ViewContainer<*, *>.LoadingIndexMiniBento(
    width: Float,
    top: Float,
    scale: Float,
    phase: () -> Int,
) {
    View {
        attr {
            absolutePosition(top = top, right = 0f)
            width(width)
            height(88f * scale)
            padding(11f * scale)
            borderRadius(18f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
        }
        LoadingBar(width * 0.65f, 13f * scale, scale, phase)
        LoadingBar(width * 0.36f, 9f * scale, scale, phase, marginTop = 6f * scale)
        LoadingBar(width * 0.8f, 18f * scale, scale, phase, marginTop = 13f * scale)
    }
}

internal fun ViewContainer<*, *>.LoadingWatchBento(
    contentWidth: Float,
    scale: Float,
    phase: () -> Int,
) {
    val featureWidth = contentWidth * 0.61f
    val sideWidth = contentWidth - featureWidth - 9f * scale
    View {
        attr {
            width(contentWidth)
            height(310f * scale)
            positionRelative()
        }
        View {
            attr {
                absolutePosition(top = 8f * scale, left = 9f * scale, right = 0f)
                height(185f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(featureWidth)
                height(183f * scale)
                padding(15f * scale)
                borderRadius(22f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            LoadingBar(52f * scale, 10f * scale, scale, phase)
            LoadingBar(84f * scale, 20f * scale, scale, phase, marginTop = 13f * scale)
            LoadingBar(featureWidth * 0.64f, 28f * scale, scale, phase, marginTop = 6f * scale)
            LoadingBar(featureWidth - 30f * scale, 46f * scale, scale, phase, marginTop = 13f * scale)
        }
        View {
            attr {
                absolutePosition(top = 11f * scale, right = 0f)
                width(sideWidth)
                height(125f * scale)
                padding(12f * scale)
                borderRadius(19f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
            }
            LoadingBar(48f * scale, 10f * scale, scale, phase)
            LoadingBar(sideWidth * 0.8f, 16f * scale, scale, phase, marginTop = 10f * scale)
            LoadingBar(50f * scale, 20f * scale, scale, phase, marginTop = 12f * scale)
        }
        LoadingBar(contentWidth * 0.49f, 50f * scale, scale, phase, marginTop = 193f * scale, marginLeft = 3f * scale, radius = 16f * scale)
        LoadingBar(contentWidth * 0.63f, 50f * scale, scale, phase, marginTop = 249f * scale, marginLeft = contentWidth * 0.24f, radius = 16f * scale)
    }
}

private fun ViewContainer<*, *>.LoadingBar(
    width: Float,
    height: Float,
    scale: Float,
    phase: () -> Int,
    marginTop: Float = 0f,
    marginLeft: Float = 0f,
    radius: Float = height / 2f,
) {
    View {
        attr {
            size(width, height)
            borderRadius(radius)
            backgroundColor(StockChatTheme.surfaceSoft)
            if (marginTop > 0f) marginTop(marginTop)
            if (marginLeft > 0f) marginLeft(marginLeft)
            val current = phase()
            opacity(if (current % 2 == 0) 1f else 0.45f)
            animation(Animation.easeInOut(0.7f), current)
        }
    }
}
