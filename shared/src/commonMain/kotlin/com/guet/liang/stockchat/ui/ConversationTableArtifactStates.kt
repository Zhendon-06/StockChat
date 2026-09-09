package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.closePage
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 对比表格详情页头部与加载 / 未找到 / 空数据 / 错误状态。

internal fun ConversationTableArtifactPage.PageHeader(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                height(pagerData.statusBarHeight + HEADER_HEIGHT)
                padding(
                    top = pagerData.statusBarHeight + 12f,
                    left = 18f,
                    right = 18f,
                )
                backgroundColor(StockChatTheme.background)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    size(44f, 44f)
                    borderRadius(22f)
                    backgroundColor(StockChatTheme.surface)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    allCenter()
                }
                event {
                    click { ctx.closePage() }
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
            View {
                attr {
                    flex(1f)
                    marginLeft(14f)
                }
                Text {
                    attr {
                        text("会话表格对比")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text("关键行情指标汇总")
                        fontSize(11f)
                        color(StockChatTheme.textTertiary)
                        marginTop(2f)
                        lines(1)
                    }
                }
            }
        }
    }
}

internal fun ConversationTableArtifactPage.LoadingState(container: ViewContainer<*, *>) {
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
            }
            View {
                attr {
                    size(48f, 48f)
                    borderRadius(16f)
                    backgroundColor(StockChatTheme.accentSoft)
                    allCenter()
                }
                Text {
                    attr {
                        text("…")
                        fontSize(24f)
                        color(StockChatTheme.accent)
                        marginBottom(8f)
                    }
                }
            }
            Text {
                attr {
                    text("正在读取表格对比")
                    fontSize(14f)
                    color(StockChatTheme.textSecondary)
                    marginTop(14f)
                }
            }
        }
    }
}

internal fun ConversationTableArtifactPage.NotFoundState(container: ViewContainer<*, *>) {
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
                padding(left = 36f, right = 36f)
            }
            Text {
                attr {
                    text("未找到该会话对比")
                    fontSize(20f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("对应会话可能已删除，请返回对比列表重新选择。")
                    fontSize(14f)
                    lineHeight(21f)
                    textAlignCenter()
                    color(StockChatTheme.textSecondary)
                    marginTop(8f)
                }
            }
        }
    }
}

internal fun ConversationTableArtifactPage.EmptyState(
    container: ViewContainer<*, *>,
    state: ComparisonDetailUiState.Empty,
) {
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
                padding(left = 36f, right = 36f)
            }
            View {
                attr {
                    size(64f, 64f)
                    borderRadius(22f)
                    backgroundColor(StockChatTheme.accentSoft)
                    allCenter()
                }
                Text {
                    attr {
                        text("表")
                        fontSize(24f)
                        fontWeightBold()
                        color(StockChatTheme.accent)
                    }
                }
            }
            Text {
                attr {
                    text("该会话还没有可对比标的")
                    fontSize(20f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(18f)
                }
            }
            Text {
                attr {
                    text("在聊天中提及股票或指数名称、代码，或让 AI 生成相关行情后再试。")
                    fontSize(14f)
                    lineHeight(21f)
                    textAlignCenter()
                    color(StockChatTheme.textSecondary)
                    marginTop(8f)
                }
            }
            Text {
                attr {
                    text("${state.title} · 来源消息 ${state.sourceMessageCount} 条")
                    fontSize(11f)
                    color(StockChatTheme.textTertiary)
                    marginTop(12f)
                    lines(2)
                    textAlignCenter()
                }
            }
        }
    }
}

internal fun ConversationTableArtifactPage.ErrorState(container: ViewContainer<*, *>, message: String) {
    val ctx = this
    with(container) {
        View {
            attr {
                absolutePositionAllZero()
                allCenter()
                padding(left = 36f, right = 36f)
            }
            Text {
                attr {
                    text("表格对比读取失败")
                    fontSize(20f)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text(message)
                    fontSize(14f)
                    lineHeight(21f)
                    textAlignCenter()
                    color(StockChatTheme.textSecondary)
                    marginTop(8f)
                }
            }
            View {
                attr {
                    height(42f)
                    borderRadius(21f)
                    backgroundColor(StockChatTheme.accent)
                    padding(left = 22f, right = 22f)
                    allCenter()
                    marginTop(20f)
                }
                event {
                    click { ctx.loadComparison() }
                }
                Text {
                    attr {
                        text("重新汇总")
                        fontSize(14f)
                        fontWeightBold()
                        color(Color.WHITE)
                    }
                }
            }
        }
    }
}
