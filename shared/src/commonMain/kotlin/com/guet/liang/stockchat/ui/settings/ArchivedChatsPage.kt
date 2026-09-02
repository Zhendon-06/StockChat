package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ChatSessionSummary
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(ARCHIVED_CHATS_PAGE_NAME, supportInLocal = true)
internal class ArchivedChatsPage : BasePager() {
    private var sessions by observableList<ChatSessionSummary>()
    private var themeMode by observable(ThemeMode.SYSTEM)

    override fun created() {
        super.created()
        reloadSessions()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        reloadSessions()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(ctx.palette().background)
            }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "归档的聊天记录",
                palette = ctx::palette,
                onBack = ctx::closePage,
            )
            View {
                attr {
                    absolutePosition(
                        top = ctx.pagerData.statusBarHeight + SETTINGS_HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = ctx.pagerData.safeAreaInsets.bottom,
                    )
                }
                vif({ ctx.sessions.isEmpty() }) {
                    ctx.EmptyState(this)
                }
                vif({ ctx.sessions.isNotEmpty() }) {
                    Scroller {
                        attr {
                            absolutePositionAllZero()
                            showScrollerIndicator(false)
                            bouncesEnable(true)
                            padding(bottom = 28f.settingsDp())
                        }
                        Text {
                            attr {
                                width((ctx.pagerData.pageViewWidth - 44f.settingsDp()).coerceAtLeast(1f))
                                alignSelfCenter()
                                text("归档后的聊天不会出现在抽屉中，可在这里恢复。")
                                fontSize(12f.settingsDp())
                                color(ctx.palette().textSecondary)
                                marginTop(14f.settingsDp())
                                marginBottom(2f.settingsDp())
                            }
                        }
                        vfor({ ctx.sessions }) { session ->
                            ctx.ArchivedSessionCard(this, session)
                        }
                    }
                }
            }
        }
    }

    private fun ArchivedSessionCard(
        container: ViewContainer<*, *>,
        session: ChatSessionSummary,
    ) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 12f.settingsDp(),
            ) {
                View {
                    attr {
                        padding(
                            top = 16f.settingsDp(),
                            left = 18f.settingsDp(),
                            right = 14f.settingsDp(),
                            bottom = 14f.settingsDp(),
                        )
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    View {
                        attr {
                            flex(1f)
                            marginRight(10f.settingsDp())
                        }
                        Text {
                            attr {
                                text(session.title.ifBlank { "新对话" })
                                fontSize(16f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                                lines(2)
                            }
                        }
                        Text {
                            attr {
                                text("归档于 ${ctx.formatUpdatedAt(session.updatedAt)}")
                                fontSize(11f.settingsDp())
                                color(ctx.palette().textTertiary)
                                marginTop(7f.settingsDp())
                            }
                        }
                    }
                    View {
                        attr {
                            height(38f.settingsDp())
                            borderRadius(13f.settingsDp())
                            padding(left = 12f.settingsDp(), right = 12f.settingsDp())
                            backgroundColor(ctx.palette().accentSoft)
                            allCenter()
                        }
                        event {
                            click { ctx.restoreSession(session.id) }
                        }
                        Text {
                            attr {
                                text("恢复")
                                fontSize(12f.settingsDp())
                                fontWeightMedium()
                                color(ctx.palette().accent)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun EmptyState(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    padding(left = 38f.settingsDp(), right = 38f.settingsDp())
                }
                View {
                    attr {
                        size(72f.settingsDp(), 72f.settingsDp())
                        borderRadius(24f.settingsDp())
                        backgroundColor(ctx.palette().accentSoft)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("⌁")
                            fontSize(32f.settingsDp())
                            color(ctx.palette().accent)
                        }
                    }
                }
                Text {
                    attr {
                        text("暂无归档对话")
                        fontSize(20f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().textPrimary)
                        marginTop(18f.settingsDp())
                    }
                }
                Text {
                    attr {
                        text("在聊天抽屉中点击“管理”，即可归档对话并在这里查看。")
                        fontSize(13f.settingsDp())
                        lineHeight(20f.settingsDp())
                        textAlignCenter()
                        color(ctx.palette().textSecondary)
                        marginTop(8f.settingsDp())
                    }
                }
            }
        }
    }

    private fun restoreSession(sessionId: String) {
        if (ChatHistoryDatabase.repository().restoreSession(sessionId)) {
            reloadSessions()
            bridgeModule.toast("已恢复到最近对话")
        }
    }

    private fun reloadSessions() {
        themeMode = StockChatSettingsStore.repository.loadSnapshot().appearance.themeMode
        sessions.clear()
        sessions.addAll(ChatHistoryDatabase.repository().loadArchivedSessions())
    }

    private fun formatUpdatedAt(epochMillis: Long): String {
        if (epochMillis <= 0L) {
            return "时间未知"
        }
        return bridgeModule.dateFormatter(epochMillis, "yyyy-MM-dd HH:mm")
            .ifBlank { "时间未知" }
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private fun palette(): SettingsPalette {
        val isDark = when (themeMode) {
            ThemeMode.SYSTEM -> isNightMode()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        return if (isDark) SettingsPalettes.Dark else SettingsPalettes.Light
    }
}
