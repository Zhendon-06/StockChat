package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList

@DslMarker
public annotation class KuiklyTableDsl

public enum class TableAlignment {
    Start,
    Center,
    End,
}

/** Common Word/Excel-like table border presets. */
public enum class TableBorderPreset {
    None,
    Row,
    Column,
    Outer,
    Header,
    All,
}

/** Controls the amount of vertical space used by table rows. */
public enum class TableDensity(
    public val rowHeight: Float,
    public val verticalPadding: Float,
) {
    Compact(rowHeight = 38f, verticalPadding = 6f),
    Comfortable(rowHeight = 46f, verticalPadding = 10f),
    Spacious(rowHeight = 56f, verticalPadding = 14f),
}

/** Controls the visual treatment of the header row. */
public enum class TableHeaderStyle {
    Filled,
    Plain,
    Accent,
    Dark,
}

/** A small set of ready-to-use table themes for product screens and demos. */
public enum class TableStylePreset {
    Default,
    Compact,
    Spacious,
    Minimal,
    Dark,
    Blue,
}

public data class TableBorderOptions(
    /** Draw a line between adjacent rows. */
    val row: Boolean = true,
    /** Draw a line between adjacent columns. */
    val column: Boolean = true,
    /** Draw the outside edge of the table. */
    val outer: Boolean = true,
    /** Draw the header cell edges. */
    val header: Boolean = true,
) {
    public val horizontal: Boolean get() = row
    public val vertical: Boolean get() = column

    public companion object {
        public fun forPreset(preset: TableBorderPreset): TableBorderOptions = when (preset) {
            TableBorderPreset.None -> TableBorderOptions(false, false, false, false)
            TableBorderPreset.Row -> TableBorderOptions(row = true, column = false, outer = false, header = false)
            TableBorderPreset.Column -> TableBorderOptions(row = false, column = true, outer = false, header = false)
            TableBorderPreset.Outer -> TableBorderOptions(row = false, column = false, outer = true, header = false)
            TableBorderPreset.Header -> TableBorderOptions(row = false, column = false, outer = false, header = true)
            TableBorderPreset.All -> TableBorderOptions()
        }
    }
}

@KuiklyTableDsl
public class TableBorderOptionsBuilder {
    public var row: Boolean = true
    public var column: Boolean = true
    public var outer: Boolean = true
    public var header: Boolean = true

    public fun preset(value: TableBorderPreset) {
        val options = TableBorderOptions.forPreset(value)
        row = options.row
        column = options.column
        outer = options.outer
        header = options.header
    }

    internal fun build(): TableBorderOptions = TableBorderOptions(row, column, outer, header)
}

public data class TableStyleOptions(
    public val density: TableDensity = TableDensity.Comfortable,
    public val borders: TableBorderOptions = TableBorderOptions(),
    public val stripedRows: Boolean = true,
    public val headerStyle: TableHeaderStyle = TableHeaderStyle.Filled,
    public val rowBackgroundColor: Color = Color.WHITE,
    public val alternateRowBackgroundColor: Color = Color(0xFFF8FAFC),
    public val textColor: Color = Color(0xFF1E293B),
    public val headerBackgroundColor: Color = Color(0xFF0F172A),
    public val headerTextColor: Color = Color.WHITE,
    /** Table grid line width in logical dp. `null` keeps the specification's border width. */
    public val borderWidth: Float? = null,
    /** Table grid line color. `null` keeps the specification's border color. */
    public val borderColor: Color? = null,
    /** Default cell padding. `null` keeps explicit padding or follows [density]. */
    public val cellPadding: TablePadding? = null,
    /** Default horizontal alignment. `null` keeps the specification's alignment. */
    public val alignment: TableAlignment? = null,
) {
    public companion object {
        public fun preset(preset: TableStylePreset): TableStyleOptions = when (preset) {
            TableStylePreset.Default -> TableStyleOptions()
            TableStylePreset.Compact -> TableStyleOptions(
                density = TableDensity.Compact,
                stripedRows = true,
            )
            TableStylePreset.Spacious -> TableStyleOptions(
                density = TableDensity.Spacious,
                stripedRows = false,
            )
            TableStylePreset.Minimal -> TableStyleOptions(
                borders = TableBorderOptions.forPreset(TableBorderPreset.Row),
                stripedRows = false,
                headerStyle = TableHeaderStyle.Plain,
                headerBackgroundColor = Color.WHITE,
                headerTextColor = Color(0xFF334155),
            )
            TableStylePreset.Dark -> TableStyleOptions(
                borders = TableBorderOptions(
                    row = true,
                    column = true,
                    outer = true,
                    header = true,
                ),
                stripedRows = true,
                headerStyle = TableHeaderStyle.Dark,
                rowBackgroundColor = Color(0xFF1E293B),
                alternateRowBackgroundColor = Color(0xFF263449),
                textColor = Color(0xFFE2E8F0),
                headerBackgroundColor = Color(0xFF334155),
                headerTextColor = Color.WHITE,
            )
            TableStylePreset.Blue -> TableStyleOptions(
                borders = TableBorderOptions.forPreset(TableBorderPreset.All),
                stripedRows = true,
                headerStyle = TableHeaderStyle.Accent,
                headerBackgroundColor = Color(0xFF2563EB),
                headerTextColor = Color.WHITE,
                alternateRowBackgroundColor = Color(0xFFEFF6FF),
            )
        }
    }
}

@KuiklyTableDsl
public class TableStyleBuilder {
    public var density: TableDensity = TableDensity.Comfortable
    public var stripedRows: Boolean = true
    public var headerStyle: TableHeaderStyle = TableHeaderStyle.Filled
    public var rowBackgroundColor: Color = Color.WHITE
    public var alternateRowBackgroundColor: Color = Color(0xFFF8FAFC)
    public var textColor: Color = Color(0xFF1E293B)
    public var headerBackgroundColor: Color = Color(0xFF0F172A)
    public var headerTextColor: Color = Color.WHITE
    public var borderWidth: Float? = null
    public var borderColor: Color? = null
    public var cellPadding: TablePadding? = null
    public var alignment: TableAlignment? = null

    private var borders = TableBorderOptionsBuilder()

    public fun borders(block: TableBorderOptionsBuilder.() -> Unit) {
        borders.apply(block)
    }

    public fun preset(value: TableStylePreset) {
        val options = TableStyleOptions.preset(value)
        density = options.density
        stripedRows = options.stripedRows
        headerStyle = options.headerStyle
        rowBackgroundColor = options.rowBackgroundColor
        alternateRowBackgroundColor = options.alternateRowBackgroundColor
        textColor = options.textColor
        headerBackgroundColor = options.headerBackgroundColor
        headerTextColor = options.headerTextColor
        borderWidth = options.borderWidth
        borderColor = options.borderColor
        cellPadding = options.cellPadding
        alignment = options.alignment
        borders = TableBorderOptionsBuilder().apply {
            row = options.borders.row
            column = options.borders.column
            outer = options.borders.outer
            header = options.borders.header
        }
    }

    internal fun build(): TableStyleOptions = TableStyleOptions(
        density = density,
        borders = borders.build(),
        stripedRows = stripedRows,
        headerStyle = headerStyle,
        rowBackgroundColor = rowBackgroundColor,
        alternateRowBackgroundColor = alternateRowBackgroundColor,
        textColor = textColor,
        headerBackgroundColor = headerBackgroundColor,
        headerTextColor = headerTextColor,
        borderWidth = borderWidth,
        borderColor = borderColor,
        cellPadding = cellPadding,
        alignment = alignment,
    )
}

public data class TablePadding(
    val top: Float,
    val left: Float,
    val bottom: Float,
    val right: Float,
) {
    init {
        require(top >= 0f && left >= 0f && bottom >= 0f && right >= 0f) {
            "Table padding must be non-negative"
        }
    }
}

public data class TableBorder(
    val width: Float,
    val color: Color,
    val style: BorderStyle,
) {
    init {
        require(width.isFinite() && width >= 0f) {
            "Table border width must be finite and non-negative"
        }
    }
}

public data class TableCellContext<RowT>(
    val row: RowT,
    val rowIndex: Int,
    val column: TableColumn<RowT>,
    val columnIndex: Int,
    val value: String,
)

public data class TableHeaderContext<RowT>(
    val column: TableColumn<RowT>,
    val columnIndex: Int,
)

public data class TableEditContext<RowT>(
    val row: RowT,
    val rowIndex: Int,
    val column: TableColumn<RowT>,
    val columnIndex: Int,
    val oldValue: String,
    val newValue: String,
    /** Stable key used to keep staged edits attached to a row after list changes. */
    val rowKey: String = rowIndex.toString(),
)

public typealias TableCellRenderer<RowT> =
    ViewContainer<*, *>.(context: TableCellContext<RowT>) -> Unit

public typealias TableHeaderRenderer<RowT> =
    ViewContainer<*, *>.(context: TableHeaderContext<RowT>) -> Unit

public typealias TableEditHandler<RowT> = (context: TableEditContext<RowT>) -> Unit

public typealias TableEmptyStateRenderer = ViewContainer<*, *>.() -> Unit

public class TableColumn<RowT> internal constructor(
    public val id: String,
    public val title: String,
    public val width: Float,
    public val alignment: TableAlignment?,
    public val padding: TablePadding?,
    public val value: (RowT) -> String,
    public val cellRenderer: TableCellRenderer<RowT>?,
    public val headerRenderer: TableHeaderRenderer<RowT>?,
    public val editHandler: TableEditHandler<RowT>?,
    public val editable: Boolean,
)

public class TableHeader<RowT> internal constructor(
    public val height: Float,
    public val backgroundColor: Color,
    public val textColor: Color,
    public val renderer: TableHeaderRenderer<RowT>?,
)

public class TableStyle internal constructor(
    public val rowHeight: Float,
    public val border: TableBorder,
    public val padding: TablePadding,
    internal val paddingExplicit: Boolean,
    public val alignment: TableAlignment,
    public val rowBackgroundColor: Color,
    public val alternateRowBackgroundColor: Color,
    public val textColor: Color,
    public val density: TableDensity,
    public val borders: TableBorderOptions,
    public val stripedRows: Boolean,
    public val headerStyle: TableHeaderStyle,
)

public class TableSpec<RowT> internal constructor(
    public val rows: ObservableList<RowT>,
    public val columns: List<TableColumn<RowT>>,
    public val style: TableStyle,
    public val header: TableHeader<RowT>,
    public val editBuffer: TableEditBuffer<RowT>,
    public val emptyStateRenderer: TableEmptyStateRenderer?,
    private val rowKeyProvider: ((RowT) -> String)?,
) {
    public val contentWidth: Float = columns.sumOf { it.width.toDouble() }.toFloat()

    /** Returns the stable key used for a row's staged edits. */
    public fun rowKey(row: RowT, rowIndex: Int): String =
        rowKeyProvider?.invoke(row) ?: rowIndex.toString()

    /**
     * Returns a view of this specification with a different visual style.
     * Data, columns and the edit buffer remain shared, which makes this useful
     * for Compose-style style pickers that switch appearance without rebuilding data.
     */
    public fun withStyle(options: TableStyleOptions): TableSpec<RowT> {
        val styledBorder = style.border.copy(
            width = options.borderWidth ?: style.border.width,
            color = options.borderColor ?: style.border.color,
        )
        val styledPadding = options.cellPadding ?: if (style.paddingExplicit) {
            style.padding
        } else {
            TablePadding(
                top = options.density.verticalPadding,
                left = style.padding.left,
                bottom = options.density.verticalPadding,
                right = style.padding.right,
            )
        }
        val styled = TableStyle(
            rowHeight = options.density.rowHeight,
            border = styledBorder,
            padding = styledPadding,
            paddingExplicit = options.cellPadding != null || style.paddingExplicit,
            alignment = options.alignment ?: style.alignment,
            rowBackgroundColor = options.rowBackgroundColor,
            alternateRowBackgroundColor = options.alternateRowBackgroundColor,
            textColor = options.textColor,
            density = options.density,
            borders = options.borders,
            stripedRows = options.stripedRows,
            headerStyle = options.headerStyle,
        )
        val styledHeader = TableHeader(
            height = header.height,
            backgroundColor = options.headerBackgroundColor,
            textColor = options.headerTextColor,
            renderer = header.renderer,
        )
        return TableSpec(
            rows = rows,
            columns = columns,
            style = styled,
            header = styledHeader,
            editBuffer = editBuffer,
            rowKeyProvider = rowKeyProvider,
            emptyStateRenderer = emptyStateRenderer,
        )
    }

    public companion object {
        public const val LAZY_ROW_WINDOW: Int = 30
    }
}

@KuiklyTableDsl
public class TableBorderBuilder {
    public var width: Float = 1f
    public var color: Color = Color(0xFFE2E8F0)
    public var style: BorderStyle = BorderStyle.SOLID

    internal fun build(): TableBorder = TableBorder(width, color, style)
}

@KuiklyTableDsl
public class TableColumnBuilder<RowT> internal constructor(
    private val id: String,
    private val title: String,
    initialWidth: Float,
) {
    public var width: Float = initialWidth
    public var alignment: TableAlignment? = null
    public var padding: TablePadding? = null

    /**
     * 开启行内编辑。编辑提交后自动进入表格的 [TableEditBuffer] 暂存并回显，
     * 不写回数据源；如需在提交时收到通知（校验、写回等），再配 [onEdit]。
     */
    public var editable: Boolean = false

    private var valueProvider: (RowT) -> String = { "" }
    private var cellRenderer: TableCellRenderer<RowT>? = null
    private var headerRenderer: TableHeaderRenderer<RowT>? = null
    private var editHandler: TableEditHandler<RowT>? = null

    public fun value(provider: (RowT) -> String) {
        valueProvider = provider
    }

    public fun cell(renderer: TableCellRenderer<RowT>) {
        cellRenderer = renderer
    }

    public fun header(renderer: TableHeaderRenderer<RowT>) {
        headerRenderer = renderer
    }

    /**
     * 开启该列的行内编辑（隐含 [editable] = true）：点击单元格进入编辑态，
     * 回车或失焦提交。提交后自动暂存并回显，同时以 [TableEditContext] 回调新旧值。
     */
    public fun onEdit(handler: TableEditHandler<RowT>) {
        editHandler = handler
    }

    internal fun build(): TableColumn<RowT> {
        require(id.isNotBlank()) { "Table column id must not be blank" }
        require(width > 0f) { "Table column '$id' width must be greater than zero" }
        return TableColumn(
            id = id,
            title = title,
            width = width,
            alignment = alignment,
            padding = padding,
            value = valueProvider,
            cellRenderer = cellRenderer,
            headerRenderer = headerRenderer,
            editHandler = editHandler,
            editable = editable || editHandler != null,
        )
    }
}

@KuiklyTableDsl
public class TableColumnsBuilder<RowT> internal constructor() {
    private val columns = mutableListOf<TableColumn<RowT>>()

    public fun column(
        id: String,
        title: String,
        width: Float = 120f,
        block: TableColumnBuilder<RowT>.() -> Unit = {},
    ) {
        columns += TableColumnBuilder<RowT>(id, title, width).apply(block).build()
    }

    internal fun build(): List<TableColumn<RowT>> = columns.toList()
}

@KuiklyTableDsl
public class TableHeaderBuilder<RowT> internal constructor() {
    public var height: Float = 48f
    public var backgroundColor: Color = Color(0xFF0F172A)
    public var textColor: Color = Color.WHITE

    private var renderer: TableHeaderRenderer<RowT>? = null

    public fun cell(renderer: TableHeaderRenderer<RowT>) {
        this.renderer = renderer
    }

    internal fun build(): TableHeader<RowT> {
        require(height > 0f) { "Table header height must be greater than zero" }
        return TableHeader(height, backgroundColor, textColor, renderer)
    }
}

@KuiklyTableDsl
public class TableSpecBuilder<RowT> {
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

    private var rows: ObservableList<RowT>? = null
    private var editBuffer: TableEditBuffer<RowT>? = null
    private var rowKeyProvider: ((RowT) -> String)? = null
    private var padding = TablePadding(top = 8f, left = 12f, bottom = 8f, right = 12f)
    private var border = TableBorder(width = 1f, color = Color(0xFFE2E8F0), style = BorderStyle.SOLID)
    private var header = TableHeaderBuilder<RowT>().build()
    private var styleOptions: TableStyleOptions? = null
    private var emptyStateRenderer: TableEmptyStateRenderer? = null
    private var paddingExplicit = false
    private val columns = mutableListOf<TableColumn<RowT>>()

    public fun rows(items: ObservableList<RowT>) {
        rows = items
    }

    /**
     * Seeds a table from an immutable list. Use [ObservableList] when the caller
     * needs to update rows after the table has been created.
     */
    public fun rows(items: List<RowT>) {
        rows = ObservableList(items.toMutableList())
    }

    /**
     * Supplies a stable, unique key for each row. Without this provider the row
     * index is used, which is suitable for append-only data. Production tables
     * should provide a business identifier whenever rows can be inserted, removed,
     * sorted, or replaced while edits are staged.
     */
    public fun rowKey(provider: (RowT) -> Any?) {
        rowKeyProvider = { row ->
            provider(row)?.toString()?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Table row key must not be null or blank")
        }
    }

    /**
     * 指定编辑暂存区；不指定时自动创建，可通过 [TableSpec.editBuffer] 访问。
     * 传入外部实例便于多个表格共享，或由页面提前持有以对接保存链路。
     */
    public fun editBuffer(buffer: TableEditBuffer<RowT>) {
        editBuffer = buffer
    }

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

    /** Renders content in the viewport when the data source is empty. */
    public fun emptyState(renderer: TableEmptyStateRenderer) {
        emptyStateRenderer = renderer
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
        val resolvedRowHeight = if (rowHeightExplicit || options == null) {
            configuredRowHeight
        } else {
            options.density.rowHeight
        }
        val resolvedPadding = when {
            options?.cellPadding != null -> options.cellPadding
            paddingExplicit || options == null -> padding
            else -> TablePadding(
                top = options.density.verticalPadding,
                left = padding.left,
                bottom = options.density.verticalPadding,
                right = padding.right,
            )
        }
        val resolvedPaddingExplicit = options?.cellPadding != null || paddingExplicit
        val resolvedBorder = border.copy(
            width = options?.borderWidth ?: border.width,
            color = options?.borderColor ?: border.color,
        )
        require(resolvedRowHeight > 0f) { "Table rowHeight must be greater than zero" }
        require(columns.isNotEmpty()) { "Table requires at least one column" }
        val duplicateIds = columns.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate table column ids: ${duplicateIds.joinToString()}" }
        return TableSpec(
            rows = requireNotNull(rows) { "Table rows must be configured" },
            columns = columns.toList(),
            style = TableStyle(
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
            ),
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
}

public fun <RowT> tableSpec(block: TableSpecBuilder<RowT>.() -> Unit): TableSpec<RowT> =
    TableSpecBuilder<RowT>().apply(block).build()
