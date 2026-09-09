package com.guet.liang.stockchat.ui.settings

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 模型配置页弹窗与提示：未保存更改确认、安全提示。

internal fun ModelConfigurationPage.UnsavedChangesDialog(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        vif({ ctx.unsavedDialogOpen }) {
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(Color(0x88000000))
                    zIndex(30)
                }
                event {
                    click { }
                }
            }
            View {
                attr {
                    absolutePositionAllZero()
                    alignItemsCenter()
                    justifyContentCenter()
                    zIndex(31)
                }
                View {
                    attr {
                        width((ctx.pagerData.pageViewWidth - 56f.settingsDp()).coerceAtLeast(1f))
                        alignSelfCenter()
                        borderRadius(20f.settingsDp())
                        backgroundColor(ctx.palette().surface)
                        padding(all = 20f.settingsDp())
                    }
                Text {
                    attr {
                        text("保存模型配置？")
                        fontSize(19f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().textPrimary)
                    }
                }
                Text {
                    attr {
                        text("当前页面有尚未保存的修改，退出后这些修改将丢失。")
                        fontSize(13f.settingsDp())
                        lineHeight(20f.settingsDp())
                        color(ctx.palette().textSecondary)
                        marginTop(10f.settingsDp())
                    }
                }
                View {
                    attr {
                        flexDirectionRow()
                        justifyContentFlexEnd()
                        alignItemsCenter()
                        marginTop(20f.settingsDp())
                    }
                    View {
                        attr {
                            height(38f.settingsDp())
                            padding(left = 12f.settingsDp(), right = 12f.settingsDp())
                            allCenter()
                        }
                        event {
                            click { ctx.unsavedDialogOpen = false }
                        }
                        Text {
                            attr {
                                text("继续编辑")
                                fontSize(13f.settingsDp())
                                color(ctx.palette().textSecondary)
                            }
                        }
                    }
                    View {
                        attr {
                            height(38f.settingsDp())
                            padding(left = 12f.settingsDp(), right = 12f.settingsDp())
                            allCenter()
                        }
                        event {
                            click { ctx.discardAndClose() }
                        }
                        Text {
                            attr {
                                text("放弃修改")
                                fontSize(13f.settingsDp())
                                color(ctx.palette().warning)
                            }
                        }
                    }
                    View {
                        attr {
                            height(38f.settingsDp())
                            padding(left = 16f.settingsDp(), right = 16f.settingsDp())
                            borderRadius(19f.settingsDp())
                            backgroundColor(ctx.palette().accent)
                            allCenter()
                            marginLeft(4f.settingsDp())
                        }
                        event {
                            click { ctx.saveAndClose() }
                        }
                        Text {
                            attr {
                                text("保存并退出")
                                fontSize(13f.settingsDp())
                                fontWeightBold()
                                color(Color.WHITE)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

internal fun ModelConfigurationPage.SecurityNotice(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                width((ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f))
                alignSelfCenter()
                marginTop(14f.settingsDp())
                borderRadius(16f.settingsDp())
                backgroundColor(ctx.palette().warningSoft)
                padding(
                    top = 13f.settingsDp(),
                    left = 14f.settingsDp(),
                    right = 14f.settingsDp(),
                    bottom = 13f.settingsDp(),
                )
            }
            Text {
                attr {
                    text("安全提示")
                    fontSize(12f.settingsDp())
                    fontWeightBold()
                    color(ctx.palette().warning)
                }
            }
            Text {
                attr {
                    text("API Key 和已获取的模型列表会保存在本机，便于下次启动继续使用。请避免在分享截图或日志中暴露密钥。")
                    fontSize(11f.settingsDp())
                    lineHeight(17f.settingsDp())
                    color(ctx.palette().warning)
                    marginTop(4f.settingsDp())
                }
            }
        }
    }
}
