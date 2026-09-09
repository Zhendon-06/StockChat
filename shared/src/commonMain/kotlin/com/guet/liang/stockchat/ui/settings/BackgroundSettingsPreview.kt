package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.ui.StockChatTheme
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun BackgroundSettingsPage.BackgroundPreview(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        SettingsCard(
            width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
            palette = ctx::palette,
            marginTop = 12f.settingsDp(),
        ) {
            View {
                attr {
                    height(300f.settingsDp())
                    padding(
                        top = 18f.settingsDp(),
                        left = 18f.settingsDp(),
                        right = 18f.settingsDp(),
                        bottom = 18f.settingsDp(),
                    )
                    ctx.applyPreviewGradient(this)
                }
                vif({ !ctx.settings.customImageUri.isNullOrBlank() }) {
                    Image {
                        attr {
                            absolutePositionAllZero()
                            resizeCover()
                            src(ctx.settings.customImageUri.orEmpty(), false)
                            touchEnable(false)
                        }
                    }
                }
                View {
                    attr {
                        absolutePositionAllZero()
                        backgroundColor(ctx.previewSofteningMaskColor())
                        touchEnable(false)
                    }
                }
                View {
                    attr {
                        absolutePositionAllZero()
                        backgroundColor(ctx.previewMaskColor())
                        touchEnable(false)
                    }
                }
                ctx.BackgroundPreviewHeader(this)
                ctx.BackgroundUserPreview(this)
                ctx.BackgroundAnswerPreview(this)
            }
        }
    }
}

internal fun BackgroundSettingsPage.BackgroundPreviewHeader(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text("聊天背景预览")
                    fontSize(15f.settingsDp())
                    fontWeightBold()
                    color(ctx.previewPrimaryTextColor())
                }
            }
            View { attr { flex(1f) } }
            View {
                attr {
                    height(26f.settingsDp())
                    borderRadius(13f.settingsDp())
                    padding(left = 10f.settingsDp(), right = 10f.settingsDp())
                    backgroundColor(Color(StockChatTheme.COLOR_FFFFFFFF, 0.74f))
                    allCenter()
                }
                Text {
                    attr {
                        text(ctx.backgroundLabel())
                        fontSize(10f.settingsDp())
                        fontWeightMedium()
                        color(Color(StockChatTheme.COLOR_FF475569))
                    }
                }
            }
        }
    }
}

internal fun BackgroundSettingsPage.BackgroundUserPreview(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                alignSelfFlexEnd()
                maxWidth(
                    (ctx.pagerData.pageViewWidth - 96f.settingsDp()).coerceAtLeast(
                        180f.settingsDp()
                    )
                )
                marginTop(34f.settingsDp())
                padding(
                    top = 11f.settingsDp(),
                    left = 14f.settingsDp(),
                    right = 14f.settingsDp(),
                    bottom = 11f.settingsDp(),
                )
                borderRadius(18f.settingsDp())
                backgroundColor(Color(StockChatTheme.COLOR_FFFFFFFF, 0.82f))
            }
            Text {
                attr {
                    text("帮我看看今天沪深 300 的表现")
                    fontSize(ctx.settings.chatTextSizeSp * SETTINGS_UI_SCALE)
                    lineHeight(ctx.settings.chatTextSizeSp * 1.45f * SETTINGS_UI_SCALE)
                    color(ctx.previewPrimaryTextColor())
                }
            }
        }
    }
}

internal fun BackgroundSettingsPage.BackgroundAnswerPreview(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                maxWidth(
                    (ctx.pagerData.pageViewWidth - 72f.settingsDp()).coerceAtLeast(
                        210f.settingsDp()
                    )
                )
                marginTop(14f.settingsDp())
                padding(
                    top = 13f.settingsDp(),
                    left = 14f.settingsDp(),
                    right = 14f.settingsDp(),
                    bottom = 13f.settingsDp(),
                )
                borderRadius(18f.settingsDp())
                backgroundColor(Color(StockChatTheme.COLOR_FFFFFFFF, 0.9f))
            }
            Text {
                attr {
                    text("沪深 300 演示行情温和上涨，关注成交量与权重板块持续性。")
                    fontSize(ctx.settings.chatTextSizeSp * SETTINGS_UI_SCALE)
                    lineHeight(ctx.settings.chatTextSizeSp * 1.48f * SETTINGS_UI_SCALE)
                    color(ctx.previewPrimaryTextColor())
                }
            }
            Text {
                attr {
                    text("+0.47%  ·  演示数据")
                    fontSize(
                        ((ctx.settings.chatTextSizeSp - 1f).coerceAtLeast(11f)) * SETTINGS_UI_SCALE
                    )
                    fontWeightBold()
                    color(Color(StockChatTheme.COLOR_FFD84943))
                    marginTop(7f.settingsDp())
                }
            }
        }
    }
}
