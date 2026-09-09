package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.maxTextLengthLegacy
import com.guet.liang.stockchat.model.ChatSessionSummary
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

// StockChatDrawerOverlays：输入组件与交互的独立区块。

internal fun StockChatPage.SessionRenameOverlay(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        vif({ ctx.renameSessionId.isNotEmpty() }) {
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(Color(0x66000000))
                    zIndex(20)
                }
                event { click { ctx.closeRenameDialog() } }
            }
            View {
                attr {
                    absolutePosition(top = pagerData.statusBarHeight + metrics.dp(150f), left = metrics.dp(28f), right = metrics.dp(28f))
                    borderRadius(metrics.dp(18f))
                    backgroundColor(StockChatTheme.surface)
                    padding(all = metrics.dp(20f))
                    zIndex(21)
                }
                Text {
                    attr {
                        text("重命名对话")
                        fontSize(metrics.dp(18f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                TextArea {
                    ref { ctx.renameInputRef = it }
                    attr {
                        width(pagerData.pageViewWidth - metrics.dp(96f))
                        height(metrics.dp(46f))
                        marginTop(metrics.dp(16f))
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.borderStrong))
                        borderRadius(metrics.dp(10f))
                        text(ctx.renameInputText)
                        fontSize(metrics.dp(15f))
                        color(StockChatTheme.textPrimary)
                        placeholder("输入对话名称")
                        placeholderColor(StockChatTheme.textTertiary)
                        maxTextLengthLegacy(40)
                    }
                    event { textDidChange(isSyncEdit = true) { ctx.renameInputText = it.text } }
                }
                View {
                    attr {
                        flexDirectionRow()
                        justifyContentFlexEnd()
                        alignItemsCenter()
                        marginTop(metrics.dp(16f))
                    }
                    ctx.RenameCancelButton(this)
                    ctx.RenameSaveButton(this)
                }
            }
        }
    }
}

internal fun StockChatPage.RenameSaveButton(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                height(metrics.dp(36f))
                padding(left = metrics.dp(14f), right = metrics.dp(14f))
                borderRadius(metrics.dp(18f))
                backgroundColor(StockChatTheme.accent)
                allCenter()
            }
            event { click { ctx.commitSessionRename() } }
            Text {
                attr {
                    text("保存")
                    fontSize(metrics.dp(14f))
                    fontWeightBold()
                    color(Color.WHITE)
                }
            }
        }
    }
}

internal fun StockChatPage.RenameCancelButton(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                height(metrics.dp(36f))
                padding(left = metrics.dp(14f), right = metrics.dp(14f))
                allCenter()
            }
            event { click { ctx.closeRenameDialog() } }
            Text {
                attr {
                    text("取消")
                    fontSize(metrics.dp(14f))
                    color(StockChatTheme.textSecondary)
                }
            }
        }
    }
}

internal fun StockChatPage.openRenameDialog(session: ChatSessionSummary) {
    if (!managingSessions) {
        return
    }
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    resetKeyboardState()
    renameSessionId = session.id
    renameInputText = session.title.ifBlank { "新对话" }
}

internal fun StockChatPage.closeRenameDialog() {
    if (renameInputRefReady) {
        renameInputRef.view?.blur()
    }
    renameSessionId = ""
    renameInputText = ""
}

internal fun StockChatPage.commitSessionRename() {
    val sessionId = renameSessionId
    val title = renameInputText.trim().take(40)
    if (sessionId.isBlank()) {
        return
    }
    if (title.isBlank()) {
        bridgeModule.toast("对话名称不能为空")
        return
    }
    sessionController.rename(sessionId, title)
    closeRenameDialog()
    refreshRecentSessions()
    bridgeModule.toast("已重命名")
}
