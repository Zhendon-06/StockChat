package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.table.TableAlignment
import com.guet.liang.kuiklytableview.table.TableColumnsBuilder
import com.guet.liang.stockchat.model.ConversationStockComparisonRow

internal fun TableColumnsBuilder<ConversationStockComparisonRow>.comparisonIdentityColumns(
    contentColors: ComparisonTableContentColors,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
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
}

internal fun TableColumnsBuilder<ConversationStockComparisonRow>.comparisonRangeColumns(
    contentColors: ComparisonTableContentColors,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
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
}

internal fun TableColumnsBuilder<ConversationStockComparisonRow>.comparisonMetricColumns(
    contentColors: ComparisonTableContentColors,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
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
}

internal fun TableColumnsBuilder<ConversationStockComparisonRow>.comparisonTradingColumns(
    contentColors: ComparisonTableContentColors,
    onRowClick: (ConversationStockComparisonRow) -> Unit,
) {
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
