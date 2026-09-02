package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.SharedChatRecord
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
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

@Page(SHARED_CHATS_PAGE_NAME, supportInLocal = true)
internal class SharedChatsPage : BasePager() {
    private var records by observableList<SharedChatRecord>()
    private var expandedRecordId by observable("")
    private var themeMode by observable(ThemeMode.SYSTEM)

    override fun created() {
        super.created()
        reloadRecords()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(ctx.palette().background)
            }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "我的分享",
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
                vif({ ctx.records.isEmpty() }) {
                    ctx.EmptyState(this)
                }
                vif({ ctx.records.isNotEmpty() }) {
                    Scroller {
                        attr {
                            absolutePositionAllZero()
                            padding(bottom = 28f.settingsDp(), left = 0f, right = 0f, top = 0f)
                            showScrollerIndicator(false)
                            bouncesEnable(true)
                        }
                        ctx.SummaryCard(this)
                        vfor({ ctx.records }) { record ->
                            ctx.SharedRecordCard(this, record)
                        }
                        Text {
                            attr {
                                width((ctx.pagerData.pageViewWidth - 44f.settingsDp()).coerceAtLeast(1f))
                                alignSelfCenter()
                                text("这里记录已经发起系统分享的聊天内容；是否最终发送由系统分享面板决定。")
                                fontSize(11f.settingsDp())
                                lineHeight(18f.settingsDp())
                                color(ctx.palette().textTertiary)
                                marginTop(8f.settingsDp())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun SummaryCard(container: ViewContainer<*, *>) {
        val ctx = this
        val realCount = records.count { !it.isDemo }
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 12f.settingsDp(),
            ) {
                View {
                    attr {
                        height(102f.settingsDp())
                        padding(
                            top = 18f.settingsDp(),
                            left = 20f.settingsDp(),
                            right = 20f.settingsDp(),
                            bottom = 18f.settingsDp(),
                        )
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    View {
                        attr {
                            size(54f.settingsDp(), 54f.settingsDp())
                            borderRadius(18f.settingsDp())
                            backgroundColor(ctx.palette().accentSoft)
                            allCenter()
                        }
                        Text {
                            attr {
                                text("↗")
                                fontSize(25f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().accent)
                                marginBottom(3f.settingsDp())
                            }
                        }
                    }
                    View {
                        attr {
                            flex(1f)
                            marginLeft(14f.settingsDp())
                        }
                        Text {
                            attr {
                                text("共 ${ctx.records.size} 条分享记录")
                                fontSize(18f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(if (realCount > 0) "本机真实记录 $realCount 条" else "当前展示股票问答 Demo 记录")
                                fontSize(12f.settingsDp())
                                color(ctx.palette().textSecondary)
                                marginTop(5f.settingsDp())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun SharedRecordCard(
        container: ViewContainer<*, *>,
        record: SharedChatRecord,
    ) {
        val ctx = this
        val expanded = expandedRecordId == record.id
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 12f.settingsDp(),
            ) {
                View {
                    attr {
                        padding(
                            top = 17f.settingsDp(),
                            left = 18f.settingsDp(),
                            right = 18f.settingsDp(),
                            bottom = 14f.settingsDp(),
                        )
                    }
                    event {
                        click {
                            ctx.expandedRecordId = if (expanded) "" else record.id
                        }
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        View {
                            attr {
                                height(24f.settingsDp())
                                borderRadius(12f.settingsDp())
                                padding(left = 9f.settingsDp(), right = 9f.settingsDp())
                                backgroundColor(
                                    if (record.isDemo) ctx.palette().surfaceMuted else ctx.palette().accentSoft,
                                )
                                allCenter()
                            }
                            Text {
                                attr {
                                    text(if (record.isDemo) "演示" else record.destinationLabel)
                                    fontSize(10f.settingsDp())
                                    fontWeightMedium()
                                    color(if (record.isDemo) ctx.palette().textSecondary else ctx.palette().accent)
                                }
                            }
                        }
                        View {
                            attr { flex(1f) }
                        }
                        Text {
                            attr {
                                text(ctx.formatSharedAt(record.sharedAtEpochMillis))
                                fontSize(10f.settingsDp())
                                color(ctx.palette().textTertiary)
                            }
                        }
                        Text {
                            attr {
                                text(if (expanded) "⌃" else "⌄")
                                fontSize(17f.settingsDp())
                                color(ctx.palette().textTertiary)
                                marginLeft(7f.settingsDp())
                            }
                        }
                    }
                    Text {
                        attr {
                            text(record.question.ifBlank { record.content.title })
                            fontSize(16f.settingsDp())
                            fontWeightBold()
                            lineHeight(22f.settingsDp())
                            color(ctx.palette().textPrimary)
                            marginTop(11f.settingsDp())
                            lines(2)
                        }
                    }
                    Text {
                        attr {
                            text(record.content.title)
                            fontSize(12f.settingsDp())
                            color(ctx.palette().accent)
                            marginTop(7f.settingsDp())
                            lines(1)
                        }
                    }
                    Text {
                        attr {
                            text(record.content.text)
                            fontSize(13f.settingsDp())
                            lineHeight(20f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginTop(8f.settingsDp())
                            lines(if (expanded) 20 else 3)
                        }
                    }
                    vif({ ctx.expandedRecordId == record.id }) {
                        View {
                            attr {
                                height(1f.settingsDp())
                                backgroundColor(ctx.palette().divider)
                                marginTop(14f.settingsDp())
                            }
                        }
                        View {
                            attr {
                                height(52f.settingsDp())
                                flexDirectionRow()
                                alignItemsFlexEnd()
                            }
                            ctx.RecordActionButton(this, "复制", secondary = true) {
                                ctx.bridgeModule.copyToPasteboard(record.content.text)
                                ctx.bridgeModule.toast("分享内容已复制")
                            }
                            ctx.RecordActionButton(this, "再次分享", secondary = false) {
                                ctx.shareAgain(record)
                            }
                            View {
                                attr { flex(1f) }
                            }
                            View {
                                attr {
                                    height(44f.settingsDp())
                                    padding(left = 10f.settingsDp(), right = 4f.settingsDp())
                                    allCenter()
                                }
                                event {
                                    click { ctx.deleteRecord(record.id) }
                                }
                                Text {
                                    attr {
                                        text("删除")
                                        fontSize(12f.settingsDp())
                                        color(ctx.palette().negative)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun RecordActionButton(
        container: ViewContainer<*, *>,
        label: String,
        secondary: Boolean,
        onClick: () -> Unit,
    ) {
        val ctx = this
        with(container) {
            View {
                attr {
                    height(44f.settingsDp())
                    borderRadius(17f.settingsDp())
                    padding(left = 13f.settingsDp(), right = 13f.settingsDp())
                    marginRight(8f.settingsDp())
                    backgroundColor(if (secondary) ctx.palette().surfaceMuted else ctx.palette().accent)
                    allCenter()
                }
                event {
                    click { onClick() }
                }
                Text {
                    attr {
                        text(label)
                        fontSize(12f.settingsDp())
                        fontWeightMedium()
                        color(if (secondary) ctx.palette().textPrimary else Color.WHITE)
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
                            text("↗")
                            fontSize(32f.settingsDp())
                            color(ctx.palette().accent)
                        }
                    }
                }
                Text {
                    attr {
                        text("还没有分享记录")
                        fontSize(20f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().textPrimary)
                        marginTop(18f.settingsDp())
                    }
                }
                Text {
                    attr {
                        text("在聊天消息的更多菜单中选择“分享”，记录会自动出现在这里。")
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

    private fun shareAgain(record: SharedChatRecord) {
        acquireModule<ShareModule>(ShareModule.MODULE_NAME).share(record.content) { result ->
            when (result) {
                ShareResult.Success -> {
                    StockChatSettingsStore.repository.recordSharedChat(
                        sessionId = record.sessionId,
                        question = record.question,
                        content = record.content,
                        destinationLabel = "再次分享",
                    )
                    reloadRecords()
                }
                ShareResult.Cancelled -> Unit
                is ShareResult.Failure -> bridgeModule.toast(result.errorMessage)
            }
        }
    }

    private fun deleteRecord(recordId: String) {
        if (StockChatSettingsStore.repository.deleteSharedChat(recordId)) {
            expandedRecordId = ""
            reloadRecords()
        }
    }

    private fun reloadRecords() {
        val snapshot = StockChatSettingsStore.repository.loadSnapshot()
        themeMode = snapshot.appearance.themeMode
        records.clear()
        records.addAll(snapshot.sharedChats)
    }

    private fun formatSharedAt(epochMillis: Long): String {
        if (epochMillis <= 0L) {
            return "时间未知"
        }
        return bridgeModule.dateFormatter(epochMillis, "yyyy-MM-dd HH:mm")
            .ifBlank { "分享记录" }
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
