package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ContainerAttr
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.ceil

private const val TABLE_PRELOAD_ROW_COUNT = 4
private const val MIN_LAZY_ROW_WINDOW = 12

public class KuiklyTableView<RowT> internal constructor(
    private val spec: TableSpec<RowT>,
    private val metrics: TableMetrics,
    private val viewportHeight: Float,
) : ComposeView<ComposeAttr, ComposeEvent>() {

    private var reactiveRows by observableList<RowT>()
    private val rowCountProvider: () -> Int = { reactiveRows.size }
    private val columnSeparatorOffsets = calculateColumnSeparatorOffsets(spec.columns)
    private val lazyRowWindow = calculateLazyRowWindow(
        bodyHeight = viewportHeight - spec.header.height,
        rowHeight = spec.style.rowHeight,
    )

    init {
        reactiveRows = spec.rows
    }

    override fun createAttr(): ComposeAttr = ComposeAttr()

    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val context = this
        return {
            Scroller {
                attr {
                    height(context.viewportHeight)
                    alignSelfStretch()
                    flexDirectionRow()
                    alignItemsStretch()
                    showScrollerIndicator(true)
                }
                View {
                    attr {
                        width(context.spec.contentWidth)
                        height(context.viewportHeight)
                        alignSelfStretch()
                        flexDirectionColumn()
                        positionRelative()
                    }
                    context.renderHeader(this)
                    List {
                        attr {
                            height(context.viewportHeight - context.spec.header.height)
                            width(context.spec.contentWidth)
                            firstContentLoadMaxIndex(8)
                            preloadViewDistance(
                                context.spec.style.rowHeight * TABLE_PRELOAD_ROW_COUNT,
                            )
                            showScrollerIndicator(true)
                        }
                        vif({ context.reactiveRows.isEmpty() }) {
                            context.spec.emptyStateRenderer?.invoke(this)
                        }
                        vforLazy(
                            itemList = { context.reactiveRows },
                            maxLoadItem = context.lazyRowWindow,
                        ) { row, rowIndex, _ ->
                            addChild(
                                TableRowView(
                                    spec = context.spec,
                                    row = row,
                                    rowIndex = rowIndex,
                                    rowCountProvider = context.rowCountProvider,
                                    metrics = context.metrics,
                                ),
                            ) {}
                        }
                    }
                    context.renderGridOverlay(this)
                }
            }
        }
    }

    private fun renderHeader(container: ViewContainer<*, *>) {
        val context = this
        with(container) {
            View {
                attr {
                    width(context.spec.contentWidth)
                    height(context.spec.header.height)
                    flexDirectionRow()
                    backgroundColor(context.spec.header.backgroundColor)
                }
                context.spec.columns.forEachIndexed { columnIndex, column ->
                    context.renderHeaderCell(this, column, columnIndex)
                }
            }
        }
    }

    private fun renderHeaderCell(
        container: ViewContainer<*, *>,
        column: TableColumn<RowT>,
        columnIndex: Int,
    ) {
        val context = this
        val padding = column.padding ?: spec.style.padding
        val alignment = column.alignment ?: spec.style.alignment
        with(container) {
            View {
                attr {
                    width(column.width)
                    height(context.spec.header.height)
                    flexDirectionRow()
                    alignItemsCenter()
                    applyHorizontalAlignment(alignment)
                    padding(padding.top, padding.left, padding.bottom, padding.right)
                }
                val renderer = column.headerRenderer ?: context.spec.header.renderer
                if (renderer != null) {
                    renderer(TableHeaderContext(column, columnIndex))
                } else {
                    Text {
                        attr {
                            flex(1f)
                            text(column.title)
                            applyHeaderTypography(context.spec.style.headerStyle)
                            color(context.spec.header.textColor)
                            lines(1)
                            applyTextAlignment(alignment)
                        }
                    }
                }
            }
        }
    }

    /** 列线提升到横向滚动内容层，每列只保留一条窄 View，避免逐单元格重复创建。 */
    private fun renderGridOverlay(container: ViewContainer<*, *>) {
        val options = spec.style.borders
        val border = spec.style.border
        val hasStaticLines = options.outer ||
            options.header ||
            (options.column && columnSeparatorOffsets.isNotEmpty())
        if (border.width <= 0f || !hasStaticLines) return

        val context = this
        with(container) {
            if (options.outer || options.header) {
                renderHorizontalGridBorder(
                    border = border,
                    top = 0f,
                    lineWidth = context.spec.contentWidth,
                )
                val sideHeight: () -> Float = if (options.outer) {
                    { context.visibleGridBottom() }
                } else {
                    { context.spec.header.height }
                }
                renderVerticalGridBorder(border, left = 0f, lineHeight = sideHeight)
                renderVerticalGridBorder(
                    border = border,
                    left = (context.spec.contentWidth - border.width).coerceAtLeast(0f),
                    lineHeight = sideHeight,
                )
            }
            when {
                options.header -> renderHorizontalGridBorder(
                    border = border,
                    top = (context.spec.header.height - border.width).coerceAtLeast(0f),
                    lineWidth = context.spec.contentWidth,
                )
                options.outer -> vif({ context.reactiveRows.isEmpty() }) {
                    renderHorizontalGridBorder(
                        border = border,
                        top = (context.spec.header.height - border.width).coerceAtLeast(0f),
                        lineWidth = context.spec.contentWidth,
                    )
                }
            }
            if (options.column || options.header) {
                val separatorHeight: () -> Float = if (options.column) {
                    { context.visibleGridBottom() }
                } else {
                    { context.spec.header.height }
                }
                context.columnSeparatorOffsets.forEach { offset ->
                    renderVerticalGridBorder(
                        border = border,
                        left = (offset - border.width).coerceAtLeast(0f),
                        lineHeight = separatorHeight,
                    )
                }
            }
        }
    }

    private fun visibleGridBottom(): Float {
        val bodyHeight = viewportHeight - spec.header.height
        val renderedRowsHeight = (reactiveRows.size * spec.style.rowHeight).coerceAtMost(bodyHeight)
        return spec.header.height + renderedRowsHeight
    }

    override fun viewDestroyed() {
        super.viewDestroyed()
        reactiveRows = ObservableList()
    }
}

private class TableRowView<RowT>(
    private val spec: TableSpec<RowT>,
    private val row: RowT,
    private val rowIndex: Int,
    private val rowCountProvider: () -> Int,
    private val metrics: TableMetrics,
) : ComposeView<ComposeAttr, ComposeEvent>() {

    private var editingColumnIndex: Int by observable(NOT_EDITING)
    private var cachedRowKey: String? = null

    override fun createAttr(): ComposeAttr = ComposeAttr()

    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun created() {
        super.created()
        metrics.onRowCreated(rowIndex, spec.columns.size)
    }

    override fun body(): ViewBuilder {
        val context = this
        return {
            attr {
                width(context.spec.contentWidth)
                height(context.spec.style.rowHeight)
                flexDirectionRow()
                backgroundColor(
                    if (!context.spec.style.stripedRows || context.rowIndex % 2 == 0) {
                        context.spec.style.rowBackgroundColor
                    } else {
                        context.spec.style.alternateRowBackgroundColor
                    },
                )
                positionRelative()
            }
            context.spec.columns.forEachIndexed { columnIndex, column ->
                context.renderCell(this, column, columnIndex)
            }
            context.renderRowDivider(this)
        }
    }

    override fun viewDestroyed() {
        metrics.onRowDisposed(spec.columns.size)
        super.viewDestroyed()
    }

    private fun renderCell(
        container: ViewContainer<*, *>,
        column: TableColumn<RowT>,
        columnIndex: Int,
    ) {
        val context = this
        val padding = column.padding ?: spec.style.padding
        val alignment = column.alignment ?: spec.style.alignment
        with(container) {
            View {
                attr {
                    width(column.width)
                    height(context.spec.style.rowHeight)
                    flexDirectionRow()
                    alignItemsCenter()
                    applyHorizontalAlignment(alignment)
                    padding(padding.top, padding.left, padding.bottom, padding.right)
                }
                if (column.editable) {
                    event {
                        click {
                            context.editingColumnIndex = columnIndex
                        }
                    }
                    vif({ context.editingColumnIndex == columnIndex }) {
                        context.renderCellEditor(this, column, columnIndex)
                    }
                    velse {
                        context.renderCellContent(this, column, columnIndex, alignment)
                    }
                } else {
                    context.renderCellContent(this, column, columnIndex, alignment)
                }
            }
        }
    }

    private fun renderCellContent(
        container: ViewContainer<*, *>,
        column: TableColumn<RowT>,
        columnIndex: Int,
        alignment: TableAlignment,
    ) {
        val context = this
        with(container) {
            val renderer = column.cellRenderer
            if (renderer != null) {
                renderer(
                    TableCellContext(
                        row = context.row,
                        rowIndex = context.rowIndex,
                        column = column,
                        columnIndex = columnIndex,
                        value = context.displayValue(column),
                    ),
                )
            } else {
                Text {
                    attr {
                        flex(1f)
                        text(context.displayValue(column))
                        fontSize(13f)
                        color(context.spec.style.textColor)
                        lines(1)
                        applyTextAlignment(alignment)
                    }
                }
            }
        }
    }

    private fun renderCellEditor(
        container: ViewContainer<*, *>,
        column: TableColumn<RowT>,
        columnIndex: Int,
    ) {
        val context = this
        val oldValue = displayValue(column)
        with(container) {
            Input {
                attr {
                    flex(1f)
                    alignSelfStretch()
                    text(oldValue)
                    autofocus(true)
                    fontSize(13f)
                    color(context.spec.style.textColor)
                    returnKeyTypeDone()
                }
                event {
                    inputReturn { params ->
                        context.commitEdit(column, columnIndex, oldValue, params.text)
                    }
                    inputBlur { params ->
                        context.commitEdit(column, columnIndex, oldValue, params.text)
                    }
                }
            }
        }
    }

    private fun commitEdit(
        column: TableColumn<RowT>,
        columnIndex: Int,
        oldValue: String,
        newValue: String,
    ) {
        // 回车提交后编辑器被移除会再触发一次 blur，这里避免重复提交
        if (editingColumnIndex != columnIndex) {
            return
        }
        // 先暂存再退出编辑态：退出触发 velse 重建单元格，重建时即可读到暂存值回显
        if (newValue != oldValue) {
            val editContext = TableEditContext(
                row = row,
                rowIndex = rowIndex,
                column = column,
                columnIndex = columnIndex,
                oldValue = oldValue,
                newValue = newValue,
                rowKey = stableRowKey(),
            )
            spec.editBuffer.stage(editContext)
            column.editHandler?.invoke(editContext)
        }
        editingColumnIndex = NOT_EDITING
    }

    /** 单元格展示值：暂存值优先于数据源原值。 */
    private fun displayValue(column: TableColumn<RowT>): String {
        if (spec.editBuffer.isEmpty()) {
            return column.value(row)
        }
        return spec.editBuffer.stagedValue(stableRowKey(), column.id) ?: column.value(row)
    }

    private fun stableRowKey(): String {
        val rowKey = cachedRowKey
        if (rowKey != null) return rowKey
        return spec.rowKey(row, rowIndex).also { cachedRowKey = it }
    }

    /** 横向网格线按行合并，默认全边框模式每行只增加一个轻量 View。 */
    private fun renderRowDivider(container: ViewContainer<*, *>) {
        val options = spec.style.borders
        val border = spec.style.border
        if (border.width <= 0f || (!options.row && !options.outer)) return

        val context = this
        with(container) {
            when {
                options.row && options.outer -> renderHorizontalBorder(border)
                options.row -> vif({ context.rowIndex < context.rowCountProvider() - 1 }) {
                    renderHorizontalBorder(border)
                }
                else -> vif({ context.rowIndex == context.rowCountProvider() - 1 }) {
                    renderHorizontalBorder(border)
                }
            }
        }
    }

    private companion object {
        const val NOT_EDITING = -1
    }
}

private fun ViewContainer<*, *>.renderHorizontalBorder(border: TableBorder) {
    View {
        attr {
            absolutePosition(left = 0f, bottom = 0f, right = 0f)
            height(border.width)
            backgroundColor(border.color)
            touchEnable(false)
        }
    }
}

private fun ViewContainer<*, *>.renderHorizontalGridBorder(
    border: TableBorder,
    top: Float,
    lineWidth: Float,
) {
    View {
        attr {
            absolutePosition(top = top, left = 0f)
            width(lineWidth)
            height(border.width)
            backgroundColor(border.color)
            touchEnable(false)
        }
    }
}

private fun ViewContainer<*, *>.renderVerticalGridBorder(
    border: TableBorder,
    left: Float,
    lineHeight: () -> Float,
) {
    View {
        attr {
            absolutePosition(top = 0f, left = left)
            width(border.width)
            height(lineHeight())
            backgroundColor(border.color)
            touchEnable(false)
        }
    }
}

internal fun <RowT> calculateColumnSeparatorOffsets(
    columns: kotlin.collections.List<TableColumn<RowT>>,
): FloatArray {
    if (columns.size < 2) return FloatArray(0)
    val offsets = FloatArray(columns.size - 1)
    var offset = 0f
    for (index in offsets.indices) {
        offset += columns[index].width
        offsets[index] = offset
    }
    return offsets
}

internal fun calculateLazyRowWindow(
    bodyHeight: Float,
    rowHeight: Float,
    maxWindow: Int = TableSpec.LAZY_ROW_WINDOW,
): Int {
    require(bodyHeight.isFinite() && bodyHeight > 0f) {
        "Table body height must be finite and greater than zero"
    }
    require(rowHeight.isFinite() && rowHeight > 0f) {
        "Table row height must be finite and greater than zero"
    }
    require(maxWindow >= MIN_LAZY_ROW_WINDOW) {
        "Table lazy row window must be at least $MIN_LAZY_ROW_WINDOW"
    }

    val visibleRows = ceil(bodyHeight.toDouble() / rowHeight.toDouble())
    val coverageWindow = ceil(visibleRows * 1.5)
    val preferredWindow = ceil((visibleRows + TABLE_PRELOAD_ROW_COUNT) * 1.5)
    val effectiveMaximum = maxOf(maxWindow.toDouble(), coverageWindow)
    return preferredWindow
        .coerceIn(MIN_LAZY_ROW_WINDOW.toDouble(), effectiveMaximum)
        .coerceAtMost(Int.MAX_VALUE.toDouble())
        .toInt()
}

public fun <RowT> ViewContainer<*, *>.KuiklyTable(
    spec: TableSpec<RowT>,
    viewportHeight: Float,
    metrics: TableMetrics = TableMetrics(),
    style: TableStyleOptions? = null,
    init: KuiklyTableView<RowT>.() -> Unit = {},
) {
    val effectiveSpec = style?.let(spec::withStyle) ?: spec
    require(viewportHeight > effectiveSpec.header.height) {
        "Table viewportHeight must be greater than header height"
    }
    addChild(KuiklyTableView(effectiveSpec, metrics, viewportHeight)) {
        attr {
            height(viewportHeight)
            alignSelfStretch()
        }
        init()
    }
}

/**
 * Compose-style convenience entry point. It creates the specification and mounts
 * the table in one expression while keeping the style as an explicit parameter.
 */
public fun <RowT> ViewContainer<*, *>.KuiklyTable(
    rows: ObservableList<RowT>,
    viewportHeight: Float,
    style: TableStyleOptions = TableStyleOptions(),
    metrics: TableMetrics = TableMetrics(),
    configure: TableSpecBuilder<RowT>.() -> Unit,
    init: KuiklyTableView<RowT>.() -> Unit = {},
) {
    KuiklyTable(
        spec = tableSpec {
            rows(rows)
            style(style)
            configure()
        },
        viewportHeight = viewportHeight,
        metrics = metrics,
        init = init,
    )
}

private fun ContainerAttr.applyHorizontalAlignment(alignment: TableAlignment) {
    when (alignment) {
        TableAlignment.Start -> justifyContentFlexStart()
        TableAlignment.Center -> justifyContentCenter()
        TableAlignment.End -> justifyContentFlexEnd()
    }
}

private fun com.tencent.kuikly.core.views.TextAttr.applyTextAlignment(alignment: TableAlignment) {
    when (alignment) {
        TableAlignment.Start -> textAlignLeft()
        TableAlignment.Center -> textAlignCenter()
        TableAlignment.End -> textAlignRight()
    }
}

private fun com.tencent.kuikly.core.views.TextAttr.applyHeaderTypography(style: TableHeaderStyle) {
    when (style) {
        TableHeaderStyle.Filled -> {
            fontSize(14f)
            fontWeightSemiBold()
        }
        TableHeaderStyle.Plain -> {
            fontSize(13f)
            fontWeightNormal()
        }
        TableHeaderStyle.Accent -> {
            fontSize(15f)
            fontWeightBold()
        }
        TableHeaderStyle.Dark -> {
            fontSize(14f)
            fontWeightBold()
        }
    }
}
