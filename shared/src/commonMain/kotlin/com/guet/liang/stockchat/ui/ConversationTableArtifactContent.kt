package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.ComparisonContentUi
import com.guet.liang.stockchat.controller.ComparisonRefreshPhase
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 对比表格详情页内容区：表格、汇总指标与刷新状态。

internal fun ConversationTableArtifactPage.ComparisonContent(
    container: ViewContainer<*, *>,
    content: ComparisonContentUi,
) {
    val ctx = this
    val contentHeight =
        (pagerData.pageViewHeight -
                pagerData.statusBarHeight -
                HEADER_HEIGHT -
                pagerData.safeAreaInsets.bottom)
            .coerceAtLeast(0f)
    val tableHeight = (contentHeight - NON_TABLE_CONTENT_HEIGHT).coerceAtLeast(MIN_TABLE_HEIGHT)
    with(container) {
        Scroller {
            attr {
                absolutePositionAllZero()
                padding(top = 14f, left = 16f, right = 16f, bottom = 12f)
                showScrollerIndicator(false)
                bouncesEnable(true)
            }
            View {
                attr {
                    width(ctx.contentWidth())
                    alignSelfCenter()
                }
                ctx.ComparisonSummary(this, content)
                ctx.RefreshStatus(this, content)
                ctx.ComparisonTableHeader(this)
                View {
                    attr {
                        height(tableHeight)
                        alignSelfStretch()
                        borderRadius(14f)
                        themedBorder()
                        backgroundColor(StockChatTheme.surface)
                        overflow(true)
                    }
                    ConversationStockComparisonTable(
                        rows = content.snapshot.rows,
                        viewportHeight = tableHeight,
                        onRowClick = ctx::openStockDetail,
                    )
                }
                View {
                    attr {
                        height(RISK_NOTICE_HEIGHT)
                        borderRadius(14f)
                        backgroundColor(StockChatTheme.warningSoft)
                        padding(left = 14f, right = 14f)
                        justifyContentCenter()
                        marginTop(12f)
                    }
                    Text {
                        attr {
                            text(content.snapshot.disclaimer)
                            fontSize(12f)
                            lineHeight(18f)
                            color(StockChatTheme.warning)
                        }
                    }
                }
            }
        }
    }
}

internal fun ConversationTableArtifactPage.ComparisonSummary(
    container: ViewContainer<*, *>,
    content: ComparisonContentUi,
) {
    val ctx = this
    val snapshot = content.snapshot
    val userMentionedCount = snapshot.rows.count(ConversationStockComparisonRow::mentionedByUser)
    val aiGeneratedCount = snapshot.rows.count(ConversationStockComparisonRow::generatedByAi)
    with(container) {
        View {
            attr {
                height(SUMMARY_HEIGHT)
                borderRadius(20f)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                padding(top = 14f, left = 16f, right = 16f, bottom = 14f)
            }
            Text {
                attr {
                    text(snapshot.title)
                    fontSize(18f)
                    fontWeightBold()
                    lineHeight(23f)
                    lines(2)
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("从 ${snapshot.sourceMessageCount} 条消息中去重汇总，不再复述整段问答")
                    fontSize(11f)
                    color(StockChatTheme.textSecondary)
                    marginTop(5f)
                    lines(1)
                }
            }
            View {
                attr {
                    flex(1f)
                    flexDirectionRow()
                    alignItemsFlexEnd()
                    marginTop(10f)
                }
                ctx.ComparisonSummaryMetric(this, "${snapshot.rows.size}", "全部标的", isFirst = true)
                ctx.ComparisonSummaryMetric(this, "$userMentionedCount", "用户提及")
                ctx.ComparisonSummaryMetric(this, "$aiGeneratedCount", "AI 生成")
            }
        }
    }
}

internal fun ConversationTableArtifactPage.ComparisonSummaryMetric(
    container: ViewContainer<*, *>,
    value: String,
    label: String,
    isFirst: Boolean = false,
) {
    with(container) {
        View {
            attr {
                flex(1f)
                height(42f)
                borderRadius(12f)
                backgroundColor(
                    if (isFirst) StockChatTheme.accentSoft else StockChatTheme.surfaceSoft
                )
                themedBorder()
                marginRight(if (label == "AI 生成") 0f else 8f)
                flexDirectionRow()
                alignItemsCenter()
                padding(left = 10f, right = 8f)
            }
            Text {
                attr {
                    text(value)
                    fontSize(17f)
                    fontWeightBold()
                    color(if (isFirst) StockChatTheme.accent else StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text(label)
                    fontSize(10f)
                    color(StockChatTheme.textSecondary)
                    marginLeft(5f)
                    lines(1)
                }
            }
        }
    }
}

internal fun ConversationTableArtifactPage.RefreshStatus(
    container: ViewContainer<*, *>,
    content: ComparisonContentUi,
) {
    val ctx = this
    val (statusBackground, statusColor) = comparisonRefreshColors(content.refreshPhase)
    val statusText = comparisonRefreshText(content)
    val canRefresh =
        content.refreshPhase != ComparisonRefreshPhase.REFRESHING &&
            content.refreshPhase != ComparisonRefreshPhase.SESSION_ONLY
    with(container) {
        View {
            attr {
                height(REFRESH_STATUS_HEIGHT)
                borderRadius(13f)
                backgroundColor(statusBackground)
                padding(left = 13f, right = 13f)
                marginTop(10f)
                flexDirectionRow()
                alignItemsCenter()
            }
            if (canRefresh) {
                event { click { ctx.refreshMarketData() } }
            }
            Text {
                attr {
                    text(statusText)
                    fontSize(11f)
                    fontWeightMedium()
                    color(statusColor)
                    flex(1f)
                    lines(1)
                }
            }
            if (canRefresh) {
                Text {
                    attr {
                        text("重新刷新")
                        fontSize(11f)
                        fontWeightBold()
                        color(statusColor)
                        marginLeft(8f)
                    }
                }
            }
        }
    }
}

private fun ConversationTableArtifactPage.ComparisonTableHeader(container: ViewContainer<*, *>) {
    with(container) {
        View {
            attr {
                height(TABLE_SECTION_HEADER_HEIGHT)
                flexDirectionRow()
                alignItemsCenter()
                marginTop(10f)
            }
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text("关键交易指标")
                        fontSize(16f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("点击任意一行进入行情详情")
                        fontSize(10f)
                        color(StockChatTheme.textTertiary)
                        marginTop(2f)
                    }
                }
            }
            Text {
                attr {
                    text("左右滑动查看全部列")
                    fontSize(11f)
                    color(StockChatTheme.textTertiary)
                }
            }
        }
    }
}

private fun comparisonRefreshColors(phase: ComparisonRefreshPhase): Pair<Color, Color> =
    when (phase) {
        ComparisonRefreshPhase.PARTIAL,
        ComparisonRefreshPhase.FAILED -> StockChatTheme.warningSoft to StockChatTheme.warning
        ComparisonRefreshPhase.SESSION_ONLY ->
            StockChatTheme.recessed to StockChatTheme.textSecondary
        else -> StockChatTheme.accentSoft to StockChatTheme.accent
    }

private fun comparisonRefreshText(content: ComparisonContentUi): String =
    when (content.refreshPhase) {
        ComparisonRefreshPhase.REFRESHING ->
            "正在同步最新行情 ${content.completedCount}/${content.refreshTargetCount}"
        ComparisonRefreshPhase.CURRENT -> "已同步 ${content.refreshedCount} 个标的的最新行情"
        ComparisonRefreshPhase.PARTIAL ->
            "已更新 ${content.refreshedCount}/${content.refreshTargetCount}，其余保留会话行情"
        ComparisonRefreshPhase.FAILED -> "实时行情暂不可用，当前展示会话中的最近数据"
        ComparisonRefreshPhase.SESSION_ONLY -> "已完成会话表格汇总，暂无可刷新的证券代码"
    }
