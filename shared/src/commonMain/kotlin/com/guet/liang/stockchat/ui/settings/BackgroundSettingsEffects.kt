package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.ChatTextColorMode
import com.guet.liang.stockchat.ui.StockChatTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Size
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Slider
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.roundToInt

internal fun BackgroundSettingsPage.EffectAdjustments(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        Text {
            attr {
                width((ctx.pagerData.pageViewWidth - 40f.settingsDp()).coerceAtLeast(1f))
                alignSelfCenter()
                text("效果调整")
                fontSize(13f.settingsDp())
                fontWeightMedium()
                color(ctx.palette().textTertiary)
                marginTop(18f.settingsDp())
                marginBottom(2f.settingsDp())
            }
        }
        SettingsCard(
            width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
            palette = ctx::palette,
            marginTop = 8f.settingsDp(),
        ) {
            ctx.AdjustmentSlider(
                this,
                title = "背景柔化",
                subtitle = "降低背景细节对股票数据阅读的干扰",
                valueLabel = "${ctx.settings.blurRadius.roundToInt()}.00",
                value = ctx.settings.blurRadius,
                range =
                    ChatBackgroundSettings.MIN_BLUR_RADIUS..ChatBackgroundSettings.MAX_BLUR_RADIUS,
            ) { value ->
                ctx.updateSettings(ctx.settings.copy(blurRadius = value))
            }
            SettingsDivider(ctx::palette)
            ctx.AdjustmentSlider(
                this,
                title = "蒙版强度",
                subtitle = "增强统一蒙版，让页面元素更干净",
                valueLabel = ctx.formatTwoDecimals(ctx.settings.maskOpacity),
                value = ctx.settings.maskOpacity,
                range =
                    ChatBackgroundSettings.MIN_MASK_OPACITY..ChatBackgroundSettings.MAX_MASK_OPACITY,
            ) { value ->
                ctx.updateSettings(ctx.settings.copy(maskOpacity = value))
            }
            SettingsDivider(ctx::palette)
            ctx.AdjustmentSlider(
                this,
                title = "蒙版明暗",
                subtitle = "提高或压暗蒙版，不直接修改背景方案",
                valueLabel = ctx.formatTwoDecimals(ctx.settings.maskBrightness),
                value = ctx.settings.maskBrightness,
                range =
                    ChatBackgroundSettings.MIN_MASK_BRIGHTNESS..ChatBackgroundSettings
                            .MAX_MASK_BRIGHTNESS,
            ) { value ->
                ctx.updateSettings(ctx.settings.copy(maskBrightness = value))
            }
            SettingsDivider(ctx::palette)
            ctx.AdjustmentSlider(
                this,
                title = "聊天文本大小",
                subtitle = "仅调整用户消息、AI 回复与思考区字号",
                valueLabel = "${ctx.formatOneDecimal(ctx.settings.chatTextSizeSp)}sp",
                value = ctx.settings.chatTextSizeSp,
                range =
                    ChatBackgroundSettings.MIN_CHAT_TEXT_SIZE_SP..ChatBackgroundSettings
                            .MAX_CHAT_TEXT_SIZE_SP,
            ) { value ->
                ctx.updateSettings(ctx.settings.copy(chatTextSizeSp = value))
            }
            SettingsDivider(ctx::palette)
            ctx.TextColorPicker(this)
        }
    }
}

internal fun BackgroundSettingsPage.AdjustmentSlider(
    container: ViewContainer<*, *>,
    title: String,
    subtitle: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChanged: (Float) -> Unit,
) {
    val ctx = this
    val minimum = range.start
    val maximum = range.endInclusive
    val sliderWidth = (pagerData.pageViewWidth - 84f.settingsDp()).coerceAtLeast(200f.settingsDp())
    with(container) {
        View {
            attr {
                height(132f.settingsDp())
                padding(
                    top = 16f.settingsDp(),
                    left = 18f.settingsDp(),
                    right = 18f.settingsDp(),
                    bottom = 12f.settingsDp(),
                )
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr { flex(1f) }
                    Text {
                        attr {
                            text(title)
                            fontSize(15f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text(subtitle)
                            fontSize(11f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginTop(4f.settingsDp())
                            lines(1)
                        }
                    }
                }
                Text {
                    attr {
                        text(valueLabel)
                        fontSize(12f.settingsDp())
                        color(ctx.palette().textTertiary)
                        marginLeft(12f.settingsDp())
                    }
                }
            }
            Slider {
                attr {
                    size(sliderWidth, 42f.settingsDp())
                    marginTop(12f.settingsDp())
                    currentProgress(((value - minimum) / (maximum - minimum)).coerceIn(0f, 1f))
                    progressColor(ctx.palette().accent)
                    trackColor(ctx.palette().divider)
                    thumbColor(ctx.palette().surface)
                    thumbSize(Size(22f.settingsDp(), 22f.settingsDp()))
                    trackThickness(4f.settingsDp())
                    padding(
                        top = 9f.settingsDp(),
                        left = 1f.settingsDp(),
                        bottom = 9f.settingsDp(),
                        right = 1f.settingsDp(),
                    )
                }
                event {
                    progressDidChanged { progress ->
                        onValueChanged(minimum + (maximum - minimum) * progress)
                    }
                }
            }
        }
    }
}

internal fun BackgroundSettingsPage.TextColorPicker(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                height(128f.settingsDp())
                padding(
                    top = 16f.settingsDp(),
                    left = 18f.settingsDp(),
                    right = 18f.settingsDp(),
                    bottom = 14f.settingsDp(),
                )
            }
            Text {
                attr {
                    text("聊天文本颜色")
                    fontSize(15f.settingsDp())
                    fontWeightBold()
                    color(ctx.palette().textPrimary)
                }
            }
            Text {
                attr {
                    text("自动模式会综合背景图片与蒙版明暗选择高对比度文字")
                    fontSize(11f.settingsDp())
                    color(ctx.palette().textSecondary)
                    marginTop(4f.settingsDp())
                }
            }
            View {
                attr {
                    height(44f.settingsDp())
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(12f.settingsDp())
                }
                ChatTextColorMode.values().forEach { mode -> ctx.TextColorChoice(this, mode) }
            }
        }
    }
}

internal fun BackgroundSettingsPage.TextColorChoice(
    container: ViewContainer<*, *>,
    mode: ChatTextColorMode,
) {
    val ctx = this
    val selected = ctx.settings.chatTextColorMode == mode
    with(container) {
        View {
            attr {
                if (mode == ChatTextColorMode.AUTOMATIC) {
                    height(44f.settingsDp())
                    padding(left = 12f.settingsDp(), right = 12f.settingsDp())
                    borderRadius(19f.settingsDp())
                } else {
                    size(38f.settingsDp(), 38f.settingsDp())
                    borderRadius(19f.settingsDp())
                }
                marginRight(9f.settingsDp())
                backgroundColor(ctx.textModeColor(mode))
                border(
                    Border(
                        if (selected) 2f else 1f,
                        BorderStyle.SOLID,
                        if (selected) ctx.palette().accent else ctx.palette().divider,
                    )
                )
                allCenter()
            }
            event { click { ctx.updateSettings(ctx.settings.copy(chatTextColorMode = mode)) } }
            if (mode == ChatTextColorMode.AUTOMATIC) {
                Text {
                    attr {
                        text(if (selected) "✓ 自动" else "自动")
                        fontSize(11f.settingsDp())
                        fontWeightMedium()
                        color(ctx.palette().textPrimary)
                    }
                }
            } else if (selected) {
                Text {
                    attr {
                        text("✓")
                        fontSize(15f.settingsDp())
                        fontWeightBold()
                        color(
                            if (mode == ChatTextColorMode.LIGHT)
                                Color(StockChatTheme.COLOR_FF374151)
                            else Color.WHITE
                        )
                    }
                }
            }
        }
    }
}
