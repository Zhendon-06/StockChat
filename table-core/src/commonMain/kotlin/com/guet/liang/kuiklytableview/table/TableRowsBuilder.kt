package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList

/** Row identity, staged edits, and empty content shared by table specifications. */
public open class TableRowsBuilder<RowT> {
    protected var rows: ObservableList<RowT>? = null
    protected var editBuffer: TableEditBuffer<RowT>? = null
    protected var rowKeyProvider: ((RowT) -> String)? = null
    protected var emptyStateRenderer: TableEmptyStateRenderer? = null

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

    /** Renders content in the viewport when the data source is empty. */
    public fun emptyState(renderer: TableEmptyStateRenderer) {
        emptyStateRenderer = renderer
    }

}
