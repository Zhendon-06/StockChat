package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.ui.KuiklyTable
import com.guet.liang.kuiklytableview.table.TableAlignment
import com.guet.liang.kuiklytableview.table.TableCellContext
import com.guet.liang.kuiklytableview.table.TableHeaderContext
import com.guet.liang.kuiklytableview.table.TableHeaderStyle
import com.guet.liang.kuiklytableview.table.TableStyleOptions
import com.guet.liang.kuiklytableview.table.TableStylePreset
import com.guet.liang.kuiklytableview.table.tableSpec
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.views.Text


internal fun ViewContainer<*, *>.StockTableStylePreview(
    selectedStyle: () -> StockTableStyleChoice,
    viewportHeight: Float = 238f,
    uiScale: Float = 1f,
    customColor: (() -> Color)? = null,
    refreshKey: (() -> Int)? = null,
) {
    StockTableStyleChoice.all.forEach { choice ->
        vif({ selectedStyle() == choice }) {
            vif({ (refreshKey?.invoke() ?: 0) % 2 == 0 }) {
                renderStockTableStylePreview(
                    choice = choice,
                    viewportHeight = viewportHeight,
                    uiScale = uiScale,
                    customColor = customColor,
                )
            }
            velse {
                renderStockTableStylePreview(
                    choice = choice,
                    viewportHeight = viewportHeight,
                    uiScale = uiScale,
                    customColor = customColor,
                )
            }
        }
    }
}

private fun ViewContainer<*, *>.renderStockTableStylePreview(
    choice: StockTableStyleChoice,
    viewportHeight: Float,
    uiScale: Float,
    customColor: (() -> Color)?,
) {
    val presetStyle = choice.styleOptions()
    val selectedColor = customColor?.invoke() ?: presetStyle.headerBackgroundColor
    StockTableStylePreviewContent(
        style = presetStyle.copy(
            headerBackgroundColor = selectedColor,
            headerTextColor = if (customColor != null && selectedColor.isLightColor()) {
                Color(StockChatTheme.COLOR_FF1D2027)
            } else if (customColor != null) {
                Color.WHITE
            } else {
                presetStyle.headerTextColor
            },
        ),
        viewportHeight = viewportHeight,
        uiScale = uiScale,
    )
}

private fun Color.isLightColor(): Boolean {
    val red = (hexColor shr 16 and 0xFF).toInt()
    val green = (hexColor shr 8 and 0xFF).toInt()
    val blue = (hexColor and 0xFF).toInt()
    return red * 299 + green * 587 + blue * 114 >= 150_000
}

private fun ViewContainer<*, *>.StockTableStylePreviewContent(
    style: TableStyleOptions,
    viewportHeight: Float,
    uiScale: Float,
) {
    val spec = tableSpec<StockTablePreviewRow> {
        rows(stockTablePreviewRows)
        rowKey { row -> row.code }
        style(style)
        rowHeight = style.density.rowHeight * uiScale
        padding(
            horizontal = 12f * uiScale,
            vertical = style.density.verticalPadding * uiScale,
        )
        header {
            height = 42f * uiScale
            cell { context ->
                renderStockTablePreviewHeader(context, style, uiScale)
            }
        }
        columns {
            column("name", "标的", width = 112f * uiScale) {
                value { row -> row.name }
                cell { context ->
                    renderStockTablePreviewCell(context, style, uiScale)
                }
            }
            column("code", "代码", width = 112f * uiScale) {
                value { row -> row.code }
                cell { context ->
                    renderStockTablePreviewCell(context, style, uiScale)
                }
            }
            column("price", "最新价", width = 96f * uiScale) {
                alignment = TableAlignment.End
                value { row -> row.price }
                cell { context ->
                    renderStockTablePreviewCell(context, style, uiScale)
                }
            }
            column("change", "涨跌幅", width = 94f * uiScale) {
                alignment = TableAlignment.End
                value { row -> row.change }
                cell { context ->
                    renderStockTablePreviewCell(
                        context = context,
                        style = style,
                        uiScale = uiScale,
                        color = if (context.row.change.startsWith("-")) {
                            StockChatTheme.negative
                        } else {
                            StockChatTheme.positive
                        },
                        emphasized = true,
                    )
                }
            }
            column("turnover", "成交额", width = 106f * uiScale) {
                alignment = TableAlignment.End
                value { row -> row.turnover }
                cell { context ->
                    renderStockTablePreviewCell(context, style, uiScale)
                }
            }
        }
    }

    KuiklyTable(
        spec = spec,
        viewportHeight = viewportHeight,
    )
}

private fun ViewContainer<*, *>.renderStockTablePreviewCell(
    context: TableCellContext<StockTablePreviewRow>,
    style: TableStyleOptions,
    uiScale: Float,
    color: Color = style.textColor,
    emphasized: Boolean = false,
) {
    Text {
        attr {
            flex(1f)
            text(context.value)
            fontSize(13f * uiScale)
            if (emphasized) {
                fontWeightSemiBold()
            } else {
                fontWeightNormal()
            }
            color(color)
            lines(1)
            when (context.column.alignment) {
                TableAlignment.Center -> textAlignCenter()
                TableAlignment.End -> textAlignRight()
                else -> textAlignLeft()
            }
        }
    }
}

private fun ViewContainer<*, *>.renderStockTablePreviewHeader(
    context: TableHeaderContext<StockTablePreviewRow>,
    style: TableStyleOptions,
    uiScale: Float,
) {
    Text {
        attr {
            flex(1f)
            text(context.column.title)
            fontSize(stockTablePreviewHeaderFontSize(style.headerStyle) * uiScale)
            when (style.headerStyle) {
                TableHeaderStyle.Filled -> fontWeightSemiBold()
                TableHeaderStyle.Plain -> fontWeightNormal()
                TableHeaderStyle.Accent,
                TableHeaderStyle.Dark,
                -> fontWeightBold()
            }
            color(style.headerTextColor)
            lines(1)
            when (context.column.alignment) {
                TableAlignment.Center -> textAlignCenter()
                TableAlignment.End -> textAlignRight()
                else -> textAlignLeft()
            }
        }
    }
}

private fun stockTablePreviewHeaderFontSize(style: TableHeaderStyle): Float = when (style) {
    TableHeaderStyle.Filled -> 14f
    TableHeaderStyle.Plain -> 13f
    TableHeaderStyle.Accent -> 15f
    TableHeaderStyle.Dark -> 14f
}
