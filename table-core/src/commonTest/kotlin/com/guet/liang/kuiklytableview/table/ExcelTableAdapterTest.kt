package com.guet.liang.kuiklytableview.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExcelTableAdapterTest {
    @Test
    fun usesHeaderRowAndNormalizesRaggedRows() {
        val sheet = ExcelSheet(
            name = "Users",
            cells = listOf(
                listOf("Name", "Age"),
                listOf("Ada", "36"),
                listOf("Grace"),
            ),
        )

        val spec = sheet.toTableSpec()

        assertEquals(listOf("Name", "Age"), spec.columns.map { it.title })
        assertEquals(2, spec.rows.size)
        assertEquals("Ada", spec.columns[0].value(spec.rows[0]))
        assertEquals("", spec.columns[1].value(spec.rows[1]))
        assertEquals(2, spec.rows[1].sheetRowIndex)
    }

    @Test
    fun supportsHeaderlessSheetsAndExcelColumnNames() {
        val cells = List(28) { index -> "value-$index" }
        val sheet = ExcelSheet("Wide", listOf(cells))

        val spec = sheet.toTableSpec(ExcelTableOptions(headerRowIndex = null))

        assertEquals(28, spec.columns.size)
        assertEquals("A", spec.columns[0].title)
        assertEquals("Z", spec.columns[25].title)
        assertEquals("AA", spec.columns[26].title)
        assertEquals("AB", spec.columns[27].title)
        assertEquals("value-27", spec.columns[27].value(spec.rows.single()))
    }

    @Test
    fun selectsWorkbookSheetAndAllowsStyleConfiguration() {
        val workbook = ExcelWorkbook(
            listOf(
                ExcelSheet("First", listOf(listOf("A"), listOf("1"))),
                ExcelSheet("Second", listOf(listOf("B"), listOf("2"))),
            ),
        )

        val spec = workbook.toTableSpec(sheetIndex = 1) {
            rowHeight = 52f
        }

        assertEquals("B", spec.columns.single().title)
        assertEquals("2", spec.columns.single().value(spec.rows.single()))
        assertEquals(52f, spec.style.rowHeight)
    }

    @Test
    fun rejectsEmptySheetsAndInvalidSheetIndexes() {
        assertFailsWith<IllegalArgumentException> {
            ExcelSheet("Empty", emptyList()).toTableSpec()
        }

        val workbook = ExcelWorkbook(listOf(ExcelSheet("Only", listOf(listOf("A")))))
        assertFailsWith<IllegalArgumentException> {
            workbook.toTableSpec(sheetIndex = 1)
        }
    }
}
