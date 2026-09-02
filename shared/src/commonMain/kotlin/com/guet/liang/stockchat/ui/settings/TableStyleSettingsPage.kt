package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.TableStylePreset
import com.guet.liang.stockchat.model.TableStyleSettings
import com.guet.liang.stockchat.model.ThemeMode
import com.guet.liang.stockchat.ui.StockTableStyleChoice
import com.guet.liang.stockchat.ui.StockTableStylePreview
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(TABLE_STYLE_SETTINGS_PAGE_NAME, supportInLocal = true)
internal class TableStyleSettingsPage : BasePager() {
    private var themeMode by observable(ThemeMode.SYSTEM)
    private var savedSettings = TableStyleSettings()
    private var selectedPreset by observable(TableStylePreset.DEFAULT)

    override fun created() {
        super.created()
        val appearance = StockChatSettingsStore.repository.loadSnapshot().appearance
        themeMode = appearance.themeMode
        savedSettings = appearance.tableStyle
        selectedPreset = appearance.tableStyle.preset
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(ctx.palette().background)
            }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "表格样式",
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
                ctx.TablePreview(this)
                ctx.StyleChoices(this)
                ctx.ApplyButton(this)
                Text {
                    attr {
                        width(settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN))
                        alignSelfCenter()
                        text("表格内均为演示行情 · 仅供参考，不构成投资建议")
                        fontSize(12f.settingsDp())
                        lineHeight(18f.settingsDp())
                        textAlignCenter()
                        color(ctx.palette().textTertiary)
                        marginTop(12f.settingsDp())
                    }
                }
                View {
                    attr {
                        height(28f.settingsDp())
                    }
                }
            }
        }
    }

    private fun TablePreview(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
                marginTop = 14f.settingsDp(),
            ) {
                View {
                    attr {
                        padding(
                            top = 18f.settingsDp(),
                            left = 16f.settingsDp(),
                            right = 16f.settingsDp(),
                            bottom = 18f.settingsDp(),
                        )
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            justifyContentSpaceBetween()
                        }
                        View {
                            attr {
                                flex(1f)
                                marginRight(12f.settingsDp())
                            }
                            Text {
                                attr {
                                    text("股票行情预览")
                                    fontSize(18f.settingsDp())
                                    fontWeightBold()
                                    color(ctx.palette().textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text("左右滑动可查看完整字段")
                                    fontSize(12f.settingsDp())
                                    color(ctx.palette().textSecondary)
                                    marginTop(4f.settingsDp())
                                }
                            }
                        }
                        View {
                            attr {
                                borderRadius(12f.settingsDp())
                                backgroundColor(ctx.palette().accentSoft)
                                padding(
                                    top = 7f.settingsDp(),
                                    left = 10f.settingsDp(),
                                    right = 10f.settingsDp(),
                                    bottom = 7f.settingsDp(),
                                )
                            }
                            Text {
                                attr {
                                    text(ctx.selectedPreset.displayName)
                                    fontSize(12f.settingsDp())
                                    fontWeightBold()
                                    color(ctx.palette().accent)
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            height(TABLE_PREVIEW_HEIGHT)
                            marginTop(16f.settingsDp())
                            borderRadius(14f.settingsDp())
                            overflow(true)
                            backgroundColor(ctx.palette().surfaceMuted)
                        }
                        StockTableStylePreview(
                            selectedStyle = { ctx.previewChoice(ctx.selectedPreset) },
                            viewportHeight = TABLE_PREVIEW_HEIGHT,
                            uiScale = SETTINGS_UI_SCALE,
                        )
                    }
                }
            }
        }
    }

    private fun StyleChoices(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
            ) {
                View {
                    attr {
                        padding(
                            top = 18f.settingsDp(),
                            left = 20f.settingsDp(),
                            right = 20f.settingsDp(),
                            bottom = 10f.settingsDp(),
                        )
                    }
                    Text {
                        attr {
                            text("选择样式")
                            fontSize(17f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("样式会应用到聊天中的股票表格与对比结果。")
                            fontSize(12f.settingsDp())
                            lineHeight(18f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginTop(4f.settingsDp())
                        }
                    }
                }
                StockTableStyleChoice.all.forEachIndexed { index, choice ->
                    ctx.StyleChoiceRow(this, choice)
                    if (index < StockTableStyleChoice.all.lastIndex) {
                        SettingsDivider(
                            palette = { ctx.palette() },
                            inset = 84f.settingsDp(),
                        )
                    }
                }
            }
        }
    }

    private fun StyleChoiceRow(
        container: ViewContainer<*, *>,
        choice: StockTableStyleChoice,
    ) {
        val ctx = this
        with(container) {
            View {
                attr {
                    minHeight(74f.settingsDp())
                    padding(
                        top = 10f.settingsDp(),
                        left = 18f.settingsDp(),
                        right = 18f.settingsDp(),
                        bottom = 10f.settingsDp(),
                    )
                    flexDirectionRow()
                    alignItemsCenter()
                    backgroundColor(
                        if (ctx.isSelected(choice)) {
                            ctx.palette().accentSoft
                        } else {
                            ctx.palette().surface
                        },
                    )
                }
                event {
                    click {
                        ctx.selectedPreset = ctx.settingsPreset(choice)
                    }
                }
                View {
                    attr {
                        size(48f.settingsDp(), 48f.settingsDp())
                        borderRadius(13f.settingsDp())
                        backgroundColor(
                            if (ctx.isSelected(choice)) {
                                ctx.palette().surface
                            } else {
                                ctx.palette().surfaceMuted
                            },
                        )
                        allCenter()
                    }
                    repeat(3) { lineIndex ->
                        View {
                            attr {
                                width(27f.settingsDp())
                                height(if (lineIndex == 0) 5f.settingsDp() else 3f.settingsDp())
                                borderRadius(2f.settingsDp())
                                backgroundColor(ctx.choiceAccent(choice))
                                if (lineIndex > 0) {
                                    marginTop(4f.settingsDp())
                                }
                            }
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        marginLeft(14f.settingsDp())
                        marginRight(12f.settingsDp())
                    }
                    Text {
                        attr {
                            text(choice.title)
                            fontSize(16f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text(choice.description)
                            fontSize(12f.settingsDp())
                            lineHeight(18f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginTop(3f.settingsDp())
                        }
                    }
                }
                View {
                    attr {
                        size(24f.settingsDp(), 24f.settingsDp())
                        borderRadius(12f.settingsDp())
                        backgroundColor(
                            if (ctx.isSelected(choice)) {
                                ctx.palette().accent
                            } else {
                                ctx.palette().surfaceMuted
                            },
                        )
                        allCenter()
                    }
                    Text {
                        attr {
                            text(if (ctx.isSelected(choice)) "✓" else "")
                            fontSize(14f.settingsDp())
                            fontWeightBold()
                            color(Color.WHITE)
                            marginBottom(1f.settingsDp())
                        }
                    }
                }
            }
        }
    }

    private fun ApplyButton(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width(settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN))
                    height(50f.settingsDp())
                    alignSelfCenter()
                    marginTop(18f.settingsDp())
                    borderRadius(25f.settingsDp())
                    backgroundColor(ctx.palette().accent)
                    allCenter()
                }
                event {
                    click { ctx.applyAndClose() }
                }
                Text {
                    attr {
                        text("应用此样式")
                        fontSize(16f.settingsDp())
                        fontWeightBold()
                        color(Color.WHITE)
                    }
                }
            }
        }
    }

    private fun isSelected(choice: StockTableStyleChoice): Boolean {
        return selectedPreset == settingsPreset(choice)
    }

    private fun choiceAccent(choice: StockTableStyleChoice): Color {
        return when (choice) {
            StockTableStyleChoice.BLUE -> Color(0xFF3977E3)
            StockTableStyleChoice.DARK -> Color(0xFF444A56)
            else -> if (isSelected(choice)) palette().accent else palette().textTertiary
        }
    }

    private fun applyAndClose() {
        StockChatSettingsStore.repository.setTableStyle(
            savedSettings.copy(preset = selectedPreset),
        )
        closePage()
    }

    private fun palette(): SettingsPalette {
        val isDark = when (themeMode) {
            ThemeMode.SYSTEM -> isNightMode()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        return if (isDark) SettingsPalettes.Dark else SettingsPalettes.Light
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private fun previewChoice(preset: TableStylePreset): StockTableStyleChoice {
        return when (preset) {
            TableStylePreset.DEFAULT -> StockTableStyleChoice.DEFAULT
            TableStylePreset.COMPACT -> StockTableStyleChoice.COMPACT
            TableStylePreset.SPACIOUS -> StockTableStyleChoice.SPACIOUS
            TableStylePreset.MINIMAL -> StockTableStyleChoice.MINIMAL
            TableStylePreset.BLUE -> StockTableStyleChoice.BLUE
            TableStylePreset.DARK -> StockTableStyleChoice.DARK
        }
    }

    private fun settingsPreset(choice: StockTableStyleChoice): TableStylePreset {
        return when (choice) {
            StockTableStyleChoice.DEFAULT -> TableStylePreset.DEFAULT
            StockTableStyleChoice.COMPACT -> TableStylePreset.COMPACT
            StockTableStyleChoice.SPACIOUS -> TableStylePreset.SPACIOUS
            StockTableStyleChoice.MINIMAL -> TableStylePreset.MINIMAL
            StockTableStyleChoice.BLUE -> TableStylePreset.BLUE
            StockTableStyleChoice.DARK -> TableStylePreset.DARK
        }
    }

    private companion object {
        const val PAGE_HORIZONTAL_MARGIN = 16f
        val TABLE_PREVIEW_HEIGHT = 232f.settingsDp()
    }
}
