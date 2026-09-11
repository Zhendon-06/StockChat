package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场加载 / 空数据 / 错误状态。

// 今日市场骨架屏：首帧就摆出市场温度卡、指数卡、样本股的版式，占位条按 phase 呼吸，
// 让"正在加载"和"没有数据"一眼可分。phase 由页面上的定时器驱动，首个 tick 延后一个周期，
// 避免骨架节点创建批次命中动画键后从 (0,0) 飞入。
private const val MARKET_SKELETON_DIM_OPACITY = 0.45f
private const val MARKET_SKELETON_PULSE_SECONDS = 0.7f

internal fun ViewContainer<*, *>.TodayMarketLoading(
    scale: Float,
    pageWidth: Float,
    skeletonPhase: () -> Int,
) {
    val contentWidth = (pageWidth - 36f * scale).coerceAtLeast(1f)
    View {
        attr { width(contentWidth) }
        MarketSkeletonMoodCard(scale, contentWidth, skeletonPhase)
        MarketSkeletonBar(width = contentWidth, height = 10f * scale, marginTop = 14f * scale, phase = skeletonPhase)
        MarketSkeletonSectionTitle(scale, skeletonPhase)
        repeat(4) {
            MarketSkeletonIndexCard(scale, contentWidth, skeletonPhase)
        }
        MarketSkeletonSectionTitle(scale, skeletonPhase)
        MarketSkeletonStockRow(scale, contentWidth, skeletonPhase)
        View {
            attr {
                alignItemsCenter()
                marginTop(22f * scale)
                marginBottom(8f * scale)
            }
            Text {
                attr {
                    text("正在整理今日市场…")
                    fontSize(14f * scale)
                    color(StockChatTheme.textSecondary)
                }
            }
            Text {
                attr {
                    text("稍等一下，先看指数整体表现")
                    fontSize(12f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(6f * scale)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketSkeletonMoodCard(scale: Float, contentWidth: Float, phase: () -> Int) {
    val innerWidth = contentWidth - 32f * scale
    View {
        attr {
            padding(16f * scale)
            borderRadius(20f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                justifyContentSpaceBetween()
            }
            MarketSkeletonBar(width = 76f * scale, height = 16f * scale, phase = phase)
            MarketSkeletonBar(width = 60f * scale, height = 28f * scale, phase = phase)
        }
        MarketSkeletonBar(width = innerWidth, height = 12f * scale, marginTop = 16f * scale, phase = phase)
        MarketSkeletonBar(width = innerWidth * 0.68f, height = 12f * scale, marginTop = 8f * scale, phase = phase)
        View {
            attr {
                flexDirectionRow()
                marginTop(16f * scale)
            }
            repeat(3) { index ->
                View {
                    attr {
                        flex(1f)
                        if (index > 0) marginLeft(10f * scale)
                        padding(10f * scale)
                        borderRadius(12f * scale)
                        backgroundColor(StockChatTheme.background)
                    }
                    MarketSkeletonBar(width = 40f * scale, height = 10f * scale, phase = phase)
                    MarketSkeletonBar(width = 56f * scale, height = 18f * scale, marginTop = 8f * scale, phase = phase)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketSkeletonSectionTitle(scale: Float, phase: () -> Int) {
    MarketSkeletonBar(width = 84f * scale, height = 18f * scale, marginTop = 20f * scale, phase = phase)
    MarketSkeletonBar(width = 132f * scale, height = 10f * scale, marginTop = 8f * scale, marginBottom = 10f * scale, phase = phase)
}

private fun ViewContainer<*, *>.MarketSkeletonIndexCard(scale: Float, contentWidth: Float, phase: () -> Int) {
    View {
        attr {
            width(contentWidth)
            flexDirectionRow()
            alignItemsCenter()
            padding(14f * scale)
            marginBottom(10f * scale)
            borderRadius(16f * scale)
            backgroundColor(StockChatTheme.surface)
            themedBorder()
        }
        View {
            attr { flex(1f) }
            MarketSkeletonBar(width = 80f * scale, height = 14f * scale, phase = phase)
            MarketSkeletonBar(width = 56f * scale, height = 10f * scale, marginTop = 8f * scale, phase = phase)
            MarketSkeletonBar(width = 110f * scale, height = 22f * scale, marginTop = 12f * scale, phase = phase)
        }
        MarketSkeletonBar(width = 96f * scale, height = 48f * scale, radius = 8f * scale, phase = phase)
    }
}

private fun ViewContainer<*, *>.MarketSkeletonStockRow(scale: Float, contentWidth: Float, phase: () -> Int) {
    val gap = 10f * scale
    val cardWidth = (contentWidth - gap * 2f) / 3f
    View {
        attr {
            flexDirectionRow()
            width(contentWidth)
        }
        repeat(3) { index ->
            View {
                attr {
                    width(cardWidth)
                    height(78f * scale)
                    if (index > 0) marginLeft(gap)
                    padding(12f * scale)
                    borderRadius(15f * scale)
                    backgroundColor(StockChatTheme.surface)
                    themedBorder()
                }
                MarketSkeletonBar(width = cardWidth * 0.55f, height = 12f * scale, phase = phase)
                MarketSkeletonBar(width = cardWidth * 0.4f, height = 10f * scale, marginTop = 8f * scale, phase = phase)
                MarketSkeletonBar(width = cardWidth * 0.7f, height = 14f * scale, marginTop = 10f * scale, phase = phase)
            }
        }
    }
}

/** Breathing placeholder bar; the phase read here is the animation key, so nothing else observable is read in this block. */
private fun ViewContainer<*, *>.MarketSkeletonBar(
    width: Float,
    height: Float,
    phase: () -> Int,
    radius: Float = height / 2f,
    marginTop: Float = 0f,
    marginBottom: Float = 0f,
) {
    View {
        attr {
            size(width, height)
            borderRadius(radius)
            backgroundColor(StockChatTheme.surfaceSoft)
            if (marginTop > 0f) marginTop(marginTop)
            if (marginBottom > 0f) marginBottom(marginBottom)
            val current = phase()
            opacity(if (current % 2 == 0) 1f else MARKET_SKELETON_DIM_OPACITY)
            animation(Animation.easeInOut(MARKET_SKELETON_PULSE_SECONDS), current)
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
