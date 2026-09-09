package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.model.BackgroundPreset
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun BackgroundSettingsPage.BackgroundPresetPicker(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        SettingsCard(
            width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
            palette = ctx::palette,
        ) {
            View {
                attr {
                    padding(
                        top = 18f.settingsDp(),
                        left = 18f.settingsDp(),
                        right = 18f.settingsDp(),
                        bottom = 18f.settingsDp(),
                    )
                }
                Text {
                    attr {
                        text("背景方案")
                        fontSize(16f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().textPrimary)
                    }
                }
                Text {
                    attr {
                        text("选择一个适合阅读行情与 AI 解读的背景")
                        fontSize(12f.settingsDp())
                        color(ctx.palette().textSecondary)
                        marginTop(4f.settingsDp())
                    }
                }
                Scroller {
                    attr {
                        height(88f.settingsDp())
                        flexDirectionRow()
                        marginTop(14f.settingsDp())
                        showScrollerIndicator(false)
                        bouncesEnable(true)
                    }
                    BackgroundPreset.values().forEach { preset ->
                        ctx.BackgroundPresetChoice(this, preset)
                    }
                }
                ctx.BackgroundImageActions(this)
            }
        }
    }
}

internal fun BackgroundSettingsPage.BackgroundPresetChoice(
    container: ViewContainer<*, *>,
    preset: BackgroundPreset,
) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(92f.settingsDp())
                height(80f.settingsDp())
                marginRight(10f.settingsDp())
                borderRadius(15f.settingsDp())
                border(
                    Border(
                        if (ctx.isPresetSelected(preset)) {
                            2f.settingsDp()
                        } else {
                            1f.settingsDp()
                        },
                        BorderStyle.SOLID,
                        if (ctx.isPresetSelected(preset)) {
                            ctx.palette().accent
                        } else {
                            ctx.palette().divider
                        },
                    )
                )
                overflow(true)
            }
            event {
                click {
                    ctx.updateSettings(ctx.settings.copy(preset = preset, customImageUri = null))
                }
            }
            View {
                attr {
                    height(49f.settingsDp())
                    ctx.applyPresetGradient(this, preset)
                }
            }
            View {
                attr {
                    height(30f.settingsDp())
                    backgroundColor(ctx.palette().surface)
                    allCenter()
                }
                Text {
                    attr {
                        text(preset.displayName)
                        fontSize(11f.settingsDp())
                        fontWeightMedium()
                        color(ctx.palette().textPrimary)
                    }
                }
            }
        }
    }
}

internal fun BackgroundSettingsPage.BackgroundImageActions(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                height(44f.settingsDp())
                flexDirectionRow()
                alignItemsCenter()
                marginTop(12f.settingsDp())
            }
            View {
                attr {
                    flex(1f)
                    height(44f.settingsDp())
                    borderRadius(21f.settingsDp())
                    backgroundColor(ctx.palette().accentSoft)
                    border(Border(1f, BorderStyle.SOLID, ctx.palette().accent))
                    allCenter()
                }
                event { click { ctx.chooseBackgroundImage() } }
                Text {
                    attr {
                        text(
                            if (ctx.settings.customImageUri.isNullOrBlank()) "选择本地图片" else "更换背景图片"
                        )
                        fontSize(13f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().accent)
                    }
                }
            }
            vif({ !ctx.settings.customImageUri.isNullOrBlank() }) {
                View {
                    attr {
                        width(76f.settingsDp())
                        height(44f.settingsDp())
                        borderRadius(21f.settingsDp())
                        backgroundColor(ctx.palette().surfaceMuted)
                        marginLeft(10f.settingsDp())
                        allCenter()
                    }
                    event { click { ctx.updateSettings(ctx.settings.copy(customImageUri = null)) } }
                    Text {
                        attr {
                            text("清除")
                            fontSize(13f.settingsDp())
                            color(ctx.palette().textSecondary)
                        }
                    }
                }
            }
        }
    }
}
