package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
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
                backgroundColor(ctx.pagePalette().background)
            }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "字体大小",
                actionText = "确认",
                palette = { ctx.pagePalette() },
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
                width = ctx.fontPageContentWidth(),
                palette = { ctx.pagePalette() },
                marginTop = 9f,
            ) {
                View {
                    attr {
                        height(492f)
                        padding(
                            top = 20f,
                            left = 21f,
                            right = 21f,
                            bottom = 28f,
                        )
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            justifyContentFlexEnd()
                        }
                        View {
                            attr {
                                maxWidth(ctx.pagerData.pageViewWidth - 94f)
                                borderRadius(20f)
                                backgroundColor(ctx.pagePalette().surfaceMuted)
                                padding(
                                    top = 10f,
                                    left = 13f,
                                    right = 13f,
                                    bottom = 10f,
                                )
                            }
                            Text {
                                attr {
                                    text("帮我预览一下字号大小")
                                    fontSize(16f * ctx.previewScale())
                                    lineHeight(23f * ctx.previewScale())
                                    color(ctx.pagePalette().textPrimary)
                                }
                            }
                        }
                    }
                    Text {
                        attr {
                            text(
                                "你可以通过拖动下面的滑块来设置字号大小。设置后会改变全局的字号大小，" +
                                    "如果在使用过程中遇到问题，可以向我们反馈。",
                            )
                            fontSize(16f * ctx.previewScale())
                            lineHeight(26f * ctx.previewScale())
                            color(ctx.pagePalette().textPrimary)
                            marginTop(19f)
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
                width = ctx.fontPageContentWidth(),
                palette = { ctx.pagePalette() },
                marginTop = 16f,
            ) {
                View {
                    attr {
                        height(74f)
                        padding(
                            top = 16f,
                            left = 18f,
                            right = 16f,
                            bottom = 14f,
                        )
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                        View {
                            attr {
                                flex(1f)
                                marginRight(16f)
                            }
                            Text {
                                attr {
                                    text("跟随系统")
                                    fontSize(17f)
                                    fontWeightBold()
                                    color(ctx.pagePalette().textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text("开启后字体大小将跟随系统设置")
                                    fontSize(14f)
                                    lineHeight(20f)
                                    color(ctx.pagePalette().textSecondary)
                                    marginTop(4f)
                                }
                            }
                        }
                    Switch {
                        attr {
                            size(52f, 32f)
                            isOn(ctx.followsSystem)
                            onColor(ctx.pagePalette().accent)
                            unOnColor(ctx.pagePalette().surfaceMuted)
                            thumbColor(ctx.pagePalette().surface)
                        }
                        event {
                            switchOnChanged { isOn ->
                                ctx.followsSystem = isOn
                            }
                        }
                    }
                }
                View {
                    attr {
                        width((ctx.fontPageContentWidth() - 32f).coerceAtLeast(1f))
                        height(1f)
                        alignSelfCenter()
                        backgroundColor(ctx.pagePalette().divider)
                    }
                }
                View {
                    attr {
                        height(93f)
                        padding(top = 15f, left = 18f, right = 18f, bottom = 8f)
                    }
                    View {
                        attr {
                            width(264f)
                            height(40f)
                            alignSelfCenter()
                            opacity(if (ctx.followsSystem) 0.45f else 1f)
                        }
                        Slider {
                            attr {
                                size(264f, 40f)
                                currentProgress(ctx.sliderProgress())
                                progressColor(ctx.pagePalette().accent)
                                trackColor(ctx.pagePalette().surfaceMuted)
                                thumbColor(ctx.pagePalette().surface)
                                trackThickness(5f)
                                thumbSize(Size(28f, 28f))
                                padding(left = 0f, right = 0f)
                            }
                            event {
                                progressDidChanged { progress ->
                                    if (!ctx.followsSystem) {
                                        ctx.fontScale = ctx.scaleForProgress(progress)
                                    }
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            width(264f)
                            height(28f)
                            alignSelfCenter()
                            marginTop(5f)
                            opacity(if (ctx.followsSystem) 0.45f else 1f)
                        }
                        Text {
                            attr {
                                absolutePosition(left = 0f, top = 0f)
                                width(24f)
                                text("A")
                                fontSize(15f)
                                color(ctx.pagePalette().textSecondary)
                            }
                        }
                        Text {
                            attr {
                                absolutePosition(
                                    left = (264f * ctx.sliderProgress() - 32f).coerceAtLeast(0f),
                                    top = 0f,
                                )
                                width(64f)
                                text(ctx.manualScaleLabel())
                                fontSize(16f)
                                textAlignCenter()
                                color(ctx.pagePalette().textSecondary)
                            }
                        }
                        Text {
                            attr {
                                absolutePosition(right = 0f, top = 0f)
                                width(24f)
                                text("A")
                                fontSize(30f)
                                textAlignRight()
                                color(ctx.pagePalette().textSecondary)
                            }
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

    private fun pagePalette(): SettingsPalette {
        val basePalette = palette()
        return if (basePalette === SettingsPalettes.Dark) {
            basePalette
        } else {
            basePalette.copy(
                background = Color(0xFFF3F7FA),
                surfaceMuted = Color(0xFFF1F1F1),
            )
        }
    }

    private fun fontPageContentWidth(): Float {
        return (pagerData.pageViewWidth - PAGE_HORIZONTAL_MARGIN * 2f).coerceAtLeast(1f)
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private companion object {
        const val PAGE_HORIZONTAL_MARGIN = 16f
    }
}
