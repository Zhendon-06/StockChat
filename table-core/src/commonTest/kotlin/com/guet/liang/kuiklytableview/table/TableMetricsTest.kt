package com.guet.liang.kuiklytableview.table

import kotlin.test.Test
import kotlin.test.assertEquals

class TableMetricsTest {
    @Test
    fun recordsFirstScreenCreationAndUpdateRedraws() {
        val metrics = TableMetrics()

        metrics.onRowCreated(rowIndex = 0, cellCount = 30)
        metrics.onRowCreated(rowIndex = 1, cellCount = 30)
        metrics.recordFirstScreenRender(12.5)

        var snapshot = metrics.snapshot()
        assertEquals(12.5, snapshot.firstScreenRenderMs)
        assertEquals(2, snapshot.activeRows)
        assertEquals(60, snapshot.activeCells)

        metrics.markRowsChanged(listOf(1))
        metrics.onRowDisposed(cellCount = 30)
        metrics.onRowCreated(rowIndex = 1, cellCount = 30)
        snapshot = metrics.snapshot()

        assertEquals(1, snapshot.lastUpdateRedrawnRows)
        assertEquals(30, snapshot.lastUpdateRedrawnCells)
        assertEquals(3, snapshot.totalRowsCreated)
        assertEquals(90, snapshot.totalCellsCreated)
    }
}
