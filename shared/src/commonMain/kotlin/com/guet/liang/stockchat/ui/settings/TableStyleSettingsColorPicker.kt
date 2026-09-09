package com.guet.liang.stockchat.ui.settings

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun TableStyleSettingsPage.ColorPalette(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        SettingsCard(
            width =
                settingsContentWidth(
                    ctx.pagerData.pageViewWidth,
                    TableStyleSettingsPage.PAGE_HORIZONTAL_MARGIN,
                ),
            palette = { ctx.palette() },
        ) {
            View {
                attr {
                    padding(
                        top = 18f.settingsDp(),
                        left = 20f.settingsDp(),
                        right = 20f.settingsDp(),
                        bottom = 16f.settingsDp(),
                    )
                }
                Text {
                    attr {
                        text("自定义配色")
                        fontSize(17f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().textPrimary)
                    }
                }
                Text {
                    attr {
                        text("从调色盘中选择表头强调色，实时应用到表格预览。")
                        fontSize(12f.settingsDp())
                        lineHeight(18f.settingsDp())
                        color(ctx.palette().textSecondary)
                        marginTop(4f.settingsDp())
                    }
                }
                ctx.ColorField(this)
                ctx.HueSlider(this)
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        marginTop(14f.settingsDp())
                    }
                    Text {
                        attr {
                            text("当前颜色")
                            fontSize(12f.settingsDp())
                            color(ctx.palette().textSecondary)
                        }
                    }
                    View {
                        attr {
                            size(16f.settingsDp(), 16f.settingsDp())
                            borderRadius(8f.settingsDp())
                            marginLeft(8f.settingsDp())
                            backgroundColor(Color(ctx.selectedCustomColorArgb))
                            border(Border(1f, BorderStyle.SOLID, ctx.palette().divider))
                        }
                    }
                    Text {
                        attr {
                            text(ctx.formatHexColor(ctx.selectedCustomColorArgb))
                            fontSize(12f.settingsDp())
                            color(ctx.palette().textPrimary)
                            marginLeft(6f.settingsDp())
                        }
                    }
                }
            }
        }
    }
}

internal fun TableStyleSettingsPage.ColorField(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(
                    settingsContentWidth(
                        ctx.pagerData.pageViewWidth,
                        TableStyleSettingsPage.PAGE_HORIZONTAL_MARGIN,
                    ) - 40f.settingsDp()
                )
                height(TableStyleSettingsPage.COLOR_FIELD_HEIGHT)
                marginTop(16f.settingsDp())
                borderRadius(14f.settingsDp())
                overflow(true)
                capture(CaptureRule.pan(CaptureRuleDirection.ALL))
            }
            event {
                click { params -> ctx.selectColorField(params.x, params.y) }
                pan { params -> ctx.handleColorFieldPan(params) }
            }
            Canvas({ attr { absolutePositionAllZero() } }) { canvas, width, height ->
                ctx.drawColorField(canvas, width, height)
            }
        }
    }
}

internal fun TableStyleSettingsPage.HueSlider(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(
                    settingsContentWidth(
                        ctx.pagerData.pageViewWidth,
                        TableStyleSettingsPage.PAGE_HORIZONTAL_MARGIN,
                    ) - 40f.settingsDp()
                )
                height(TableStyleSettingsPage.COLOR_HUE_HEIGHT)
                marginTop(12f.settingsDp())
                borderRadius(9f.settingsDp())
                overflow(true)
                capture(CaptureRule.pan(CaptureRuleDirection.ALL))
            }
            event {
                click { params -> ctx.selectHue(params.x) }
                pan { params -> ctx.handleHuePan(params) }
            }
            Canvas({ attr { absolutePositionAllZero() } }) { canvas, width, height ->
                ctx.drawHueSlider(canvas, width, height)
            }
        }
    }
}
