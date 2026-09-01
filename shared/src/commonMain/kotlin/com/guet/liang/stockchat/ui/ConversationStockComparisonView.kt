package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.table.KuiklyTable
import com.guet.liang.kuiklytableview.table.TableAlignment
import com.guet.liang.kuiklytableview.table.TableBorderOptions
import com.guet.liang.kuiklytableview.table.TableDensity
import com.guet.liang.kuiklytableview.table.TableHeaderStyle
import com.guet.liang.kuiklytableview.table.TablePadding
import com.guet.liang.kuiklytableview.table.TableStyleOptions
import com.guet.liang.kuiklytableview.table.tableSpec
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal const val STOCK_COMPARISON_TABLE_HEADER_HEIGHT = 48f
internal const val STOCK_COMPARISON_TABLE_ROW_HEIGHT = 82f

internal fun ViewContainer<*, *>.ConversationStockComparisonTable(
    rows: List<ConversationStockComparisonRow>,
    viewportHeight: Float,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    val tableSpec = tableSpec<ConversationStockComparisonRow> {
        rows(rows)
        rowKey { row -> "${row.providerSymbol}:${row.symbol}:${row.displayName}" }
        rowHeight = STOCK_COMPARISON_TABLE_ROW_HEIGHT
        padding(horizontal = 10f, vertical = 7f)
        style(
            TableStyleOptions(
                density = TableDensity.Comfortable,
                borders = TableBorderOptions(),
                stripedRows = true,
                headerStyle = TableHeaderStyle.Accent,
                rowBackgroundColor = StockChatTheme.surface,
                alternateRowBackgroundColor = StockChatTheme.surfaceSoft,
                textColor = StockChatTheme.textPrimary,
                headerBackgroundColor = Color(0xFF176D57),
                headerTextColor = Color.WHITE,
                borderWidth = 1f,
                borderColor = StockChatTheme.border,
                cellPadding = TablePadding(7f, 10f, 7f, 10f),
            )
        )
        header {
            height = STOCK_COMPARISON_TABLE_HEADER_HEIGHT
        }
        columns {
            column("instrument", "标的 / 会话来源", width = 210f) {
                value { row -> row.displayName }
                cell { cell -> ComparisonInstrumentCell(cell.row, onRowClick) }
            }
            column("price", "最新价", width = 96f) {
                alignment = TableAlignment.End
                value { row -> row.price.orPlaceholder() }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = cell.row.price.orPlaceholder(),
                        secondary = cell.row.dataSource.label,
                        alignment = TableAlignment.End,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("change", "涨跌", width = 112f) {
                alignment = TableAlignment.End
                value { row -> row.changePercent.orPlaceholder() }
                cell { cell -> ComparisonChangeCell(cell.row, onRowClick) }
            }
            column("open", "今开 / 昨收", width = 112f) {
                alignment = TableAlignment.End
                value { row -> row.open.orPlaceholder() }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = cell.row.open.orPlaceholder(),
                        secondary = "昨 ${cell.row.previousClose.orPlaceholder()}",
                        alignment = TableAlignment.End,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("range", "最高 / 最低", width = 118f) {
                alignment = TableAlignment.End
                value { row -> row.high.orPlaceholder() }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = "高 ${cell.row.high.orPlaceholder()}",
                        secondary = "低 ${cell.row.low.orPlaceholder()}",
                        alignment = TableAlignment.End,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("amplitude", "振幅", width = 92f) {
                alignment = TableAlignment.End
                value { row -> row.amplitude.asPercent() }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = cell.row.amplitude.asPercent(),
                        alignment = TableAlignment.End,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("turnover", "换手率", width = 98f) {
                alignment = TableAlignment.End
                value { row -> row.turnoverRate.asPercent() }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = cell.row.turnoverRate.asPercent(),
                        alignment = TableAlignment.End,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("pe", "市盈率", width = 96f) {
                alignment = TableAlignment.End
                value { row -> row.priceEarningsRatio.orPlaceholder() }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = cell.row.priceEarningsRatio.orPlaceholder(),
                        alignment = TableAlignment.End,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("amount", "成交额", width = 136f) {
                alignment = TableAlignment.End
                value { row -> row.amount.withUnit(row.amountUnit) }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = cell.row.amount.withUnit(cell.row.amountUnit),
                        secondary = cell.row.volume.withUnit(cell.row.volumeUnit, "量 "),
                        alignment = TableAlignment.End,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("trend", "近期走势", width = 128f) {
                alignment = TableAlignment.Center
                value { row -> row.trendPoints.size.toString() }
                cell { cell -> ComparisonTrendCell(cell.row, onRowClick) }
            }
            column("updated", "数据时间", width = 172f) {
                value { row -> row.updatedAt.orPlaceholder() }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = cell.row.updatedAt.orPlaceholder(),
                        secondary = "点击查看详情  ›",
                        onRowClick = onRowClick,
                    )
                }
            }
        }
        emptyState {
            View {
                attr {
                    width(STOCK_COMPARISON_TABLE_CONTENT_WIDTH)
                    height((viewportHeight - STOCK_COMPARISON_TABLE_HEADER_HEIGHT).coerceAtLeast(1f))
                    allCenter()
                }
                Text {
                    attr {
                        text("该会话暂未识别到可对比的股票或指数")
                        fontSize(14f)
                        color(StockChatTheme.textSecondary)
                    }
                }
            }
        }
    }

    KuiklyTable(
        spec = tableSpec,
        viewportHeight = viewportHeight,
    )
}

private fun ViewContainer<*, *>.ComparisonInstrumentCell(
    row: ConversationStockComparisonRow,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    ComparisonClickableCell(row, onRowClick) {
        Text {
            attr {
                text(row.displayName)
                fontSize(14f)
                fontWeightBold()
                color(StockChatTheme.textPrimary)
                lines(1)
            }
        }
        Text {
            attr {
                text(listOf(row.marketLabel, row.symbol).filter(String::isNotBlank).joinToString(" · "))
                fontSize(11f)
                color(StockChatTheme.textSecondary)
                marginTop(2f)
                lines(1)
            }
        }
        View {
            attr {
                height(20f)
                flexDirectionRow()
                alignItemsCenter()
                marginTop(5f)
            }
            if (row.mentionedByUser) {
                ComparisonSourceBadge("用户提及", Color(0xFFEAF2FF), Color(0xFF356AA0))
            }
            if (row.generatedByAi) {
                ComparisonSourceBadge("AI 生成", StockChatTheme.accentSoft, StockChatTheme.accent)
            }
            if (!row.mentionedByUser && !row.generatedByAi) {
                ComparisonSourceBadge("会话识别", StockChatTheme.recessed, StockChatTheme.textSecondary)
            }
        }
    }
}

private fun ViewContainer<*, *>.ComparisonSourceBadge(
    label: String,
    backgroundColor: Color,
    textColor: Color,
) {
    View {
        attr {
            height(18f)
            borderRadius(9f)
            backgroundColor(backgroundColor)
            padding(left = 7f, right = 7f)
            marginRight(5f)
            allCenter()
        }
        Text {
            attr {
                text(label)
                fontSize(9f)
                fontWeightMedium()
                color(textColor)
                lines(1)
            }
        }
    }
}

private fun ViewContainer<*, *>.ComparisonChangeCell(
    row: ConversationStockComparisonRow,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    val changeColor = when {
        row.change.startsWith("-") || row.changePercent.startsWith("-") -> StockChatTheme.negative
        row.change.isBlank() && row.changePercent.isBlank() -> StockChatTheme.textSecondary
        else -> StockChatTheme.positive
    }
    ComparisonClickableCell(row, onRowClick, alignEnd = true) {
        Text {
            attr {
                text(row.changePercent.orPlaceholder())
                fontSize(14f)
                fontWeightBold()
                color(changeColor)
                lines(1)
                textAlignRight()
            }
        }
        Text {
            attr {
                text(row.change.orPlaceholder())
                fontSize(11f)
                color(changeColor)
                marginTop(5f)
                lines(1)
                textAlignRight()
            }
        }
    }
}

private fun ViewContainer<*, *>.ComparisonValueCell(
    row: ConversationStockComparisonRow,
    primary: String,
    secondary: String = "",
    alignment: TableAlignment = TableAlignment.Start,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    val alignEnd = alignment == TableAlignment.End
    ComparisonClickableCell(row, onRowClick, alignEnd) {
        Text {
            attr {
                text(primary)
                fontSize(13f)
                fontWeightMedium()
                color(StockChatTheme.textPrimary)
                lines(if (secondary.isBlank()) 2 else 1)
                if (alignEnd) textAlignRight()
            }
        }
        if (secondary.isNotBlank()) {
            Text {
                attr {
                    text(secondary)
                    fontSize(10f)
                    color(StockChatTheme.textTertiary)
                    marginTop(5f)
                    lines(1)
                    if (alignEnd) textAlignRight()
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.ComparisonTrendCell(
    row: ConversationStockComparisonRow,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    ComparisonClickableCell(row, onRowClick) {
        if (row.trendPoints.size > 1) {
            ComparisonSparkline(
                points = row.trendPoints,
                positive = !row.change.startsWith("-") && !row.changePercent.startsWith("-"),
            )
        } else {
            Text {
                attr {
                    text("暂无走势")
                    fontSize(11f)
                    color(StockChatTheme.textTertiary)
                    textAlignCenter()
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.ComparisonSparkline(
    points: List<Float>,
    positive: Boolean,
) {
    Canvas({
        attr {
            size(104f, 42f)
        }
    }) { context, canvasWidth, canvasHeight ->
        val minimum = points.minOrNull() ?: 0f
        val maximum = points.maxOrNull() ?: 1f
        val range = (maximum - minimum).takeIf { it > 0f } ?: 1f
        context.beginPath()
        points.forEachIndexed { index, value ->
            val x = index.toFloat() / (points.size - 1).toFloat() * canvasWidth
            val normalized = (value - minimum) / range
            val y = 3f + (1f - normalized) * (canvasHeight - 6f)
            if (index == 0) {
                context.moveTo(x, y)
            } else {
                context.lineTo(x, y)
            }
        }
        context.lineWidth(2.2f)
        context.lineCapRound()
        context.strokeStyle(if (positive) StockChatTheme.positive else StockChatTheme.negative)
        context.stroke()
    }
}

private fun ViewContainer<*, *>.ComparisonClickableCell(
    row: ConversationStockComparisonRow,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
    alignEnd: Boolean = false,
    content: ViewContainer<*, *>.() -> Unit,
) {
    View {
        attr {
            flex(1f)
            alignSelfStretch()
            justifyContentCenter()
            if (alignEnd) alignItemsFlexEnd()
        }
        if (row.providerSymbol.isNotBlank() || row.symbol.isNotBlank()) {
            event {
                click { onRowClick(row) }
            }
        }
        content()
    }
}

private fun String.orPlaceholder(): String = trim().ifBlank { "—" }

private fun String.asPercent(): String {
    val normalized = trim()
    return when {
        normalized.isBlank() -> "—"
        normalized.endsWith("%") -> normalized
        else -> "$normalized%"
    }
}

private fun String.withUnit(unit: String, prefix: String = ""): String {
    val normalized = trim()
    if (normalized.isBlank()) {
        return "—"
    }
    return "$prefix$normalized${unit.trim().takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()}"
}

private const val STOCK_COMPARISON_TABLE_CONTENT_WIDTH =
    210f + 96f + 112f + 112f + 118f + 92f + 98f + 96f + 136f + 128f + 172f
