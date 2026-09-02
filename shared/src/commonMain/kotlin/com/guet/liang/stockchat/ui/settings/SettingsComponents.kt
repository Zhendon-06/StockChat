package com.guet.liang.stockchat.ui.settings

import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class SettingsSegmentOption<T>(
    val value: T,
    val label: String,
)

internal fun ViewContainer<*, *>.SettingsPageHeader(
    statusBarHeight: Float,
    title: String,
    palette: () -> SettingsPalette,
    actionText: String = "",
    onBack: () -> Unit,
    onAction: () -> Unit = {},
) {
    View {
        attr {
            height(statusBarHeight + SETTINGS_HEADER_HEIGHT)
            backgroundColor(palette().background)
        }
        View {
            attr {
                absolutePosition(
                    top = statusBarHeight + 8f.settingsDp(),
                    left = 12f.settingsDp(),
                )
                size(48f, 48f)
                allCenter()
            }
            event {
                click { onBack() }
            }
            Text {
                attr {
                    text("‹")
                    fontSize(38f.settingsDp())
                    fontWeightMedium()
                    color(palette().textPrimary)
                    marginBottom(4f.settingsDp())
                }
            }
        }
        View {
            attr {
                absolutePosition(
                    top = statusBarHeight,
                    left = 68f.settingsDp(),
                    right = 68f.settingsDp(),
                )
                height(SETTINGS_HEADER_HEIGHT)
                allCenter()
            }
            Text {
                attr {
                    text(title)
                    fontSize(21f.settingsDp())
                    fontWeightBold()
                    color(palette().textPrimary)
                    lines(1)
                    textAlignCenter()
                }
            }
        }
        if (actionText.isNotEmpty()) {
            View {
                attr {
                    absolutePosition(
                        top = statusBarHeight + 8f.settingsDp(),
                        right = 14f.settingsDp(),
                    )
                    minWidth(48f)
                    height(48f)
                    padding(left = 8f.settingsDp(), right = 8f.settingsDp())
                    allCenter()
                }
                event {
                    click { onAction() }
                }
                Text {
                    attr {
                        text(actionText)
                        fontSize(16f.settingsDp())
                        fontWeightBold()
                        color(palette().textPrimary)
                        lines(1)
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.SettingsCard(
    width: Float,
    palette: () -> SettingsPalette,
    marginTop: Float = 14f.settingsDp(),
    content: ViewBuilder,
) {
    View {
        attr {
            width(width)
            alignSelfCenter()
            marginTop(marginTop)
            borderRadius(20f.settingsDp())
            backgroundColor(palette().surface)
            overflow(true)
        }
        content()
    }
}

internal fun ViewContainer<*, *>.SettingsNavigationRow(
    title: String,
    palette: () -> SettingsPalette,
    value: () -> String = { "" },
    subtitle: String = "",
    showDisclosure: Boolean = true,
    minHeight: Float = if (subtitle.isEmpty()) 72f else 84f,
    onClick: () -> Unit = {},
) {
    View {
        attr {
            minHeight(minHeight.settingsDp())
            padding(
                top = 14f.settingsDp(),
                left = 20f.settingsDp(),
                right = 16f.settingsDp(),
                bottom = 14f.settingsDp(),
            )
            flexDirectionRow()
            alignItemsCenter()
        }
        event {
            click { onClick() }
        }
        View {
            attr {
                flex(1f)
                marginRight(12f.settingsDp())
            }
            Text {
                attr {
                    text(title)
                    fontSize(17f.settingsDp())
                    fontWeightMedium()
                    color(palette().textPrimary)
                    lines(1)
                }
            }
            if (subtitle.isNotEmpty()) {
                Text {
                    attr {
                        text(subtitle)
                        fontSize(12f.settingsDp())
                        lineHeight(18f.settingsDp())
                        color(palette().textSecondary)
                        marginTop(4f.settingsDp())
                        lines(2)
                    }
                }
            }
        }
        Text {
            attr {
                text(value())
                fontSize(14f.settingsDp())
                color(palette().textSecondary)
                lines(1)
                maxWidth(142f.settingsDp())
                textAlignRight()
            }
        }
        if (showDisclosure) {
            Text {
                attr {
                    text("›")
                    fontSize(31f.settingsDp())
                    color(palette().textTertiary)
                    marginLeft(8f.settingsDp())
                    marginBottom(2f.settingsDp())
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.SettingsDivider(
    palette: () -> SettingsPalette,
    inset: Float = 20f.settingsDp(),
) {
    View {
        attr {
            height(1f.settingsDp())
            marginLeft(inset)
            backgroundColor(palette().divider)
        }
    }
}

internal fun <T> ViewContainer<*, *>.SettingsSegmentedRow(
    title: String,
    options: List<SettingsSegmentOption<T>>,
    selectedValue: () -> T,
    palette: () -> SettingsPalette,
    onSelect: (T) -> Unit,
) {
    View {
        attr {
            height(78f.settingsDp())
            padding(left = 20f.settingsDp(), right = 16f.settingsDp())
            flexDirectionRow()
            alignItemsCenter()
        }
        Text {
            attr {
                text(title)
                fontSize(17f.settingsDp())
                fontWeightMedium()
                color(palette().textPrimary)
                lines(1)
            }
        }
        View {
            attr {
                flex(1f)
            }
        }
        View {
            attr {
                width(244f.settingsDp())
                height(44f.settingsDp())
                padding(all = 3f.settingsDp())
                borderRadius(13f.settingsDp())
                backgroundColor(palette().surfaceMuted)
                flexDirectionRow()
            }
            options.forEach { option ->
                View {
                    attr {
                        flex(1f)
                        height(44f.settingsDp())
                        borderRadius(10f.settingsDp())
                        backgroundColor(
                            if (selectedValue() == option.value) {
                                palette().surface
                            } else {
                                palette().surfaceMuted
                            },
                        )
                        allCenter()
                    }
                    event {
                        click { onSelect(option.value) }
                    }
                    Text {
                        attr {
                            text(option.label)
                            fontSize(14f.settingsDp())
                            if (selectedValue() == option.value) {
                                fontWeightBold()
                            } else {
                                fontWeightMedium()
                            }
                            color(
                                if (selectedValue() == option.value) {
                                    palette().textPrimary
                                } else {
                                    palette().textSecondary
                                },
                            )
                            lines(1)
                        }
                    }
                }
            }
        }
    }
}

internal val SETTINGS_HEADER_HEIGHT: Float = 64f.settingsDp()
