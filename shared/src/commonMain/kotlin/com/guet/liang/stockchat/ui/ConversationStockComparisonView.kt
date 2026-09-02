package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.table.KuiklyTable
import com.guet.liang.kuiklytableview.table.TableAlignment
import com.guet.liang.kuiklytableview.table.TableStyleOptions
import com.guet.liang.kuiklytableview.table.tableSpec
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.guet.liang.stockchat.model.TableStylePreset
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal const val STOCK_COMPARISON_TABLE_HEADER_HEIGHT = 48f
internal const val STOCK_COMPARISON_TABLE_ROW_HEIGHT = 82f
private const val STOCK_COMPARISON_TABLE_VERTICAL_PADDING = 7f

private data class ComparisonTableLayout(
    val rowHeight: Float,
    val verticalPadding: Float,
)

private fun comparisonTableLayout(preset: TableStylePreset): ComparisonTableLayout = when (preset) {
    TableStylePreset.COMPACT -> ComparisonTableLayout(
        rowHeight = 74f,
        verticalPadding = 4f,
    )
    TableStylePreset.SPACIOUS -> ComparisonTableLayout(
        rowHeight = 94f,
        verticalPadding = 10f,
    )
    TableStylePreset.DEFAULT,
    TableStylePreset.MINIMAL,
    TableStylePreset.BLUE,
    TableStylePreset.DARK -> ComparisonTableLayout(
        rowHeight = STOCK_COMPARISON_TABLE_ROW_HEIGHT,
        verticalPadding = STOCK_COMPARISON_TABLE_VERTICAL_PADDING,
    )
}

private data class ComparisonTableContentColors(
    val primary: Color,
    val secondary: Color,
    val placeholder: Color,
    val positive: Color,
    val negative: Color,
    val userBadgeBackground: Color,
    val userBadgeText: Color,
    val aiBadgeBackground: Color,
    val aiBadgeText: Color,
    val sourceBadgeBackground: Color,
)

private fun comparisonTableContentColors(
    tableStyle: TableStyleOptions,
    isDarkPreset: Boolean,
): ComparisonTableContentColors {
    return if (isDarkPreset) {
        ComparisonTableContentColors(
            primary = tableStyle.textColor,
            secondary = Color(0xFFCBD5E1),
            placeholder = Color(0xFF94A3B8),
            positive = Color(0xFFFF746D),
            negative = Color(0xFF42C79E),
            userBadgeBackground = Color(0xFF1E3A5F),
            userBadgeText = Color(0xFF93C5FD),
            aiBadgeBackground = Color(0xFF174C3C),
            aiBadgeText = Color(0xFF6EE7B7),
            sourceBadgeBackground = Color(0xFF334155),
        )
    } else {
        ComparisonTableContentColors(
            primary = tableStyle.textColor,
            secondary = Color(0xFF64748B),
            placeholder = Color(0xFF94A3B8),
            positive = StockChatTheme.positive,
            negative = StockChatTheme.negative,
            userBadgeBackground = Color(0xFFEAF2FF),
            userBadgeText = Color(0xFF356AA0),
            aiBadgeBackground = StockChatTheme.accentSoft,
            aiBadgeText = StockChatTheme.accent,
            sourceBadgeBackground = StockChatTheme.recessed,
        )
    }
}

internal fun ViewContainer<*, *>.ConversationStockComparisonTable(
    rows: List<ConversationStockComparisonRow>,
    viewportHeight: Float,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    val tableStyleSettings = StockChatSettingsStore.repository
        .loadSnapshot()
        .appearance
        .tableStyle
    val tableStyle = tableStyleSettings.toKuiklyTableStyleOptions()
    val contentColors = comparisonTableContentColors(
        tableStyle = tableStyle,
        isDarkPreset = tableStyleSettings.preset == TableStylePreset.DARK,
    )
    val tableLayout = comparisonTableLayout(tableStyleSettings.preset)
    val tableSpec = tableSpec<ConversationStockComparisonRow> {
        rows(rows)
        rowKey { row -> "${row.providerSymbol}:${row.symbol}:${row.displayName}" }
        rowHeight = tableLayout.rowHeight
        padding(horizontal = 10f, vertical = tableLayout.verticalPadding)
        style(tableStyle)
        header {
            height = STOCK_COMPARISON_TABLE_HEADER_HEIGHT
        }
        columns {
            column("instrument", "表格 / 会话来源", width = 210f) {
                value { row -> row.displayName }
                cell { cell -> ComparisonInstrumentCell(cell.row, contentColors, onRowClick) }
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
                        colors = contentColors,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("change", "涨跌", width = 112f) {
                alignment = TableAlignment.End
                value { row -> row.changePercent.orPlaceholder() }
                cell { cell -> ComparisonChangeCell(cell.row, contentColors, onRowClick) }
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
                        colors = contentColors,
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
                        colors = contentColors,
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
                        colors = contentColors,
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
                        colors = contentColors,
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
                        colors = contentColors,
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
                        colors = contentColors,
                        onRowClick = onRowClick,
                    )
                }
            }
            column("trend", "近期走势", width = 128f) {
                alignment = TableAlignment.Center
                value { row -> row.trendPoints.size.toString() }
                cell { cell -> ComparisonTrendCell(cell.row, contentColors, onRowClick) }
            }
            column("updated", "数据时间", width = 172f) {
                value { row -> row.updatedAt.orPlaceholder() }
                cell { cell ->
                    ComparisonValueCell(
                        row = cell.row,
                        primary = cell.row.updatedAt.orPlaceholder(),
                        secondary = "点击查看详情  ›",
                        colors = contentColors,
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
                    backgroundColor(tableStyle.rowBackgroundColor)
                    allCenter()
                }
                Text {
                    attr {
                        text("该会话暂未识别到可对比的股票或指数")
                        fontSize(14f)
                        color(contentColors.placeholder)
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
    colors: ComparisonTableContentColors,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    ComparisonClickableCell(row, onRowClick) {
        Text {
            attr {
                text(row.displayName)
                fontSize(14f)
                fontWeightBold()
                color(colors.primary)
                lines(1)
            }
        }
        Text {
            attr {
                text(listOf(row.marketLabel, row.symbol).filter(String::isNotBlank).joinToString(" · "))
                fontSize(11f)
                color(colors.secondary)
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
                ComparisonSourceBadge("用户提及", colors.userBadgeBackground, colors.userBadgeText)
            }
            if (row.generatedByAi) {
                ComparisonSourceBadge("AI 生成", colors.aiBadgeBackground, colors.aiBadgeText)
            }
            if (!row.mentionedByUser && !row.generatedByAi) {
                ComparisonSourceBadge("会话识别", colors.sourceBadgeBackground, colors.secondary)
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
    colors: ComparisonTableContentColors,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    val changeColor = when {
        row.change.startsWith("-") || row.changePercent.startsWith("-") -> colors.negative
        row.change.isBlank() && row.changePercent.isBlank() -> colors.placeholder
        else -> colors.positive
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
    colors: ComparisonTableContentColors,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    val alignEnd = alignment == TableAlignment.End
    ComparisonClickableCell(row, onRowClick, alignEnd) {
        Text {
            attr {
                text(primary)
                fontSize(13f)
                fontWeightMedium()
                color(colors.primary)
                lines(if (secondary.isBlank()) 2 else 1)
                if (alignEnd) textAlignRight()
            }
        }
        if (secondary.isNotBlank()) {
            Text {
                attr {
                    text(secondary)
                    fontSize(10f)
                    color(colors.secondary)
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
    colors: ComparisonTableContentColors,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
    ComparisonClickableCell(row, onRowClick) {
        if (row.trendPoints.size > 1) {
            ComparisonSparkline(
                points = row.trendPoints,
                positive = !row.change.startsWith("-") && !row.changePercent.startsWith("-"),
                colors = colors,
            )
        } else {
            Text {
                attr {
                    text("暂无走势")
                    fontSize(11f)
                    color(colors.placeholder)
                    textAlignCenter()
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.ComparisonSparkline(
    points: List<Float>,
    positive: Boolean,
    colors: ComparisonTableContentColors,
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
        context.strokeStyle(if (positive) colors.positive else colors.negative)
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
