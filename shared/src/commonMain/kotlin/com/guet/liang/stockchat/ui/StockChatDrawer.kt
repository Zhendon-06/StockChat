package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.ChatSessionSummary
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 左侧抽屉：品牌头部、功能入口、最近对话列表、会话重命名弹窗与抽屉手势。

private const val DRAWER_SWIPE_DISTANCE = 56f

internal fun StockChatPage.DrawerLayer(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    val drawerWidth = metrics.drawerWidth
    with(container) {
        View {
            attr {
                absolutePosition(top = 0f, left = 0f, bottom = 0f)
                width(drawerWidth)
                backgroundColor(StockChatTheme.surface)
                zIndex(1)
                transform(Translate(0f, 0f, if (ctx.drawerOpen) 0f else -drawerWidth, 0f))
                animation(Animation.springEaseOut(0.38f, 0.9f, 0.2f), ctx.drawerOpen)
                touchEnable(ctx.drawerOpen)
                padding(
                    top = pagerData.statusBarHeight + metrics.dp(18f),
                    left = metrics.dp(22f),
                    right = metrics.dp(22f),
                    bottom = pagerData.safeAreaInsets.bottom + metrics.dp(18f),
                )
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            event { pan { params -> ctx.handleDrawerPan(params) } }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                ctx.DrawerBrandIcon(this)
                ctx.DrawerBrandName(this)
                View {
                    attr {
                        size(metrics.dp(36f), metrics.dp(36f))
                        borderRadius(metrics.dp(10f))
                        themedBorder()
                        allCenter()
                    }
                    event { click { ctx.openSettings() } }
                    Text {
                        attr {
                            text("⚙")
                            fontSize(metrics.dp(18f))
                            color(StockChatTheme.textPrimary)
                        }
                    }
                }
            }
            ctx.HomeTabSwitcher(this, marginTopDp = 20f, enabled = { ctx.drawerOpen })
            ctx.DrawerMenuItem(this, "ranking_icon.png", "思维导图", metrics.scale) { ctx.openMindMapArtifactLibrary() }
            ctx.DrawerMenuItem(this, "table_icon.png", "表格", metrics.scale) { ctx.openStockComparisonLibrary() }
            ctx.DrawerMenuItem(this, "level_icon.png", "收藏卡片", metrics.scale) { ctx.openFavoriteCards() }
            View {
                attr {
                    height(1f)
                    backgroundColor(StockChatTheme.border)
                    marginTop(metrics.dp(6f))
                    marginBottom(metrics.dp(8f))
                }
            }

            ctx.DrawerNewConversation(this)
            ctx.DrawerSessionHeading(this)
            ctx.DrawerSessionList(this)
        }
    }
}

internal fun StockChatPage.DrawerMenuItem(
    container: ViewContainer<*, *>,
    iconAsset: String,
    label: String,
    scale: Float,
    onClick: () -> Unit,
) {
    with(container) {
        View {
            attr {
                height(58f * scale)
                flexDirectionRow()
                alignItemsCenter()
                marginTop(5f * scale)
            }
            event { click { onClick() } }
            Image {
                attr {
                    size(24f * scale, 24f * scale)
                    resizeContain()
                    src(ImageUri.commonAssets(iconAsset))
                    marginRight(18f * scale)
                }
            }
            Text {
                attr {
                    text(label)
                    fontSize(17f * scale)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("›")
                    fontSize(26f * scale)
                    color(StockChatTheme.textTertiary)
                }
            }
        }
    }
}

internal fun StockChatPage.DrawerConversation(container: ViewContainer<*, *>, session: ChatSessionSummary, scale: Float) {
    val ctx = this
    with(container) {
        View {
            attr {
                padding(top = 11f * scale, left = 12f * scale, bottom = 11f * scale, right = 12f * scale)
                flexDirectionRow()
                alignItemsCenter()
                borderRadius(14f * scale)
                marginTop(5f * scale)
                backgroundColor(
                    if (session.id == ctx.activeSessionId) {
                        StockChatTheme.accentSoft
                    } else {
                        Color(0x00000000)
                    }
                )
            }
            event {
                click {
                    if (!ctx.managingSessions) {
                        ctx.selectSession(session.id)
                    }
                }
            }
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text(session.title.ifBlank { "新对话" })
                        fontSize(14f * scale)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text("已保存到本地数据库")
                        fontSize(11f * scale)
                        color(StockChatTheme.textTertiary)
                        marginTop(4f * scale)
                    }
                }
            }
            vif({ ctx.managingSessions }) {
                ctx.SessionRenameAction(this, session, scale)
                ctx.SessionArchiveAction(this, session, scale)
                ctx.SessionDeleteAction(this, session, scale)
            }
        }
    }
}

internal fun StockChatPage.handleDrawerPan(params: PanGestureParams) {
    when (params.state) {
        "start",
        "move" -> {
            if (params.state == "move" && drawerPanStartX == 0f && drawerPanStartY == 0f) {
                drawerPanStartX = params.pageX
                drawerPanStartY = params.pageY
            }
            if (params.state == "move") {
                val deltaX = params.pageX - drawerPanStartX
                val deltaY = params.pageY - drawerPanStartY
                val isHorizontalSwipe =
                    kotlin.math.abs(deltaX) >= DRAWER_SWIPE_DISTANCE && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)
                if (isHorizontalSwipe && deltaX < 0f && drawerOpen) {
                    closeDrawer()
                }
            }
            if (params.state == "start") {
                drawerPanStartX = params.pageX
                drawerPanStartY = params.pageY
            }
        }
        "end" -> finishDrawerPan(params)
    }
}

internal fun StockChatPage.openDrawer() {
    cancelVoiceInput()
    conversationMenuOpen = false
    messageMenuTargetId = ""
    modelMenuOpen = false
    drawerOpen = true
}

internal fun StockChatPage.closeDrawer() {
    drawerOpen = false
    if (managingSessions || renameSessionId.isNotEmpty()) {
        managingSessions = false
        closeRenameDialog()
    }
}

internal fun StockChatPage.toggleSessionManagement() {
    managingSessions = !managingSessions
    if (!managingSessions) {
        closeRenameDialog()
    }
}

private fun StockChatPage.DrawerBrandIcon(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                size(metrics.dp(44f), metrics.dp(44f))
                borderRadius(metrics.dp(15f))
                backgroundColor(StockChatTheme.accentSoft)
                allCenter()
            }
            Text {
                attr {
                    text("S")
                    fontSize(metrics.dp(20f))
                    fontWeightBold()
                    color(StockChatTheme.accent)
                }
            }
        }
    }
}

private fun StockChatPage.DrawerBrandName(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                flex(1f)
                marginLeft(metrics.dp(12f))
            }
            Text {
                attr {
                    text("StockMate")
                    fontSize(metrics.dp(20f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("AI 股票问答")
                    fontSize(metrics.dp(12f))
                    color(StockChatTheme.textSecondary)
                    marginTop(metrics.dp(2f))
                }
            }
        }
    }
}

private fun StockChatPage.DrawerSessionHeading(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text("最近对话")
                    fontSize(metrics.dp(13f))
                    color(StockChatTheme.textTertiary)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text(if (ctx.managingSessions) "完成" else "管理")
                    fontSize(metrics.dp(13f))
                    color(StockChatTheme.accent)
                }
                event { click { ctx.toggleSessionManagement() } }
            }
        }
    }
}

private fun StockChatPage.DrawerNewConversation(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                height(metrics.dp(48f))
                flexDirectionRow()
                alignItemsCenter()
                marginTop(metrics.dp(2f))
            }
            event { click { ctx.startNewChat() } }
            Text {
                attr {
                    text("＋")
                    fontSize(metrics.dp(25f))
                    color(StockChatTheme.textPrimary)
                    marginRight(metrics.dp(10f))
                }
            }
            Text {
                attr {
                    text("新建对话")
                    fontSize(metrics.dp(16f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
        }
    }
}

private fun StockChatPage.DrawerSessionList(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        Scroller {
            attr {
                flex(1f)
                showScrollerIndicator(false)
                bouncesEnable(true)
                capture(CaptureRule.pan(CaptureRuleDirection.VERTICAL))
                padding(bottom = metrics.dp(8f))
            }
            vif({ ctx.recentSessions.isEmpty() }) {
                Text {
                    attr {
                        text("暂无已保存的对话")
                        fontSize(metrics.dp(13f))
                        color(StockChatTheme.textTertiary)
                        marginTop(metrics.dp(12f))
                    }
                }
            }
            vfor({ ctx.recentSessions }) { session -> ctx.DrawerConversation(this, session, metrics.scale) }
        }
    }
}

private fun StockChatPage.SessionRenameAction(container: ViewContainer<*, *>, session: ChatSessionSummary, scale: Float) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(44f * scale)
                height(36f * scale)
                allCenter()
            }
            event { click { ctx.openRenameDialog(session) } }
            Text {
                attr {
                    text("编辑")
                    fontSize(12f * scale)
                    color(StockChatTheme.accent)
                }
            }
        }
    }
}

private fun StockChatPage.SessionArchiveAction(container: ViewContainer<*, *>, session: ChatSessionSummary, scale: Float) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(44f * scale)
                height(36f * scale)
                allCenter()
            }
            event { click { ctx.archiveSession(session.id) } }
            Text {
                attr {
                    text("归档")
                    fontSize(12f * scale)
                    color(StockChatTheme.accent)
                }
            }
        }
    }
}

private fun StockChatPage.SessionDeleteAction(container: ViewContainer<*, *>, session: ChatSessionSummary, scale: Float) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(44f * scale)
                height(36f * scale)
                allCenter()
            }
            event { click { ctx.deleteSession(session.id) } }
            Text {
                attr {
                    text("删除")
                    fontSize(12f * scale)
                    color(StockChatTheme.positive)
                }
            }
        }
    }
}

private fun StockChatPage.finishDrawerPan(params: PanGestureParams) {

    val deltaX = params.pageX - drawerPanStartX
    val deltaY = params.pageY - drawerPanStartY
    val isHorizontalSwipe = kotlin.math.abs(deltaX) >= DRAWER_SWIPE_DISTANCE && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)
    if (isHorizontalSwipe) {
        if (deltaX > 0f && !drawerOpen) {
            openDrawer()
        } else if (deltaX < 0f && drawerOpen) {
            closeDrawer()
        }
    }
    drawerPanStartX = 0f
    drawerPanStartY = 0f
}
