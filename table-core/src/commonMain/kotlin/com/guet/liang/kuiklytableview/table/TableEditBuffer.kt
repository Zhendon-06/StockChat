package com.guet.liang.kuiklytableview.table

/**
 * 一条暂存的单元格修改。
 *
 * 同一单元格被多次编辑时只保留一条记录：[oldValue] 始终是首次编辑前的原值，
 * [newValue] 是最近一次提交的值，[row] 是最近一次提交时的行数据。
 */
public data class TableStagedEdit<RowT>(
    val rowIndex: Int,
    val columnId: String,
    val row: RowT,
    val oldValue: String,
    val newValue: String,
    val rowKey: String = rowIndex.toString(),
)

/**
 * 表格编辑暂存区：单元格编辑提交后先进入暂存区，不直接视为已保存。
 *
 * - [stage] 由 `onEdit` 回调转入，同一单元格多次编辑自动合并；
 *   改回原值时该暂存记录自动撤销。
 * - [snapshot] 只读查看当前暂存内容；[drain] 取走全部暂存并清空，
 *   供后续保存链路消费；[clear] 直接丢弃。
 * - [observe] 订阅暂存数量变化，便于页面展示"已暂存 N 处修改"。
 */
public class TableEditBuffer<RowT> {
    private val edits = LinkedHashMap<CellKey, TableStagedEdit<RowT>>()
    private val listeners = mutableListOf<(count: Int) -> Unit>()

    public val size: Int get() = edits.size

    public fun isEmpty(): Boolean = edits.isEmpty()

    public fun stage(context: TableEditContext<RowT>) {
        val key = cellKey(context.rowKey, context.column.id)
        val originalValue = edits[key]?.oldValue ?: context.oldValue
        if (context.newValue == originalValue) {
            edits.remove(key)
        } else {
            edits[key] = TableStagedEdit(
                rowIndex = context.rowIndex,
                columnId = context.column.id,
                row = context.row,
                oldValue = originalValue,
                newValue = context.newValue,
                rowKey = context.rowKey,
            )
        }
        notifyListeners()
    }

    /** 某单元格当前的暂存值；未被编辑过（或已改回原值）时返回 null。 */
    public fun stagedValue(rowIndex: Int, columnId: String): String? =
        stagedValue(rowIndex.toString(), columnId)

    /** Returns the staged value for a stable row key and column id. */
    public fun stagedValue(rowKey: String, columnId: String): String? =
        edits[cellKey(rowKey, columnId)]?.newValue

    public fun snapshot(): List<TableStagedEdit<RowT>> = edits.values.toList()

    public fun drain(): List<TableStagedEdit<RowT>> {
        val drained = snapshot()
        if (drained.isNotEmpty()) {
            edits.clear()
            notifyListeners()
        }
        return drained
    }

    public fun clear() {
        if (edits.isEmpty()) {
            return
        }
        edits.clear()
        notifyListeners()
    }

    public fun observe(listener: (count: Int) -> Unit) {
        listeners += listener
    }

    private fun notifyListeners() {
        val count = edits.size
        listeners.forEach { it(count) }
    }

    private data class CellKey(val rowKey: String, val columnId: String)

    private fun cellKey(rowKey: String, columnId: String): CellKey = CellKey(rowKey, columnId)
}
