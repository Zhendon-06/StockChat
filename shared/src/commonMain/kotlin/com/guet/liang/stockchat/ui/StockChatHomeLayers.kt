package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 主内容层：聊天层 / 今日市场层的切换与顶部会话栏。

internal fun StockChatPage.MainLayer(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    val drawerWidth = metrics.drawerWidth
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                backgroundColor(StockChatTheme.background)
                zIndex(2)
                transform(Translate(0f, 0f, if (ctx.drawerOpen) drawerWidth else 0f, 0f))
                animation(Animation.springEaseOut(0.38f, 0.9f, 0.2f), ctx.drawerOpen)
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            event {
                pan { params -> ctx.handleDrawerPan(params) }
                // 点输入框以外的任意区域（顶栏/聊天区/欢迎区等，凡是没被子视图
                // 消费的点击都会冒泡到这里）：键盘弹起时先收键盘（面板保持展开）；
                // 键盘已收起时再点，面板才收缩还给页面空间。输入 dock 自己吞掉
                // 区域内的点击，不会冒泡到这
                click {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.handleBlankAreaTap()
                    }
                }
            }
            ctx.ChatLayer(this)
            ctx.TodayMarketLayer(this)
            ctx.ConversationTopBar(this)
            ctx.HomeTabCapsule(this)
            ctx.ComposerDock(this)
            vif({
                ctx.selectedHomeTab == HOME_TAB_CHAT &&
                    ctx.homeState.chatStage == StockChatHomeChatStage.CONVERSATION &&
                    !ctx.messageListNearBottom
            }) {
                ctx.ScrollToBottomButton(this)
            }
            vif({ ctx.selectedHomeTab == HOME_TAB_CHAT }) {
                ctx.VoiceRecordingOverlay(this)
                ctx.MessageMenuOverlay(this)
                ctx.ModelMenuOverlay(this)
                ctx.ConversationMenuOverlay(this)
            }
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(Color(StockChatTheme.COLOR_FF141A18))
                    opacity(if (ctx.drawerOpen) 0.34f else 0f)
                    touchEnable(ctx.drawerOpen)
                    zIndex(9)
                    animation(Animation.easeOut(0.3f), ctx.drawerOpen)
                    capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                }
                event {
                    click { ctx.closeDrawer() }
                    pan { params -> ctx.handleDrawerPan(params) }
                }
            }
        }
    }
}

// 聊天欢迎内容单独挂载；今日市场内容由 TodayMarketLayer 管理。
internal fun StockChatPage.HomeContentLayer(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                overflow(true)
                val visible = ctx.homeState.chatStage == StockChatHomeChatStage.WELCOME && !ctx.homeState.welcomeObscured
                visibility(visible)
                opacity(if (visible) 1f else 0f)
                touchEnable(visible)
                animate(Animation.easeOut(0.2f), visible)
            }
            ctx.WelcomeContent(this)
        }
    }
}

internal fun StockChatPage.TodayMarketLayer(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val active = ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET
                absolutePosition(top = pagerData.statusBarHeight + metrics.dp(66f), left = 0f, right = 0f, bottom = 0f)
                opacity(if (active) 1f else 0f)
                transform(Translate(0f, 0f, 0f, if (active) 0f else metrics.dp(12f)))
                touchEnable(active)
                zIndex(if (active) 2 else 0)
                animate(
                    if (active) {
                        Animation.easeIn(0.14f)
                    } else {
                        Animation.easeOut(0.24f).delay(0.16f)
                    },
                    ctx.selectedHomeTab,
                )
            }
            event { click {} }
            TodayMarketContent(
                state = { ctx.todayMarketState },
                skeletonPhase = { ctx.todayMarketSkeletonPhase },
                pageWidth = ctx.pagerData.pageViewWidth,
                scale = ctx.layoutMetrics.scale,
                safeAreaBottom = ctx.pagerData.safeAreaInsets.bottom,
                touchEnabled = { ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET },
                onQuoteClick = { quote ->
                    if (ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET) {
                        ctx.openStockDetail(quote, HOME_TAB_TODAY_MARKET)
                    }
                },
                onRetry = {
                    if (ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET) {
                        ctx.dispatchHome(StockChatHomeEvent.TodayMarketRetryRequested)
                    }
                },
                scrollerRef = { ctx.todayMarketScrollerRef = it },
                onScroll = { offset -> ctx.todayMarketScrollOffsetY = offset },
                restoreOffsetY = ctx.todayMarketScrollOffsetY,
            )
        }
    }
}

internal fun StockChatPage.ChatLayer(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val active = ctx.selectedHomeTab == HOME_TAB_CHAT
                absolutePositionAllZero()
                opacity(if (active) 1f else 0f)
                transform(Translate(0f, 0f, 0f, if (active) 0f else -metrics.dp(12f)))
                touchEnable(active)
                zIndex(if (active) 2 else 0)
                backgroundLinearGradient(
                    Direction.TO_BOTTOM_RIGHT,
                    ColorStop(StockChatTheme.chatBackgroundStart, 0f),
                    ColorStop(StockChatTheme.chatBackgroundEnd, 1f),
                )
                animate(
                    if (active) {
                        Animation.easeIn(0.16f)
                    } else {
                        Animation.easeOut(0.24f).delay(0.14f)
                    },
                    ctx.selectedHomeTab,
                )
            }
            event {
                click {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.handleBlankAreaTap()
                    }
                }
            }
            vif({ !StockChatTheme.backgroundImageUri.isNullOrBlank() }) {
                Image {
                    attr {
                        absolutePositionAllZero()
                        resizeCover()
                        src(StockChatTheme.backgroundImageUri.orEmpty(), false)
                        touchEnable(false)
                    }
                }
            }
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(StockChatTheme.backgroundSofteningMask)
                    touchEnable(false)
                }
            }
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(StockChatTheme.backgroundMask)
                    touchEnable(false)
                }
            }
            ctx.ChatConversationContent(this)
            vif({ ctx.homeState.chatStage == StockChatHomeChatStage.CONVERSATION }) {
                ctx.ChatBottomFade(this)
                ctx.ConversationHeader(this)
            }
        }
    }
}

internal fun StockChatPage.requestTodayMarket(requestId: Int) {
    if (!todayMarketDataSourceReady) {
        return
    }
    todayMarketDataSource.load { result -> dispatchHome(StockChatHomeEvent.TodayMarketLoadCompleted(requestId, result)) }
}

internal fun StockChatPage.ConversationTopBar(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val interactive = ctx.selectedHomeTab == HOME_TAB_CHAT || ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET
                absolutePosition(top = pagerData.statusBarHeight + metrics.dp(14f), left = metrics.dp(18f))
                touchEnable(interactive)
                zIndex(8)
            }
            HamburgerButton(scale = metrics.scale) {
                if (ctx.selectedHomeTab != HOME_TAB_CHAT && ctx.selectedHomeTab != HOME_TAB_TODAY_MARKET) {
                    return@HamburgerButton
                }
                if (ctx.inputRefReady) {
                    ctx.inputRef.view?.blur()
                }
                ctx.openDrawer()
            }
        }
    }
}

internal fun StockChatPage.ConversationHeader(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePosition(top = pagerData.statusBarHeight + metrics.dp(14f), left = metrics.dp(78f), right = metrics.dp(145f))
                height(metrics.dp(52f))
                allCenter()
                zIndex(4)
            }
            Text {
                attr {
                    text(ctx.conversationTitle())
                    fontSize(metrics.dp(20f))
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                    textAlignCenter()
                    // 给定确定宽度并限单行，长标题截断显示，避免溢出遮挡两侧按钮
                    width(pagerData.pageViewWidth - metrics.dp(78f) - metrics.dp(145f))
                    lines(1)
                }
            }
        }
        View {
            attr {
                absolutePosition(top = pagerData.statusBarHeight + metrics.dp(14f), right = metrics.dp(18f))
                width(metrics.dp(127f))
                height(metrics.dp(52f))
                borderRadius(metrics.dp(26f))
                backgroundColor(StockChatTheme.surface)
                boxShadow(BoxShadow(metrics.dp(1f), metrics.dp(5f), metrics.dp(14f), Color(0x1A000000)))
                flexDirectionRow()
                alignItemsCenter()
                justifyContentSpaceAround()
                padding(left = metrics.dp(7f), right = metrics.dp(7f))
                zIndex(5)
            }
            ctx.ConversationNewButton(this)
            View {
                attr {
                    size(metrics.dp(44f), metrics.dp(44f))
                    allCenter()
                }
                event {
                    click {
                        if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                            ctx.openConversationMenu()
                        }
                    }
                }
                ctx.MoreMark(this, metrics.scale)
            }
        }
    }
}

internal fun StockChatPage.NewConversationMark(container: ViewContainer<*, *>, scale: Float) {
    with(container) {
        View {
            attr {
                size(30f * scale, 30f * scale)
                borderRadius(15f * scale)
                border(Border(3f * scale, BorderStyle.SOLID, StockChatTheme.textPrimary))
                allCenter()
            }
            View {
                attr {
                    size(14f * scale, 3f * scale)
                    borderRadius(2f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
            View {
                attr {
                    absolutePosition(top = 8.5f * scale, left = 13.5f * scale)
                    size(3f * scale, 14f * scale)
                    borderRadius(2f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
            View {
                attr {
                    absolutePosition(left = 1f * scale, bottom = -1f * scale)
                    size(10f * scale, 3f * scale)
                    borderRadius(2f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
        }
    }
}

internal fun StockChatPage.MoreMark(container: ViewContainer<*, *>, scale: Float) {
    with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                justifyContentCenter()
            }
            repeat(3) {
                View {
                    attr {
                        size(5f * scale, 5f * scale)
                        borderRadius(3f * scale)
                        backgroundColor(StockChatTheme.textPrimary)
                        margin(left = 3f * scale, right = 3f * scale)
                    }
                }
            }
        }
    }
}

private fun StockChatPage.ChatConversationContent(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePosition(
                    top = pagerData.statusBarHeight + metrics.dp(66f),
                    left = 0f,
                    right = 0f,
                    bottom =
                        metrics.composerContentBottom(
                            metrics.composerBottomInset(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom),
                            ctx.composerExpanded,
                            ctx.voiceMode,
                            ctx.selectedImageCount > 0,
                            ctx.composerExtraInputLines(),
                        ) + (ctx.composerDockNudge % 2) * 0.1f,
                )
                animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                animate(Animation.easeOut(0.2f), ctx.composerExpanded)
            }
            ctx.HomeContentLayer(this)
            vif({ ctx.homeState.chatStage == StockChatHomeChatStage.CONVERSATION }) { ctx.MessageList(this) }
        }
    }
}

private fun StockChatPage.ChatBottomFade(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePosition(
                    left = 0f,
                    right = 0f,
                    bottom =
                        metrics.composerContentBottom(
                            metrics.composerBottomInset(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom),
                            ctx.composerExpanded,
                            ctx.voiceMode,
                            ctx.selectedImageCount > 0,
                            ctx.composerExtraInputLines(),
                        ) + (ctx.composerDockNudge % 2) * 0.1f,
                )
                height(metrics.composerContentFadeHeight)
                backgroundLinearGradient(
                    Direction.TO_BOTTOM,
                    // 两端保持同一 RGB，只改变 alpha，避免鸿蒙插值透明黑
                    // 与浅色背景时在消息底部产生灰黑色横条。
                    ColorStop(StockChatTheme.chatBackgroundEnd.opacity(0f), 0f),
                    ColorStop(StockChatTheme.chatBackgroundEnd, 1f),
                )
                touchEnable(false)
                zIndex(5)
                animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                animate(Animation.easeOut(0.2f), ctx.composerExpanded)
            }
        }
    }
}

private fun StockChatPage.ConversationNewButton(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                size(metrics.dp(44f), metrics.dp(44f))
                allCenter()
            }
            event {
                click {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.startNewChat()
                    }
                }
            }
            ctx.NewConversationMark(this, metrics.scale)
        }
    }
}
