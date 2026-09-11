package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.closePage
import com.guet.liang.stockchat.base.openRoute
import com.guet.liang.stockchat.controller.SettingsController
import com.guet.liang.stockchat.controller.settingsController
import com.guet.liang.stockchat.controller.archivedSessions
import com.guet.liang.stockchat.model.AppearanceSettings
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.SettingsSnapshot
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.View
import kotlin.math.roundToInt

@Page(SETTINGS_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class SettingsPage : BasePager() {
    private lateinit var controller: SettingsController
    private var settingsSnapshot by
        observable(
            SettingsSnapshot(
                AppearanceSettings(),
                emptyList(),
                ModelConfiguration("", emptyList()),
                emptyList(),
            )
        )
    private var archivedSessionCount by observable(0)

    override fun created() {
        super.created()
        controller = settingsController()
        reloadSettings()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        reloadSettings()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(ctx.palette().background) }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "设置",
                palette = { ctx.palette() },
                onBack = { ctx.closePage() },
            )
            Scroller {
                attr {
                    absolutePosition(
                        top = ctx.pagerData.statusBarHeight + SETTINGS_HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = ctx.pagerData.safeAreaInsets.bottom,
                    )
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                }
                ctx.AppearanceCard(this)
                ctx.SharedChatsCard(this)
                ctx.ArchivedChatsCard(this)
                ctx.ModelConfigurationCard(this)
                View { attr { height(30f.settingsDp()) } }
            }
        }
    }

    private fun AppearanceCard(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
                marginTop = 14f.settingsDp(),
            ) {
                SettingsSegmentedRow(
                    title = "主题",
                    options = themeOptions,
                    selectedValue = { ctx.settingsSnapshot.appearance.themeMode },
                    palette = { ctx.palette() },
                    onSelect = { mode -> ctx.setThemeMode(mode) },
                )
                SettingsDivider(palette = { ctx.palette() })
                SettingsNavigationRow(
                    title = "文字大小",
                    value = { ctx.fontSizeLabel() },
                    palette = { ctx.palette() },
                    onClick = { ctx.openSettingsPage(FONT_SIZE_SETTINGS_PAGE_NAME) },
                )
                SettingsDivider(palette = { ctx.palette() })
                SettingsNavigationRow(
                    title = "表格样式",
                    value = { ctx.settingsSnapshot.appearance.tableStyle.preset.displayName },
                    palette = { ctx.palette() },
                    onClick = { ctx.openSettingsPage(TABLE_STYLE_SETTINGS_PAGE_NAME) },
                )
            }
        }
    }

    private fun SharedChatsCard(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
            ) {
                SettingsNavigationRow(
                    title = "我的分享",
                    value = { "${ctx.settingsSnapshot.sharedChats.size} 条" },
                    palette = { ctx.palette() },
                    onClick = { ctx.openSettingsPage(SHARED_CHATS_PAGE_NAME) },
                )
            }
        }
    }

    private fun ModelConfigurationCard(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
            ) {
                SettingsNavigationRow(
                    title = "模型配置",
                    value = { ctx.activeProviderLabel() },
                    subtitle = "内置通义千问，支持配置阿里云、DeepSeek、GLM、Kimi 与 MiMo",
                    palette = { ctx.palette() },
                    onClick = { ctx.openSettingsPage(MODEL_CONFIGURATION_PAGE_NAME) },
                )
            }
        }
    }

    private fun ArchivedChatsCard(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
            ) {
                SettingsNavigationRow(
                    title = "归档",
                    value = { "${ctx.archivedSessionCount} 条" },
                    subtitle = "查看已归档的聊天记录",
                    palette = { ctx.palette() },
                    onClick = { ctx.openSettingsPage(ARCHIVED_CHATS_PAGE_NAME) },
                )
            }
        }
    }

    private fun setThemeMode(themeMode: ThemeMode) {
        if (settingsSnapshot.appearance.themeMode == themeMode) {
            return
        }
        controller.setThemeMode(themeMode)
        reloadSettings()
    }

    private fun reloadSettings() {
        settingsSnapshot = controller.snapshot()
        archivedSessionCount = controller.archivedSessions().size
    }

    private fun palette(): SettingsPalette = settingsPalette(settingsSnapshot.appearance.themeMode)

    private fun fontSizeLabel(): String {
        val fontSize = settingsSnapshot.appearance.fontSize
        if (fontSize.followsSystem) {
            return "跟随系统"
        }
        return if (fontSize.scale == FontSizeSettings.DEFAULT_SCALE) {
            "标准"
        } else {
            "${(fontSize.scale * 100f).roundToInt()}%"
        }
    }

    private fun activeProviderLabel(): String {
        return activeProvider(settingsSnapshot)?.displayName ?: "未配置"
    }

    private fun activeProvider(snapshot: SettingsSnapshot): ModelProviderConfig? {
        val configuration = snapshot.modelConfiguration
        return configuration.providers.firstOrNull { provider ->
            provider.id == configuration.activeProviderId
        }
    }

    private fun openSettingsPage(pageName: String) {
        openRoute(pageName, JSONObject())
    }

    private companion object {
        const val PAGE_HORIZONTAL_MARGIN = 16f

        val themeOptions =
            ThemeMode.values().map { mode ->
                SettingsSegmentOption(value = mode, label = mode.displayName)
            }
    }
}
