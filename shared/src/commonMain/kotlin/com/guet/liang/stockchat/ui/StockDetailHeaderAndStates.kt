package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.StockDetailControllerState
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 详情页头部与加载 / 空数据 / 错误状态。

internal fun StockDetailPage.DetailHeader(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                height(pagerData.statusBarHeight + 68f)
                padding(top = pagerData.statusBarHeight + 12f, left = 18f, right = 18f)
                backgroundColor(StockChatTheme.background)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    size(44f, 44f)
                    borderRadius(22f)
                    backgroundColor(StockChatTheme.surface)
                    themedBorder()
                    allCenter()
                }
                event {
                    click { ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage() }
                }
                Text {
                    attr {
                        text("‹")
                        fontSize(34f)
                        color(StockChatTheme.textPrimary)
                        marginBottom(3f)
                    }
                }
            }
            ctx.DetailTabSwitcher(this)
            ctx.DetailFavoriteButton(this)
            View {
                attr {
                    height(36f)
                    borderRadius(18f)
                    padding(left = 13f, right = 13f)
                    marginLeft(8f)
                    backgroundColor(StockChatTheme.surface)
                    themedBorder()
                    allCenter()
                }
                event { click { ctx.shareQuote() } }
                Text {
                    attr {
                        text("分享")
                        fontSize(scaledFontSize(13f))
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
        }
    }
}

internal fun StockDetailPage.LoadingState(container: ViewContainer<*, *>) {
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
            }
            View {
                attr {
                    size(42f, 42f)
                    borderRadius(21f)
                    backgroundColor(StockChatTheme.accentSoft)
                    allCenter()
                }
                Text {
                    attr {
                        text("…")
                        fontSize(22f)
                        color(StockChatTheme.accent)
                        marginBottom(8f)
                    }
                }
            }
            Text {
                attr {
                    text("正在加载行情")
                    fontSize(scaledFontSize(14f))
                    color(StockChatTheme.textSecondary)
                    marginTop(14f)
                }
            }
        }
    }
}

internal fun StockDetailPage.EmptyState(container: ViewContainer<*, *>) {
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
                padding(left = 32f, right = 32f)
            }
            Text {
                attr {
                    text("暂无该标的行情")
                    fontSize(scaledFontSize(19f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("暂未收录该股票或指数的行情信息。")
                    fontSize(scaledFontSize(14f))
                    color(StockChatTheme.textSecondary)
                    marginTop(8f)
                    textAlignCenter()
                }
            }
        }
    }
}

internal fun StockDetailPage.ErrorState(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
                padding(left = 32f, right = 32f)
            }
            Text {
                attr {
                    text("行情加载失败")
                    fontSize(scaledFontSize(19f))
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text((ctx.detailState as? StockDetailControllerState.Error)?.message ?: "请稍后重试")
                    fontSize(scaledFontSize(14f))
                    lineHeight(scaledFontSize(21f))
                    color(StockChatTheme.textSecondary)
                    marginTop(8f)
                    textAlignCenter()
                }
            }
            View {
                attr {
                    height(40f)
                    borderRadius(20f)
                    padding(left = 20f, right = 20f)
                    marginTop(20f)
                    backgroundColor(StockChatTheme.accent)
                    allCenter()
                }
                event { click { ctx.loadDetail() } }
                Text {
                    attr {
                        text("重新加载")
                        fontSize(scaledFontSize(14f))
                        fontWeightMedium()
                        color(Color.WHITE)
                    }
                }
            }
        }
    }
}

private fun StockDetailPage.DetailFavoriteButton(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                size(36f, 36f)
                borderRadius(18f)
                marginLeft(8f)
                backgroundColor(
                    if (ctx.currentDetailQuote()?.let(ctx::isFavorite) == true) {
                        StockChatTheme.warningSoft
                    } else {
                        StockChatTheme.surface
                    }
                )
                themedBorder()
                allCenter()
                touchEnable(ctx.currentDetailQuote() != null)
            }
            event { click { ctx.currentDetailQuote()?.let(ctx::toggleFavorite) } }
            Text {
                attr {
                    text(
                        if (ctx.currentDetailQuote()?.let(ctx::isFavorite) == true) {
                            "★"
                        } else {
                            "☆"
                        }
                    )
                    fontSize(21f)
                    color(
                        if (ctx.currentDetailQuote()?.let(ctx::isFavorite) == true) {
                            StockChatTheme.warning
                        } else {
                            StockChatTheme.accent
                        }
                    )
                }
            }
        }
    }
}
