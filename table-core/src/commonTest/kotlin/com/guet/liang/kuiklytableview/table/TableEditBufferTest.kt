package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.reactive.collection.ObservableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableEditBufferTest {
    private data class Person(val id: Int, val name: String)

    private val spec = tableSpec<Person> {
        rows(ObservableList(mutableListOf(Person(1, "Ada"), Person(2, "Grace"))))
        columns {
            column("name", "Name") {
                value { it.name }
                onEdit { }
            }
        }
    }

    private fun editContext(rowIndex: Int, oldValue: String, newValue: String) =
        TableEditContext(
            row = Person(rowIndex + 1, newValue),
            rowIndex = rowIndex,
            column = spec.columns[0],
            columnIndex = 0,
            oldValue = oldValue,
            newValue = newValue,
        )

    @Test
    fun stagesEditsPerCell() {
        val buffer = TableEditBuffer<Person>()
        buffer.stage(editContext(rowIndex = 0, oldValue = "Ada", newValue = "Ada Lovelace"))
        buffer.stage(editContext(rowIndex = 1, oldValue = "Grace", newValue = "Grace Hopper"))

        assertEquals(2, buffer.size)
        assertEquals("Ada", buffer.snapshot()[0].oldValue)
        assertEquals("Ada Lovelace", buffer.snapshot()[0].newValue)
    }

    @Test
    fun mergesRepeatedEditsOfSameCellKeepingOriginalValue() {
        val buffer = TableEditBuffer<Person>()
        buffer.stage(editContext(rowIndex = 0, oldValue = "Ada", newValue = "A"))
        buffer.stage(editContext(rowIndex = 0, oldValue = "A", newValue = "Ada Lovelace"))

        assertEquals(1, buffer.size)
        val edit = buffer.snapshot().single()
        assertEquals("Ada", edit.oldValue)
        assertEquals("Ada Lovelace", edit.newValue)
    }

    @Test
    fun revertingToOriginalValueRemovesStagedEdit() {
        val buffer = TableEditBuffer<Person>()
        buffer.stage(editContext(rowIndex = 0, oldValue = "Ada", newValue = "A"))
        buffer.stage(editContext(rowIndex = 0, oldValue = "A", newValue = "Ada"))

        assertTrue(buffer.isEmpty())
    }

    @Test
    fun stagedValueReturnsLatestValuePerCell() {
        val buffer = TableEditBuffer<Person>()
        buffer.stage(editContext(rowIndex = 0, oldValue = "Ada", newValue = "Ada Lovelace"))

        assertEquals("Ada Lovelace", buffer.stagedValue(0, "name"))
        assertEquals(null, buffer.stagedValue(1, "name"))
        assertEquals(null, buffer.stagedValue(0, "other"))
    }

    @Test
    fun stableRowKeyKeepsEditAttachedWhenIndexIsReused() {
        val buffer = TableEditBuffer<Person>()
        buffer.stage(
            TableEditContext(
                row = Person(42, "Ada"),
                rowIndex = 0,
                column = spec.columns[0],
                columnIndex = 0,
                oldValue = "Ada",
                newValue = "Ada Lovelace",
                rowKey = "42",
            ),
        )

        assertEquals("Ada Lovelace", buffer.stagedValue("42", "name"))
        assertEquals(null, buffer.stagedValue(1, "name"))
    }

    @Test
    fun drainReturnsAndClearsStagedEdits() {
        val buffer = TableEditBuffer<Person>()
        buffer.stage(editContext(rowIndex = 0, oldValue = "Ada", newValue = "A"))

        val drained = buffer.drain()

        assertEquals(1, drained.size)
        assertTrue(buffer.isEmpty())
        assertTrue(buffer.drain().isEmpty())
    }

    @Test
    fun notifiesObserversOnEveryChange() {
        val buffer = TableEditBuffer<Person>()
        val counts = mutableListOf<Int>()
        buffer.observe { counts += it }

        buffer.stage(editContext(rowIndex = 0, oldValue = "Ada", newValue = "A"))
        buffer.stage(editContext(rowIndex = 1, oldValue = "Grace", newValue = "G"))
        buffer.clear()

        assertEquals(listOf(1, 2, 0), counts)
    }
}
