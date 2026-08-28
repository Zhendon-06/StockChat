package com.guet.liang.kuiklytableview.table

public data class TableMetricsSnapshot(
    val firstScreenRenderMs: Double?,
    val activeRows: Int,
    val activeCells: Int,
    val totalRowsCreated: Int,
    val totalCellsCreated: Int,
    val lastUpdateRedrawnRows: Int,
    val lastUpdateRedrawnCells: Int,
)

public class TableMetrics {
    private val pendingUpdatedRows = mutableSetOf<Int>()
    private var listener: ((TableMetricsSnapshot) -> Unit)? = null
    private var firstScreenRenderMs: Double? = null
    private var activeRows = 0
    private var activeCells = 0
    private var totalRowsCreated = 0
    private var totalCellsCreated = 0
    private var lastUpdateRedrawnRows = 0
    private var lastUpdateRedrawnCells = 0

    public fun observe(listener: ((TableMetricsSnapshot) -> Unit)?) {
        this.listener = listener
        listener?.invoke(snapshot())
    }

    public fun markRowsChanged(rowIndices: Collection<Int>) {
        pendingUpdatedRows.clear()
        pendingUpdatedRows.addAll(rowIndices)
        lastUpdateRedrawnRows = 0
        lastUpdateRedrawnCells = 0
        notifyChanged()
    }

    public fun recordFirstScreenRender(firstPaintCostMs: Double) {
        require(firstPaintCostMs >= 0.0) { "First screen render time must be non-negative" }
        firstScreenRenderMs = firstPaintCostMs
        notifyChanged()
    }

    public fun snapshot(): TableMetricsSnapshot = TableMetricsSnapshot(
        firstScreenRenderMs = firstScreenRenderMs,
        activeRows = activeRows,
        activeCells = activeCells,
        totalRowsCreated = totalRowsCreated,
        totalCellsCreated = totalCellsCreated,
        lastUpdateRedrawnRows = lastUpdateRedrawnRows,
        lastUpdateRedrawnCells = lastUpdateRedrawnCells,
    )

    internal fun onRowCreated(rowIndex: Int, cellCount: Int) {
        activeRows += 1
        activeCells += cellCount
        totalRowsCreated += 1
        totalCellsCreated += cellCount
        if (pendingUpdatedRows.remove(rowIndex)) {
            lastUpdateRedrawnRows += 1
            lastUpdateRedrawnCells += cellCount
        }
        notifyChanged()
    }

    internal fun onRowDisposed(cellCount: Int) {
        activeRows = (activeRows - 1).coerceAtLeast(0)
        activeCells = (activeCells - cellCount).coerceAtLeast(0)
        notifyChanged()
    }

    private fun notifyChanged() {
        listener?.invoke(snapshot())
    }
}
