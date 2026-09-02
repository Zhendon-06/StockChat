package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.table.KuiklyTable
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
import com.tencent.kuikly.core.views.Text

internal enum class StockTableStyleChoice(
    val title: String,
    val description: String,
    val preset: TableStylePreset,
) {
    DEFAULT("经典", "完整网格与斑马纹", TableStylePreset.Default),
    COMPACT("紧凑", "同屏展示更多行情", TableStylePreset.Compact),
    SPACIOUS("宽松", "更大的行距与留白", TableStylePreset.Spacious),
    MINIMAL("极简", "仅保留横向分隔线", TableStylePreset.Minimal),
    BLUE("蓝色", "蓝色强调表头", TableStylePreset.Blue),
    DARK("深色", "深色行情表格", TableStylePreset.Dark),
    ;

    fun styleOptions(): TableStyleOptions = TableStyleOptions.preset(preset)

    companion object {
        val all: List<StockTableStyleChoice> = values().toList()
    }
}

private data class StockTablePreviewRow(
    val name: String,
    val code: String,
    val price: String,
    val change: String,
    val turnover: String,
)

private val stockTablePreviewRows = listOf(
    StockTablePreviewRow("上证指数", "000001.SH", "3,857.93", "+0.41%", "5,826 亿"),
    StockTablePreviewRow("贵州茅台", "600519.SH", "1,478.20", "+1.26%", "42.8 亿"),
    StockTablePreviewRow("宁德时代", "300750.SZ", "284.56", "-0.73%", "68.3 亿"),
    StockTablePreviewRow("中国平安", "601318.SH", "56.88", "+0.18%", "31.6 亿"),
)

internal fun ViewContainer<*, *>.StockTableStylePreview(
    selectedStyle: () -> StockTableStyleChoice,
    viewportHeight: Float = 238f,
    uiScale: Float = 1f,
) {
    StockTableStyleChoice.all.forEach { choice ->
        vif({ selectedStyle() == choice }) {
            StockTableStylePreviewContent(
                style = choice.styleOptions(),
                viewportHeight = viewportHeight,
                uiScale = uiScale,
            )
        }
    }
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
