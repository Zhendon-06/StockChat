package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 主页「AI 问答 / 今日市场」分段开关与顶部胶囊：抽屉与主页共用同一状态。

internal const val HOME_TAB_CHAT = 0

internal const val HOME_TAB_TODAY_MARKET = 1

private const val HOME_CAPSULE_TRAVEL_DURATION = 0.46f

// 「AI 问答 / 今日市场」分段开关：主页与抽屉共用，选中态样式由同一状态驱动，保证两处一致
internal fun StockChatPage.HomeTabSwitcher(
    container: ViewContainer<*, *>,
    marginTopDp: Float = 0f,
    widthDp: Float? = null,
    elevated: Boolean = false,
    enabled: () -> Boolean = { true },
) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                if (widthDp != null) {
                    width(metrics.dp(widthDp))
                }
                height(metrics.dp(44f))
                borderRadius(metrics.dp(22f))
                backgroundColor(StockChatTheme.recessed)
                if (elevated) {
                    boxShadow(
                        BoxShadow(
                            0f,
                            metrics.dp(3f),
                            metrics.dp(12f),
                            Color(0x18000000),
                        )
                    )
                }
                flexDirectionRow()
                padding(all = metrics.dp(3f))
                touchEnable(enabled())
                if (marginTopDp > 0f) {
                    marginTop(metrics.dp(marginTopDp))
                }
            }
            ctx.HomeTabItem(this, "AI 问答", HOME_TAB_CHAT, enabled)
            ctx.HomeTabItem(this, "今日市场", HOME_TAB_TODAY_MARKET, enabled)
        }
    }
}

internal fun StockChatPage.HomeTabItem(
    container: ViewContainer<*, *>,
    label: String,
    tabIndex: Int,
    enabled: () -> Boolean = { true },
) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val selected = ctx.selectedHomeTab == tabIndex
                flex(1f)
                borderRadius(metrics.dp(19f))
                // 选中态为带阴影的白色胶囊，未选中态透明；阴影不参与布局，切换时布局稳定
                backgroundColor(
                    if (selected) StockChatTheme.surface else Color(0x00000000)
                )
                boxShadow(
                    BoxShadow(
                        metrics.dp(0f),
                        metrics.dp(2f),
                        metrics.dp(8f),
                        if (selected) Color(0x1F000000) else Color(0x00000000),
                    )
                )
                allCenter()
                // 触摸门禁只放在外层容器和 click 回调里：enabled() 读的是本节点
                // 未注册动画的 observable，放进 attr 会打断选中态动画
                animate(Animation.easeOut(0.18f), ctx.selectedHomeTab)
            }
            event {
                click {
                    if (enabled()) {
                        ctx.selectHomeTab(tabIndex)
                    }
                }
            }
            Text {
                attr {
                    text(label)
                    fontSize(metrics.dp(15f))
                    if (ctx.selectedHomeTab == tabIndex) {
                        fontWeightBold()
                    }
                    color(
                        if (ctx.selectedHomeTab == tabIndex) {
                            StockChatTheme.textPrimary
                        } else {
                            StockChatTheme.textSecondary
                        }
                    )
                }
            }
        }
    }
}

// 胶囊的可见性、位置与动画时钟只依赖 HomeFlow 的单一派生展示态。
internal fun StockChatPage.HomeTabCapsule(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val presentation = ctx.homeState.capsulePresentation
                val marketActive =
                    presentation == StockChatHomeCapsulePresentation.MARKET_BOTTOM
                val visible = presentation != StockChatHomeCapsulePresentation.HIDDEN
                val switcherWidth = metrics.dp(232f)
                val switcherHeight = metrics.dp(44f)
                val collapsedContentBottom = metrics.composerContentBottom(
                    pagerData.safeAreaInsets.bottom,
                    focused = false,
                )
                val heroTop = pagerData.statusBarHeight + metrics.dp(66f)
                val heroBottom = pagerData.pageViewHeight -
                    collapsedContentBottom - metrics.dp(136f)
                val heroCenter = (heroTop + heroBottom) / 2f
                val suggestionTop = pagerData.pageViewHeight -
                    collapsedContentBottom - metrics.dp(52f)
                val chatTop = minOf(
                    heroCenter + metrics.welcomeHeroSize / 2f + metrics.dp(68f),
                    suggestionTop - switcherHeight - metrics.dp(24f),
                )
                val marketTop = pagerData.pageViewHeight -
                    pagerData.safeAreaInsets.bottom - metrics.dp(14f) - switcherHeight
                absolutePosition(
                    top = if (marketActive) marketTop else chatTop,
                    left = (pagerData.pageViewWidth - switcherWidth) / 2f,
                )
                width(switcherWidth)
                height(switcherHeight)
                touchEnable(visible)
                zIndex(8)
                animate(
                    Animation.springEaseOut(
                        HOME_CAPSULE_TRAVEL_DURATION,
                        0.92f,
                        0.16f,
                    ),
                    ctx.homeState.capsulePresentation.ordinal,
                )
            }
            View {
                attr {
                    val presentation = ctx.homeState.capsulePresentation
                    val marketActive =
                        presentation == StockChatHomeCapsulePresentation.MARKET_BOTTOM
                    val visible = presentation != StockChatHomeCapsulePresentation.HIDDEN
                    absolutePositionAllZero()
                    opacity(if (visible) 1f else 0f)
                    touchEnable(visible)
                    animate(
                        if (marketActive) {
                            Animation.easeOut(0.18f).delay(0.12f)
                        } else {
                            Animation.easeOut(0.14f)
                        },
                        ctx.homeState.capsulePresentation.ordinal,
                    )
                }
                event {
                    click { }
                }
                ctx.HomeTabSwitcher(
                    this,
                    widthDp = 232f,
                    elevated = true,
                    enabled = {
                        ctx.homeState.capsulePresentation !=
                            StockChatHomeCapsulePresentation.HIDDEN
                    },
                )
            }
        }
    }
}

internal fun StockChatPage.selectHomeTab(tabIndex: Int) {
    val destination = when (tabIndex) {
        HOME_TAB_CHAT -> StockChatHomeDestination.AI_CHAT
        HOME_TAB_TODAY_MARKET -> StockChatHomeDestination.TODAY_MARKET
        else -> return
    }
    dispatchHome(StockChatHomeEvent.DestinationSelected(destination))
}
