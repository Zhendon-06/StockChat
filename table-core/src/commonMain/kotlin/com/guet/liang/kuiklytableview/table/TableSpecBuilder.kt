package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList

/** Builds columns and visual settings around a shared row configuration. */
public class TableSpecBuilder<RowT> : TableRowsBuilder<RowT>() {
    private var configuredRowHeight = 44f
    private var rowHeightExplicit = false
    public var rowHeight: Float
        get() = configuredRowHeight
        set(value) {
            configuredRowHeight = value
            rowHeightExplicit = true
        }
    public var alignment: TableAlignment = TableAlignment.Start
    public var rowBackgroundColor: Color = Color.WHITE
    public var alternateRowBackgroundColor: Color = Color(0xFFF8FAFC)
    public var textColor: Color = Color(0xFF1E293B)

    private var padding = TablePadding(top = 8f, left = 12f, bottom = 8f, right = 12f)
    private var border = TableBorder(width = 1f, color = Color(0xFFE2E8F0), style = BorderStyle.SOLID)
    private var header = TableHeaderBuilder<RowT>().build()
    private var styleOptions: TableStyleOptions? = null
    private var paddingExplicit = false
    private val columns = mutableListOf<TableColumn<RowT>>()

    /** Applies reusable visual options to the table. */
    public fun style(options: TableStyleOptions) {
        styleOptions = options
    }

    /** Applies reusable visual options using the builder DSL. */
    public fun style(block: TableStyleBuilder.() -> Unit) {
        styleOptions = TableStyleBuilder().apply(block).build()
    }

    /** Applies one of the built-in Word/Excel-like presets. */
    public fun style(preset: TableStylePreset) {
        styleOptions = TableStyleOptions.preset(preset)
    }

    public fun columns(block: TableColumnsBuilder<RowT>.() -> Unit) {
        columns += TableColumnsBuilder<RowT>().apply(block).build()
    }

    public fun padding(all: Float) {
        paddingExplicit = true
        padding = TablePadding(all, all, all, all)
    }

    public fun padding(horizontal: Float, vertical: Float) {
        paddingExplicit = true
        padding = TablePadding(vertical, horizontal, vertical, horizontal)
    }

    public fun padding(top: Float, left: Float, bottom: Float, right: Float) {
        paddingExplicit = true
        padding = TablePadding(top, left, bottom, right)
    }

    public fun border(block: TableBorderBuilder.() -> Unit) {
        border = TableBorderBuilder().apply(block).build()
    }

    public fun header(block: TableHeaderBuilder<RowT>.() -> Unit) {
        header = TableHeaderBuilder<RowT>().apply(block).build()
    }

    internal fun build(): TableSpec<RowT> {
        val options = styleOptions
        val style = resolvedStyle()
        require(style.rowHeight > 0f) { "Table rowHeight must be greater than zero" }
        require(columns.isNotEmpty()) { "Table requires at least one column" }
        val duplicateIds = columns.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate table column ids: ${duplicateIds.joinToString()}" }
        return TableSpec(
            rows = requireNotNull(rows) { "Table rows must be configured" },
            columns = columns.toList(),
            style = style,
            header = if (options == null) {
                header
            } else {
                TableHeader(
                    height = header.height,
                    backgroundColor = options.headerBackgroundColor,
                    textColor = options.headerTextColor,
                    renderer = header.renderer,
                )
            },
            editBuffer = editBuffer ?: TableEditBuffer(),
            rowKeyProvider = rowKeyProvider,
            emptyStateRenderer = emptyStateRenderer,
        )
    }
    private fun resolvedStyle(): TableStyle {
        val options = styleOptions
        val resolvedRowHeight = if (rowHeightExplicit || options == null) {
            configuredRowHeight
        } else {
            options.density.rowHeight
        }
        val resolvedPadding = resolvedPadding(options)
        val resolvedPaddingExplicit = options?.cellPadding != null || paddingExplicit
        val resolvedBorder = border.copy(
            width = options?.borderWidth ?: border.width,
            color = options?.borderColor ?: border.color,
        )
        return TableStyle(
                rowHeight = resolvedRowHeight,
                border = resolvedBorder,
                padding = resolvedPadding,
                paddingExplicit = resolvedPaddingExplicit,
                alignment = options?.alignment ?: alignment,
                rowBackgroundColor = options?.rowBackgroundColor ?: rowBackgroundColor,
                alternateRowBackgroundColor = options?.alternateRowBackgroundColor ?: alternateRowBackgroundColor,
                textColor = options?.textColor ?: textColor,
                density = options?.density ?: TableDensity.Comfortable,
                borders = options?.borders ?: TableBorderOptions(),
                stripedRows = options?.stripedRows ?: true,
                headerStyle = options?.headerStyle ?: TableHeaderStyle.Filled,
            )
    }
    private fun resolvedPadding(options: TableStyleOptions?): TablePadding {
        return when {
            options?.cellPadding != null -> options.cellPadding
            paddingExplicit || options == null -> padding
            else -> TablePadding(
                top = options.density.verticalPadding,
                left = padding.left,
                bottom = options.density.verticalPadding,
                right = padding.right,
            )
        }
    }
}
