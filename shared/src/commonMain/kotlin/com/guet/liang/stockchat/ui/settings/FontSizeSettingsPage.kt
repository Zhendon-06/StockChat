package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Size
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Slider
import com.tencent.kuikly.core.views.Switch
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.roundToInt

@Page(FONT_SIZE_SETTINGS_PAGE_NAME, supportInLocal = true)
internal class FontSizeSettingsPage : BasePager() {
    private var themeMode by observable(ThemeMode.SYSTEM)
    private var followsSystem by observable(true)
    private var fontScale by observable(FontSizeSettings.DEFAULT_SCALE)

    override fun created() {
        super.created()
        val appearance = StockChatSettingsStore.repository.loadSnapshot().appearance
        themeMode = appearance.themeMode
        followsSystem = appearance.fontSize.followsSystem
        fontScale = appearance.fontSize.scale
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(ctx.palette().background)
            }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "文字大小",
                actionText = "确认",
                palette = { ctx.palette() },
                onBack = { ctx.closePage() },
                onAction = { ctx.saveAndClose() },
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
                ctx.FontPreview(this)
                ctx.FontControls(this)
                View {
                    attr {
                        height(28f.settingsDp())
                    }
                }
            }
        }
    }

    private fun FontPreview(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
                marginTop = 14f.settingsDp(),
            ) {
                View {
                    attr {
                        minHeight(330f.settingsDp())
                        padding(
                            top = 24f.settingsDp(),
                            left = 22f.settingsDp(),
                            right = 22f.settingsDp(),
                            bottom = 28f.settingsDp(),
                        )
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            justifyContentFlexEnd()
                        }
                        View {
                            attr {
                                maxWidth(ctx.pagerData.pageViewWidth - 94f.settingsDp())
                                borderRadius(20f.settingsDp())
                                backgroundColor(ctx.palette().surfaceMuted)
                                padding(
                                    top = 12f.settingsDp(),
                                    left = 17f.settingsDp(),
                                    right = 17f.settingsDp(),
                                    bottom = 12f.settingsDp(),
                                )
                            }
                            Text {
                                attr {
                                    text("帮我预览一下字号大小")
                                    fontSize(16f.settingsDp() * ctx.previewScale())
                                    lineHeight(24f.settingsDp() * ctx.previewScale())
                                    color(ctx.palette().textPrimary)
                                }
                            }
                        }
                    }
                    Text {
                        attr {
                            text(
                                "你可以通过拖动下面的滑块来设置字号大小。设置后会改变全局的文字大小，" +
                                    "如果在使用过程中遇到问题，可以向我们反馈。",
                            )
                            fontSize(17f.settingsDp() * ctx.previewScale())
                            lineHeight(28f.settingsDp() * ctx.previewScale())
                            color(ctx.palette().textPrimary)
                            marginTop(30f.settingsDp())
                        }
                    }
                    View {
                        attr {
                            marginTop(24f.settingsDp())
                            borderRadius(12f.settingsDp())
                            backgroundColor(ctx.palette().accentSoft)
                            padding(
                                top = 10f.settingsDp(),
                                left = 12f.settingsDp(),
                                right = 12f.settingsDp(),
                                bottom = 10f.settingsDp(),
                            )
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                text("Aa")
                                fontSize(14f.settingsDp() * ctx.previewScale())
                                fontWeightBold()
                                color(ctx.palette().accent)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.currentScaleLabel())
                                fontSize(13f.settingsDp() * ctx.previewScale())
                                color(ctx.palette().textSecondary)
                                marginLeft(8f.settingsDp())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FontControls(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = settingsContentWidth(ctx.pagerData.pageViewWidth, PAGE_HORIZONTAL_MARGIN),
                palette = { ctx.palette() },
            ) {
                View {
                    attr {
                        minHeight(88f.settingsDp())
                        padding(
                            top = 16f.settingsDp(),
                            left = 20f.settingsDp(),
                            right = 18f.settingsDp(),
                            bottom = 16f.settingsDp(),
                        )
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    View {
                        attr {
                            flex(1f)
                            marginRight(16f.settingsDp())
                        }
                        Text {
                            attr {
                                text("跟随系统")
                                fontSize(17f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("开启后字体大小将跟随系统设置")
                                fontSize(13f.settingsDp())
                                lineHeight(19f.settingsDp())
                                color(ctx.palette().textSecondary)
                                marginTop(4f.settingsDp())
                            }
                        }
                    }
                    Switch {
                        attr {
                            size(52f.settingsDp(), 32f.settingsDp())
                            isOn(ctx.followsSystem)
                            onColor(ctx.palette().accent)
                            unOnColor(ctx.palette().surfaceMuted)
                            thumbColor(ctx.palette().surface)
                        }
                        event {
                            switchOnChanged { isOn ->
                                ctx.followsSystem = isOn
                            }
                        }
                    }
                }
                SettingsDivider(palette = { ctx.palette() })
                View {
                    attr {
                        padding(
                            top = 18f.settingsDp(),
                            left = 20f.settingsDp(),
                            right = 20f.settingsDp(),
                            bottom = 22f.settingsDp(),
                        )
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            justifyContentSpaceBetween()
                        }
                        Text {
                            attr {
                                text("手动调整")
                                fontSize(15f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.manualScaleLabel())
                                fontSize(14f.settingsDp())
                                fontWeightMedium()
                                color(
                                    if (ctx.followsSystem) {
                                        ctx.palette().textTertiary
                                    } else {
                                        ctx.palette().accent
                                    },
                                )
                            }
                        }
                    }
                    View {
                        attr {
                            height(48f.settingsDp())
                            marginTop(10f.settingsDp())
                            flexDirectionRow()
                            alignItemsCenter()
                            opacity(if (ctx.followsSystem) 0.45f else 1f)
                        }
                        Text {
                            attr {
                                width(24f.settingsDp())
                                text("A")
                                fontSize(15f.settingsDp())
                                color(ctx.palette().textSecondary)
                            }
                        }
                        Slider {
                            attr {
                                size(ctx.pagerData.pageViewWidth - 128f.settingsDp(), 40f.settingsDp())
                                currentProgress(ctx.sliderProgress())
                                progressColor(ctx.palette().accent)
                                trackColor(ctx.palette().surfaceMuted)
                                thumbColor(ctx.palette().surface)
                                trackThickness(5f.settingsDp())
                                thumbSize(Size(28f.settingsDp(), 28f.settingsDp()))
                                padding(left = 14f.settingsDp(), right = 14f.settingsDp())
                            }
                            event {
                                progressDidChanged { progress ->
                                    if (!ctx.followsSystem) {
                                        ctx.fontScale = ctx.scaleForProgress(progress)
                                    }
                                }
                            }
                        }
                        Text {
                            attr {
                                width(24f.settingsDp())
                                text("A")
                                fontSize(25f.settingsDp())
                                textAlignRight()
                                color(ctx.palette().textSecondary)
                            }
                        }
                    }
                    Text {
                        attr {
                            text("拖动滑块可实时预览，点击右上角“确认”后应用。")
                            fontSize(12f.settingsDp())
                            lineHeight(18f.settingsDp())
                            color(ctx.palette().textTertiary)
                            marginTop(2f.settingsDp())
                        }
                    }
                }
            }
        }
    }

    private fun previewScale(): Float {
        return if (followsSystem) FontSizeSettings.DEFAULT_SCALE else fontScale
    }

    private fun sliderProgress(): Float {
        return (fontScale - FontSizeSettings.MIN_SCALE) /
            (FontSizeSettings.MAX_SCALE - FontSizeSettings.MIN_SCALE)
    }

    private fun scaleForProgress(progress: Float): Float {
        val scaleRange = FontSizeSettings.MAX_SCALE - FontSizeSettings.MIN_SCALE
        return (FontSizeSettings.MIN_SCALE + progress.coerceIn(0f, 1f) * scaleRange)
            .coerceIn(FontSizeSettings.MIN_SCALE, FontSizeSettings.MAX_SCALE)
    }

    private fun currentScaleLabel(): String {
        return if (followsSystem) {
            "跟随系统 · 标准预览"
        } else {
            "当前预览 · ${manualScaleLabel()}"
        }
    }

    private fun manualScaleLabel(): String {
        return if ((fontScale - FontSizeSettings.DEFAULT_SCALE).let { delta ->
                delta > -0.005f && delta < 0.005f
            }
        ) {
            "标准"
        } else {
            "${(fontScale * 100f).roundToInt()}%"
        }
    }

    private fun saveAndClose() {
        StockChatSettingsStore.repository.setFontSize(
            FontSizeSettings(
                followsSystem = followsSystem,
                scale = fontScale,
            ),
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

    private companion object {
        const val PAGE_HORIZONTAL_MARGIN = 16f
    }
}
