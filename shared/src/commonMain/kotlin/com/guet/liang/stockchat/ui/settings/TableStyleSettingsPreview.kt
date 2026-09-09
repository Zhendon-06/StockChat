package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.ui.StockTableStyleChoice
import com.guet.liang.stockchat.ui.StockTableStylePreview
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun TableStyleSettingsPage.TablePreview(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        SettingsCard(
            width =
                settingsContentWidth(
                    ctx.pagerData.pageViewWidth,
                    TableStyleSettingsPage.PAGE_HORIZONTAL_MARGIN,
                ),
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
                ctx.TablePreviewHeader(this)
                View {
                    attr {
                        height(TableStyleSettingsPage.TABLE_PREVIEW_HEIGHT)
                        marginTop(16f.settingsDp())
                        borderRadius(14f.settingsDp())
                        overflow(true)
                        backgroundColor(ctx.palette().surfaceMuted)
                    }
                    StockTableStylePreview(
                        selectedStyle = { ctx.previewChoice(ctx.selectedPreset) },
                        viewportHeight = TableStyleSettingsPage.TABLE_PREVIEW_HEIGHT,
                        uiScale = SETTINGS_UI_SCALE,
                        customColor = { Color(ctx.selectedCustomColorArgb) },
                        refreshKey = { ctx.previewRefreshKey },
                    )
                }
            }
        }
    }
}

internal fun TableStyleSettingsPage.TablePreviewHeader(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
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
    }
}

internal fun TableStyleSettingsPage.StyleChoices(container: ViewContainer<*, *>) {
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
                    SettingsDivider(palette = { ctx.palette() }, inset = 84f.settingsDp())
                }
            }
        }
        ctx.ColorPalette(this)
    }
}

internal fun TableStyleSettingsPage.StyleChoiceRow(
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
                    }
                )
            }
            event { click { ctx.selectedPreset = ctx.settingsPreset(choice) } }
            ctx.StyleChoiceIcon(this, choice)
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
                        }
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

internal fun TableStyleSettingsPage.StyleChoiceIcon(
    container: ViewContainer<*, *>,
    choice: StockTableStyleChoice,
) {
    val ctx = this
    with(container) {
        View {
            attr {
                size(48f.settingsDp(), 48f.settingsDp())
                borderRadius(13f.settingsDp())
                backgroundColor(
                    if (ctx.isSelected(choice)) {
                        ctx.palette().surface
                    } else {
                        ctx.palette().surfaceMuted
                    }
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
    }
}

internal fun TableStyleSettingsPage.ApplyButton(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(
                    settingsContentWidth(
                        ctx.pagerData.pageViewWidth,
                        TableStyleSettingsPage.PAGE_HORIZONTAL_MARGIN,
                    )
                )
                height(50f.settingsDp())
                alignSelfCenter()
                marginTop(18f.settingsDp())
                borderRadius(25f.settingsDp())
                backgroundColor(ctx.palette().accent)
                allCenter()
            }
            event { click { ctx.applyAndClose() } }
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
